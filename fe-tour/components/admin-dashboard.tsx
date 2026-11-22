"use client";

import { useEffect, useState } from "react";
import { buildDashboardCards, shouldShowSignIn, type DashboardCard } from "@/lib/admin-stats";
import { ApiError, getAdminStats } from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminLoading, AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import { statusLabel } from "@/lib/bookings";
import { formatDate, formatPrice, seatsLabel } from "@/lib/format";
import type { AdminStats } from "@/lib/types";

const BOOKING_STATUSES = ["PENDING", "PAID", "CONFIRMED", "COMPLETED", "CANCELLED"] as const;

const TONE_TEXT: Record<DashboardCard["tone"], string> = {
  forest: "text-forest",
  terracotta: "text-terracotta",
  charcoal: "text-charcoal",
};

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
    return <AdminLoading>Loading dashboard…</AdminLoading>;
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

  const cards = buildDashboardCards(stats);

  return (
    <div>
      <AdminPageHeading title="Dashboard" description={`Statistics generated ${formatDate(stats.generatedAt)}`} />

      {errorPanel}

      <div className="mt-5 grid grid-cols-[repeat(auto-fit,minmax(min(100%,12rem),1fr))] gap-3">
        {cards.map((card) => (
          <div key={card.label} className="admin-panel min-w-0 p-4">
            <p className="text-sm font-medium text-muted">{card.label}</p>
            <p className={`mt-1 break-words text-xl font-semibold tracking-tight tabular-nums sm:text-2xl ${TONE_TEXT[card.tone]}`}>
              {card.value}
            </p>
          </div>
        ))}
      </div>

      <div className="mt-6 grid gap-6 min-[1200px]:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <section className="min-w-0">
          <h2 className="text-lg font-semibold tracking-tight">Bookings by status</h2>
          <AdminTableRegion label="Bookings by status" className="mt-3">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-line text-muted">
                <tr>
                  <th scope="col" className="font-semibold">Status</th>
                  <th scope="col" className="text-right font-semibold">Bookings</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {BOOKING_STATUSES.map((status) => (
                  <tr key={status}>
                    <td className="text-charcoal">{statusLabel(status)}</td>
                    <td className="text-right text-muted tabular-nums">{stats.bookingsByStatus[status]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableRegion>
        </section>

        <section className="min-w-0">
          <h2 className="text-lg font-semibold tracking-tight">Top tours</h2>
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
      </div>

      <section className="mt-6 min-w-0">
        <h2 className="text-lg font-semibold tracking-tight">Capacity risks</h2>
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
  );
}
