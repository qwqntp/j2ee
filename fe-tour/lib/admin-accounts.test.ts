import { describe, expect, it } from "vitest";
import type { AdminAccount } from "./types";
import {
  accountStatusLabel,
  canChangeAdminRole,
  filterAccounts,
  isFinalActiveAdmin,
} from "./admin-accounts";

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

describe("filterAccounts", () => {
  const accounts: AdminAccount[] = [
    {
      id: 1,
      username: "alice",
      email: "ALICE@EXAMPLE.COM",
      roleName: "ROLE_USER",
      status: 1,
      emailVerified: 1,
      roleId: 2,
    },
    {
      id: 2,
      username: "bob",
      email: null,
      roleName: "ROLE_ADMIN",
      status: 0,
      emailVerified: 0,
      roleId: 1,
    },
  ];

  it("keeps account filter matching case-insensitively across username and email", () => {
    expect(filterAccounts(accounts, { query: "alice", roleName: "", status: "all" })).toHaveLength(1);
  });

  it("matches role name case-insensitively and filters by status", () => {
    expect(filterAccounts(accounts, { query: "ADMIN", roleName: "", status: "all" })).toHaveLength(1);
    expect(filterAccounts(accounts, { query: "", roleName: "ROLE_USER", status: "all" })).toHaveLength(1);
    expect(filterAccounts(accounts, { query: "", roleName: "", status: "1" })).toHaveLength(1);
  });

  it("does not mutate the input and returns all for an empty filter", () => {
    const snapshot = JSON.stringify(accounts);
    expect(filterAccounts(accounts, { query: "", roleName: "", status: "all" })).toHaveLength(2);
    expect(JSON.stringify(accounts)).toBe(snapshot);
  });
});

describe("isFinalActiveAdmin", () => {
  const accounts: AdminAccount[] = [
    {
      id: 1,
      username: "admin",
      email: null,
      roleName: "ROLE_ADMIN",
      status: 1,
      emailVerified: 1,
      roleId: 1,
    },
    {
      id: 2,
      username: "user",
      email: null,
      roleName: "ROLE_USER",
      status: 1,
      emailVerified: 1,
      roleId: 2,
    },
  ];

  it("flags the only active admin in the loaded list", () => {
    expect(isFinalActiveAdmin(accounts, 1)).toBe(true);
  });

  it("does not flag non-admin or locked accounts", () => {
    expect(isFinalActiveAdmin(accounts, 2)).toBe(false);
    expect(isFinalActiveAdmin([{ ...accounts[0], status: 0 }], 1)).toBe(false);
  });

  it("does not flag an admin when another active admin is loaded", () => {
    expect(isFinalActiveAdmin([...accounts, { ...accounts[0], id: 3, username: "admin2" }], 1)).toBe(false);
  });
});
