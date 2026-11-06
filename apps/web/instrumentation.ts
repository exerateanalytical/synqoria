/**
 * SYNQORIA P03-B0072: Next.js Node-process identity initializer.
 * Runs before server requests, never per-request. Edge runtime does not
 * open database sockets. Absent explicit deployment configuration auth
 * remains fail-closed with 503 from the existing verified route dispatcher.
 */
export async function register():Promise<void>{
 if(process.env.NEXT_RUNTIME!=="nodejs")return;
 const {initializeProductionStaffIdentity}=await import("./lib/staff-identity-production-startup.mjs");
 await initializeProductionStaffIdentity();
}
