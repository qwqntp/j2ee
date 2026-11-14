"use client";

import { useEffect, useState } from "react";
import { ApiError, createPayment, listBookings, updateBookingStatus } from "@/lib/api";
import { ensureSession } from "@/lib/auth";
import { statusLabel, statusTone } from "@/lib/bookings";
import { formatDateRange, formatPrice } from "@/lib/format";
import type { Booking } from "@/lib/types";

const PAGE_SIZE = 20;

export function AdminBookings() {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await ensureSession();
        if (!active || !session) return;
        const result = await listBookings(session.accessToken, page, PAGE_SIZE);
        if (!active) return;
        setBookings(result.content);
        setTotalElements(result.totalElements);
        setHasNext(result.hasNext);
        setError(null);
      } catch (err) {
        if (!active) return;
        setError(
          err instanceof ApiError ? err.message : "Could not load bookings. Try again.",
        );
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [page, reloadKey]);

  async function run(
    booking: Booking,
    action: (token: string) => Promise<unknown>,
    message: string,
  ) {
    setBusyId(booking.id);
    setError(null);
    setNotice(null);
    try {
      const session = await ensureSession();
      if (!session) return;
      await action(session.accessToken);
      setNotice(message);
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "The action failed. Try again.");
    } finally {
      setBusyId(null);
    }
  }

  if (loading && bookings.length === 0) {
    return <p className="text-sm text-muted">Loading bookings…</p>;
  }

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow text-forest">Reservations</p>
          <h2 className="mt-2 font-display text-3xl tracking-tight">
            {totalElements === 1 ? "1 booking" : `${totalElements} bookings`}
          </h2>
        </div>
      </div>

      {error ? (
        <p
          role="alert"
          className="mt-6 rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-4 text-sm text-terracotta"
        >
          {error}
        </p>
      ) : null}
      {notice ? (
        <p className="mt-6 rounded-[3px] border border-forest/30 bg-sage/15 p-4 text-sm text-forest">
          {notice}
        </p>
      ) : null}

      {bookings.length === 0 ? (
        <div className="mt-8 rounded-[4px] border border-line bg-white p-6 text-sm text-muted">
          No bookings yet.
        </div>
      ) : (
        <div className="mt-6 overflow-x-auto rounded-[4px] border border-line bg-white">
          <table className="w-full min-w-[56rem] text-left text-sm">
            <thead className="border-b border-line bg-sage/10 text-[11px] uppercase tracking-[0.12em] text-muted">
              <tr>
                <th className="px-4 py-3 font-semibold">Code</th>
                <th className="px-4 py-3 font-semibold">Tour</th>
                <th className="px-4 py-3 font-semibold">Dates</th>
                <th className="px-4 py-3 font-semibold">Guests</th>
                <th className="px-4 py-3 font-semibold">Total</th>
                <th className="px-4 py-3 font-semibold">Status</th>
                <th className="px-4 py-3 font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {bookings.map((booking) => {
                const busy = busyId === booking.id;
                return (
                  <tr key={booking.id} className="align-top">
                    <td className="px-4 py-4 font-medium text-charcoal">
                      {booking.bookingCode}
                    </td>
                    <td className="px-4 py-4 text-muted">
                      {booking.tour?.tourName ?? "—"}
                    </td>
                    <td className="px-4 py-4 text-muted">
                      {formatDateRange(booking.tour?.startDate, booking.tour?.endDate)}
                    </td>
                    <td className="px-4 py-4 text-muted">{booking.guestCount}</td>
                    <td className="px-4 py-4 text-charcoal">
                      {formatPrice(booking.totalAmount)}
                    </td>
                    <td className="px-4 py-4">
                      <span
                        className={`inline-flex rounded-[2px] border px-2 py-1 text-xs font-medium ${statusTone(booking.status)}`}
                      >
                        {statusLabel(booking.status)}
                      </span>
                    </td>
                    <td className="px-4 py-4">
                      <div className="flex flex-wrap gap-2">
                        {booking.status === "PENDING" ? (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              run(
                                booking,
                                (token) =>
                                  createPayment(token, {
                                    bookingId: booking.id,
                                    paymentMethod: "CASH",
                                    status: "SUCCESS",
                                  }),
                                `Cash payment recorded for ${booking.bookingCode}.`,
                              )
                            }
                            className="rounded-[2px] border border-charcoal/25 px-3 py-1.5 text-xs font-medium text-charcoal transition-colors hover:border-forest hover:text-forest disabled:opacity-50"
                          >
                            Record cash
                          </button>
                        ) : null}
                        {booking.status === "PAID" ? (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              run(
                                booking,
                                (token) =>
                                  updateBookingStatus(token, booking.id, "CONFIRMED"),
                                `${booking.bookingCode} confirmed.`,
                              )
                            }
                            className="rounded-[2px] border border-charcoal/25 px-3 py-1.5 text-xs font-medium text-charcoal transition-colors hover:border-forest hover:text-forest disabled:opacity-50"
                          >
                            Confirm
                          </button>
                        ) : null}
                        {booking.status === "CONFIRMED" ? (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              run(
                                booking,
                                (token) =>
                                  updateBookingStatus(token, booking.id, "COMPLETED"),
                                `${booking.bookingCode} completed.`,
                              )
                            }
                            className="rounded-[2px] border border-charcoal/25 px-3 py-1.5 text-xs font-medium text-charcoal transition-colors hover:border-forest hover:text-forest disabled:opacity-50"
                          >
                            Complete
                          </button>
                        ) : null}
                        {booking.status === "PENDING" ? (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              run(
                                booking,
                                (token) =>
                                  updateBookingStatus(token, booking.id, "CANCELLED"),
                                `${booking.bookingCode} cancelled.`,
                              )
                            }
                            className="rounded-[2px] px-3 py-1.5 text-xs font-medium text-terracotta transition-colors hover:bg-terracotta/10 disabled:opacity-50"
                          >
                            Cancel
                          </button>
                        ) : null}
                        {busy ? <span className="text-xs text-muted">Working…</span> : null}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <div className="mt-6 flex items-center justify-between gap-4">
        <button
          type="button"
          disabled={page === 0 || loading}
          onClick={() => setPage((current) => Math.max(0, current - 1))}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Previous
        </button>
        <p className="text-sm text-muted">Page {page + 1}</p>
        <button
          type="button"
          disabled={!hasNext || loading}
          onClick={() => setPage((current) => current + 1)}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Next
        </button>
      </div>
    </div>
  );
}
