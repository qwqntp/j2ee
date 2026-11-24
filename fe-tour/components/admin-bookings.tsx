"use client";

import Link from "next/link";
import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import {
  ApiError,
  createPayment,
  getBooking,
  listAdminBookings,
  listBookingPayments,
  updateBookingStatus,
} from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminStatusBadge, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { AdminIcon } from "@/components/admin/admin-icons";
import { adminActionStyles } from "@/components/admin/admin-action-styles";
import { statusLabel } from "@/lib/bookings";
import {
  buildBookingListQuery,
  createDebouncedCommit,
  createKeyedRequestTracker,
  resetAdminPage,
  type BookingListFilters,
} from "@/lib/booking-list";
import { formatDate, formatDateRange, formatPrice } from "@/lib/format";
import type { AdminBookingListItem, AdminListSort, Booking, Payment } from "@/lib/types";

const DEFAULT_SIZE = 10;
const PAGE_SIZES = [10, 20, 50];
const STATUSES: Array<NonNullable<BookingListFilters["status"]>> = [
  "PENDING",
  "PAID",
  "CONFIRMED",
  "COMPLETED",
  "CANCELLED",
];
const SORTS: Array<{ value: AdminListSort; label: string }> = [
  { value: "createdAt,desc", label: "Newest first" },
  { value: "createdAt,asc", label: "Oldest first" },
  { value: "totalAmount,desc", label: "Total: high to low" },
  { value: "totalAmount,asc", label: "Total: low to high" },
];

type FilterForm = {
  status: BookingListFilters["status"];
  from: string;
  to: string;
  q: string;
  sort: AdminListSort;
};

type CommittedQuery = {
  page: number;
  size: number;
  filters: BookingListFilters;
};

const EMPTY_FORM: FilterForm = { status: "", from: "", to: "", q: "", sort: "createdAt,desc" };

function toFilters(form: FilterForm): BookingListFilters {
  return {
    status: form.status || undefined,
    from: form.from || undefined,
    to: form.to || undefined,
    q: form.q.trim() || undefined,
    sort: form.sort,
  };
}

