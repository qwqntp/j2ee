"use client";

import { useEffect, useState } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { ApiError, getAdminPassengers } from "@/lib/api";
import { buildPassengerCsv, type PassengerManifest } from "@/lib/admin-passengers";
import { formatDate } from "@/lib/format";

export function AdminPassengers({ id }: { id: number }) {
  const { getSession } = useAdminAccess();
  const [data, setData] = useState<PassengerManifest | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const session = await getSession();
        if (!session || !active) return;
        const result = await getAdminPassengers(session.accessToken, id);
        if (active) { setData(result); setError(null); }
      } catch (err) {
        if (active) setError(err instanceof ApiError ? err.message : "Could not load passengers.");
      }
    }
    void load();
    return () => { active = false; };
  }, [getSession, id, reload]);

  function download() {
    if (!data) return;
    const url = URL.createObjectURL(new Blob(["\uFEFF", buildPassengerCsv(data.passengers)], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `departure-${id}-passengers.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  if (error) return <div role="alert">{error} <button className="admin-row-action" onClick={() => setReload((n) => n + 1)}>Try again</button></div>;
  if (!data) return <AdminTableSkeleton label="Loading passengers…" />;
  return (
    <div>
      <style>{`@media print { .admin-workspace { display: block !important; height: auto !important; overflow: visible !important; } .admin-main, .admin-table { overflow: visible !important; } }`}</style>
      <AdminPageHeading title="Departure passengers" description={`${data.tourName} · ${formatDate(data.startDate)}`} action={
        <div className="flex gap-2 print:hidden"><button className="admin-row-action" onClick={() => window.print()}>Print</button><button className="admin-row-action" onClick={download}>Download CSV</button></div>
      } />
      <p className="mt-4">Capacity: {data.capacity} · Booked: {data.booked} · Remaining: {data.remaining}</p>
      <AdminListPanel>
        {data.passengers.length === 0 ? <AdminEmptyState title="No passengers found" description="This departure has no recorded travelers on active bookings." /> :
          <AdminTableRegion label="Departure passengers">
            <table className="w-full text-left text-sm">
              <thead><tr>{["Full name", "Phone", "Email", "Date of birth", "Gender", "ID document", "Booking code", "Booking status", "Contact note"].map((label) => <th className="px-3 py-2" key={label}>{label}</th>)}</tr></thead>
              <tbody className="divide-y divide-line">{data.passengers.map((p, index) => <tr key={`${p.bookingId}-${index}`}>{[p.fullName, p.phone, p.email, p.dateOfBirth, p.gender, p.idDocument, p.bookingCode, p.bookingStatus, p.contactNote].map((value, i) => <td className="px-3 py-3" key={i}>{value || "—"}</td>)}</tr>)}</tbody>
            </table>
          </AdminTableRegion>}
      </AdminListPanel>
    </div>
  );
}
