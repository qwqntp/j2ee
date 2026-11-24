"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { AdminIcon } from "@/components/admin/admin-icons";
import { adminActionStyles } from "@/components/admin/admin-action-styles";
import { listAdminCustomers } from "@/lib/api";
import { customerInitials } from "@/lib/customer-initials";
import type { AdminCustomer } from "@/lib/admin-customers";
import type { PageData } from "@/lib/types";

export function AdminCustomers() {
  const { getSession } = useAdminAccess();
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageData<AdminCustomer> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const session = await getSession();
        if (!session || !active) return;
        const result = await listAdminCustomers(session.accessToken, page, query);
        if (active) { setData(result); setError(null); }
      } catch (err) { if (active) setError(err instanceof Error ? err.message : "Could not load customers."); }
    }
    void load();
    return () => { active = false; };
  }, [getSession, page, query, reload]);

  return <div><AdminPageHeading title="Customers" description="Find a customer and review their bookings." />
    <AdminListPanel>
      <form className="admin-filter-bar admin-toolbar admin-toolbar-search-only" onSubmit={(e) => { e.preventDefault(); setPage(0); setQuery(search.trim()); }}>
        <label className="admin-toolbar-search"><span className="sr-only">Search name, email or phone</span><AdminIcon name="search" className="size-4" /><input className="field" type="search" placeholder="Name, email or phone" maxLength={100} value={search} onChange={(e) => setSearch(e.target.value)} /></label>
        <div className="admin-toolbar-row">
          <button className="btn-primary admin-toolbar-primary" type="submit">Search</button>
          <button className="admin-toolbar-ghost" type="button" onClick={() => { setSearch(""); setQuery(""); setPage(0); }}>Reset</button>
        </div>
      </form>
      {error && <p role="alert" className="p-4">{error} <button className="admin-row-action" onClick={() => setReload((n) => n + 1)}>Try again</button></p>}
      {!data && !error ? <AdminTableSkeleton label="Loading customers…" embedded /> : data && data.content.length === 0 ? <AdminEmptyState title="No customers found" /> : data && <AdminTableRegion label="Customers">
        <table className="w-full text-left text-sm"><thead><tr>{["Name", "Email", "Phone", "Actions"].map((v) => <th className="px-3 py-2" key={v}>{v}</th>)}</tr></thead>
          <tbody className="divide-y divide-line">{data.content.map((c) => <tr key={c.id}><td><div className="flex items-center gap-3"><span aria-hidden="true" className="grid size-8 shrink-0 place-items-center rounded-full bg-sage/30 text-xs font-semibold text-forest-deep">{customerInitials(c.name)}</span><span className="font-medium">{c.name}</span></div></td><td className="whitespace-nowrap">{c.email || "—"}</td><td className="whitespace-nowrap">{c.phone || "—"}</td><td><Link className={adminActionStyles.view} href={`/admin/customers/${c.id}`} aria-label={`View customer ${c.name}`} title="View customer"><AdminIcon name="eye" className="size-4" /></Link></td></tr>)}</tbody></table>
      </AdminTableRegion>}
      {data && <div className="flex items-center justify-between p-4"><button className="admin-row-action" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} customers</span><button className="admin-row-action" disabled={!data.hasNext} onClick={() => setPage(page + 1)}>Next</button></div>}
    </AdminListPanel>
  </div>;
}
