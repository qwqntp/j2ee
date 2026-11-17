import type { Invoice } from "./types";

export type InvoiceStatusFilter = "All" | "PAID" | "UNPAID";

export type InvoiceFilter = {
  status: InvoiceStatusFilter;
  query: string;
};

export function filterInvoices(rows: Invoice[], { status, query }: InvoiceFilter): Invoice[] {
  const needle = query.trim().toLowerCase();

  return rows.filter((invoice) => {
    if (status !== "All" && invoice.status !== status) return false;
    if (!needle) return true;

    return [
      invoice.bookingId.toString(),
      invoice.customerName ?? "",
      invoice.customerEmail ?? "",
      invoice.paymentMethod ?? "",
    ].some((field) => field.toLowerCase().includes(needle));
  });
}
