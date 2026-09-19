export function assertValidUid(uid: string): void {
  if (!uid || uid.includes("/") || uid.includes("..")) {
    throw new Error(`invalid_uid: ${uid}`);
  }
}
