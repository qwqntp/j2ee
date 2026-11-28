import type { AuthSession } from "./types";

export function isConsoleRole(role: string | null | undefined): boolean {
  return role === "ROLE_ADMIN" || role === "ROLE_STAFF";
}

export function hasConsoleAccess(validated: AuthSession | null, stored: AuthSession | null): boolean {
  return !!validated && !!stored && validated.account.id === stored.account.id
    && validated.account.roleName === stored.account.roleName && isConsoleRole(validated.account.roleName);
}

export function consoleHome(role: string | null | undefined): string {
  return role === "ROLE_ADMIN" ? "/admin" : "/admin/bookings";
}

export function canAccessAdminPage(role: string | null | undefined, pathname: string): boolean {
  const path = pathname.replace(/\/+$/, "");
  if (role === "ROLE_ADMIN") return path === "/admin" || path.startsWith("/admin/");
  if (role !== "ROLE_STAFF") return false;
  return /^\/admin\/(bookings|payments|customers|invoices|departures)$/.test(path)
    || /^\/admin\/(customers|invoices)\/[0-9]+$/.test(path)
    || /^\/admin\/departures\/[0-9]+\/passengers$/.test(path);
}

export function loginDestination(role: string | null | undefined, next: string | null): string {
  if (isConsoleRole(role)) return consoleHome(role);
  return next || "/tours";
}
