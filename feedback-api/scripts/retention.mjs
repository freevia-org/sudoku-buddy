import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { REPORT_TTL_SECONDS } from "../src/retention.js";

export { REPORT_TTL_SECONDS };
export const RECEIPT_PATTERN = /^[a-f0-9]{64}$/;
export const WORKSPACE_MARKER = ".sudoku-buddy-private-report-workspace.json";

export function validateReceipt(receipt) {
  if (typeof receipt !== "string" || !RECEIPT_PATTERN.test(receipt)) {
    throw new Error("Receipt must be a 64-character lowercase SHA-256 digest.");
  }
  return receipt;
}

/**
 * Keep local copies no longer than the exact expiry supplied by KV. A missing or
 * unreasonable expiry is a hard stop: never create an untracked, unbounded export.
 */
export function retentionRecord(receipt, expiration, nowSeconds = Math.floor(Date.now() / 1000)) {
  validateReceipt(receipt);
  if (!Number.isSafeInteger(expiration) || expiration <= 0) {
    throw new Error(`Report ${receipt} has no valid KV expiry; it was not exported.`);
  }
  const uploadedAt = expiration - REPORT_TTL_SECONDS;
  if (uploadedAt > nowSeconds + 60 || expiration > nowSeconds + REPORT_TTL_SECONDS + 60) {
    throw new Error(`Report ${receipt} expires later than the 90-day policy allows; it was not exported.`);
  }
  return {
    receipt,
    uploadedAt: new Date(uploadedAt * 1000).toISOString(),
    deleteBy: new Date(expiration * 1000).toISOString(),
    deleteByEpochSeconds: expiration,
  };
}

export async function ensureManagedWorkspace(workspace) {
  const root = path.resolve(workspace);
  await mkdir(root, { recursive: true, mode: 0o700 });
  const marker = path.join(root, WORKSPACE_MARKER);
  try {
    const markerValue = JSON.parse(await readFile(marker, "utf8"));
    if (markerValue.purpose !== "private user report review copies") {
      throw new Error("Private report workspace marker has an unexpected value.");
    }
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    const entries = await readdir(root);
    if (entries.length !== 0) {
      throw new Error(`Unmanaged files exist in ${root}. Inventory and delete legacy exports before using this workspace.`);
    }
    await writeFile(marker, JSON.stringify({ purpose: "private user report review copies" }) + "\n", {
      flag: "wx",
      mode: 0o600,
    });
    return root;
  }

  const entries = await readdir(root, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === WORKSPACE_MARKER) continue;
    if (!entry.isDirectory() || !RECEIPT_PATTERN.test(entry.name)) {
      throw new Error(`Unexpected item in managed workspace: ${entry.name}. Review it before exporting.`);
    }
    let record;
    try {
      record = JSON.parse(await readFile(path.join(root, entry.name, "retention.json"), "utf8"));
    } catch {
      throw new Error(`Report workspace ${entry.name} has no readable retention record. Delete that report directory immediately.`);
    }
    if (record.receipt !== entry.name || !Number.isSafeInteger(record.deleteByEpochSeconds)) {
      throw new Error(`Report workspace ${entry.name} has an invalid retention record. Delete that report directory immediately.`);
    }
  }
  return root;
}

export async function removeReceiptCopies(workspace, receipt) {
  validateReceipt(receipt);
  const root = path.resolve(workspace);
  await mkdir(root, { recursive: true, mode: 0o700 });
  // The first exporter wrote flat <receipt>.zip files. Delete those during migration
  // as well as the current per-receipt folder and everything a reviewer kept inside it.
  await Promise.all([
    rm(path.join(root, receipt), { recursive: true, force: true }),
    rm(path.join(root, `${receipt}.zip`), { force: true }),
  ]);
}

export async function purgeExpiredCopies(workspace, nowSeconds = Math.floor(Date.now() / 1000)) {
  const root = path.resolve(workspace);
  await mkdir(root, { recursive: true, mode: 0o700 });
  try {
    const markerValue = JSON.parse(await readFile(path.join(root, WORKSPACE_MARKER), "utf8"));
    if (markerValue.purpose !== "private user report review copies") {
      throw new Error("Private report workspace marker has an unexpected value.");
    }
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    // Migrate the original flat ZIP exports and any known receipt folders by deleting
    // them now. Their old format has no trustworthy upload deadline or copy manifest.
    const legacy = await readdir(root, { withFileTypes: true });
    const reportEntries = legacy.filter((entry) =>
      (entry.isFile() && /^[a-f0-9]{64}\.zip$/.test(entry.name)) ||
      (entry.isDirectory() && RECEIPT_PATTERN.test(entry.name)),
    );
    const unknown = legacy.filter((entry) =>
      entry.name !== WORKSPACE_MARKER && !reportEntries.includes(entry),
    );
    if (unknown.length > 0) {
      throw new Error(`Unmanaged items exist in ${root}. Inventory this path before deleting or using it for report review.`);
    }
    await Promise.all(reportEntries.map((entry) => rm(path.join(root, entry.name), { recursive: true, force: false })));
    await writeFile(path.join(root, WORKSPACE_MARKER), JSON.stringify({ purpose: "private user report review copies" }) + "\n", {
      flag: "wx",
      mode: 0o600,
    });
    if (reportEntries.length > 0) {
      return [...new Set(reportEntries.map((entry) => entry.name.replace(/\.zip$/, "")))];
    }
  }
  const entries = await readdir(root, { withFileTypes: true });
  const removed = [];
  for (const entry of entries) {
    if (entry.name === WORKSPACE_MARKER) continue;
    if (!entry.isDirectory() || !RECEIPT_PATTERN.test(entry.name)) {
      throw new Error(`Unexpected item in managed workspace: ${entry.name}. Review it and remove report data before continuing.`);
    }
    const directory = path.join(root, entry.name);
    let record;
    try {
      record = JSON.parse(await readFile(path.join(directory, "retention.json"), "utf8"));
    } catch {
      await rm(directory, { recursive: true, force: false });
      removed.push(entry.name);
      continue;
    }
    const uploadedAt = Date.parse(record.uploadedAt) / 1000;
    const validRecord = record.receipt === entry.name &&
      Number.isSafeInteger(record.deleteByEpochSeconds) &&
      record.deleteByEpochSeconds === Date.parse(record.deleteBy) / 1000 &&
      Number.isFinite(uploadedAt) &&
      record.deleteByEpochSeconds - uploadedAt === REPORT_TTL_SECONDS;
    if (!validRecord) {
      await rm(directory, { recursive: true, force: false });
      removed.push(entry.name);
      continue;
    }
    if (record.deleteByEpochSeconds <= nowSeconds) {
      await rm(directory, { recursive: true, force: false });
      removed.push(entry.name);
    }
  }
  return removed;
}
