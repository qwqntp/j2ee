import Link from "next/link";
import type { AdminDeparture } from "../lib/admin-departures";
import { formatDate } from "../lib/format";
import { AdminStatusBadge, AdminTableRegion } from "./admin/admin-ui";

export function AdminDepartureTable({ departures }: { departures: AdminDeparture[] }) {
  return <AdminTableRegion label="Departures">
    <table className="w-full text-left text-sm">
      <thead><tr>{["Tour", "Start", "End", "Status", "Capacity", "Booked", "Remaining", "Actions"]
        .map((label) => <th scope="col" key={label}>{label}</th>)}</tr></thead>
      <tbody className="divide-y divide-line">{departures.map((d) => <tr key={d.id}>
        <td className="font-medium">{d.tourName}</td>
        <td className="whitespace-nowrap">{formatDate(d.startDate)}<span className="block text-xs text-muted">{d.startDate.slice(11, 16)}</span></td>
        <td className="whitespace-nowrap">{formatDate(d.endDate)}<span className="block text-xs text-muted">{d.endDate.slice(11, 16)}</span></td>
        <td><AdminStatusBadge tone={d.status === "CANCELLED" ? "danger" : d.status === "OPEN" ? "positive" : "neutral"}>{d.status}</AdminStatusBadge></td>
        <td className="tabular-nums">{d.capacity}</td><td className="tabular-nums">{d.booked}</td><td className="tabular-nums">{d.remaining}</td>
        <td><Link className="admin-row-action whitespace-nowrap" href={`/admin/departures/${d.id}/passengers`}
          aria-label={`View passengers for ${d.tourName}`}>Passengers</Link></td>
      </tr>)}</tbody>
    </table>
  </AdminTableRegion>;
}
