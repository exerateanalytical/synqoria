// Device binding is a SERVER fact. This verifies it against the server's own device list (hospital-scoped by the verified token) before the device
// is allowed to claim a binding locally: it must exist, be ACTIVE, be bound to exactly the signed-in user, and have a live session window.
import type { Transport } from "./transport.ts";
import type { DeviceBinding, Session } from "./types.ts";

export type DeviceRow = { id: string; status: string; boundUser: string; sessionExpiresAt: string };
export async function verifyBinding(transport: Transport, deviceId: string, session: Session, now: number): Promise<{ ok: true; binding: DeviceBinding } | { ok: false; code: string }> {
 const r = await transport.call<DeviceRow[]>("GET", "/devices");
 if (!r.ok) return { ok: false, code: r.kind === "unauthorized" ? "session_expired" : r.code };
 if (!Array.isArray(r.data)) return { ok: false, code: "protocol_error" };
 const d = r.data.find(x => x.id === deviceId);
 if (!d) return { ok: false, code: "device_not_found" };                              // unknown, or another hospital's: indistinguishable on purpose
 if (d.status !== "ACTIVE") return { ok: false, code: "device_not_active" };
 if (d.boundUser !== session.userId) return { ok: false, code: "device_user_mismatch" };
 if (Date.parse(d.sessionExpiresAt) <= now) return { ok: false, code: "device_session_expired" };
 return { ok: true, binding: { deviceId, userId: session.userId, scope: session.scope } };
}
