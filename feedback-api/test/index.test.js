import test from "node:test";
import assert from "node:assert/strict";
import worker, { MAX_REPORT_BYTES, REPORT_TTL_SECONDS } from "../src/index.js";

function makeZip(payload = [0x50, 0x4b, 0x03, 0x04, 0x01, 0x02]) {
  return new Uint8Array(payload);
}

function makeEnv({ throttled = false, fail = false, failTrainingPut = false, failDelete = false } = {}) {
  const values = new Map();
  const writes = [];
  const trainingValues = new Map();
  const trainingWrites = [];
  const failures = { failTrainingPut, failDelete };
  const env = {
    REPORTS: {
      async get(key, options) {
        if (options.type === "stream") {
          const value = values.get(key);
          return value ? new ReadableStream({ start(controller) { controller.close(); } }) : null;
        }
        assert.deepEqual(options, { type: "arrayBuffer" });
        return values.get(key) ?? null;
      },
      async put(key, value, options) {
        if (fail) throw new Error("storage error detail must not escape");
        values.set(key, value.slice().buffer);
        writes.push({ key, options });
      },
      async delete(key) {
        if (failures.failDelete === true || failures.failDelete === "kv") throw new Error("delete error");
        values.delete(key);
      },
    },
    TRAINING_EXAMPLES: {
      async put(key, value) {
        if (failures.failTrainingPut) throw new Error("training storage error");
        trainingValues.set(key, value.slice());
        trainingWrites.push(key);
      },
      async head(key) { return trainingValues.has(key) ? { key } : null; },
      async delete(key) {
        if (failures.failDelete === true || failures.failDelete === "r2") throw new Error("delete error");
        trainingValues.delete(key);
      },
    },
    SUBMISSION_LIMITER: {
      async limit(input) {
        assert.deepEqual(input, { key: "all-report-submissions" });
        return { success: !throttled };
      },
    },
  };
  return { env, values, writes, trainingValues, trainingWrites, failures };
}

function post(body, headers = {}) {
  return new Request("https://example.test/v1/reports", {
    method: "POST",
    headers: {
      "content-type": "application/zip",
      "content-length": String(body.byteLength),
      ...headers,
    },
    body,
  });
}

test("accepts a ZIP, returns a stable receipt, and applies a 90-day expiry", async () => {
  const { env, writes } = makeEnv();
  const body = makeZip();
  const response = await worker.fetch(post(body), env);
  const result = await response.json();

  assert.equal(response.status, 201);
  assert.equal(result.status, "accepted");
  assert.match(result.receipt, /^[a-f0-9]{64}$/);
  assert.deepEqual(writes, [{ key: `reports/${result.receipt}.zip`, options: { expirationTtl: REPORT_TTL_SECONDS } }]);
  assert.equal(REPORT_TTL_SECONDS, 90 * 24 * 60 * 60);
});

test("deduplicates identical reports without a second write", async () => {
  const { env, writes } = makeEnv();
  const first = await worker.fetch(post(makeZip()), env);
  const firstJson = await first.json();
  const duplicate = await worker.fetch(post(makeZip()), env);
  const duplicateJson = await duplicate.json();

  assert.equal(duplicate.status, 200);
  assert.equal(duplicateJson.status, "already_received");
  assert.equal(duplicateJson.receipt, firstJson.receipt);
  assert.equal(writes.length, 1);
});

test("rejects wrong routes, methods, MIME types, missing length, and non-ZIP data", async () => {
  const { env } = makeEnv();
  assert.equal((await worker.fetch(new Request("https://x/"), env)).status, 404);
  assert.equal((await worker.fetch(new Request("https://x/v1/reports"), env)).status, 405);
  assert.equal((await worker.fetch(post(makeZip(), { "content-type": "application/octet-stream" }), env)).status, 415);

  const missingLength = new Request("https://x/v1/reports", {
    method: "POST",
    headers: { "content-type": "application/zip" },
    body: makeZip(),
  });
  missingLength.headers.delete("content-length");
  assert.equal((await worker.fetch(missingLength, env)).status, 411);
  assert.equal((await worker.fetch(post(new Uint8Array([1, 2, 3, 4])), env)).status, 415);
});

test("rejects declared oversize before reading the body", async () => {
  const { env, writes } = makeEnv();
  const response = await worker.fetch(
    new Request("https://x/v1/reports", {
      method: "POST",
      headers: { "content-type": "application/zip", "content-length": String(MAX_REPORT_BYTES + 1) },
      body: makeZip(),
    }),
    env,
  );
  assert.equal(response.status, 413);
  assert.equal(writes.length, 0);
});

test("rejects mismatched lengths and does not persist the report", async () => {
  const { env, writes } = makeEnv();
  const body = makeZip();
  const response = await worker.fetch(post(body, { "content-length": String(body.byteLength + 1) }), env);
  assert.equal(response.status, 400);
  assert.equal(writes.length, 0);
});

