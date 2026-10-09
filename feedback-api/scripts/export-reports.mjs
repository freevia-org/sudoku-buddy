#!/usr/bin/env node
import { createHash } from "node:crypto";
import { mkdir, open, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { prepareExportWorkspace, retentionRecord } from "./retention.mjs";

const api = "https://api.cloudflare.com/client/v4";
const accountId = process.env.CF_ACCOUNT_ID;
const namespaceId = process.env.CF_KV_NAMESPACE_ID;
const token = process.env.CLOUDFLARE_API_TOKEN;
const outputDirArgument = process.argv[2];
if (!outputDirArgument) {
  throw new Error("Pass the designated, private report workspace as the first argument.");
}

if (!accountId || !namespaceId || !token) {
  throw new Error("Set CF_ACCOUNT_ID, CF_KV_NAMESPACE_ID, and CLOUDFLARE_API_TOKEN in the environment.");
}

async function cloudflare(url) {
  const response = await fetch(url, { headers: { authorization: `Bearer ${token}` } });
  if (!response.ok) throw new Error(`Cloudflare API request failed (${response.status}).`);
  return response;
}

const prefix = `${api}/accounts/${encodeURIComponent(accountId)}/storage/kv/namespaces/${encodeURIComponent(namespaceId)}`;
const outputDir = await prepareExportWorkspace(outputDirArgument);
await mkdir(outputDir, { recursive: true, mode: 0o700 });
let cursor;
let count = 0;

do {
  const listUrl = new URL(`${prefix}/keys`);
  listUrl.searchParams.set("prefix", "reports/");
  if (cursor) listUrl.searchParams.set("cursor", cursor);
  const listing = await (await cloudflare(listUrl)).json();
  if (!listing.success || !Array.isArray(listing.result)) throw new Error("Cloudflare returned an invalid key listing.");

  for (const item of listing.result) {
    if (typeof item.name !== "string" || !/^reports\/[a-f0-9]{64}\.zip$/.test(item.name)) continue;
    const receipt = item.name.slice("reports/".length, -".zip".length);
    const retention = retentionRecord(receipt, item.expiration);
    if (Date.parse(retention.deleteBy) <= Date.now()) {
      console.log(`Skipped expired report ${receipt}; no local copy was created.`);
      continue;
    }
    const valueUrl = `${prefix}/values/${encodeURIComponent(item.name)}`;
    const bytes = new Uint8Array(await (await cloudflare(valueUrl)).arrayBuffer());
    const digest = createHash("sha256").update(bytes).digest("hex");
    if (digest !== receipt || bytes.length < 4 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
      throw new Error(`Stored report ${receipt} failed integrity or ZIP signature validation.`);
    }
    const reportDirectory = path.join(outputDir, receipt);
    await mkdir(reportDirectory, { mode: 0o700 });
    try {
      await writeFile(path.join(reportDirectory, "retention.json"), `${JSON.stringify(retention, null, 2)}\n`, {
        flag: "wx",
        mode: 0o600,
      });
      const handle = await open(path.join(reportDirectory, "report.zip"), "wx", 0o600);
      try {
        await handle.writeFile(bytes);
      } finally {
        await handle.close();
      }
    } catch (error) {
      await rm(reportDirectory, { recursive: true, force: true });
      throw error;
    }
    count += 1;
    console.log(`Exported ${receipt}; delete all local copies by ${retention.deleteBy}.`);
  }
  cursor = listing.result_info?.cursor || undefined;
} while (cursor);

console.log(`Exported ${count} report(s) under the managed private workspace ${outputDir}. Keep every working copy inside its receipt folder.`);
