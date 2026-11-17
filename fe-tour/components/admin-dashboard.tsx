"use client";

import { useEffect, useState } from "react";
import { buildDashboardCards, shouldShowSignIn, type DashboardCard } from "@/lib/admin-stats";
import { ApiError, getAdminStats } from "@/lib/api";
import { ensureSession } from "@/lib/auth";
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
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await ensureSession();
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
  }, [reloadKey]);

  const errorPanel = error ? (
    <div
      role="alert"
      className="mt-6 rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-4"
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
    return <p className="text-sm text-muted">Loading dashboard…</p>;
  }

  if (shouldShowSignIn(stats, error)) {
    return (
      <div className="rounded-[4px] border border-line bg-white p-6 text-sm text-muted">
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
      <div>
        <p className="eyebrow text-forest">Overview</p>
        <h2 className="mt-2 font-display text-3xl tracking-tight">Dashboard</h2>
        <p className="mt-1 text-sm text-muted">Generated {formatDate(stats.generatedAt)}</p>
      </div>

      {errorPanel}

      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map((card) => (
          <div key={card.label} className="rounded-[4px] border border-line bg-white p-6">
            <p className="text-xs uppercase tracking-[0.12em] text-muted">{card.label}</p>
            <p className={`mt-2 font-display text-3xl tracking-tight ${TONE_TEXT[card.tone]}`}>
              {card.value}
            </p>
          </div>
        ))}
      </div>

      <section className="mt-10">
        <h3 className="font-display text-2xl tracking-tight">Bookings by status</h3>
        <div className="mt-4 overflow-x-auto rounded-[4px] border border-line bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line bg-sage/10 text-[11px] uppercase tracking-[0.12em] text-muted">
              <tr>
                <th className="px-4 py-3 font-semibold">Status</th>
                <th className="px-4 py-3 font-semibold">Bookings</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {BOOKING_STATUSES.map((status) => (
                <tr key={status}>
                  <td className="px-4 py-3 text-charcoal">{statusLabel(status)}</td>
                  <td className="px-4 py-3 text-muted">{stats.bookingsByStatus[status]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="mt-10">
        <h3 className="font-display text-2xl tracking-tight">Top tours</h3>
        {stats.topTours.length === 0 ? (
          <p className="mt-4 rounded-[4px] border border-line bg-white p-6 text-sm text-muted">
            No booking performance yet.
          </p>
        ) : (
          <div className="mt-4 overflow-x-auto rounded-[4px] border border-line bg-white">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-line bg-sage/10 text-[11px] uppercase tracking-[0.12em] text-muted">
                <tr>
                  <th className="px-4 py-3 font-semibold">Tour</th>
                  <th className="px-4 py-3 font-semibold">Bookings</th>
                  <th className="px-4 py-3 font-semibold">Booking value</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {stats.topTours.map((tour) => (
                  <tr key={tour.tourId}>
                    <td className="px-4 py-3 font-medium text-charcoal">{tour.tourName}</td>
                    <td className="px-4 py-3 text-muted">{tour.bookingCount}</td>
                    <td className="px-4 py-3 text-charcoal">{formatPrice(tour.revenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="mt-10">
        <h3 className="font-display text-2xl tracking-tight">Capacity risks</h3>
        {stats.capacityRisks.length === 0 ? (
          <p className="mt-4 rounded-[4px] border border-line bg-white p-6 text-sm text-muted">
            No low-capacity departures in the next 90 days.
          </p>
        ) : (
          <div className="mt-4 overflow-x-auto rounded-[4px] border border-line bg-white">
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
          </div>
        )}
      </section>
    </div>
  );
}
