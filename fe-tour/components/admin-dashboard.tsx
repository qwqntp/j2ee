"use client";

import { useEffect, useState } from "react";
import { buildDashboardCards, shouldShowSignIn } from "@/lib/admin-stats";
import { ApiError, getAdminStats } from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminBarPanel, AdminStatCard } from "@/components/admin/admin-dashboard-panels";
import { AdminDashboardSkeleton } from "@/components/admin/admin-skeletons";
import { statusLabel } from "@/lib/bookings";
import { formatDate, formatPrice, seatsLabel } from "@/lib/format";
import type { AdminStats } from "@/lib/types";

const BOOKING_STATUSES = ["PENDING", "PAID", "CONFIRMED", "COMPLETED", "CANCELLED"] as const;

export function AdminDashboard() {
  const { getSession } = useAdminAccess();
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const result = await getAdminStats(session.accessToken);
        if (!active) return;
        setStats(result);
        setError(null);
      } catch (err) {
        if (!active) return;
        setError(
          err instanceof ApiError ? err.message : "Could not load dashboard stats. Try again.",
        );
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [reloadKey, getSession]);

  const errorPanel = error ? (
    <div
      role="alert"
      className="admin-notice mt-5"
    >
      <p className="text-sm text-terracotta">{error}</p>
      <button
        type="button"
        onClick={() => setReloadKey((key) => key + 1)}
        className="btn-secondary mt-3 min-h-0 px-4 py-2 text-xs"
      >
        Retry
      </button>
    </div>
  ) : null;

  if (loading && !stats) {
    return <AdminDashboardSkeleton />;
  }

  if (shouldShowSignIn(stats, error)) {
    return (
      <div className="admin-notice">
        Sign in to view dashboard statistics.
      </div>
    );
  }

  if (!stats) {
    return errorPanel;
  }

  const { primary, secondary } = buildDashboardCards(stats);

  return (
    <div>
      <AdminPageHeading title="Operations overview"
        description="Track successful payments, booking activity, and upcoming tours."
        action={<p className="admin-page-meta">Data as of {formatDate(stats.generatedAt)}</p>}
      />

      {errorPanel}
      {loading && <p role="status" className="admin-refresh">Updating dashboard…</p>}

      <dl className="admin-kpi-grid mt-5">
        {primary.map((card) => <AdminStatCard key={card.label} card={card} />)}
      </dl>

      <div className="admin-analytics-grid mt-5">
        <AdminBarPanel title="Tour booking activity" description="Booking counts for the top tours in this report."
          empty="No tour booking activity to show yet."
          rows={stats.topTours.map((tour) => ({ key: tour.tourId, label: tour.tourName, count: tour.bookingCount }))}
        />
        <AdminBarPanel title="Bookings by status" description="Booking counts by current status."
          empty="No booking statuses to show."
          rows={BOOKING_STATUSES.map((status) => ({ key: status, label: statusLabel(status), count: stats.bookingsByStatus[status] }))}
        />
      </div>

      <section className="admin-panel mt-5 px-5 py-4">
        <h2 className="text-lg font-semibold tracking-tight">Performance and capacity</h2>
        <dl className="mt-3 grid gap-x-5 gap-y-3 min-[600px]:grid-cols-3">
          {secondary.map((card) => (
            <div key={card.label} className="border-t border-line/70 pt-3">
              <dt className="text-sm text-muted">{card.label}</dt>
              <dd className="mt-1 font-semibold tabular-nums">{card.value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <div className="admin-analytics-grid mt-5">
        <section className="admin-panel admin-dashboard-table min-w-0">
          <h2 className="px-5 pt-4 text-lg font-semibold tracking-tight">Top tours</h2>
          {stats.topTours.length === 0 ? (
            <p className="admin-notice mt-3">
              No booking performance yet.
            </p>
          ) : (
            <AdminTableRegion label="Top tours" className="mt-3">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-line text-muted">
                  <tr>
                    <th scope="col" className="font-semibold">Tour</th>
                    <th scope="col" className="text-right font-semibold">Bookings</th>
                    <th scope="col" className="text-right font-semibold">Booking value</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {stats.topTours.map((tour) => (
                    <tr key={tour.tourId}>
                      <td className="font-medium text-charcoal">{tour.tourName}</td>
                      <td className="text-right text-muted tabular-nums">{tour.bookingCount}</td>
                      <td className="text-right tabular-nums">{formatPrice(tour.revenue)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableRegion>
          )}
        </section>
      <section className="admin-panel admin-dashboard-table min-w-0">
        <h2 className="px-5 pt-4 text-lg font-semibold tracking-tight">Capacity risks</h2>
        {stats.capacityRisks.length === 0 ? (
          <p className="admin-notice mt-3">
            No low-capacity departures in the next 90 days.
          </p>
        ) : (
          <AdminTableRegion label="Capacity risks" className="mt-3">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-line bg-sage/10 text-[11px] uppercase tracking-[0.12em] text-muted">
                <tr>
                  <th className="px-4 py-3 font-semibold">Tour</th>
                  <th className="px-4 py-3 font-semibold">Departure</th>
                  <th className="px-4 py-3 font-semibold">Seats</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {stats.capacityRisks.map((risk) => (
                  <tr key={risk.departureId}>
                    <td className="px-4 py-3 font-medium text-charcoal">{risk.tourName}</td>
                    <td className="px-4 py-3 text-muted">{formatDate(risk.startDate)}</td>
                    <td className="px-4 py-3 text-muted">{seatsLabel(risk.remainingSeats)}</td>
                    <td className="px-4 py-3 text-muted">{risk.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableRegion>
        )}
      </section>
      </div>
    </div>
  );
}
