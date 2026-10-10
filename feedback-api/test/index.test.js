import test from "node:test";
import assert from "node:assert/strict";
import worker, { ReportReceipt } from "../src/index.js";
import { MAX_REPORT_BYTES } from "../src/limits.js";
function makeZip(payload = [0x50, 0x4b, 3, 4, 1, 2]) {
  return new Uint8Array(payload);
}
function makeEnv({
  throttled = false,
  limiterError = false,
  fail = false,
} = {}) {
  const values = new Map(),
    trainingValues = new Map(),
    metadata = new Map(),
    objects = new Map(),
    states = new Map(),
    writes = [],
    trainingWrites = [];
  const failures = { put: fail, delete: false, kvDelete: false };
  const env = {
    REPORTS: {
      async get(key) {
        return values.get(key) ?? null;
      },
      async delete(key) {
        if (failures.kvDelete) throw Error("private detail");
        values.delete(key);
      },
      async list() {
        return {
          keys: [...values.keys()].map((name) => ({ name })),
          list_complete: true,
        };
      },
    },
    TRAINING_EXAMPLES: {
      async head(key) {
        return trainingValues.has(key)
          ? { key, customMetadata: metadata.get(key) ?? {} }
          : null;
      },
      async put(key, bytes, options) {
        if (failures.put) throw Error("private detail");
        trainingValues.set(key, new Uint8Array(bytes));
        metadata.set(key, options.customMetadata);
        trainingWrites.push(key);
      },
      async delete(key) {
        if (failures.delete) throw Error("private detail");
        trainingValues.delete(key);
      },
    },
    SUBMISSION_LIMITER: {
      async limit(input) {
        assert.deepEqual(input, { key: "all-report-submissions" });
        if (limiterError) throw Error("private detail");
        return { success: !throttled };
      },
    },
    REPORT_RECEIPTS: {
      idFromName(name) {
        return name;
      },
      get(id) {
        if (!objects.has(id)) {
          if (!states.has(id)) states.set(id, new Map());
          const m = states.get(id);
          objects.set(
            id,
            new ReportReceipt(
              {
                storage: {
                  async get(k) {
                    return m.get(k);
                  },
                  async put(k, v) {
                    m.set(k, v);
                  },
                },
              },
              env,
            ),
          );
        }
        return { fetch: (r) => objects.get(id).fetch(r) };
      },
    },
  };
  return {
    env,
    values,
    trainingValues,
    metadata,
    trainingWrites,
    writes,
    failures,
    restart(id) {
      objects.delete(id);
    },
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
function deletion(receipt) {
  return new Request(`https://example.test/v1/reports/${receipt}`, {
    method: "DELETE",
  });
}
async function receiptOf(bytes) {
  return [...new Uint8Array(await crypto.subtle.digest("SHA-256", bytes))]
    .map((x) => x.toString(16).padStart(2, "0"))
    .join("");
}
test("rejects wrong routes, methods, MIME types, missing length, and non-ZIP data", async () => {
  const { env } = makeEnv();
  assert.equal(
    (await worker.fetch(new Request("https://x/"), env)).status,
    404,
  );
  assert.equal(
    (await worker.fetch(new Request("https://x/v1/reports"), env)).status,
    405,
  );
  assert.equal(
    (
      await worker.fetch(
        post(makeZip(), { "content-type": "application/octet-stream" }),
        env,
      )
    ).status,
    415,
  );

  const missingLength = new Request("https://x/v1/reports", {
    method: "POST",
    headers: { "content-type": "application/zip" },
    body: makeZip(),
  });
  missingLength.headers.delete("content-length");
  assert.equal((await worker.fetch(missingLength, env)).status, 411);
  assert.equal(
    (await worker.fetch(post(new Uint8Array([1, 2, 3, 4])), env)).status,
    415,
  );
});

test("rejects declared oversize before reading the body", async () => {
  const { env, writes } = makeEnv();
  const response = await worker.fetch(
    new Request("https://x/v1/reports", {
      method: "POST",
      headers: {
        "content-type": "application/zip",
        "content-length": String(MAX_REPORT_BYTES + 1),
      },
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
  const response = await worker.fetch(
    post(body, { "content-length": String(body.byteLength + 1) }),
    env,
  );
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
      cancel() {
        cancelled = true;
      },
    }),
    duplex: "half",
  });
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 400);
  assert.equal(cancelled, true);
  assert.equal(writes.length, 0);
});

