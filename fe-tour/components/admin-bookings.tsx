"use client";

import Link from "next/link";
import { Fragment, useEffect, useState } from "react";
import {
  ApiError,
  createPayment,
  listBookingPayments,
  listBookings,
  updateBookingStatus,
} from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminStatusBadge, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { statusLabel } from "@/lib/bookings";
import { buildBookingListQuery, type BookingListFilters } from "@/lib/booking-list";
import { formatDate, formatDateRange, formatPrice } from "@/lib/format";
import type { Booking, Payment } from "@/lib/types";

const PAGE_SIZE = 20;
const PAGE_SIZES = [10, 20, 50];
const STATUSES: Array<NonNullable<BookingListFilters["status"]>> = [
  "PENDING",
  "PAID",
  "CONFIRMED",
  "COMPLETED",
  "CANCELLED",
];

type FilterForm = {
  status: BookingListFilters["status"];
  from: string;
  to: string;
  q: string;
};

const EMPTY_FORM: FilterForm = { status: "", from: "", to: "", q: "" };

export function AdminBookings() {
  const { getSession } = useAdminAccess();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(PAGE_SIZE);
  const [filters, setFilters] = useState<BookingListFilters>({});
  const [form, setForm] = useState<FilterForm>(EMPTY_FORM);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [payments, setPayments] = useState<Record<number, Payment[]>>({});
  const [paymentsLoadingId, setPaymentsLoadingId] = useState<number | null>(null);
  const [paymentsError, setPaymentsError] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const result = await listBookings(session.accessToken, page, size, filters);
        if (!active) return;
        setBookings(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        setHasNext(result.hasNext);
        setError(null);
      } catch (err) {
        if (!active) return;
        setError(
          err instanceof ApiError ? err.message : "Could not load bookings. Try again.",
        );
      } finally {
        if (active) {
          setLoading(false);
          setLoaded(true);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [page, size, filters, reloadKey, getSession]);

  function applyFilters() {
    const next: BookingListFilters = {
      status: form.status || undefined,
      from: form.from || undefined,
      to: form.to || undefined,
      q: form.q || undefined,
    };
    try {
      buildBookingListQuery({ page: 0, size, ...next });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invalid filters");
      return;
    }
    setError(null);
    setFilters(next);
    setPage(0);
    setExpandedId(null);
  }

  function clearFilters() {
    setForm(EMPTY_FORM);
    setFilters({});
    setPage(0);
    setExpandedId(null);
    setError(null);
  }

  async function loadPayments(bookingId: number) {
    if (payments[bookingId]) return;
    setPaymentsLoadingId(bookingId);
    setPaymentsError((current) => {
      const next = { ...current };
      delete next[bookingId];
      return next;
    });
    try {
      const session = await getSession();
      if (!session) return;
      const result = await listBookingPayments(session.accessToken, bookingId);
      setPayments((current) => ({ ...current, [bookingId]: result }));
    } catch (err) {
      setPaymentsError((current) => ({
        ...current,
        [bookingId]:
          err instanceof ApiError ? err.message : "Could not load payments. Try again.",
      }));
    } finally {
      setPaymentsLoadingId(null);
    }
  }

  function invalidatePayments(bookingId: number) {
    setPayments((current) => {
      const next = { ...current };
      delete next[bookingId];
      return next;
    });
    setPaymentsError((current) => {
      const next = { ...current };
      delete next[bookingId];
      return next;
    });
  }

  async function run(
    booking: Booking,
    action: (token: string) => Promise<unknown>,
    message: string,
  ) {
    setBusyId(booking.id);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      await action(session.accessToken);
      invalidatePayments(booking.id);
      setNotice(message);
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "The action failed. Try again.");
    } finally {
      setBusyId(null);
    }
  }

  if (!loaded) {
    return <AdminTableSkeleton label="Loading bookings…" filters={5} pagination />;
  }

  return (
    <div>
      <AdminPageHeading title="Bookings & payments"
        description="Monitor reservations, review payment history, and manage booking status." />

      <AdminListPanel busy={loading}>
      <form
        aria-label="Filter bookings"
        onSubmit={(event) => {
          event.preventDefault();
          applyFilters();
        }}
        className="admin-filter-bar"
      >
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
          <div>
            <label htmlFor="booking-status" className="label">
              Status
            </label>
            <select
              id="booking-status"
              className="field"
              value={form.status}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  status: event.target.value as BookingListFilters["status"],
                }))
              }
            >
              <option value="">All statuses</option>
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {statusLabel(status)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="booking-from" className="label">
              From
            </label>
            <input
              id="booking-from"
              type="date"
              className="field"
              value={form.from}
              onChange={(event) =>
                setForm((current) => ({ ...current, from: event.target.value }))
              }
            />
          </div>
          <div>
            <label htmlFor="booking-to" className="label">
              To
            </label>
            <input
              id="booking-to"
              type="date"
              className="field"
              value={form.to}
              onChange={(event) =>
                setForm((current) => ({ ...current, to: event.target.value }))
              }
            />
          </div>
          <div>
            <label htmlFor="booking-q" className="label">
              Search
            </label>
            <input
              id="booking-q"
              type="search"
              maxLength={100}
              placeholder="Code, account, tour"
              className="field"
              value={form.q}
              onChange={(event) =>
                setForm((current) => ({ ...current, q: event.target.value }))
              }
            />
          </div>
          <div>
            <label htmlFor="booking-size" className="label">
              Per page
            </label>
            <select
              id="booking-size"
              className="field"
              value={size}
              onChange={(event) => {
                setSize(Number(event.target.value));
                setPage(0);
                setExpandedId(null);
              }}
            >
              {PAGE_SIZES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="mt-4 flex gap-2">
          <button type="submit" className="btn-primary min-h-0 px-4 py-2 text-xs">
            Apply
          </button>
          <button
            type="button"
            onClick={clearFilters}
            className="btn-secondary min-h-0 px-4 py-2 text-xs"
          >
            Clear
          </button>
        </div>
      </form>

      {error ? (
        <p
          role="alert"
          className="admin-notice mt-5"
        >
          {error}
        </p>
      ) : null}
      {notice ? (
        <p
          role="status"
          className="admin-success mt-5"
        >
          {notice}
        </p>
      ) : null}

      {bookings.length === 0 ? (
        !error && <AdminEmptyState title="No bookings found" description="Try clearing the filters to see other bookings." />
      ) : (
        <AdminTableRegion label="Bookings">
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
                const expanded = expandedId === booking.id;
                return (
                  <Fragment key={booking.id}>
                    <tr className="align-top">
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
                        <AdminStatusBadge tone={booking.status === "CANCELLED" ? "danger" :
                          booking.status === "PENDING" ? "warning" :
                          booking.status === "PAID" || booking.status === "COMPLETED" ? "positive" : "neutral"}>
                          {statusLabel(booking.status)}
                        </AdminStatusBadge>
                      </td>
                      <td className="px-4 py-4">
                        <div className="flex flex-wrap gap-2">
                          <button
                            type="button"
                            aria-expanded={expanded}
                            onClick={() =>
                              setExpandedId((current) => (current === booking.id ? null : booking.id))
                            }
                            className="admin-row-action"
                          >
                            {expanded ? "Hide details" : "Details"}
                          </button>
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
                              className="admin-row-action"
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
                              className="admin-row-action"
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
                              className="admin-row-action"
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
                              className="admin-danger-action"
                            >
                              Cancel
                            </button>
                          ) : null}
                          {busy ? <span className="text-xs text-muted">Working…</span> : null}
                        </div>
                      </td>
                    </tr>
                    {expanded ? (
                      <tr>
                        <td colSpan={7} className="bg-sage/5 px-6 py-5">
                          <div className="grid gap-6 lg:grid-cols-2">
                            <section>
                              <p className="eyebrow text-forest">Booking note</p>
                              <p className="mt-2 text-sm text-charcoal">
                                {booking.note || "No note."}
                              </p>
                              {booking.promotion ? (
                                <p className="mt-3 text-sm text-muted">
                                  Promotion:{" "}
                                  <span className="text-charcoal">
                                    {booking.promotion.name} ({booking.promotion.code})
                                  </span>
                                </p>
                              ) : null}
                              <Link
                                href={`/admin/invoices/${booking.id}`}
                                className="btn-secondary mt-4 min-h-0 px-4 py-2 text-xs"
                              >
                                View invoice
                              </Link>
                            </section>
                            <section>
                              <p className="eyebrow text-forest">Travelers</p>
                              {booking.travelers.length === 0 ? (
                                <p className="mt-2 text-sm text-muted">No travelers recorded.</p>
                              ) : (
                                <table className="mt-2 w-full text-left text-xs">
                                  <thead className="text-muted">
                                    <tr>
                                      <th className="py-2 pr-3 font-semibold">Name</th>
                                      <th className="py-2 pr-3 font-semibold">DOB</th>
                                      <th className="py-2 pr-3 font-semibold">Email</th>
                                      <th className="py-2 font-semibold">Phone</th>
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-line">
                                    {booking.travelers.map((traveler) => (
                                      <tr key={traveler.id} className="text-charcoal">
                                        <td className="py-2 pr-3">{traveler.name}</td>
                                        <td className="py-2 pr-3">
                                          {traveler.dateOfBirth
                                            ? formatDate(traveler.dateOfBirth)
                                            : "—"}
                                        </td>
                                        <td className="py-2 pr-3">{traveler.email ?? "—"}</td>
                                        <td className="py-2">{traveler.phone ?? "—"}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )}
                            </section>
                          </div>

                          <div className="mt-6">
                            <div className="flex flex-wrap items-center gap-3">
                              <p className="eyebrow text-forest">Payment history</p>
                              <button
                                type="button"
                                disabled={paymentsLoadingId === booking.id}
                                onClick={() => loadPayments(booking.id)}
                                className="admin-row-action"
                              >
                                {paymentsLoadingId === booking.id ? "Loading…" : "Load payments"}
                              </button>
                            </div>
                            {paymentsError[booking.id] ? (
                              <p role="alert" className="mt-2 text-sm text-terracotta">
                                {paymentsError[booking.id]}
                              </p>
                            ) : null}
                            {payments[booking.id] ? (
                              payments[booking.id].length === 0 ? (
                                <p className="mt-2 text-sm text-muted">No payments recorded.</p>
                              ) : (
                                <table className="mt-3 w-full text-left text-xs">
                                  <thead className="text-muted">
                                    <tr>
                                      <th className="py-2 pr-3 font-semibold">Amount</th>
                                      <th className="py-2 pr-3 font-semibold">Method</th>
                                      <th className="py-2 pr-3 font-semibold">Status</th>
                                      <th className="py-2 pr-3 font-semibold">Paid at</th>
                                      <th className="py-2 font-semibold">Created at</th>
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-line">
                                    {payments[booking.id].map((payment) => (
                                      <tr key={payment.id} className="text-charcoal">
                                        <td className="py-2 pr-3">
                                          {formatPrice(payment.amount)}
                                        </td>
                                        <td className="py-2 pr-3">{payment.paymentMethod}</td>
                                        <td className="py-2 pr-3">{payment.status}</td>
                                        <td className="py-2 pr-3">
                                          {payment.paidAt ? formatDate(payment.paidAt) : "—"}
                                        </td>
                                        <td className="py-2">{formatDate(payment.createdAt)}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )
                            ) : null}
                          </div>
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </AdminTableRegion>
      )}

      <div className="admin-pagination mt-5">
        <button
          type="button"
          disabled={page === 0 || loading}
          onClick={() => setPage((current) => Math.max(0, current - 1))}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Previous
        </button>
        <div className="text-center text-sm text-muted">
          <p>
            Page {page + 1} of {totalPages}
          </p>
          <p>{totalElements} bookings</p>
        </div>
        <button
          type="button"
          disabled={!hasNext || loading}
          onClick={() => setPage((current) => current + 1)}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Next
        </button>
      </div>
      </AdminListPanel>
    </div>
  );
}
