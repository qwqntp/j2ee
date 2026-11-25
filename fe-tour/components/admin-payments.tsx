"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminStatusBadge, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminIcon } from "@/components/admin/admin-icons";
import { adminActionStyles } from "@/components/admin/admin-action-styles";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { ApiError, listAdminPayments, reconcileAdminPayment } from "@/lib/api";
import { buildPaymentQuery, type AdminPayment, type PaymentFilters } from "@/lib/admin-payments";
import { formatDate, formatPrice } from "@/lib/format";
import { useAdminQueryState } from "@/lib/use-admin-query-state";
import { useAdminPrivateSearch } from "@/lib/use-admin-private-search";
import { updateAdminQuery } from "@/lib/admin-query-state";
import type { PageData } from "@/lib/types";

const initial: PaymentFilters = { page: 0, status: "", method: "", from: "", to: "", q: "" };
const QUERY_SCHEMA = {
  page: { kind: "page" },
  status: { kind: "enum", allowed: ["PENDING", "SUCCESS", "FAILED"] },
  method: { kind: "enum", allowed: ["CASH", "VNPAY", "MOMO", "CARD"] },
  from: { kind: "date" }, to: { kind: "date" },
  q: { kind: "enum", allowed: [] },
} as const;

export function AdminPayments() {
  const { getSession, account } = useAdminAccess();
  const { params, update } = useAdminQueryState(QUERY_SCHEMA);
  const queryString = params.toString();
  const [privateQuery, setPrivateQuery] = useAdminPrivateSearch(account.id, "payments");
  const filters = useMemo<PaymentFilters>(() => {
    const values = new URLSearchParams(queryString);
    return { page: Number(values.get("page") ?? "1") - 1, status: values.get("status") ?? "",
      method: values.get("method") ?? "", from: values.get("from") ?? "", to: values.get("to") ?? "",
      q: privateQuery ?? "" };
  }, [queryString, privateQuery]);
  const formKey = JSON.stringify([queryString, filters.q]);
  const [draft, setDraft] = useState<{ key: string; form: PaymentFilters } | null>(null);
  const [previousNavigation, setPreviousNavigation] = useState(queryString);
  if (previousNavigation !== queryString) {
    setPreviousNavigation(queryString);
    setDraft(null);
  }
  const form = draft?.key === formKey ? draft.form : filters;
  const setForm = (next: PaymentFilters) => setDraft({ key: formKey, form: next });
  const waitingForUrl = useRef<string | null>(null);
  const requestVersion = useRef(0);
  const [data, setData] = useState<PageData<AdminPayment> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pending, setPending] = useState<number | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (privateQuery === null) return;
    if (waitingForUrl.current !== null && waitingForUrl.current !== queryString) return;
    waitingForUrl.current = null;
    let active = true;
    const version = ++requestVersion.current;
    async function load() {
      try {
        const session = await getSession();
        if (!session || !active || requestVersion.current !== version) return;
        const result = await listAdminPayments(session.accessToken, buildPaymentQuery(filters));
        if (active && requestVersion.current === version) { setData(result); setError(null); }
      } catch (err) {
        if (active && requestVersion.current === version) setError(err instanceof Error ? err.message : "Could not load payments.");
      }
    }
    void load();
    return () => { active = false; };
  }, [filters, getSession, reload, privateQuery, queryString]);

  async function reconcile(id: number) {
    setPending(id); setError(null); setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      const result = await reconcileAdminPayment(session.accessToken, id);
      setNotice(`VNPay result: ${result.status ?? "unknown"}${result.reason ? ` (${result.reason})` : ""}.`);
      setReload((n) => n + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not reconcile payment.");
    } finally { setPending(null); }
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    try { buildPaymentQuery({ ...form, page: 0 }); setError(null); commit(form); }
    catch (err) { setError(err instanceof Error ? err.message : "Invalid filters."); }
  }

  function commit(next: PaymentFilters) {
    const patch = { page: null, q: null, status: next.status, method: next.method, from: next.from, to: next.to };
    const target = updateAdminQuery(params, QUERY_SCHEMA, patch).toString();
    waitingForUrl.current = target === queryString ? null : target;
    if (target !== queryString || next.q.trim() !== privateQuery) requestVersion.current++;
    setPrivateQuery(next.q.trim());
    setDraft(null);
    update(patch);
  }

  async function copyTransaction(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setNotice("Transaction ID copied.");
    } catch {
      setNotice(`Could not copy the transaction ID: ${value}`);
    }
  }

  return <div>
    <AdminPageHeading title="Payments" description="Search and reconcile payment attempts." />
    <AdminListPanel>
      <form className="admin-filter-bar admin-toolbar" aria-label="Filter payments" onSubmit={submit}>
        <label className="admin-toolbar-search"><span className="sr-only">Search booking code or transaction ID</span><AdminIcon name="search" className="size-4" /><input className="field" type="search" placeholder="Booking code or transaction ID" maxLength={100} value={form.q} onChange={(e) => setForm({ ...form, q: e.target.value })} /></label>
        <div className="admin-toolbar-row">
          <label className="admin-toolbar-control">Status<select className="field" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}><option value="">All statuses</option>{["PENDING", "SUCCESS", "FAILED"].map((s) => <option key={s}>{s}</option>)}</select></label>
          <label className="admin-toolbar-control">Method<select className="field" value={form.method} onChange={(e) => setForm({ ...form, method: e.target.value })}><option value="">All methods</option>{["CASH", "VNPAY", "MOMO", "CARD"].map((s) => <option key={s}>{s}</option>)}</select></label>
          <div className="admin-toolbar-dates">
            <label className="admin-toolbar-control">From<input className="field" type="date" value={form.from} onChange={(e) => setForm({ ...form, from: e.target.value })} /></label>
            <label className="admin-toolbar-control">To<input className="field" type="date" value={form.to} onChange={(e) => setForm({ ...form, to: e.target.value })} /></label>
          </div>
          <div className="admin-filter-actions"><button className="btn-primary admin-toolbar-primary" type="submit">Search</button><button className="admin-toolbar-ghost" type="button" onClick={() => { commit(initial); setError(null); }}>Reset</button></div>
        </div>
      </form>
      {error && <p role="alert" className="admin-notice">{error}</p>}
      {notice && <p role="status" className="admin-refresh">{notice}</p>}
      {!data && !error ? <AdminTableSkeleton label="Loading payments…" embedded /> : data && data.content.length === 0 ? <AdminEmptyState title="No payments found" /> : data && <AdminTableRegion label="Payments">
        <table className="admin-payments-table w-full text-left text-sm"><thead><tr>{["Time", "Booking", "Amount", "Method", "Status", "Provider transaction", "Actions"].map((s) => <th className={s === "Amount" ? "text-right" : ""} key={s}>{s}</th>)}</tr></thead>
          <tbody className="divide-y divide-line">{data.content.map((p) => <tr key={p.id}>
            <td className="whitespace-nowrap"><span className="block">{formatDate(p.createdAt)}</span><span className="block text-xs text-muted">{p.createdAt.slice(11, 16)}</span></td>
            <td className="whitespace-nowrap font-medium">{p.bookingCode}</td><td className="whitespace-nowrap text-right tabular-nums">{formatPrice(p.amount)}</td><td className="whitespace-nowrap">{p.method}</td>
            <td><AdminStatusBadge tone={p.status === "SUCCESS" ? "positive" : p.status === "PENDING" ? "warning" : p.status === "FAILED" ? "danger" : "neutral"}>{p.status}</AdminStatusBadge></td>
            <td>{p.providerTransactionId ? <div className="admin-identifier"><span title={p.providerTransactionId} tabIndex={0}>{p.providerTransactionId}</span><button type="button" className="admin-copy-action" aria-label={`Copy transaction ID ${p.providerTransactionId}`} title="Copy transaction ID" onClick={() => void copyTransaction(p.providerTransactionId!)}><AdminIcon name="copy" className="size-4" /></button></div> : "—"}</td>
            <td><div className="flex items-center gap-2 whitespace-nowrap"><Link className={adminActionStyles.view} href={`/account/bookings/${p.bookingId}`} aria-label={`Open booking ${p.bookingCode}`} title="Open booking"><AdminIcon name="eye" className="size-4" /></Link> {p.status === "PENDING" && p.method === "VNPAY" && <button className="admin-row-action" disabled={pending !== null} onClick={() => void reconcile(p.id)}>{pending === p.id ? "Reconciling…" : "Reconcile"}</button>}</div></td>
          </tr>)}</tbody></table>
      </AdminTableRegion>}
      {data && <div className="admin-pagination"><button className="admin-row-action" disabled={filters.page === 0} onClick={() => update({ page: String(filters.page) })}>Previous</button><span className="text-sm text-muted">Page {filters.page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} payments</span><button className="admin-row-action" disabled={!data.hasNext} onClick={() => update({ page: String(filters.page + 2) })}>Next</button></div>}
    </AdminListPanel>
  </div>;
}
