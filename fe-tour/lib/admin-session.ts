export function getAdminRecheckAt(token: string, now: number): number {
  const limit = now + 60_000;

  // Expiry only shortens reuse of an already validated session; it never grants access.
  try {
    const payload = token.split(".")[1]?.replaceAll("-", "+").replaceAll("_", "/");
    if (!payload) return limit;
    const { exp } = JSON.parse(atob(payload));
    return typeof exp === "number" && Number.isFinite(exp) ? Math.min(limit, exp * 1_000) : limit;
  } catch {
    return limit;
  }
}
