import { describe, expect, it } from "vitest";
import { getAdminRecheckAt } from "./admin-session";

const now = 1_800_000_000_000;

describe("admin session revalidation", () => {
  it("allows reuse after validation without keeping credentials indefinitely", () => {
    const payload = btoa(JSON.stringify({ exp: (now + 900_000) / 1_000 }));
    expect(getAdminRecheckAt(`header.${payload}.signature`, now)).toBe(now + 60_000);
  });

  it("does not reuse validation beyond the token's expiry", () => {
    const payload = btoa(JSON.stringify({ exp: (now + 2_000) / 1_000 }));
    expect(getAdminRecheckAt(`header.${payload}.signature`, now)).toBe(now + 2_000);
  });

  it("bounds reuse for tokens without readable expiry", () => {
    expect(getAdminRecheckAt("opaque", now)).toBe(now + 60_000);
  });
});
