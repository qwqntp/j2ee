import type { AdminListSort } from "./types";

export type InvoiceStatusFilter = "All" | "PAID" | "UNPAID";

export type InvoiceListFilters = {
  status?: "PAID" | "UNPAID";
  q?: string;
  sort?: AdminListSort;
};

const INVOICE_STATUSES = new Set(["PAID", "UNPAID"]);
const ADMIN_SORTS = new Set<AdminListSort>([
  "createdAt,asc",
  "createdAt,desc",
  "totalAmount,asc",
  "totalAmount,desc",
]);

type InvoiceListQuery = { page: number; size: number } & InvoiceListFilters;

/** API query for the admin invoice list; the UI-only "All" value is omitted. */
export function buildInvoiceListQuery({ page, size, ...filters }: InvoiceListQuery): string {
  if (filters.status && !INVOICE_STATUSES.has(filters.status)) {
    throw new Error("Invalid status");
  }
  if (filters.sort && !ADMIN_SORTS.has(filters.sort)) {
    throw new Error("Invalid sort");
  }

  const params = new URLSearchParams();
  params.set("page", String(page));
  params.set("size", String(size));
  if (filters.status) params.set("status", filters.status);
  const query = filters.q?.trim();
  if (query) params.set("q", query);
  if (filters.sort) params.set("sort", filters.sort);
  return params.toString();
}

export function toInvoiceStatusParam(
  status: InvoiceStatusFilter,
): "PAID" | "UNPAID" | undefined {
  return status === "All" ? undefined : status;
}
