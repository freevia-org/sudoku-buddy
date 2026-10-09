import { createHash } from "node:crypto";
import { mkdir, open, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { isValidRetentionRecord, retentionRecord, validateReceipt } from "./retention.mjs";

function validateZip(bytes, receipt) {
  const digest = createHash("sha256").update(bytes).digest("hex");
  if (digest !== receipt || bytes.length < 4 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
    throw new Error(`Stored report ${receipt} failed integrity or ZIP signature validation.`);
  }
}

export async function exportReportItem(outputDir, item, fetchBytes, nowSeconds = Math.floor(Date.now() / 1000)) {
  if (typeof item?.name !== "string" || !/^reports\/[a-f0-9]{64}\.zip$/.test(item.name)) return false;
  const receipt = item.name.slice("reports/".length, -".zip".length);
  validateReceipt(receipt);
  const expected = retentionRecord(receipt, item.expiration, nowSeconds);
  if (expected.deleteByEpochSeconds <= nowSeconds) return false;

  const reportDirectory = path.join(outputDir, receipt);
  let existing = false;
  try {
    await mkdir(reportDirectory, { mode: 0o700 });
  } catch (error) {
    if (error.code !== "EEXIST") throw error;
    existing = true;
  }

  if (existing) {
    let storedRecord;
    try {
      storedRecord = JSON.parse(await readFile(path.join(reportDirectory, "retention.json"), "utf8"));
    } catch {
      throw new Error(`Existing report ${receipt} has no readable retention record; remove or review its folder before retrying.`);
    }
    if (!isValidRetentionRecord(storedRecord, receipt, nowSeconds) ||
        storedRecord.deleteByEpochSeconds !== expected.deleteByEpochSeconds) {
      throw new Error(`Existing report ${receipt} has a mismatched or invalid retention deadline; refusing to replace it.`);
    }
    try {
      const currentZip = new Uint8Array(await readFile(path.join(reportDirectory, "report.zip")));
      validateZip(currentZip, receipt);
      return false;
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
      // A prior run may have stopped after writing retention.json. Preserve any
      // reviewer files and finish the original receipt using its existing deadline.
    }
  } else {
    await writeFile(path.join(reportDirectory, "retention.json"), `${JSON.stringify(expected, null, 2)}\n`, {
      flag: "wx",
      mode: 0o600,
    });
  }

  try {
    const bytes = new Uint8Array(await fetchBytes(item, receipt));
    validateZip(bytes, receipt);
    const handle = await open(path.join(reportDirectory, "report.zip"), "wx", 0o600);
    try {
      await handle.writeFile(bytes);
    } finally {
      await handle.close();
    }
  } catch (error) {
    if (!existing) await rm(reportDirectory, { recursive: true, force: true });
    throw error;
  }
  return true;
}
