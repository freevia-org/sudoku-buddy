import { REPORT_TTL_SECONDS } from "./retention.js";

const RECEIPT_PATTERN = /^[a-f0-9]{64}$/;
const REPORT_PREFIX = "reports/";
const ANALYSIS_TTL_SECONDS = REPORT_TTL_SECONDS;

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

async function digestHex(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** One object per receipt serializes all cross-store writes and deletion for that report. */
export class ReportReceipt {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.operationQueue = Promise.resolve();
  }

  async fetch(request) {
    const url = new URL(request.url);
    const receipt = url.pathname.slice(1);
    if (!RECEIPT_PATTERN.test(receipt)) return json(400, { error: "invalid_receipt" });
    if (!this.env.REPORTS || !this.env.TRAINING_EXAMPLES) {
      return json(503, { error: "receipt_service_unavailable" });
    }

    // Each receipt has its own in-memory promise queue. This serializes external
    // KV/R2 I/O without blockConcurrencyWhile's 30-second callback timeout or a
    // global bottleneck. Durable state makes retries safe after an object restart.
    const result = this.operationQueue.then(() => this.dispatch(request, receipt));
    this.operationQueue = result.then(() => undefined, () => undefined);
    return result;
  }

  async dispatch(request, receipt) {
    return request.method === "POST"
      ? this.accept(receipt, request)
      : request.method === "DELETE"
        ? this.delete(receipt)
        : json(405, { error: "method_not_allowed" });
  }

  async accept(receipt, request) {
    try {
      if (await this.state.storage.get("deletionState")) {
        return json(410, { error: "report_deleted" });
      }
      const bytes = new Uint8Array(await request.arrayBuffer());
      if (await digestHex(bytes) !== receipt) return json(400, { error: "receipt_mismatch" });

      let deleteBy = await this.state.storage.get("analysisDeleteBy");
      if (deleteBy === undefined) {
        const key = `${REPORT_PREFIX}${receipt}.zip`;
        const listing = await this.env.REPORTS.list({ prefix: key, limit: 1 });
        const legacyEntry = listing.keys.find((entry) => entry.name === key);
        if (legacyEntry) {
          if (!Number.isSafeInteger(legacyEntry.expiration)) {
            return json(503, { error: "submission_temporarily_unavailable" });
          }
          deleteBy = legacyEntry.expiration;
          // Import the pre-DO expiry rather than assigning the legacy report a
          // fresh 90-day window. KV key listing exposes expiry without report data.
          await this.state.storage.put("analysisDeleteBy", deleteBy);
          await this.state.storage.put("kvStored", true);
        } else {
          deleteBy = Math.floor(Date.now() / 1000) + ANALYSIS_TTL_SECONDS;
          // Persist before the first KV read/write so a partial failure or object
          // restart cannot extend the analysis window on retry.
          await this.state.storage.put("analysisDeleteBy", deleteBy);
        }
      }

      const key = `${REPORT_PREFIX}${receipt}.zip`;
      let alreadyStored = Boolean(await this.state.storage.get("kvStored"));
      if (!alreadyStored) {
        const existing = await this.env.REPORTS.get(key, { type: "arrayBuffer" });
        if (existing === null) {
          if (deleteBy <= Math.floor(Date.now() / 1000) + 60) {
            return json(410, { error: "analysis_window_expired" });
          }
          await this.env.REPORTS.put(key, bytes, { expiration: deleteBy });
        } else {
          alreadyStored = true;
        }
        await this.state.storage.put("kvStored", true);
      }
      if (request.headers.get("X-Sudoku-Training-Consent") === "yes") {
        await this.env.TRAINING_EXAMPLES.put(receipt, bytes);
      }
      return json(alreadyStored ? 200 : 201, {
        receipt,
        status: alreadyStored ? "already_received" : "accepted",
      });
    } catch {
      return json(503, { error: "submission_temporarily_unavailable" });
    }
  }

  async delete(receipt) {
    const key = `${REPORT_PREFIX}${receipt}.zip`;
    try {
      const previousState = await this.state.storage.get("deletionState");
      // Persist a tombstone before touching either store. A retry can finish a
      // partially completed delete, and future uploads of this receipt stay blocked.
      if (previousState !== "deleted" && previousState !== "deleting") {
        await this.state.storage.put("deletionState", "deleting");
      }

      const [kvValue, trainingObject] = await Promise.all([
        this.env.REPORTS.get(key, { type: "stream" }),
        this.env.TRAINING_EXAMPLES.head(receipt),
      ]);
      const existed = kvValue !== null || trainingObject !== null;
      if (kvValue && typeof kvValue.cancel === "function") await kvValue.cancel();

      const results = await Promise.allSettled([
        this.env.REPORTS.delete(key),
        this.env.TRAINING_EXAMPLES.delete(receipt),
      ]);
      if (results.some((entry) => entry.status === "rejected")) {
        return json(503, { error: "deletion_incomplete", retry: true });
      }
      await this.state.storage.put("deletionState", "deleted");
      const wasAlreadyDeleted = previousState === "deleted" && !existed;
      return json(200, { receipt, status: wasAlreadyDeleted ? "already_deleted" : "deleted" });
    } catch {
      return json(503, { error: "deletion_incomplete", retry: true });
    }
  }
}
