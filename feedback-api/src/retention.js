// Historical local-export cleanup only; live R2 reports have no automatic expiry.
// Shared by the intake Worker and its private review tooling.
export const REPORT_TTL_SECONDS = 90 * 24 * 60 * 60;
