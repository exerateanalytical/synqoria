export interface VerifiedOperationsSession {
 readonly userId: string;
 readonly scope: string;
 readonly expiresAt: number;
}
export interface VerifiedOperationsSessionOptions {
 issuer: string;
 audience: string;
 fetchImpl?: typeof fetch;
 now?: number;
 requiredPermission?: "m38:sync_read" | "m38:sync_submit" | null;
}
export function verifiedOperationsSession(
 token: string | null | undefined,
 options: VerifiedOperationsSessionOptions,
): Promise<VerifiedOperationsSession>;
