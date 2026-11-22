"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  buildInvoiceListQuery,
  toInvoiceStatusParam,
  type InvoiceStatusFilter,
} from "@/lib/admin-invoices";
import { ApiError, listInvoices } from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminStatusBadge, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { createDebouncedCommit, resetAdminPage } from "@/lib/booking-list";
import { formatDate, formatPrice } from "@/lib/format";
import type { AdminInvoiceListItem, AdminListSort } from "@/lib/types";

const STATUSES: InvoiceStatusFilter[] = ["All", "PAID", "UNPAID"];
const DEFAULT_SIZE = 10;
const PAGE_SIZES = [10, 20, 50];
const SORTS: Array<{ value: AdminListSort; label: string }> = [
  { value: "createdAt,desc", label: "Newest first" },
  { value: "createdAt,asc", label: "Oldest first" },
  { value: "totalAmount,desc", label: "Total: high to low" },
  { value: "totalAmount,asc", label: "Total: low to high" },
];

type FilterForm = {
  status: InvoiceStatusFilter;
  q: string;
  sort: AdminListSort;
};

type CommittedQuery = {
  page: number;
  size: number;
  status: "PAID" | "UNPAID" | undefined;
  q: string | undefined;
  sort: AdminListSort;
};

const EMPTY_FORM: FilterForm = { status: "All", q: "", sort: "createdAt,desc" };

function toCommittedFilter(form: FilterForm) {
  return {
    status: toInvoiceStatusParam(form.status),
    q: form.q.trim() || undefined,
    sort: form.sort,
  };
}

export function AdminInvoices() {
  const { getSession } = useAdminAccess();
  const [invoices, setInvoices] = useState<AdminInvoiceListItem[]>([]);
  const [query, setQuery] = useState<CommittedQuery>({
    page: 0,
    size: DEFAULT_SIZE,
    ...toCommittedFilter(EMPTY_FORM),
  });
  const [form, setForm] = useState<FilterForm>(EMPTY_FORM);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const commitFilters = useCallback((nextForm: FilterForm, nextSize?: number) => {
    try {
      buildInvoiceListQuery({ page: 0, size: nextSize ?? DEFAULT_SIZE, ...toCommittedFilter(nextForm) });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invalid filters");
      return;
    }
    setError(null);
    setQuery((current) => resetAdminPage(current, {
      size: nextSize ?? current.size,
      ...toCommittedFilter(nextForm),
    }));
  }, []);

  const debouncedSearch = useState(() =>
    createDebouncedCommit<FilterForm>((next) => commitFilters(next)),
  )[0];
  useEffect(() => () => debouncedSearch.cancel(), [debouncedSearch]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const result = await listInvoices(
          session.accessToken,
          query.page,
          query.size,
          { status: query.status, q: query.q, sort: query.sort },
          controller.signal,
        );
        if (!active) return;
        setInvoices(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        setHasNext(result.hasNext);
        setError(null);
      } catch (err) {
        if (!active || controller.signal.aborted) return;
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
      controller.abort();
    };
  }, [query, reloadKey, getSession]);

  function updateForm(patch: Partial<FilterForm>, debounce = false) {
    const next = { ...form, ...patch };
    setForm(next);
    if (debounce) {
      debouncedSearch.schedule(next);
    } else {
      debouncedSearch.cancel();
      commitFilters(next);
    }
  }

  function clearFilters() {
    debouncedSearch.cancel();
    setForm(EMPTY_FORM);
    commitFilters(EMPTY_FORM);
  }

  function changeSize(nextSize: number) {
    debouncedSearch.cancel();
    commitFilters(form, nextSize);
  }

  if (!loaded) {
    return <AdminTableSkeleton label="Loading invoices…" filters={4} pagination />;
  }

  const filtering = Boolean(query.status || query.q);

  return (
    <div>
      <AdminPageHeading title="Invoices"
        description="Status, search and sort apply to every invoice; pagination uses the server total." />

      <AdminListPanel busy={loading}>
      <form
        aria-label="Filter invoices"
        onSubmit={(event) => {
          event.preventDefault();
          debouncedSearch.cancel();
          commitFilters(form);
        }}
        className="admin-filter-bar"
      >
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <label htmlFor="invoice-status" className="label">
              Status
            </label>
            <select
              id="invoice-status"
              className="field"
              value={form.status}
              onChange={(event) => updateForm({ status: event.target.value as InvoiceStatusFilter })}
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
              value={form.q}
              onChange={(event) => updateForm({ q: event.target.value }, true)}
            />
          </div>
          <div>
            <label htmlFor="invoice-sort" className="label">
              Sort
            </label>
            <select
              id="invoice-sort"
              className="field"
              value={form.sort}
              onChange={(event) => updateForm({ sort: event.target.value as AdminListSort })}
            >
              {SORTS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="invoice-size" className="label">
              Per page
            </label>
            <select
              id="invoice-size"
              className="field"
              value={query.size}
              onChange={(event) => changeSize(Number(event.target.value))}
            >
              {PAGE_SIZES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="mt-4 flex gap-2">
          <button type="button" onClick={clearFilters} className="btn-secondary min-h-0 px-4 py-2 text-xs">
            Clear
          </button>
        </div>
      </form>

      {error ? (
        <div role="alert" className="admin-notice mt-5">
          <p>{error}</p>
          <button
            type="button"
            onClick={() => setReloadKey((key) => key + 1)}
            className="btn-secondary mt-3 min-h-0 px-4 py-2 text-xs"
          >
            Try again
          </button>
        </div>
      ) : null}

      {invoices.length === 0 ? (
        !error && <AdminEmptyState
          title={filtering ? "No invoices match these filters." : "No invoices yet."}
          description={filtering ? "Try a different search or status." : undefined}
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
              {invoices.map((invoice) => (
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
          disabled={query.page === 0 || loading}
          onClick={() => setQuery((current) => ({ ...current, page: Math.max(0, current.page - 1) }))}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Previous
        </button>
        <div className="text-center text-sm text-muted">
          <p>
            Page {query.page + 1} of {Math.max(totalPages, 1)}
          </p>
          <p>{totalElements} invoices</p>
        </div>
        <button
          type="button"
          disabled={!hasNext || loading}
          onClick={() => setQuery((current) => ({ ...current, page: current.page + 1 }))}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Next
        </button>
      </div>
      </AdminListPanel>
    </div>
  );
}