test("one R2 report has no expiry or KV copy and survives restart/retry", async () => {
  const s = makeEnv(),
    body = makeZip();
  const first = await worker.fetch(
    post(body, { "X-Sudoku-Training-Consent": "yes" }),
    s.env,
  );
  assert.equal(first.status, 201);
  const { receipt } = await first.json();
  assert.equal(s.values.size, 0);
  assert.equal(s.trainingValues.size, 1);
  assert.deepEqual(s.metadata.get(receipt), { trainingConsent: "true" });
  s.restart(receipt);
  const retry = await worker.fetch(
    post(body, { "X-Sudoku-Training-Consent": "yes" }),
    s.env,
  );
  assert.equal(retry.status, 200);
  assert.equal(s.trainingWrites.length, 1);
});
test("analysis-only clients retain one report without training permission", async () => {
  for (const consent of [undefined, "YES", "no"]) {
    const s = makeEnv();
    const r = await worker.fetch(
      post(makeZip(), consent ? { "X-Sudoku-Training-Consent": consent } : {}),
      s.env,
    );
    assert.equal(r.status, 201);
    const { receipt } = await r.json();
    assert.equal(s.metadata.get(receipt).trainingConsent, "false");
    assert.equal(s.values.size, 0);
  }
});
test("explicit consent can upgrade an analysis-only report in place", async () => {
  const s = makeEnv();
  const first = await worker.fetch(post(makeZip()), s.env);
  const { receipt } = await first.json();
  await worker.fetch(
    post(makeZip(), { "X-Sudoku-Training-Consent": "yes" }),
    s.env,
  );
  assert.equal(s.trainingValues.size, 1);
  assert.equal(s.metadata.get(receipt).trainingConsent, "true");
  await worker.fetch(post(makeZip()), s.env);
  assert.equal(s.metadata.get(receipt).trainingConsent, "true");
});
test("storage errors fail closed and a retry can complete", async () => {
  const s = makeEnv({ fail: true });
  assert.equal((await worker.fetch(post(makeZip()), s.env)).status, 503);
  assert.equal(s.values.size, 0);
  s.failures.put = false;
  assert.equal((await worker.fetch(post(makeZip()), s.env)).status, 201);
  delete s.env.TRAINING_EXAMPLES;
  assert.equal((await worker.fetch(post(makeZip()), s.env)).status, 503);
});
test("receipt deletion is idempotent and tombstones prevent resurrection after restart", async () => {
  const s = makeEnv();
  const r = await worker.fetch(post(makeZip()), s.env);
  const { receipt } = await r.json();
  assert.equal((await worker.fetch(deletion(receipt), s.env)).status, 200);
  assert.equal(s.trainingValues.size, 0);
  s.restart(receipt);
  assert.deepEqual(
    await (await worker.fetch(deletion(receipt), s.env)).json(),
    { receipt, status: "already_deleted" },
  );
  assert.equal((await worker.fetch(post(makeZip()), s.env)).status, 410);
});
test("failed deletion never claims completion; retry finishes after restart", async () => {
  const s = makeEnv();
  const r = await worker.fetch(post(makeZip()), s.env);
  const { receipt } = await r.json();
  s.failures.delete = true;
  assert.equal((await worker.fetch(deletion(receipt), s.env)).status, 503);
  assert.equal((await worker.fetch(post(makeZip()), s.env)).status, 410);
  s.failures.delete = false;
  s.restart(receipt);
  assert.equal((await worker.fetch(deletion(receipt), s.env)).status, 200);
  assert.equal(s.trainingValues.size, 0);
});
test("scheduled migration preserves legacy data and analysis permission without local exports", async () => {
  const s = makeEnv(),
    body = makeZip(),
    receipt = await receiptOf(body);
  s.values.set(`reports/${receipt}.zip`, body.buffer);
  await worker.scheduled({}, s.env);
  assert.equal(s.values.size, 0);
  assert.deepEqual(s.trainingValues.get(receipt), body);
  assert.equal(s.metadata.get(receipt).trainingConsent, "false");
  await worker.scheduled({}, s.env);
  assert.equal(s.trainingWrites.length, 1);
});
test("migration keeps KV until the R2 write succeeds and tolerates deletion failure", async () => {
  const s = makeEnv({ fail: true }),
    body = makeZip(),
    receipt = await receiptOf(body);
  s.values.set(`reports/${receipt}.zip`, body.buffer);
  await assert.rejects(worker.scheduled({}, s.env));
  assert.equal(s.values.size, 1);
  s.failures.put = false;
  s.failures.kvDelete = true;
  await assert.rejects(worker.scheduled({}, s.env));
  assert.equal(s.trainingValues.size, 1);
  s.failures.kvDelete = false;
  await worker.scheduled({}, s.env);
  assert.equal(s.values.size, 0);
  assert.equal(s.trainingWrites.length, 1);
});
test("migration removes duplicates without overwriting consented R2 objects", async () => {
  const s = makeEnv(),
    body = makeZip(),
    receipt = await receiptOf(body);
  s.values.set(`reports/${receipt}.zip`, body.buffer);
  s.trainingValues.set(receipt, body);
  await worker.scheduled({}, s.env);
  assert.equal(s.values.size, 0);
  assert.equal(s.trainingWrites.length, 0);
  assert.deepEqual(s.trainingValues.get(receipt), body);
});
test("migration never resurrects a deleted receipt or accepts mismatched legacy content", async () => {
  const s = makeEnv(),
    body = makeZip(),
    receipt = await receiptOf(body);
  await worker.fetch(deletion(receipt), s.env);
  s.values.set(`reports/${receipt}.zip`, body.buffer);
  await worker.scheduled({}, s.env);
  assert.equal(s.trainingValues.size, 0);
  assert.equal(s.values.size, 0);
  const other = "a".repeat(64);
  s.values.set(`reports/${other}.zip`, body.buffer);
  await assert.rejects(worker.scheduled({}, s.env));
  assert.equal(s.values.size, 1);
  assert.equal(s.trainingValues.size, 0);
});
test("migration method and stored contents have no public route", async () => {
  const s = makeEnv();
  assert.equal(
    (
      await worker.fetch(
        new Request("https://x/v1/reports/" + "a".repeat(64), {
          method: "PATCH",
        }),
        s.env,
      )
    ).status,
    405,
  );
  assert.equal(
    (
      await worker.fetch(
        new Request("https://x/v1/reports/" + "a".repeat(64)),
        s.env,
      )
    ).status,
    405,
  );
});