export function AdminBookings() {
  const { getSession } = useAdminAccess();
  const [bookings, setBookings] = useState<AdminBookingListItem[]>([]);
  const [query, setQuery] = useState<CommittedQuery>({
    page: 0,
    size: DEFAULT_SIZE,
    filters: toFilters(EMPTY_FORM),
  });
  const [form, setForm] = useState<FilterForm>(EMPTY_FORM);
  const formRef = useRef(form);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [details, setDetails] = useState<Record<number, Booking>>({});
  const detailCache = useRef<Record<number, Booking>>({});
  const [detailLoadingId, setDetailLoadingId] = useState<number | null>(null);
  const [detailError, setDetailError] = useState<Record<number, string>>({});
  const [detailReloadKey, setDetailReloadKey] = useState(0);
  const detailTracker = useRef(createKeyedRequestTracker());
  const [payments, setPayments] = useState<Record<number, Payment[]>>({});
  const [paymentsLoadingId, setPaymentsLoadingId] = useState<number | null>(null);
  const [paymentsError, setPaymentsError] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const commitFilters = useCallback((nextForm: FilterForm, nextSize?: number) => {
    const filters = toFilters(nextForm);
    try {
      buildBookingListQuery({ page: 0, size: nextSize ?? DEFAULT_SIZE, ...filters });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invalid filters");
      return;
    }
    setError(null);
    setQuery((current) => resetAdminPage(current, {
      size: nextSize ?? current.size,
      filters,
    }));
    setExpandedId(null);
  }, []);

  const debouncedSearch = useState(() =>
    createDebouncedCommit<FilterForm>((next) => commitFilters(next)),
  )[0];
  useEffect(() => () => debouncedSearch.cancel(), [debouncedSearch]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const result = await listAdminBookings(
          session.accessToken,
          query.page,
          query.size,
          query.filters,
          controller.signal,
        );
        if (!active) return;
        setBookings(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        setHasNext(result.hasNext);
        setError(null);
      } catch (err) {
        if (!active || controller.signal.aborted) return;
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
      controller.abort();
    };
  }, [query, reloadKey, getSession]);

  useEffect(() => {
    if (expandedId === null || detailCache.current[expandedId]) return;
    const bookingId = expandedId;
    const controller = new AbortController();
    const tracker = detailTracker.current;
    const token = tracker.begin(bookingId);
    let active = true;

    async function loadDetail() {
      setDetailLoadingId(bookingId);
      setDetailError((current) => {
        if (!(bookingId in current)) return current;
        const next = { ...current };
        delete next[bookingId];
        return next;
      });
      try {
        const session = await getSession();
        if (!active || !session || !tracker.isCurrent(bookingId, token)) return;
        const result = await getBooking(session.accessToken, bookingId, controller.signal);
        if (!active || !tracker.isCurrent(bookingId, token)) return;
        detailCache.current = { ...detailCache.current, [bookingId]: result };
        setDetails((current) => ({ ...current, [bookingId]: result }));
      } catch (err) {
        if (!active || controller.signal.aborted || !tracker.isCurrent(bookingId, token)) return;
        setDetailError((current) => ({
          ...current,
          [bookingId]:
            err instanceof ApiError ? err.message : "Could not load booking details. Try again.",
        }));
      } finally {
        if (active) {
          setDetailLoadingId((current) => (current === bookingId ? null : current));
        }
      }
    }

    void loadDetail();
    return () => {
      active = false;
      tracker.cancel(bookingId);
      controller.abort();
      setDetailLoadingId((current) => (current === bookingId ? null : current));
    };
  }, [expandedId, detailReloadKey, getSession]);

  function updateForm(patch: Partial<FilterForm>, debounce = false) {
    const next = { ...formRef.current, ...patch };
    formRef.current = next;
    setForm(next);
    if (debounce) {
      debouncedSearch.schedule(next);
    } else {
      debouncedSearch.cancel();
      commitFilters(next);
    }
  }

  function clearFilters() {
    debouncedSearch.cancel();
    formRef.current = EMPTY_FORM;
    setForm(EMPTY_FORM);
    commitFilters(EMPTY_FORM);
  }

  function changeSize(nextSize: number) {
    debouncedSearch.cancel();
    commitFilters(formRef.current, nextSize);
  }

  function retryDetail(bookingId: number) {
    setDetailError((current) => {
      if (!(bookingId in current)) return current;
      const next = { ...current };
      delete next[bookingId];
      return next;
    });
    setDetailReloadKey((key) => key + 1);
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

  function invalidateDetail(bookingId: number) {
    if (bookingId in detailCache.current) {
      const next = { ...detailCache.current };
      delete next[bookingId];
      detailCache.current = next;
      setDetails(next);
    }
    setDetailError((current) => {
      if (!(bookingId in current)) return current;
      const next = { ...current };
      delete next[bookingId];
      return next;
    });
    setDetailReloadKey((key) => key + 1);
  }

  async function run(
    booking: AdminBookingListItem,
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
      invalidateDetail(booking.id);
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
          debouncedSearch.cancel();
          commitFilters(formRef.current);
        }}
        className="admin-filter-bar admin-toolbar"
      >
        <label className="admin-toolbar-search">
          <span className="sr-only">Search bookings</span><AdminIcon name="search" className="size-4" />
          <input id="booking-q" type="search" maxLength={100} placeholder="Code, account, tour" className="field"
            value={form.q} onChange={(event) => updateForm({ q: event.target.value }, true)} />
        </label>
        <div className="admin-toolbar-row">
          <div className="admin-toolbar-control">
            <label htmlFor="booking-status" className="label">
              Status
            </label>
            <select
              id="booking-status"
              className="field"
              value={form.status}
              onChange={(event) =>
                updateForm({
                  status: event.target.value as BookingListFilters["status"],
                })
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
          <div className="admin-toolbar-dates">
          <div className="admin-toolbar-control">
            <label htmlFor="booking-from" className="label">
              From
            </label>
            <input
              id="booking-from"
              type="date"
              className="field"
              value={form.from}
              onChange={(event) => updateForm({ from: event.target.value })}
            />
          </div>
          <div className="admin-toolbar-control">
            <label htmlFor="booking-to" className="label">
              To
            </label>
            <input
              id="booking-to"
              type="date"
              className="field"
              value={form.to}
              onChange={(event) => updateForm({ to: event.target.value })}
            />
          </div>
          </div>
          <div className="admin-toolbar-control">
            <label htmlFor="booking-sort" className="label">
              Sort
            </label>
            <select
              id="booking-sort"
              className="field"
              value={form.sort}
              onChange={(event) => updateForm({ sort: event.target.value as AdminListSort })}
            >
              {SORTS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          <div className="admin-toolbar-control">
            <label htmlFor="booking-size" className="label">
              Per page
            </label>
            <select
              id="booking-size"
              className="field"
              value={query.size}
              onChange={(event) => changeSize(Number(event.target.value))}
            >
              {PAGE_SIZES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
          <button type="button" onClick={clearFilters} className="admin-toolbar-ghost">
            Clear
          </button>
        </div>
      </form>

      {error ? (
        <div role="alert" className="admin-notice mt-5">
          <p>{error}</p>
          <button
            type="button"
            onClick={() => setReloadKey((key) => key + 1)}
            className="btn-secondary mt-3 min-h-0 px-4 py-2 text-xs"
          >
            Try again
          </button>
        </div>
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
                const detail = details[booking.id];
                return (
                  <Fragment key={booking.id}>
                    <tr className="align-top">
                      <td className="px-4 py-4 font-medium text-charcoal">
                        {booking.bookingCode}
                      </td>
                      <td className="px-4 py-4 text-muted">
                        {booking.tourName ?? "—"}
                      </td>
                      <td className="px-4 py-4 text-muted">
                        {formatDateRange(booking.startDate, booking.endDate)}
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
                            aria-label={`${expanded ? "Hide" : "View"} details for booking ${booking.bookingCode}`}
                            title={expanded ? "Hide details" : "View details"}
                            onClick={() =>
                              setExpandedId((current) => (current === booking.id ? null : booking.id))
                            }
                            className={adminActionStyles.view}
                          >
                            <AdminIcon name="eye" className="size-4" />
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
                          {detail ? (
                            <div className="grid gap-6 lg:grid-cols-2">
                              <section>
                                <p className="eyebrow text-forest">Booking note</p>
                                <p className="mt-2 text-sm text-charcoal">
                                  {detail.note || "No note."}
                                </p>
                                {detail.promotion ? (
                                  <p className="mt-3 text-sm text-muted">
                                    Promotion:{" "}
                                    <span className="text-charcoal">
                                      {detail.promotion.name} ({detail.promotion.code})
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
                                {detail.travelers.length === 0 ? (
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
                                      {detail.travelers.map((traveler) => (
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
                          ) : detailError[booking.id] ? (
                            <div role="alert" className="text-sm text-terracotta">
                              <p>{detailError[booking.id]}</p>
                              <button
                                type="button"
                                onClick={() => retryDetail(booking.id)}
                                className="admin-row-action mt-2"
                              >
                                Retry
                              </button>
                            </div>
                          ) : (
                            <p role="status" className="text-sm text-muted">
                              {detailLoadingId === booking.id ? "Loading details…" : "Loading details…"}
                            </p>
                          )}

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
          disabled={query.page === 0 || loading}
          onClick={() => setQuery((current) => ({ ...current, page: Math.max(0, current.page - 1) }))}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Previous
        </button>
        <div className="text-center text-sm text-muted">
          <p>
            Page {query.page + 1} of {Math.max(totalPages, 1)}
          </p>
          <p>{totalElements} bookings</p>
        </div>
        <button
          type="button"
          disabled={!hasNext || loading}
          onClick={() => setQuery((current) => ({ ...current, page: current.page + 1 }))}
          className="btn-secondary min-h-0 px-4 py-2 text-xs disabled:opacity-50"
        >
          Next
        </button>
      </div>
      </AdminListPanel>
    </div>
  );
}
