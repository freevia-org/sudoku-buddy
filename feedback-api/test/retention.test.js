import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  REPORT_TTL_SECONDS,
  ensureManagedWorkspace,
  isValidRetentionRecord,
  prepareExportWorkspace,
  purgeExpiredCopies,
  removeReceiptCopies,
  retentionRecord,
} from "../scripts/retention.mjs";

const receiptA = "a".repeat(64);
const receiptB = "b".repeat(64);

async function withWorkspace(run) {
  const parent = await mkdtemp(path.join(os.tmpdir(), "sudoku-report-retention-"));
  const workspace = path.join(parent, "private-reports");
  try {
    await run(workspace);
  } finally {
    await rm(parent, { recursive: true, force: true });
  }
}

test("requires a valid KV expiry and caps the local deadline at the 90-day expiry", () => {
  const now = 1_800_000_000;
  const expiration = now + REPORT_TTL_SECONDS;
  const record = retentionRecord(receiptA, expiration, now);
  assert.equal(Date.parse(record.uploadedAt) / 1000, now);
  assert.equal(record.deleteByEpochSeconds, expiration);
  assert.equal(isValidRetentionRecord(record, receiptA, now), true);
  const futureUpload = retentionRecord(receiptA, now + REPORT_TTL_SECONDS + 3600, now + 3600);
  assert.equal(isValidRetentionRecord(futureUpload, receiptA, now), false);
  const extendedDeadline = {
    ...record,
    uploadedAt: new Date((now - 3600) * 1000).toISOString(),
  };
  assert.equal(isValidRetentionRecord(extendedDeadline, receiptA, now), false);
  assert.throws(() => retentionRecord(receiptA, undefined, now), /no valid KV expiry/);
  assert.throws(() => retentionRecord(receiptA, now + REPORT_TTL_SECONDS + 61, now), /90-day policy/);
});

test("purges an expired report folder and every nested reviewed working copy", async () => {
  await withWorkspace(async (workspace) => {
    await ensureManagedWorkspace(workspace);
    const reportDir = path.join(workspace, receiptA);
    await mkdir(path.join(reportDir, "review", "derived"), { recursive: true });
    const deadline = 1_800_000_000;
    const record = retentionRecord(receiptA, deadline, deadline - REPORT_TTL_SECONDS);
    await writeFile(path.join(reportDir, "retention.json"), JSON.stringify(record));
    await writeFile(path.join(reportDir, "report.zip"), "private report");
    await writeFile(path.join(reportDir, "review", "derived", "labels.csv"), "private working copy");

    assert.deepEqual(await purgeExpiredCopies(workspace, deadline - 1), []);
    assert.deepEqual(await purgeExpiredCopies(workspace, deadline), [receiptA]);
    await assert.rejects(readFile(path.join(reportDir, "review", "derived", "labels.csv")));
  });
});

test("export startup purges expired report folders before preparing the workspace", async () => {
  await withWorkspace(async (workspace) => {
    await ensureManagedWorkspace(workspace);
    const reportDir = path.join(workspace, receiptA);
    const deadline = 1_800_000_000;
    const record = retentionRecord(receiptA, deadline, deadline - REPORT_TTL_SECONDS);
    await mkdir(path.join(reportDir, "review", "derived"), { recursive: true });
    await writeFile(path.join(reportDir, "retention.json"), JSON.stringify(record));
    await writeFile(path.join(reportDir, "report.zip"), "expired report");
    await writeFile(path.join(reportDir, "review", "derived", "labels.csv"), "expired working copy");

    assert.equal(await prepareExportWorkspace(workspace, deadline), path.resolve(workspace));
    await assert.rejects(readFile(path.join(reportDir, "report.zip")));
    await assert.rejects(readFile(path.join(reportDir, "review", "derived", "labels.csv")));
  });
});

test("purges a receipt folder immediately when its retention record is missing or malformed", async () => {
  await withWorkspace(async (workspace) => {
    await ensureManagedWorkspace(workspace);
    const missingRecord = path.join(workspace, receiptA);
    const malformedRecord = path.join(workspace, receiptB);
    await mkdir(missingRecord);
    await mkdir(malformedRecord);
    await writeFile(path.join(missingRecord, "report.zip"), "untracked copy");
    await writeFile(path.join(malformedRecord, "retention.json"), "not json");

    assert.deepEqual(await purgeExpiredCopies(workspace), [receiptA, receiptB]);
    await assert.rejects(readFile(path.join(missingRecord, "report.zip")));
    await assert.rejects(readFile(path.join(malformedRecord, "retention.json")));
  });
});

test("receipt deletion removes the managed folder and legacy flat export", async () => {
  await withWorkspace(async (workspace) => {
    const reportDir = path.join(workspace, receiptA);
    await mkdir(reportDir, { recursive: true });
    await writeFile(path.join(reportDir, "working-copy.jpg"), "copy");
    await writeFile(path.join(workspace, `${receiptA}.zip`), "legacy export");
    await removeReceiptCopies(workspace, receiptA);
    await assert.rejects(readFile(path.join(reportDir, "working-copy.jpg")));
    await assert.rejects(readFile(path.join(workspace, `${receiptA}.zip`)));
  });
});

test("first cleanup removes untracked legacy report exports but refuses unrelated files", async () => {
  await withWorkspace(async (workspace) => {
    await mkdir(workspace, { recursive: true });
    const oldFolder = path.join(workspace, receiptA);
    await mkdir(oldFolder);
    await writeFile(path.join(oldFolder, "reviewed.json"), "copy");
    await writeFile(path.join(workspace, `${receiptB}.zip`), "copy");
    assert.deepEqual(await purgeExpiredCopies(workspace), [receiptA, receiptB]);

    const unrelated = path.join(workspace, "notes.txt");
    await writeFile(unrelated, "do not delete silently");
    await assert.rejects(purgeExpiredCopies(workspace), /Unexpected item/);
  });
});
