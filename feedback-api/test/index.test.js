import test from "node:test";
import assert from "node:assert/strict";
import worker, { ReportReceipt } from "../src/index.js";
import { MAX_REPORT_BYTES } from "../src/limits.js";
import { REPORT_TTL_SECONDS } from "../src/retention.js";

function makeZip(payload = [0x50, 0x4b, 0x03, 0x04, 0x01, 0x02]) {
  return new Uint8Array(payload);
}

function makeEnv({ throttled = false, limiterError = false, fail = false, failTrainingPut = false, failDelete = false, pauseKvPut = false, staleNegative = false, staleList = false } = {}) {
  const values = new Map();
  const expirations = new Map();
  const writes = [];
  const trainingValues = new Map();
  const trainingWrites = [];
  const failures = { failTrainingPut, failDelete };
  const calls = { kvGet: 0, kvPut: 0, kvDelete: 0, r2Put: 0, r2Head: 0, r2Delete: 0 };
  let markKvPutStarted;
  let releaseKvPut;
  const kvPutStarted = new Promise((resolve) => { markKvPutStarted = resolve; });
  const kvPutGate = new Promise((resolve) => { releaseKvPut = resolve; });
  const objects = new Map();
  const objectStorage = new Map();
  const env = {
    REPORTS: {
      async get(key, options) {
        calls.kvGet++;
        if (options.type === "stream") {
          const value = values.get(key);
          return value ? new ReadableStream({ start(controller) { controller.close(); } }) : null;
        }
        assert.deepEqual(options, { type: "arrayBuffer" });
        if (staleNegative) return null;
        return values.get(key) ?? null;
      },
      async put(key, value, options) {
        calls.kvPut++;
        if (fail) throw new Error("storage error detail must not escape");
        if (pauseKvPut) {
          markKvPutStarted();
          await kvPutGate;
        }
        values.set(key, value.slice().buffer);
        expirations.set(key, options.expiration);
        writes.push({ key, options });
      },
      async delete(key) {
        calls.kvDelete++;
        if (failures.failDelete === true || failures.failDelete === "kv") throw new Error("delete error");
        values.delete(key);
        expirations.delete(key);
      },
      async list({ prefix }) {
        if (staleList) return { keys: [], list_complete: true, cursor: "" };
        return {
          keys: [...values.keys()].filter((key) => key.startsWith(prefix)).map((name) => ({ name, expiration: expirations.get(name) })),
          list_complete: true,
          cursor: "",
        };
      },
    },
    TRAINING_EXAMPLES: {
      async put(key, value) {
        calls.r2Put++;
        if (failures.failTrainingPut) throw new Error("training storage error");
        trainingValues.set(key, value.slice());
        trainingWrites.push(key);
      },
      async head(key) { calls.r2Head++; return trainingValues.has(key) ? { key } : null; },
      async delete(key) {
        calls.r2Delete++;
        if (failures.failDelete === true || failures.failDelete === "r2") throw new Error("delete error");
        trainingValues.delete(key);
      },
    },
    SUBMISSION_LIMITER: {
      async limit(input) {
        assert.deepEqual(input, { key: "all-report-submissions" });
        if (limiterError) throw new Error("limiter unavailable");
        return { success: !throttled };
      },
    },
    REPORT_RECEIPTS: {
      idFromName(name) { return name; },
      get(id) {
        if (!objects.has(id)) {
          if (!objectStorage.has(id)) objectStorage.set(id, new Map());
          const storageValues = objectStorage.get(id);
          const state = {
            storage: {
              async get(key) { return storageValues.get(key); },
              async put(key, value) { storageValues.set(key, value); },
            },
          };
          objects.set(id, new ReportReceipt(state, env));
        }
        const object = objects.get(id);
        return { fetch: (request) => object.fetch(request) };
      },
    },
  };
  return {
    env, values, expirations, writes, trainingValues, trainingWrites, failures, calls, kvPutStarted, releaseKvPut,
    restart(id) { objects.delete(id); },
    durableState(id, key) { return objectStorage.get(id)?.get(key); },
    forgetDurableState(id, key) { objectStorage.get(id)?.delete(key); },
  };
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
  assert.deepEqual(writes, [{ key: `reports/${result.receipt}.zip`, options: { expiration: writes[0].options.expiration } }]);
  assert.ok(writes[0].options.expiration > Math.floor(Date.now() / 1000));
  assert.ok(writes[0].options.expiration <= Math.floor(Date.now() / 1000) + REPORT_TTL_SECONDS + 1);
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

test("persists the original absolute expiry across stale-negative KV reads and restarts", async () => {
  const state = makeEnv({ staleNegative: true });
  const { env, writes } = state;
  const body = makeZip();
  const first = await worker.fetch(post(body), env);
  assert.equal(first.status, 201);
  const { receipt } = await first.clone().json();
  const originalExpiry = writes[0].options.expiration;
  // Simulate a restart after KV accepted its write but before the DO persisted
  // the kvStored marker. The remote read then remains stale-negative.
  state.forgetDurableState(receipt, "kvStored");
  state.restart(receipt);
  const retry = await worker.fetch(post(body), env);
  assert.equal(retry.status, 201);
  assert.equal(writes.length, 2);
  assert.equal(writes[0].options.expiration, originalExpiry);
  assert.equal(writes[1].options.expiration, originalExpiry);
  assert.equal(state.durableState(receipt, "analysisDeleteBy"), originalExpiry);
});

test("imports the existing absolute expiry for a legacy KV report without downloading its contents", async () => {
  const state = makeEnv({ staleNegative: true });
  const body = makeZip();
  const receipt = await crypto.subtle.digest("SHA-256", body).then((digest) =>
    [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join(""));
  const key = `reports/${receipt}.zip`;
  const oldExpiry = Math.floor(Date.now() / 1000) + 86400;
  state.values.set(key, body.slice().buffer);
  state.expirations.set(key, oldExpiry);
  const response = await worker.fetch(post(body), state.env);
  assert.equal(response.status, 200);
  assert.equal(state.writes.length, 0);
  assert.equal(state.durableState(receipt, "analysisDeleteBy"), oldExpiry);
  assert.equal(state.durableState(receipt, "kvStored"), true);
});

test("fails closed when a legacy key is listed without an absolute expiry", async () => {
  const state = makeEnv();
  const body = makeZip();
  const receipt = await crypto.subtle.digest("SHA-256", body).then((digest) =>
    [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join(""));
  state.values.set(`reports/${receipt}.zip`, body.slice().buffer);
  const response = await worker.fetch(post(body), state.env);
  assert.equal(response.status, 503);
  assert.equal(state.writes.length, 0);
});

test("preserves expiry and completes a partial consented upload after object restart", async () => {
  const state = makeEnv({ failTrainingPut: true });
  const body = makeZip();
  const failed = await worker.fetch(post(body, { "X-Sudoku-Training-Consent": "yes" }), state.env);
  assert.equal(failed.status, 503);
  assert.equal(state.writes.length, 1);
  const originalExpiry = state.writes[0].options.expiration;
  const receipt = await crypto.subtle.digest("SHA-256", body).then((digest) =>
    [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join(""));

  state.restart(receipt);
  state.failures.failTrainingPut = false;
  const retry = await worker.fetch(post(body, { "X-Sudoku-Training-Consent": "yes" }), state.env);
  assert.equal(retry.status, 200);
  assert.equal(state.writes.length, 1);
  assert.equal(state.writes[0].options.expiration, originalExpiry);
  assert.equal(state.trainingValues.size, 1);
  assert.equal(state.durableState(receipt, "analysisDeleteBy"), originalExpiry);
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
  const state = makeEnv();
  const { env, values, trainingValues, failures } = state;
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
  state.restart(receipt);
  const retry = await worker.fetch(new Request(`https://x/v1/reports/${receipt}`, { method: "DELETE" }), env);
  assert.equal(retry.status, 200);
  assert.deepEqual(await retry.json(), { receipt, status: "deleted" });
  assert.equal(values.size, 0);
  assert.equal(trainingValues.size, 0);
});

test("rate-limits valid deletion before reading or changing either store", async () => {
  const receipt = "b".repeat(64);
  const throttled = makeEnv({ throttled: true });
  const request = () => new Request(`https://x/v1/reports/${receipt}`, { method: "DELETE" });
  const limited = await worker.fetch(request(), throttled.env);
  assert.equal(limited.status, 429);
  assert.deepEqual(throttled.calls, { kvGet: 0, kvPut: 0, kvDelete: 0, r2Put: 0, r2Head: 0, r2Delete: 0 });

  const unavailable = makeEnv({ limiterError: true });
  const failed = await worker.fetch(request(), unavailable.env);
  assert.equal(failed.status, 503);
  assert.deepEqual(await failed.json(), { error: "submission_temporarily_unavailable" });
  assert.deepEqual(unavailable.calls, { kvGet: 0, kvPut: 0, kvDelete: 0, r2Put: 0, r2Head: 0, r2Delete: 0 });
});

test("serializes a delete behind an in-flight consented write and keeps a durable tombstone", async () => {
  const { env, values, trainingValues, calls, kvPutStarted, releaseKvPut } = makeEnv({ pauseKvPut: true });
  const body = makeZip();
  const upload = worker.fetch(post(body, { "X-Sudoku-Training-Consent": "yes" }), env);
  await kvPutStarted;
  const receipt = await crypto.subtle.digest("SHA-256", body).then((digest) =>
    [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join(""));
  const deletion = worker.fetch(new Request(`https://x/v1/reports/${receipt}`, { method: "DELETE" }), env);
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(calls.r2Head, 0);
  assert.equal(calls.kvDelete, 0);
  assert.equal(calls.r2Delete, 0);

  releaseKvPut();
  const [uploadResponse, deleteResponse] = await Promise.all([upload, deletion]);
  assert.equal(uploadResponse.status, 201);
  assert.equal(deleteResponse.status, 200);
  assert.deepEqual(await deleteResponse.json(), { receipt, status: "deleted" });
  assert.equal(values.size, 0);
  assert.equal(trainingValues.size, 0);

  const retry = await worker.fetch(post(body, { "X-Sudoku-Training-Consent": "yes" }), env);
  assert.equal(retry.status, 410);
  assert.equal(trainingValues.size, 0);
});
