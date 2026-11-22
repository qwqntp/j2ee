export const ADMIN_SECTIONS = [
  { href: "/admin", label: "Dashboard", icon: "dashboard" },
  { href: "/admin/bookings", label: "Bookings & payments", icon: "bookings" },
  { href: "/admin/accounts", label: "Accounts", icon: "accounts" },
  { href: "/admin/promotions", label: "Promotions", icon: "promotions" },
  { href: "/admin/invoices", label: "Invoices", icon: "invoices" },
  { href: "/admin/catalog", label: "Catalog", icon: "catalog" },
  { href: "/admin/tours", label: "Tours & departures", icon: "tours" },
] as const;

export type AdminSection = (typeof ADMIN_SECTIONS)[number];

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
