import { describe, expect, it } from "vitest";
import { ADMIN_SECTIONS, getAdminLocation } from "./admin-navigation";

describe("admin navigation", () => {
  it("lists each real top-level management route once", () => {
    expect(ADMIN_SECTIONS.map(({ href }) => href)).toEqual([
      "/admin", "/admin/bookings", "/admin/payments", "/admin/customers", "/admin/accounts", "/admin/promotions",
      "/admin/invoices", "/admin/catalog", "/admin/tours",
    ]);
  });

  it("highlights Dashboard only on the admin index", () => {
    expect(getAdminLocation("/admin")).toMatchObject({ activeHref: "/admin", title: "Dashboard" });
    expect(getAdminLocation("/admin/missing").activeHref).toBeNull();
  });

  it("keeps Invoices active while naming the nested detail page", () => {
    expect(getAdminLocation("/admin/invoices/42")).toMatchObject({
      activeHref: "/admin/invoices", title: "Invoice details",
    });
  });

  it("does not highlight misleading path prefixes", () => {
    expect(getAdminLocation("/adminish").activeHref).toBeNull();
    expect(getAdminLocation("/admin/invoices-extra").activeHref).toBeNull();
  });
});
