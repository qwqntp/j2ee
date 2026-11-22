"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { filterInvoices, type InvoiceStatusFilter } from "@/lib/admin-invoices";
import { ApiError, listInvoices } from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminStatusBadge, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { formatDate, formatPrice } from "@/lib/format";
import type { Invoice } from "@/lib/types";

const STATUSES: InvoiceStatusFilter[] = ["All", "PAID", "UNPAID"];
const PAGE_SIZE = 100;

export function AdminInvoices() {
  const { getSession } = useAdminAccess();
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [status, setStatus] = useState<InvoiceStatusFilter>("All");
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const result = await listInvoices(session.accessToken, page, PAGE_SIZE);
        if (!active) return;
        setInvoices(result.content);
        setHasNext(result.hasNext);
        setError(null);
      } catch (err) {
        if (!active) return;
        setError(err instanceof ApiError ? err.message : "Could not load invoices. Try again.");
      } finally {
        if (active) {
          setLoading(false);
          setLoaded(true);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [page, getSession]);

  const rows = filterInvoices(invoices, { status, query });

  if (!loaded) {
    return <AdminTableSkeleton label="Loading invoices…" filters={2} pagination />;
  }

  return (
    <div>
      <AdminPageHeading title="Invoices"
        description="100 invoices per page. Status and search filters apply to the current page only." />

      <AdminListPanel busy={loading}>
      <form
        aria-label="Filter invoices"
        onSubmit={(event) => event.preventDefault()}
        className="admin-filter-bar"
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="invoice-status" className="label">
              Status
            </label>
            <select
              id="invoice-status"
              className="field"
              value={status}
              onChange={(event) => setStatus(event.target.value as InvoiceStatusFilter)}
            >
              {STATUSES.map((option) => (
                <option key={option} value={option}>
                  {option === "All" ? "All statuses" : option}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="invoice-q" className="label">
              Search
            </label>
            <input
              id="invoice-q"
              type="search"
              maxLength={100}
              placeholder="Booking, customer, email, payment"
              className="field"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </div>
        </div>
      </form>

      {error ? (
        <p
          role="alert"
          className="admin-notice mt-5"
        >
          {error}
        </p>
      ) : null}

      {rows.length === 0 ? (
        !error && <AdminEmptyState
          title={query.trim() || status !== "All" ? "No invoices match these filters." : "No invoices yet."}
          description={query.trim() || status !== "All" ? "Try a different search or status." : undefined}
        />
      ) : (
        <AdminTableRegion label="Invoices">
          <table className="w-full min-w-[52rem] text-left text-sm">
            <thead className="border-b border-line bg-sage/10 text-[11px] uppercase tracking-[0.12em] text-muted">
              <tr>
                <th className="px-4 py-3 font-semibold">Invoice</th>
                <th className="px-4 py-3 font-semibold">Booking</th>
                <th className="px-4 py-3 font-semibold">Created</th>
                <th className="px-4 py-3 font-semibold">Customer</th>
                <th className="px-4 py-3 font-semibold">Payment</th>
                <th className="px-4 py-3 font-semibold">Total</th>
                <th className="px-4 py-3 font-semibold">Status</th>
                <th className="px-4 py-3 font-semibold">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((invoice) => (
                <tr key={invoice.id} className="align-top">
                  <td className="px-4 py-4 font-medium text-charcoal">#{invoice.id}</td>
                  <td className="px-4 py-4 text-muted">{invoice.bookingId}</td>
                  <td className="px-4 py-4 text-muted">{formatDate(invoice.createdAt)}</td>
                  <td className="px-4 py-4 text-charcoal">
                    {invoice.customerName ?? "—"}
                    {invoice.customerEmail ? (
                      <span className="block text-xs text-muted">{invoice.customerEmail}</span>
                    ) : null}
                  </td>
                  <td className="px-4 py-4 text-muted">{invoice.paymentMethod ?? "—"}</td>
                  <td className="px-4 py-4 text-charcoal">{formatPrice(invoice.totalAmount)}</td>
                  <td className="px-4 py-4">
                    <AdminStatusBadge tone={invoice.status === "PAID" ? "positive" : invoice.status === "UNPAID" ? "warning" : "neutral"}>
                      {invoice.status}
                    </AdminStatusBadge>
                  </td>
                  <td className="px-4 py-4">
                    <Link
                      href={`/admin/invoices/${invoice.bookingId}`}
                      className="admin-row-action"
                    >
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </AdminTableRegion>
      )}

      <div className="admin-pagination mt-5">
        <button
          type="button"
          disabled={page === 0 || loading}
          onClick={() => setPage((current) => Math.max(0, current - 1))}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Previous
        </button>
        <p className="text-center text-sm text-muted">Page {page + 1} · {rows.length} shown</p>
        <button
          type="button"
          disabled={!hasNext || loading}
          onClick={() => setPage((current) => current + 1)}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Next
        </button>
      </div>
      </AdminListPanel>
    </div>
  );
}
