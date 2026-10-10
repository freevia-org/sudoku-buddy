import { REPORT_TTL_SECONDS } from "./retention.js";

const RECEIPT_PATTERN = /^[a-f0-9]{64}$/;
const REPORT_PREFIX = "reports/";

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
  }

  async fetch(request) {
    const url = new URL(request.url);
    const receipt = url.pathname.slice(1);
    if (!RECEIPT_PATTERN.test(receipt)) return json(400, { error: "invalid_receipt" });
    if (!this.env.REPORTS || !this.env.TRAINING_EXAMPLES) {
      return json(503, { error: "receipt_service_unavailable" });
    }

    // Each receipt has its own object. blockConcurrencyWhile covers the KV and R2
    // awaits, which otherwise allow concurrent requests to interleave. Keep all
    // exceptions inside the callback so a storage failure does not reset the object.
    let result;
    try {
      await this.state.blockConcurrencyWhile(async () => {
        result = request.method === "POST"
          ? await this.accept(receipt, request)
          : request.method === "DELETE"
            ? await this.delete(receipt)
            : json(405, { error: "method_not_allowed" });
      });
    } catch {
      return request.method === "DELETE"
        ? json(503, { error: "deletion_incomplete", retry: true })
        : json(503, { error: "submission_temporarily_unavailable" });
    }
    return result;
  }

  async accept(receipt, request) {
    try {
      if (await this.state.storage.get("deletionState")) {
        return json(410, { error: "report_deleted" });
      }
      const bytes = new Uint8Array(await request.arrayBuffer());
      if (await digestHex(bytes) !== receipt) return json(400, { error: "receipt_mismatch" });

      const key = `${REPORT_PREFIX}${receipt}.zip`;
      const existing = await this.env.REPORTS.get(key, { type: "arrayBuffer" });
      if (existing === null) {
        await this.env.REPORTS.put(key, bytes, { expirationTtl: REPORT_TTL_SECONDS });
      }
      if (request.headers.get("X-Sudoku-Training-Consent") === "yes") {
        await this.env.TRAINING_EXAMPLES.put(receipt, bytes);
      }
      return json(existing === null ? 201 : 200, {
        receipt,
        status: existing === null ? "accepted" : "already_received",
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
