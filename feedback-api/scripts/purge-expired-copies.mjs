#!/usr/bin/env node
import { purgeExpiredCopies } from "./retention.mjs";

const workspace = process.argv[2];
if (!workspace) throw new Error("Pass the designated private report workspace.");

const removed = await purgeExpiredCopies(workspace);
console.log(`Removed ${removed.length} expired report workspace(s), including their working files.`);
