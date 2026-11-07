// Privacy-safe structured diagnostics. Only allow-listed field names with primitive, short values are ever emitted; everything else is dropped.
// Patient identifiers, record keys, tokens, payloads and free text can therefore not reach a log by accident.
const ALLOWED = new Set(["event", "state", "code", "count", "attempt", "batchSize", "delayMs", "status", "seq", "cursor", "queued", "bytes", "kind", "leader", "term", "ms", "version"]);
export type LogEvent = Record<string, unknown>;
export type Sink = (line: string) => void;
const UUID_LIKE = /[0-9a-f]{8}-[0-9a-f]{4}-/i;
const SAFE_VALUE = /^[A-Za-z0-9_.:-]{0,64}$/;

export function sanitize(fields: LogEvent): Record<string, string | number | boolean> {
 const out: Record<string, string | number | boolean> = {};
 for (const [k, v] of Object.entries(fields)) {
  if (!ALLOWED.has(k)) continue;
  if (typeof v === "number" && Number.isFinite(v)) out[k] = v;
  else if (typeof v === "boolean") out[k] = v;
  else if (typeof v === "string" && SAFE_VALUE.test(v) && !/^eyJ/.test(v) && !UUID_LIKE.test(v) && !/\d{6,}/.test(v)) out[k] = v;   // JWT-, UUID- and long-number-like strings are identifiers, not vocabulary
 }
 return out;
}
export class Diagnostics {
 private sink: Sink;
 private ring: string[] = [];
 constructor(sink: Sink = () => undefined) { this.sink = sink; }
 log(fields: LogEvent): void {
  const line = JSON.stringify(sanitize(fields));
  this.ring.push(line); if (this.ring.length > 200) this.ring.shift();
  this.sink(line);
 }
 recent(): string[] { return [...this.ring]; }
}
