#!/usr/bin/env node
import { removeReceiptCopies, validateReceipt } from "./retention.mjs";

const api = "https://api.cloudflare.com/client/v4";
const [receiptArgument, workspace] = process.argv.slice(2);
const receipt = validateReceipt(receiptArgument);
const accountId = process.env.CF_ACCOUNT_ID;
const namespaceId = process.env.CF_KV_NAMESPACE_ID;
const token = process.env.CLOUDFLARE_API_TOKEN;
if (!workspace) throw new Error("Pass the designated private report workspace.");
if (!accountId || !namespaceId || !token) {
  throw new Error("Set CF_ACCOUNT_ID, CF_KV_NAMESPACE_ID, and CLOUDFLARE_API_TOKEN in the environment.");
}

const key = `reports/${receipt}.zip`;
const valueUrl = `${api}/accounts/${encodeURIComponent(accountId)}/storage/kv/namespaces/${encodeURIComponent(namespaceId)}/values/${encodeURIComponent(key)}`;

async function deleteRemoteValue() {
  const headers = { authorization: `Bearer ${token}` };
  const deleted = await fetch(valueUrl, { method: "DELETE", headers });
  if (!deleted.ok) throw new Error(`Cloudflare deletion failed (${deleted.status}).`);
  const result = await deleted.json();
  if (result.success !== true) throw new Error("Cloudflare did not confirm the KV deletion.");

  // Verify against the API, not a Worker edge cache. KV is eventually consistent;
  // keep the request open briefly and fail visibly if the value still appears.
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const check = await fetch(valueUrl, { headers });
    if (check.status === 404) return;
    if (!check.ok) throw new Error(`Cloudflare deletion verification failed (${check.status}).`);
    await check.body?.cancel(); // Only the status matters; do not download another report copy.
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error("Cloudflare accepted deletion but the value remains readable; retry and verify before closing the request.");
}

const outcomes = await Promise.allSettled([
  deleteRemoteValue(),
  removeReceiptCopies(workspace, receipt),
]);
const [remote, local] = outcomes;
if (remote.status === "fulfilled" && local.status === "fulfilled") {
  console.log(`Deleted report ${receipt} from KV and its managed export/working-copy folder.`);
} else {
  console.error(`Deletion of report ${receipt} is incomplete. Retry both operations and verify the KV value is absent before closing the request.`);
  if (remote.status === "rejected") console.error(`KV: ${remote.reason.message}`);
  if (local.status === "rejected") console.error(`Workspace: ${local.reason.message}`);
  process.exitCode = 1;
}
