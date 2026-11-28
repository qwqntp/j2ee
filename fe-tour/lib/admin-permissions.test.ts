import { describe, expect, it } from "vitest";
import { canAccessAdminPage, consoleHome, hasConsoleAccess, isConsoleRole, loginDestination } from "./admin-permissions";
import type { AuthSession } from "./types";

const session = (roleName: string, id = 7) => ({
  accessToken: "test", account: { id, roleName },
}) as AuthSession;

describe("workspace authorization", () => {
  it("requires validated and stored accounts to agree on an allowed role and identity", () => {
    for (const role of ["ROLE_ADMIN", "ROLE_STAFF"]) {
      expect(isConsoleRole(role)).toBe(true);
      expect(hasConsoleAccess(session(role), session(role))).toBe(true);
      expect(hasConsoleAccess(session(role), null)).toBe(false);
      expect(hasConsoleAccess(null, session(role))).toBe(false);
      expect(hasConsoleAccess(session(role), session(role, 8))).toBe(false);
    }
    expect(hasConsoleAccess(session("ROLE_STAFF"), session("ROLE_ADMIN"))).toBe(false);
    expect(hasConsoleAccess(session("ROLE_USER"), session("ROLE_ADMIN"))).toBe(false);
    expect(hasConsoleAccess(session("ROLE_USER"), session("ROLE_USER"))).toBe(false);
    expect(isConsoleRole("CUSTOMER")).toBe(false);
  });

  it("allows only real staff pages and their known detail routes", () => {
    for (const path of ["/admin/bookings", "/admin/payments", "/admin/customers", "/admin/invoices",
      "/admin/departures", "/admin/customers/7", "/admin/invoices/8", "/admin/departures/9/passengers"]) {
      expect(canAccessAdminPage("ROLE_STAFF", path)).toBe(true);
      expect(canAccessAdminPage("ROLE_ADMIN", path)).toBe(true);
      expect(canAccessAdminPage("ROLE_USER", path)).toBe(false);
    }
    for (const path of ["/admin", "/admin/tours", "/admin/catalog", "/admin/accounts", "/admin/promotions",
      "/admin/reports", "/admin/future", "/admin/bookings-export", "/admin/departures/9/edit",
      "/admin/customers/7/edit", "/admin/invoices/8/refund", "/admin/payments/7/refunds"]) {
      expect(canAccessAdminPage("ROLE_STAFF", path)).toBe(false);
      expect(canAccessAdminPage("ROLE_ADMIN", path)).toBe(true);
    }
  });

  it("uses role destinations for password and Google login, leaving customer redirects intact", () => {
    expect(consoleHome("ROLE_ADMIN")).toBe("/admin");
    expect(consoleHome("ROLE_STAFF")).toBe("/admin/bookings");
    expect(loginDestination("ROLE_ADMIN", "/tours")).toBe("/admin");
    expect(loginDestination("ROLE_STAFF", "/admin")).toBe("/admin/bookings");
    expect(loginDestination("ROLE_STAFF", "/admin/reports")).toBe("/admin/bookings");
    expect(loginDestination("ROLE_USER", "/account/bookings/7")).toBe("/account/bookings/7");
    expect(loginDestination("ROLE_USER", null)).toBe("/tours");
  });
});
