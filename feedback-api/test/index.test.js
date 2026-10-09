import test from "node:test";
import assert from "node:assert/strict";
import worker, { MAX_REPORT_BYTES, REPORT_TTL_SECONDS } from "../src/index.js";

function makeZip(payload = [0x50, 0x4b, 0x03, 0x04, 0x01, 0x02]) {
  return new Uint8Array(payload);
}

function makeEnv({ throttled = false, fail = false } = {}) {
  const values = new Map();
  const writes = [];
  const env = {
    REPORTS: {
      async get(key, options) {
        assert.deepEqual(options, { type: "arrayBuffer" });
        return values.get(key) ?? null;
      },
      async put(key, value, options) {
        if (fail) throw new Error("storage error detail must not escape");
        values.set(key, value.slice().buffer);
        writes.push({ key, options });
      },
    },
    SUBMISSION_LIMITER: {
      async limit(input) {
        assert.deepEqual(input, { key: "all-report-submissions" });
        return { success: !throttled };
      },
    },
  };
  return { env, values, writes };
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
