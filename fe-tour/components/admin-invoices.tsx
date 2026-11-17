"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { filterInvoices, type InvoiceStatusFilter } from "@/lib/admin-invoices";
import { ApiError, listInvoices } from "@/lib/api";
import { ensureSession } from "@/lib/auth";
import { formatDate, formatPrice } from "@/lib/format";
import type { Invoice } from "@/lib/types";

const STATUSES: InvoiceStatusFilter[] = ["All", "PAID", "UNPAID"];
const PAGE_SIZE = 100;

export function AdminInvoices() {
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [status, setStatus] = useState<InvoiceStatusFilter>("All");
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await ensureSession();
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
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [page]);

  const rows = filterInvoices(invoices, { status, query });

  if (loading && invoices.length === 0) {
    return <p className="text-sm text-muted">Loading invoices…</p>;
  }

  return (
    <div>
      <p className="eyebrow text-forest">Billing</p>
      <h2 className="mt-2 font-display text-3xl tracking-tight">Invoices</h2>

      <form
        aria-label="Filter invoices"
        onSubmit={(event) => event.preventDefault()}
        className="mt-6 rounded-[4px] border border-line bg-white p-5"
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
          className="mt-6 rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-4 text-sm text-terracotta"
        >
          {error}
        </p>
      ) : null}

      {rows.length === 0 ? (
        <div className="mt-8 rounded-[4px] border border-line bg-white p-6 text-sm text-muted">
          No invoices yet.
        </div>
      ) : (
        <div className="mt-6 overflow-x-auto rounded-[4px] border border-line bg-white">
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
                    <span
                      className={`inline-flex rounded-[2px] border px-2 py-1 text-xs font-medium ${
                        invoice.status === "PAID"
                          ? "border-forest/40 bg-white text-forest"
                          : "border-terracotta/50 bg-white text-terracotta"
                      }`}
                    >
                      {invoice.status}
                    </span>
                  </td>
                  <td className="px-4 py-4">
                    <Link
                      href={`/admin/invoices/${invoice.bookingId}`}
                      className="rounded-[2px] border border-charcoal/25 px-3 py-1.5 text-xs font-medium text-charcoal transition-colors hover:border-forest hover:text-forest"
                    >
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="mt-6 flex items-center justify-between gap-4">
        <button
          type="button"
          disabled={page === 0 || loading}
          onClick={() => setPage((current) => Math.max(0, current - 1))}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Previous
        </button>
        <p className="text-center text-sm text-muted">Page {page + 1}</p>
        <button
          type="button"
          disabled={!hasNext || loading}
          onClick={() => setPage((current) => current + 1)}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Next
        </button>
      </div>
    </div>
  );
}
