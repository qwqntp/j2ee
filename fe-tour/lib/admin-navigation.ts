const ADMIN_ONLY = ["ROLE_ADMIN"] as const;
const OPERATIONS = ["ROLE_ADMIN", "ROLE_STAFF"] as const;

export const ADMIN_SECTIONS = [
  { href: "/admin", label: "Dashboard", icon: "dashboard", roles: ADMIN_ONLY },
  { href: "/admin/bookings", label: "Bookings & payments", icon: "bookings", roles: OPERATIONS },
  { href: "/admin/payments", label: "Payments", icon: "invoices", roles: OPERATIONS },
  { href: "/admin/customers", label: "Customers", icon: "accounts", roles: OPERATIONS },
  { href: "/admin/accounts", label: "Accounts", icon: "accounts", roles: ADMIN_ONLY },
  { href: "/admin/promotions", label: "Promotions", icon: "promotions", roles: ADMIN_ONLY },
  { href: "/admin/invoices", label: "Invoices", icon: "invoices", roles: OPERATIONS },
  { href: "/admin/catalog", label: "Catalog", icon: "catalog", roles: ADMIN_ONLY },
  { href: "/admin/tours", label: "Tours & departures", icon: "tours", roles: ADMIN_ONLY },
  { href: "/admin/departures", label: "Departures", icon: "calendar", roles: OPERATIONS },
] as const;

export type AdminSection = (typeof ADMIN_SECTIONS)[number];

export function getAdminSections(role: string | null | undefined) {
  return ADMIN_SECTIONS.filter((section) => section.roles.some((allowed) => allowed === role))
    .map((section) => role === "ROLE_STAFF" && section.href === "/admin/bookings"
      ? { ...section, label: "Bookings" } : section);
}

export function getAdminLocation(pathname: string) {
  const path = pathname.replace(/\/+$/, "");
  const section = ADMIN_SECTIONS.find(({ href }) =>
    path === href || (href !== "/admin" && path.startsWith(`${href}/`)),
  );
  const isInvoiceDetail = section?.href === "/admin/invoices" && path !== section.href;

  return {
    activeHref: section?.href ?? null,
    title: isInvoiceDetail ? "Invoice details" : (section?.label ?? "Admin workspace"),
    parent: isInvoiceDetail ? section : undefined,
  };
}
