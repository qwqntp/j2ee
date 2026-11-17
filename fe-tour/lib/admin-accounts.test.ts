import { describe, expect, it } from "vitest";
import { accountStatusLabel, canChangeAdminRole } from "./admin-accounts";

describe("accountStatusLabel", () => {
  it("labels account status values", () => {
    expect(accountStatusLabel(1)).toBe("Active");
    expect(accountStatusLabel(0)).toBe("Locked");
  });

  it("labels every non-active value as Locked", () => {
    expect(accountStatusLabel(2)).toBe("Locked");
    expect(accountStatusLabel(null)).toBe("Locked");
  });
});

describe("canChangeAdminRole", () => {
  it("blocks demoting the only active admin in the loaded list", () => {
    expect(canChangeAdminRole([{ roleName: "ROLE_ADMIN", status: 1 }], "ROLE_USER")).toBe(false);
    expect(canChangeAdminRole([
      { roleName: "ROLE_ADMIN", status: 1 },
      { roleName: "ROLE_ADMIN", status: 1 },
    ], "ROLE_USER")).toBe(true);
  });

  it("allows keeping the only active admin as admin", () => {
    expect(canChangeAdminRole([{ roleName: "ROLE_ADMIN", status: 1 }], "ROLE_ADMIN")).toBe(true);
  });

  it("ignores locked admins and non-admin accounts when counting", () => {
    expect(canChangeAdminRole([
      { roleName: "ROLE_ADMIN", status: 0 },
      { roleName: "ROLE_USER", status: 1 },
    ], "ROLE_USER")).toBe(true);
  });
});