test("upload/delete race is serialized and no delayed write restores a deleted report", async () => {
  const s = makeEnv(),
    body = makeZip(),
    receipt = await receiptOf(body);
  let started, release;
  const gate = new Promise((r) => {
    release = r;
  });
  const writing = new Promise((r) => {
    started = r;
  });
  const original = s.env.TRAINING_EXAMPLES.put;
  s.env.TRAINING_EXAMPLES.put = async (...args) => {
    started();
    await gate;
    return original(...args);
  };
  const upload = worker.fetch(post(body), s.env);
  await writing;
  const remove = worker.fetch(deletion(receipt), s.env);
  await new Promise((r) => setTimeout(r, 0));
  assert.equal(s.trainingValues.size, 0);
  release();
  assert.equal((await upload).status, 201);
  assert.equal((await remove).status, 200);
  assert.equal(s.trainingValues.size, 0);
  assert.equal((await worker.fetch(post(body), s.env)).status, 410);
});
test("rate limiter fails closed for submission and deletion", async () => {
  for (const options of [{ throttled: true }, { limiterError: true }]) {
    const s = makeEnv(options);
    let touched = false;
    s.env.TRAINING_EXAMPLES.head = async () => {
      touched = true;
      return null;
    };
    const expected = options.throttled ? 429 : 503;
    assert.equal((await worker.fetch(post(makeZip()), s.env)).status, expected);
    assert.equal(
      (await worker.fetch(deletion("b".repeat(64)), s.env)).status,
      expected,
    );
    assert.equal(touched, false);
  }
});

test("final configuration accepts and deletes reports without KV", async () => {
  const s = makeEnv();
  delete s.env.REPORTS;
  const response = await worker.fetch(post(makeZip()), s.env);
  assert.equal(response.status, 201);
  const { receipt } = await response.json();
  assert.equal((await worker.fetch(deletion(receipt), s.env)).status, 200);
  await worker.scheduled({}, s.env);
});
