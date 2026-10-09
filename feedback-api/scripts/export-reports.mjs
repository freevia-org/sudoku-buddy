#!/usr/bin/env node
import { mkdir } from "node:fs/promises";
import { exportReportItem } from "./export-lifecycle.mjs";
import { prepareExportWorkspace } from "./retention.mjs";

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
    const created = await exportReportItem(outputDir, item, async (report) => {
      const valueUrl = `${prefix}/values/${encodeURIComponent(report.name)}`;
      return new Uint8Array(await (await cloudflare(valueUrl)).arrayBuffer());
    });
    if (created) count += 1;
  }
  cursor = listing.result_info?.cursor || undefined;
} while (cursor);

console.log(`Exported ${count} report(s) under the managed private workspace ${outputDir}. Keep every working copy inside its receipt folder.`);
