import { describe, expect, it } from "vitest";
import { ADMIN_SECTIONS, getAdminLocation, getAdminSections } from "./admin-navigation";

describe("admin navigation", () => {
  it("lists each real top-level management route once", () => {
    expect(ADMIN_SECTIONS.map(({ href }) => href)).toEqual([
      "/admin", "/admin/bookings", "/admin/payments", "/admin/customers", "/admin/accounts", "/admin/promotions",
      "/admin/invoices", "/admin/catalog", "/admin/tours", "/admin/departures",
    ]);
  });

  it("shows staff exactly five operations sections and admin every section", () => {
    expect(getAdminSections("ROLE_STAFF").map(({ label }) => label)).toEqual([
      "Bookings", "Payments", "Customers", "Invoices", "Departures",
    ]);
    expect(getAdminSections("ROLE_ADMIN")).toEqual(ADMIN_SECTIONS);
    expect(getAdminSections("ROLE_USER")).toEqual([]);
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
