"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  buildInvoiceListQuery,
  toInvoiceStatusParam,
  type InvoiceStatusFilter,
} from "@/lib/admin-invoices";
import { ApiError, listInvoices } from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminStatusBadge, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { AdminIcon } from "@/components/admin/admin-icons";
import { adminActionStyles } from "@/components/admin/admin-action-styles";
import { useAdminQueryState } from "@/lib/use-admin-query-state";
import { useAdminPrivateSearch } from "@/lib/use-admin-private-search";
import { updateAdminQuery } from "@/lib/admin-query-state";
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
const QUERY_SCHEMA = {
  page: { kind: "page" }, size: { kind: "number", allowed: [10, 20, 50], defaultValue: 10 },
  status: { kind: "enum", allowed: ["PAID", "UNPAID"] },
  sort: { kind: "enum", allowed: ["createdAt", "totalAmount"], defaultValue: "createdAt" },
  direction: { kind: "enum", allowed: ["asc", "desc"], defaultValue: "desc" },
  q: { kind: "enum", allowed: [] },
} as const;

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
  const { getSession, account } = useAdminAccess();
  const { params, update } = useAdminQueryState(QUERY_SCHEMA);
  const queryString = params.toString();
  const [privateQuery, setPrivateQuery] = useAdminPrivateSearch(account.id, "invoices");
  const query = useMemo<CommittedQuery>(() => {
    const values = new URLSearchParams(queryString);
    return { page: Number(values.get("page") ?? "1") - 1, size: Number(values.get("size") ?? "10"),
      status: (values.get("status") ?? undefined) as CommittedQuery["status"],
      q: privateQuery || undefined,
      sort: `${values.get("sort") ?? "createdAt"},${values.get("direction") ?? "desc"}` as AdminListSort };
  }, [queryString, privateQuery]);
  const sourceForm: FilterForm = { status: query.status ?? "All", q: query.q ?? "", sort: query.sort };
  const formKey = JSON.stringify([queryString, sourceForm]);
  const [draft, setDraft] = useState<{ key: string; value: FilterForm } | null>(null);
  const [previousNavigation, setPreviousNavigation] = useState(queryString);
  if (previousNavigation !== queryString) {
    setPreviousNavigation(queryString);
    setDraft(null);
  }
  const form = draft?.key === formKey ? draft.value : sourceForm;
  const waitingForUrl = useRef<string | null>(null);
  const listRequest = useRef<AbortController | null>(null);
  const [invoices, setInvoices] = useState<AdminInvoiceListItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const commitFilters = useCallback((nextForm: FilterForm, nextSize?: number, mode: "push" | "replace" = "push") => {
    try {
      buildInvoiceListQuery({ page: 0, size: nextSize ?? DEFAULT_SIZE, ...toCommittedFilter(nextForm) });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invalid filters");
      return;
    }
    setError(null);
    const [sort, direction] = nextForm.sort.split(",");
    const patch = { page: null, q: null, size: String(nextSize ?? query.size),
      status: toInvoiceStatusParam(nextForm.status) ?? null, sort, direction };
    const target = updateAdminQuery(new URLSearchParams(queryString), QUERY_SCHEMA, patch).toString();
    waitingForUrl.current = target === queryString ? null : target;
    if (target !== queryString || nextForm.q.trim() !== privateQuery) listRequest.current?.abort();
    setPrivateQuery(nextForm.q.trim());
    setDraft(null);
    update(patch, mode);
  }, [privateQuery, query.size, queryString, setPrivateQuery, update]);

  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => {
    if (searchTimer.current !== null) clearTimeout(searchTimer.current);
  }, [commitFilters]);
  function cancelSearch() {
    if (searchTimer.current !== null) clearTimeout(searchTimer.current);
    searchTimer.current = null;
  }

  useEffect(() => {
    if (privateQuery === null || (waitingForUrl.current !== null && waitingForUrl.current !== queryString)) return;
    waitingForUrl.current = null;
    const controller = new AbortController();
    listRequest.current = controller;
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
      if (listRequest.current === controller) listRequest.current = null;
    };
  }, [query, reloadKey, getSession, privateQuery, queryString]);

  function updateForm(patch: Partial<FilterForm>, debounce = false) {
    const next = { ...form, ...patch };
    setDraft({ key: formKey, value: next });
    if (debounce) {
      cancelSearch();
      searchTimer.current = setTimeout(() => commitFilters(next, undefined, "replace"), 400);
    } else {
      cancelSearch();
      commitFilters(next);
    }
  }

  function clearFilters() {
    cancelSearch();
    setDraft(null);
    commitFilters(EMPTY_FORM);
  }

  function changeSize(nextSize: number) {
    cancelSearch();
    commitFilters(form, nextSize);
  }

  if (!loaded) {
    return <AdminTableSkeleton label="Loading invoices…" filters={4} pagination />;
  }

  const filtering = Boolean(query.status || query.q);
  const returnParams = new URLSearchParams();
  for (const key of ["page", "size", "status", "sort", "direction"]) {
    const value = params.get(key);
    if (value) returnParams.set(key, value);
  }
  const returnQuery = returnParams.toString();

  return (
    <div>
      <AdminPageHeading title="Invoices"
        description="Status, search and sort apply to every invoice; pagination uses the server total." />

      <AdminListPanel busy={loading}>
      <form
        aria-label="Filter invoices"
        onSubmit={(event) => {
          event.preventDefault();
          cancelSearch();
          commitFilters(form);
        }}
        className="admin-filter-bar admin-toolbar"
      >
        <label className="admin-toolbar-search">
          <span className="sr-only">Search invoices</span><AdminIcon name="search" className="size-4" />
          <input id="invoice-q" type="search" maxLength={100} placeholder="Booking, customer, email, payment" className="field"
            value={form.q} onChange={(event) => updateForm({ q: event.target.value }, true)} />
        </label>
        <div className="admin-toolbar-row">
          <div className="admin-toolbar-control">
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
          <div className="admin-toolbar-control">
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
          <div className="admin-toolbar-control">
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
          <button type="button" onClick={clearFilters} className="admin-toolbar-ghost">
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
                      href={`/admin/invoices/${invoice.bookingId}${returnQuery ? `?${returnQuery}` : ""}`}
                      className={adminActionStyles.view}
                      aria-label={`View invoice for booking ${invoice.bookingId}`}
                      title="View invoice"
                    >
                      <AdminIcon name="eye" className="size-4" />
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
          onClick={() => update({ page: String(query.page) })}
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
          onClick={() => update({ page: String(query.page + 2) })}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Next
        </button>
      </div>
      </AdminListPanel>
    </div>
  );
}
