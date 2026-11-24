"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { ApiError, listAdminPayments, reconcileAdminPayment } from "@/lib/api";
import { buildPaymentQuery, type AdminPayment, type PaymentFilters } from "@/lib/admin-payments";
import { formatDate, formatPrice } from "@/lib/format";
import type { PageData } from "@/lib/types";

const initial: PaymentFilters = { page: 0, status: "", method: "", from: "", to: "", q: "" };

export function AdminPayments() {
  const { getSession } = useAdminAccess();
  const [form, setForm] = useState(initial);
  const [filters, setFilters] = useState(initial);
  const [data, setData] = useState<PageData<AdminPayment> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pending, setPending] = useState<number | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const session = await getSession();
        if (!session || !active) return;
        const result = await listAdminPayments(session.accessToken, buildPaymentQuery(filters));
        if (active) { setData(result); setError(null); }
      } catch (err) {
        if (active) setError(err instanceof Error ? err.message : "Could not load payments.");
      }
    }
    void load();
    return () => { active = false; };
  }, [filters, getSession, reload]);

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
    try { buildPaymentQuery({ ...form, page: 0 }); setError(null); setFilters({ ...form, page: 0 }); }
    catch (err) { setError(err instanceof Error ? err.message : "Invalid filters."); }
  }

  return <div>
    <AdminPageHeading title="Payments" description="Search and reconcile payment attempts." />
    <AdminListPanel>
      <form className="admin-filter-bar flex flex-wrap gap-3" onSubmit={submit}>
        <label>Status <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}><option value="">All</option>{["PENDING", "SUCCESS", "FAILED"].map((s) => <option key={s}>{s}</option>)}</select></label>
        <label>Method <select value={form.method} onChange={(e) => setForm({ ...form, method: e.target.value })}><option value="">All</option>{["CASH", "VNPAY", "MOMO", "CARD"].map((s) => <option key={s}>{s}</option>)}</select></label>
        <label>From <input type="date" value={form.from} onChange={(e) => setForm({ ...form, from: e.target.value })} /></label>
        <label>To <input type="date" value={form.to} onChange={(e) => setForm({ ...form, to: e.target.value })} /></label>
        <label>Booking code or transaction ID <input type="search" maxLength={100} value={form.q} onChange={(e) => setForm({ ...form, q: e.target.value })} /></label>
        <button className="admin-row-action" type="submit">Search</button>
      </form>
      {error && <p role="alert" className="p-4">{error}</p>}
      {notice && <p role="status" className="p-4">{notice}</p>}
      {!data && !error ? <AdminTableSkeleton label="Loading payments…" embedded /> : data && data.content.length === 0 ? <AdminEmptyState title="No payments found" /> : data && <AdminTableRegion label="Payments">
        <table className="w-full text-left text-sm"><thead><tr>{["Time", "Booking", "Amount", "Method", "Status", "Provider transaction", "Actions"].map((s) => <th className="px-3 py-2" key={s}>{s}</th>)}</tr></thead>
          <tbody className="divide-y divide-line">{data.content.map((p) => <tr key={p.id}>
            <td className="px-3 py-3">{formatDate(p.createdAt)} {p.createdAt.slice(11, 16)}</td><td className="px-3 py-3">{p.bookingCode}</td><td className="px-3 py-3">{formatPrice(p.amount)}</td><td className="px-3 py-3">{p.method}</td><td className="px-3 py-3">{p.status}</td><td className="px-3 py-3">{p.providerTransactionId || "—"}</td>
            <td className="px-3 py-3"><Link className="admin-row-action" href={`/account/bookings/${p.bookingId}`}>Open booking</Link> {p.status === "PENDING" && p.method === "VNPAY" && <button className="admin-row-action" disabled={pending !== null} onClick={() => void reconcile(p.id)}>{pending === p.id ? "Reconciling…" : "Reconcile with VNPay"}</button>}</td>
          </tr>)}</tbody></table>
      </AdminTableRegion>}
      {data && <div className="flex items-center justify-between p-4"><button className="admin-row-action" disabled={filters.page === 0} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>Previous</button><span>Page {filters.page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} payments</span><button className="admin-row-action" disabled={!data.hasNext} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>Next</button></div>}
    </AdminListPanel>
  </div>;
}
