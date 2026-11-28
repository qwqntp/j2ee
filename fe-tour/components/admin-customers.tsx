"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { AdminIcon } from "@/components/admin/admin-icons";
import { adminActionStyles } from "@/components/admin/admin-action-styles";
import { listAdminCustomers } from "@/lib/api";
import { customerInitials } from "@/lib/customer-initials";
import { privateSearchStore } from "@/lib/admin-private-search";
import { useAdminQueryState } from "@/lib/use-admin-query-state";
import type { AdminCustomer } from "@/lib/admin-customers";
import type { PageData } from "@/lib/types";

const QUERY_SCHEMA = { page: { kind: "page" }, q: { kind: "enum", allowed: [] } } as const;
const subscribe = () => () => {};
const serverSearch = () => null;

export function AdminCustomers() {
  const { getSession, account } = useAdminAccess();
  const { params, update } = useAdminQueryState(QUERY_SCHEMA);
  const page = Number(params.get("page") ?? "1") - 1;
  const readSearch = useCallback(() => privateSearchStore().read(account.id, "customers"), [account.id]);
  const restored = useSyncExternalStore(subscribe, readSearch, serverSearch);
  const [draft, setDraft] = useState<{ key: string; value: string } | null>(null);
  const navigationKey = params.toString();
  const [previousNavigation, setPreviousNavigation] = useState(navigationKey);
  if (previousNavigation !== navigationKey) {
    setPreviousNavigation(navigationKey);
    setDraft(null);
  }
  const [committed, setCommitted] = useState<string | null>(null);
  const search = draft?.key === navigationKey ? draft.value : (committed ?? restored ?? "");
  const query = committed ?? restored;
  const waitingForFirstPage = useRef(false);
  const requestVersion = useRef(0);
  const [data, setData] = useState<PageData<AdminCustomer> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    if (query === null || (waitingForFirstPage.current && page !== 0)) return;
    waitingForFirstPage.current = false;
    const currentQuery = query;
    let active = true;
    const version = ++requestVersion.current;
    async function load() {
      try {
        const session = await getSession();
        if (!session || !active || requestVersion.current !== version) return;
        const result = await listAdminCustomers(session.accessToken, page, currentQuery);
        if (active && requestVersion.current === version) { setData(result); setError(null); }
      } catch (err) { if (active && requestVersion.current === version) setError(err instanceof Error ? err.message : "Could not load customers."); }
    }
    void load();
    return () => { active = false; };
  }, [getSession, page, query, reload]);

  function commitSearch(value: string) {
    const next = value.trim();
    privateSearchStore().write(account.id, "customers", next);
    waitingForFirstPage.current = page !== 0;
    if (page !== 0 || next !== query) requestVersion.current++;
    setCommitted(next);
    update({ page: null, q: null });
  }

  return <div><AdminPageHeading title="Customers" description="Find a customer and review their bookings." />
    <AdminListPanel>
      <form className="admin-filter-bar admin-toolbar admin-toolbar-search-only" onSubmit={(e) => { e.preventDefault(); commitSearch(search); }}>
        <label className="admin-toolbar-search"><span className="sr-only">Search name, email or phone</span><AdminIcon name="search" className="size-4" /><input className="field" type="search" placeholder="Name, email or phone" maxLength={100} value={search} onChange={(e) => setDraft({ key: navigationKey, value: e.target.value })} /></label>
        <div className="admin-toolbar-row">
          <button className="btn-primary admin-toolbar-primary" type="submit">Search</button>
          <button className="admin-toolbar-ghost" type="button" onClick={() => { setDraft(null); commitSearch(""); }}>Reset</button>
        </div>
      </form>
      {error && <p role="alert" className="p-4">{error} <button className="admin-row-action" onClick={() => setReload((n) => n + 1)}>Try again</button></p>}
      {!data && !error ? <AdminTableSkeleton label="Loading customers…" embedded /> : data && data.content.length === 0 ? <AdminEmptyState title="No customers found" /> : data && <AdminTableRegion label="Customers">
        <table className="w-full text-left text-sm"><thead><tr>{["Name", "Email", "Phone", "Actions"].map((v) => <th className="px-3 py-2" key={v}>{v}</th>)}</tr></thead>
          <tbody className="divide-y divide-line">{data.content.map((c) => <tr key={c.id}><td><div className="flex items-center gap-3"><span aria-hidden="true" className="grid size-8 shrink-0 place-items-center rounded-full bg-sage/30 text-xs font-semibold text-forest-deep">{customerInitials(c.name)}</span><span className="font-medium">{c.name}</span></div></td><td className="whitespace-nowrap">{c.email || "—"}</td><td className="whitespace-nowrap">{c.phone || "—"}</td><td><Link className={adminActionStyles.view} href={`/admin/customers/${c.id}`} aria-label={`View customer ${c.name}`} title="View customer"><AdminIcon name="eye" className="size-4" /></Link></td></tr>)}</tbody></table>
      </AdminTableRegion>}
      {data && <div className="flex items-center justify-between p-4"><button className="admin-row-action" disabled={page === 0} onClick={() => update({ page: String(page) })}>Previous</button><span>Page {page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} customers</span><button className="admin-row-action" disabled={!data.hasNext} onClick={() => update({ page: String(page + 2) })}>Next</button></div>}
    </AdminListPanel>
  </div>;
}
