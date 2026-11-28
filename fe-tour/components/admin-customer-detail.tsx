"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { getAdminCustomer, listAdminCustomerBookings } from "@/lib/api";
import { formatPrice } from "@/lib/format";
import { useAdminQueryState } from "@/lib/use-admin-query-state";
import type { AdminCustomer, AdminCustomerBooking } from "@/lib/admin-customers";
import type { PageData } from "@/lib/types";

const QUERY_SCHEMA = { page: { kind: "page" } } as const;

export function AdminCustomerDetail({ id }: { id: number }) {
  const { getSession } = useAdminAccess();
  const [customer, setCustomer] = useState<AdminCustomer | null>(null);
  const [bookings, setBookings] = useState<PageData<AdminCustomerBooking> | null>(null);
  const { params, update } = useAdminQueryState(QUERY_SCHEMA);
  const page = Number(params.get("page") ?? "1") - 1;
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const session = await getSession();
        if (!session || !active) return;
        const [profile, results] = await Promise.all([getAdminCustomer(session.accessToken, id), listAdminCustomerBookings(session.accessToken, id, page)]);
        if (active) { setCustomer(profile); setBookings(results); setError(null); }
      } catch (err) { if (active) setError(err instanceof Error ? err.message : "Could not load customer."); }
    }
    void load();
    return () => { active = false; };
  }, [getSession, id, page, reload]);

  if (error) return <p role="alert">{error} <button className="admin-row-action" onClick={() => setReload((n) => n + 1)}>Try again</button></p>;
  if (!customer || !bookings) return <AdminTableSkeleton label="Loading customer…" />;
  return <div><AdminPageHeading title={customer.name} description="Customer profile and bookings" />
    <div className="admin-panel p-4"><dl className="grid gap-3 sm:grid-cols-2">{[["Email", customer.email], ["Phone", customer.phone], ["Address", customer.address], ["Date of birth", customer.dateOfBirth]].map(([label, value]) => <div key={label}><dt className="font-semibold">{label}</dt><dd>{value || "—"}</dd></div>)}</dl></div>
    <h2 className="mt-6 text-xl font-semibold">Bookings</h2>
    <AdminListPanel>{bookings.content.length === 0 ? <AdminEmptyState title="No bookings found" /> : <AdminTableRegion label="Customer bookings"><table className="w-full text-left text-sm"><thead><tr>{["Code", "Tour", "Status", "Total", "Actions"].map((v) => <th className="px-3 py-2" key={v}>{v}</th>)}</tr></thead><tbody className="divide-y divide-line">{bookings.content.map((b) => <tr key={b.id}><td className="px-3 py-3">{b.code}</td><td className="px-3 py-3">{b.tourName}</td><td className="px-3 py-3">{b.status}</td><td className="px-3 py-3">{formatPrice(b.total)}</td><td className="px-3 py-3"><Link className="admin-row-action" href={`/account/bookings/${b.id}`}>Booking</Link> <Link className="admin-row-action" href={`/admin/invoices/${b.id}`}>Invoice</Link></td></tr>)}</tbody></table></AdminTableRegion>}
      <div className="flex items-center justify-between p-4"><button className="admin-row-action" disabled={page === 0} onClick={() => update({ page: String(page) })}>Previous</button><span>Page {page + 1} of {Math.max(1, bookings.totalPages)}</span><button className="admin-row-action" disabled={!bookings.hasNext} onClick={() => update({ page: String(page + 2) })}>Next</button></div>
    </AdminListPanel>
  </div>;
}