test("stops reading a streaming body as soon as it exceeds the declared length", async () => {
  const { env, writes } = makeEnv();
  let cancelled = false;
  const request = new Request("https://x/v1/reports", {
    method: "POST",
    headers: { "content-type": "application/zip", "content-length": "4" },
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(makeZip());
        controller.enqueue(new Uint8Array([1, 2, 3, 4]));
      },
      cancel() { cancelled = true; },
    }),
    duplex: "half",
  });
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 400);
  assert.equal(cancelled, true);
  assert.equal(writes.length, 0);
});

test("applies the coarse shared rate limit without an IP-derived key", async () => {
  const { env, writes } = makeEnv({ throttled: true });
  const response = await worker.fetch(post(makeZip()), env);
  assert.equal(response.status, 429);
  assert.equal(response.headers.get("retry-after"), "60");
  assert.equal(writes.length, 0);
});

test("does not leak storage errors or log request data", async () => {
  const { env } = makeEnv({ fail: true });
  const response = await worker.fetch(post(makeZip()), env);
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: "submission_temporarily_unavailable" });
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("copies to private training storage only for the exact consent value", async () => {
  for (const value of [undefined, "no", "YES", "true"]) {
    const { env, trainingWrites } = makeEnv();
    const headers = value === undefined ? {} : { "X-Sudoku-Training-Consent": value };
    const response = await worker.fetch(post(makeZip(), headers), env);
    assert.equal(response.status, 201);
    assert.deepEqual(trainingWrites, []);
  }

  const { env, trainingWrites, trainingValues } = makeEnv();
  const body = makeZip();
  const response = await worker.fetch(post(body, { "X-Sudoku-Training-Consent": "yes" }), env);
  const result = await response.json();
  assert.equal(response.status, 201);
  assert.deepEqual(trainingWrites, [result.receipt]);
  assert.deepEqual([...trainingValues.get(result.receipt)], [...body]);
});

test("consented submission fails closed without R2 or when the training copy fails", async () => {
  const noBucket = makeEnv();
  delete noBucket.env.TRAINING_EXAMPLES;
  const response = await worker.fetch(post(makeZip(), { "X-Sudoku-Training-Consent": "yes" }), noBucket.env);
  assert.equal(response.status, 503);
  assert.equal(noBucket.values.size, 0);

  const failing = makeEnv({ failTrainingPut: true });
  const failedResponse = await worker.fetch(post(makeZip(), { "X-Sudoku-Training-Consent": "yes" }), failing.env);
  assert.equal(failedResponse.status, 503);
  assert.deepEqual(await failedResponse.json(), { error: "submission_temporarily_unavailable" });
  // KV remains a normal 90-day analysis report; retrying the same upload can finish R2.
  assert.equal(failing.values.size, 1);
  failing.failures.failTrainingPut = false;
  const retry = await worker.fetch(post(makeZip(), { "X-Sudoku-Training-Consent": "yes" }), failing.env);
  assert.equal(retry.status, 200);
  assert.equal(failing.trainingValues.size, 1);
  assert.equal(failing.writes.length, 1);
});

test("deletes both stores by validated receipt and is idempotent", async () => {
  const { env, values, trainingValues } = makeEnv();
  const body = makeZip();
  const submitted = await worker.fetch(post(body, { "X-Sudoku-Training-Consent": "yes" }), env);
  const { receipt } = await submitted.json();
  const request = () => new Request(`https://example.test/v1/reports/${receipt}`, { method: "DELETE" });

  const deleted = await worker.fetch(request(), env);
  assert.equal(deleted.status, 200);
  assert.deepEqual(await deleted.json(), { receipt, status: "deleted" });
  assert.equal(values.size, 0);
  assert.equal(trainingValues.size, 0);

  const repeated = await worker.fetch(request(), env);
  assert.equal(repeated.status, 200);
  assert.deepEqual(await repeated.json(), { receipt, status: "already_deleted" });
});

test("delete rejects malformed receipts and partial store failure never reports success", async () => {
  const { env, values, trainingValues, failures } = makeEnv();
  const malformed = await worker.fetch(new Request(`https://x/v1/reports/${"a".repeat(63)}g`, { method: "DELETE" }), env);
  assert.equal(malformed.status, 400);
  const wrongMethod = await worker.fetch(new Request(`https://x/v1/reports/${"a".repeat(64)}`, { method: "GET" }), env);
  assert.equal(wrongMethod.status, 405);
  const receipt = "a".repeat(64);
  values.set(`reports/${receipt}.zip`, makeZip().buffer);
  trainingValues.set(receipt, makeZip());
  failures.failDelete = "r2";
  const failed = await worker.fetch(new Request(`https://x/v1/reports/${receipt}`, { method: "DELETE" }), env);
  assert.equal(failed.status, 503);
  assert.deepEqual(await failed.json(), { error: "deletion_incomplete", retry: true });
  failures.failDelete = false;
  const retry = await worker.fetch(new Request(`https://x/v1/reports/${receipt}`, { method: "DELETE" }), env);
  assert.equal(retry.status, 200);
  assert.deepEqual(await retry.json(), { receipt, status: "deleted" });
  assert.equal(values.size, 0);
  assert.equal(trainingValues.size, 0);
});
