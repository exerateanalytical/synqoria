// Bounded exponential backoff with full jitter. The random source is injected so tests are deterministic.
export type BackoffConfig = { baseMs: number; capMs: number; maxAttempts: number };
export const DEFAULT_BACKOFF: BackoffConfig = { baseMs: 1000, capMs: 5 * 60_000, maxAttempts: 12 };
/** Delay in ms before attempt `attempt` (1-based), or null once the attempt budget is spent (surface to the user instead of retrying forever). */
export function nextDelay(attempt: number, rng: () => number, cfg: BackoffConfig = DEFAULT_BACKOFF): number | null {
 if (attempt < 1 || attempt > cfg.maxAttempts) return null;
 const ceiling = Math.min(cfg.capMs, cfg.baseMs * 2 ** (attempt - 1));
 return Math.floor(rng() * ceiling) + 1;                                   // 1..ceiling, never zero (no tight loops)
}
