const RECEIPT_PATTERN = /^[a-f0-9]{64}$/;
const REPORT_PREFIX = "reports/";

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

async function digestHex(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

/** One object per receipt serializes report writes, legacy migration, and deletion. */
export class ReportReceipt {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.operationQueue = Promise.resolve();
  }

  async fetch(request) {
    const url = new URL(request.url);
    const receipt = url.pathname.slice(1);
    if (!RECEIPT_PATTERN.test(receipt))
      return json(400, { error: "invalid_receipt" });
    if (!this.env.TRAINING_EXAMPLES) {
      return json(503, { error: "receipt_service_unavailable" });
    }

    // Each receipt has its own in-memory promise queue. This serializes external
    // KV/R2 I/O without blockConcurrencyWhile's 30-second callback timeout or a
    // global bottleneck. Durable state makes retries safe after an object restart.
    const result = this.operationQueue.then(() =>
      this.dispatch(request, receipt),
    );
    this.operationQueue = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  }

  async dispatch(request, receipt) {
    return request.method === "POST"
      ? this.accept(receipt, request)
      : request.method === "DELETE"
        ? this.delete(receipt)
        : request.method === "PATCH"
          ? this.migrate(receipt)
          : json(405, { error: "method_not_allowed" });
  }

  async accept(receipt, request) {
    try {
      if (await this.state.storage.get("deletionState")) {
        return json(410, { error: "report_deleted" });
      }
      const bytes = new Uint8Array(await request.arrayBuffer());
      if ((await digestHex(bytes)) !== receipt)
        return json(400, { error: "receipt_mismatch" });

      const existing = await this.env.TRAINING_EXAMPLES.head(receipt);
      const alreadyStored = existing !== null;
      const trainingConsent =
        request.headers.get("X-Sudoku-Training-Consent") === "yes";
      // Legacy R2 objects were created only with training consent. Explicit false
      // marks analysis-only reports; a later consented retry may upgrade permission.
      const upgradeConsent =
        existing?.customMetadata?.trainingConsent === "false" &&
        trainingConsent;
      if (!alreadyStored || upgradeConsent) {
        await this.env.TRAINING_EXAMPLES.put(receipt, bytes, {
          httpMetadata: { contentType: "application/zip" },
          customMetadata: { trainingConsent: String(trainingConsent) },
        });
      }
      // During cutover, remove a legacy duplicate only after R2 has accepted it.
      if (this.env.REPORTS)
        await this.env.REPORTS.delete(`${REPORT_PREFIX}${receipt}.zip`);
      return json(alreadyStored ? 200 : 201, {
        receipt,
        status: alreadyStored ? "already_received" : "accepted",
      });
    } catch {
      return json(503, { error: "submission_temporarily_unavailable" });
    }
  }

  // Binding-only migration, never exposed by the public Worker router. Runs in
  // the same receipt queue as uploads/deletes, so a deletion cannot be undone.
  async migrate(receipt) {
    try {
      if (!this.env.REPORTS) return json(200, { status: "migration_complete" });
      const key = `${REPORT_PREFIX}${receipt}.zip`;
      if (await this.state.storage.get("deletionState")) {
        await this.env.REPORTS.delete(key);
        return json(200, { status: "already_deleted" });
      }
      const existing = await this.env.TRAINING_EXAMPLES.head(receipt);
      if (!existing) {
        const bytes = await this.env.REPORTS.get(key, { type: "arrayBuffer" });
        if (bytes === null) return json(200, { status: "not_found" });
        if ((await digestHex(bytes)) !== receipt)
          return json(503, { error: "legacy_receipt_mismatch" });
        await this.env.TRAINING_EXAMPLES.put(receipt, bytes, {
          httpMetadata: { contentType: "application/zip" },
          customMetadata: { trainingConsent: "false" },
        });
      }
      await this.env.REPORTS.delete(key);
      return json(200, { status: "migration_complete" });
    } catch {
      return json(503, { error: "migration_incomplete" });
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

      const trainingObject = await this.env.TRAINING_EXAMPLES.head(receipt);
      const existed = trainingObject !== null;
      const results = await Promise.allSettled([
        this.env.TRAINING_EXAMPLES.delete(receipt),
        ...(this.env.REPORTS ? [this.env.REPORTS.delete(key)] : []),
      ]);
      if (results.some((entry) => entry.status === "rejected")) {
        return json(503, { error: "deletion_incomplete", retry: true });
      }
      await this.state.storage.put("deletionState", "deleted");
      const wasAlreadyDeleted = previousState === "deleted" && !existed;
      return json(200, {
        receipt,
        status: wasAlreadyDeleted ? "already_deleted" : "deleted",
      });
    } catch {
      return json(503, { error: "deletion_incomplete", retry: true });
    }
  }
}
