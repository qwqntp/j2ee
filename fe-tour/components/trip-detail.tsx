"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import {
  ApiError,
  cancelBooking,
  completeBooking,
  confirmBooking,
  getBooking,
  reconcileVnpayBooking,
  startVnpay,
} from "@/lib/api";
import { clearSession, ensureSession, useSession } from "@/lib/auth";
import {
  canCancelBooking,
  canViewInvoice,
  statusDescription,
  statusLabel,
  statusTone,
} from "@/lib/bookings";
import { formatDate, formatDateRange, formatDuration, formatPrice } from "@/lib/format";
import { publishFlash } from "@/lib/flash";
import { registerUserRequest } from "@/lib/user-state";
import { tourFallbackImage } from "@/lib/tour-images";
import type { Booking } from "@/lib/types";
import { openVnpayCheckout, vnpayCheckoutEnabled, vnpayReconcileError, vnpayReconcileNotice } from "@/lib/vnpay";
import type { VnpayReconcileNotice } from "@/lib/vnpay";
import { EmptyState } from "./empty-state";
import { IconArrowRight, IconCalendar, IconClock, IconMapPin, IconUsers } from "./icons";
import { TripDetailSkeleton } from "./skeleton";
import { TourImage } from "./tour-image";

export function TripDetail({
  bookingId,
  basePath = "/my-trips",
}: {
  bookingId: number;
  basePath?: string;
}) {
  const router = useRouter();
  const session = useSession();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [cancelConflict, setCancelConflict] = useState<string | null>(null);
  const [advancing, setAdvancing] = useState(false);
  const [advanceError, setAdvanceError] = useState<string | null>(null);
  const [paying, setPaying] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [checkingPayment, setCheckingPayment] = useState(false);
  const [reconcileNotice, setReconcileNotice] = useState<VnpayReconcileNotice | null>(null);
  const [paymentBlocked, setPaymentBlocked] = useState(false);
  const paymentInFlight = useRef(false);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const loginHref = `/login?next=${encodeURIComponent(`${basePath}/${bookingId}`)}`;
  const accountId = session?.account.id ?? null;

  useEffect(() => {
    const controller = new AbortController();
    const untrack = registerUserRequest(controller);
    let active = true;

    async function load() {
      setBooking(null);
      setLoading(true);
      setMissing(false);
      setError(null);

      const current = await ensureSession();
      if (!active || controller.signal.aborted) return;
      if (!current) {
        setLoading(false);
        router.replace(loginHref);
        return;
      }

      try {
        const result = await getBooking(current.accessToken, bookingId, controller.signal);
        if (!active || controller.signal.aborted) return;
        setBooking(result);
        setError(null);
        setLoading(false);
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        if (err instanceof ApiError && err.status === 401) {
          clearSession();
          router.replace(loginHref);
          return;
        }
        if (err instanceof ApiError && err.status === 404) {
          setMissing(true);
          setError("This trip is not in your account. It may have been removed.");
          setLoading(false);
          return;
        }
        setError(
          err instanceof ApiError
            ? err.message
            : "Could not reach the booking service. Try again.",
        );
        setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
      untrack();
      controller.abort();
    };
  }, [accountId, bookingId, loginHref, reloadKey, router]);

  useEffect(() => {
    if (!loading && session === null) {
      router.replace(loginHref);
    }
  }, [loading, session, loginHref, router]);

  useEffect(() => {
    if (confirming) confirmRef.current?.focus();
  }, [confirming]);

  function retry() {
    setLoading(true);
    setError(null);
    setReloadKey((key) => key + 1);
  }

  async function handleVnpay() {
    if (!booking || paymentInFlight.current) return;
    paymentInFlight.current = true;
    setPaying(true);
    setPaymentError(null);
    setReconcileNotice(null);
    setPaymentBlocked(false);
    try {
      const current = await ensureSession();
      if (!current) {
        router.replace(loginHref);
        return;
      }
      const result = await openVnpayCheckout(
        booking.id,
        () => startVnpay(current.accessToken, booking.id),
        (url) => window.location.assign(url),
      );
      if (result.error) setPaymentError(result.error);
    } finally {
      paymentInFlight.current = false;
      setPaying(false);
    }
  }

  async function handleCheckPayment() {
    if (!booking || paymentInFlight.current) return;
    paymentInFlight.current = true;
    setCheckingPayment(true);
    setPaymentError(null);
    try {
      const current = await ensureSession();
      if (!current) {
        router.replace(loginHref);
        return;
      }
      const checked = await reconcileVnpayBooking(current.accessToken, booking.id);
      const notice = vnpayReconcileNotice(checked);
      setReconcileNotice(notice);
      setPaymentBlocked(notice.tone === "pending");
      if (notice.tone !== "pending") setCancelConflict(null);
      try {
        // Reflect a settlement or a released attempt without a full page skeleton.
        setBooking(await getBooking(current.accessToken, booking.id));
      } catch {
        // The reconcile answer is still valid; a refresh will re-read the booking.
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearSession();
        router.replace(loginHref);
        return;
      }
      setPaymentError(vnpayReconcileError(err));
    } finally {
      paymentInFlight.current = false;
      setCheckingPayment(false);
    }
  }

  async function handleLifecycle(action: "confirm" | "complete") {
    if (!booking || advancing) return;
    setAdvancing(true);
    setAdvanceError(null);

    try {
      const current = await ensureSession();
      if (!current) {
        router.replace(loginHref);
        return;
      }
      const updated =
        action === "confirm"
          ? await confirmBooking(current.accessToken, booking.id)
          : await completeBooking(current.accessToken, booking.id);
      setBooking({ ...booking, status: updated.status });
      publishFlash(
        action === "confirm"
          ? "Booking confirmed. Seats are secured."
          : "Trip marked as completed. Enjoy the memories.",
      );
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearSession();
        router.replace(loginHref);
        return;
      }
      setAdvanceError(
        err instanceof ApiError
          ? err.message
          : "Could not update the booking. Try again.",
      );
    } finally {
      setAdvancing(false);
    }
  }

  async function handleCancel() {
    if (!booking || cancelling) return;
    setCancelling(true);
    setCancelError(null);
    setCancelConflict(null);

    try {
      const current = await ensureSession();
      if (!current) {
        router.replace(loginHref);
        return;
      }
      const updated = await cancelBooking(current.accessToken, booking.id);
      setBooking(updated);
      setConfirming(false);
      publishFlash("Booking cancelled. Your seats have been released.");
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearSession();
        router.replace(loginHref);
        return;
      }
      if (err instanceof ApiError && err.status === 409) {
        setConfirming(false);
        setCancelConflict(
          err.message || "This booking can no longer be cancelled.",
        );
        setReloadKey((key) => key + 1);
        return;
      }
      setCancelError(
        err instanceof ApiError ? err.message : "Could not cancel this booking. Try again.",
      );
    } finally {
      setCancelling(false);
    }
  }

  if (loading) {
    return <TripDetailSkeleton />;
  }

  const backLabel = basePath === "/account/bookings" ? "My orders" : "My trips";

  if (!booking) {
    return (
      <section className="shell py-16">
        <EmptyState
          title={missing ? "Trip not found." : "We could not load this trip."}
          description={
            error ??
            "The booking may have been removed, or the link may be incomplete. Your other trips are still here."
          }
          action={
            missing ? { href: basePath, label: `Back to ${backLabel.toLowerCase()}` } : undefined
          }
        />
        {!missing ? (
          <div className="mt-6 text-center">
            <button type="button" onClick={retry} className="btn-secondary">
              Try again
            </button>
          </div>
        ) : null}
      </section>
    );
  }

  const tour = booking.tour;
  const guestLabel = booking.guestCount === 1 ? "traveller" : "travellers";
  const seatLabel = booking.guestCount === 1 ? "seat" : "seats";
  const fallback = tour ? tourFallbackImage(tour.tourName) : null;

  return (
    <>
      <section className="border-b border-line bg-white/50">
        <div className="shell py-8 sm:py-10">
          <nav aria-label="Breadcrumb" className="text-xs text-muted">
            <ol className="flex flex-wrap items-center gap-2">
              <li>
                <Link href={basePath} className="transition-colors hover:text-forest">
                  {backLabel}
                </Link>
              </li>
              <li aria-hidden="true">/</li>
              <li aria-current="page" className="text-charcoal">
                {booking.bookingCode}
              </li>
            </ol>
          </nav>

          <div className="mt-7 grid gap-8 lg:grid-cols-[1.4fr_0.6fr] lg:items-end">
            <div>
              <div className="flex flex-wrap items-center gap-3">
                <span
                  className={`rounded-[2px] border px-2.5 py-1 text-[11px] font-semibold tracking-wide ${statusTone(booking.status)}`}
                >
                  {statusLabel(booking.status)}
                </span>
                {tour ? (
                  <span className="tag">
                    <IconMapPin className="h-4 w-4 text-forest" />
                    {tour.meetingPoint ?? "Meeting point to be confirmed"}
                  </span>
                ) : null}
              </div>

              <h1 className="mt-4 max-w-3xl font-display text-4xl leading-[1.02] tracking-tight text-charcoal sm:text-5xl">
                {tour?.tourName ?? `Booking ${booking.bookingCode}`}
              </h1>
              <p className="mt-4 max-w-2xl text-lg leading-8 text-muted">
                {statusDescription(booking.status)}
              </p>

              {tour ? (
                <div className="mt-5 flex flex-wrap gap-2">
                  <span className="tag">
                    <IconCalendar className="h-4 w-4 text-forest" />
                    {formatDateRange(tour.startDate, tour.endDate)}
                  </span>
                  <span className="tag">
                    <IconClock className="h-4 w-4 text-forest" />
                    {formatDuration(tour.startDate, tour.endDate)}
                  </span>
                  <span className="tag">
                    <IconUsers className="h-4 w-4 text-forest" />
                    {booking.guestCount} {guestLabel} · {booking.guestCount} {seatLabel}
                  </span>
                </div>
              ) : null}
            </div>

            <div className="rounded-[4px] border border-line bg-white p-5">
              <p className="eyebrow text-muted">Booking code</p>
              <p className="mt-1 text-lg font-semibold tracking-wide">{booking.bookingCode}</p>
              <p className="mt-3 text-sm text-muted">Booked {formatDate(booking.createdAt)}</p>
              {tour ? (
                <Link href={`/tours/${tour.tourId}`} className="btn-secondary mt-4 w-full">
                  View journey
                  <IconArrowRight className="h-4 w-4" />
                </Link>
              ) : null}
            </div>
          </div>
        </div>
      </section>

      <section className="shell grid gap-10 py-10 sm:py-12 lg:grid-cols-[1.5fr_1fr] lg:items-start">
        <div className="space-y-8">
          <section className="overflow-hidden rounded-[4px] border border-line bg-white">
            {tour ? (
              <div className="relative aspect-[16/8] overflow-hidden bg-sage/20">
                <TourImage
                  src={tour.imageUrl}
                  alt={tour.tourName}
                  fallbackSrc={fallback}
                  className="absolute inset-0 h-full w-full"
                />
              </div>
            ) : null}
            <div className="p-6">
              <p className="eyebrow text-forest">The journey</p>
              <dl className="mt-5 space-y-3 text-sm">
                <div className="flex justify-between gap-4 border-b border-line pb-3">
                  <dt className="text-muted">Dates</dt>
                  <dd className="text-right font-medium">
                    {formatDateRange(tour?.startDate, tour?.endDate)}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-line pb-3">
                  <dt className="text-muted">Duration</dt>
                  <dd className="text-right font-medium">
                    {formatDuration(tour?.startDate, tour?.endDate)}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-line pb-3">
                  <dt className="text-muted">Meeting point</dt>
                  <dd className="max-w-[16rem] text-right font-medium">
                    {tour?.meetingPoint ?? "Confirmed on booking"}
                  </dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-muted">Departure</dt>
                  <dd className="text-right font-medium">
                    {tour ? statusLabel(tour.departureStatus) : "To be confirmed"}
                  </dd>
                </div>
              </dl>
            </div>
          </section>

          <section className="rounded-[4px] border border-line bg-white p-6">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <p className="eyebrow text-forest">Travellers</p>
                <h2 className="mt-2 font-display text-2xl tracking-tight">
                  {booking.guestCount} {seatLabel} reserved.
                </h2>
              </div>
              <p className="text-sm text-muted">
                {booking.guestCount} {guestLabel}
              </p>
            </div>

            {booking.travelers.length ? (
              <ul className="mt-5 divide-y divide-line border-t border-line">
                {booking.travelers.map((traveler) => (
                  <li key={traveler.id} className="py-3 text-sm">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-medium">{traveler.name}</span>
                      <span className="text-muted">
                        {[traveler.phone, traveler.email].filter(Boolean).join(" · ") ||
                          "No contact details"}
                      </span>
                    </div>
                    {traveler.dateOfBirth || traveler.gender || traveler.idDocument ? (
                      <p className="mt-1 text-xs text-muted">
                        {[
                          traveler.gender
                            ? traveler.gender.charAt(0) +
                              traveler.gender.slice(1).toLowerCase()
                            : null,
                          traveler.dateOfBirth
                            ? `Born ${formatDate(traveler.dateOfBirth)}`
                            : null,
                          traveler.idDocument ? `ID ${traveler.idDocument}` : null,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </p>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-4 text-sm leading-6 text-muted">
                Traveller names were not captured for this booking. Contact details added at
                checkout appear in the booking note below.
              </p>
            )}
          </section>

          {booking.note ? (
            <section className="rounded-[4px] border border-line bg-white p-6">
              <p className="eyebrow text-forest">Booking note</p>
              <p className="mt-3 whitespace-pre-line text-sm leading-7 text-muted">
                {booking.note}
              </p>
            </section>
          ) : null}
        </div>

        <aside className="space-y-5 lg:sticky lg:top-28">
          <section className="rounded-[4px] border border-line bg-white p-6 shadow-panel">
            <p className="eyebrow text-forest">Order</p>
            <dl className="mt-5 space-y-3 text-sm">
              <div className="flex justify-between gap-4 border-b border-line pb-3">
                <dt className="text-muted">Unit price</dt>
                <dd className="font-medium">{formatPrice(booking.unitPrice)}</dd>
              </div>
              <div className="flex justify-between gap-4 border-b border-line pb-3">
                <dt className="text-muted">
                  {booking.guestCount} × {seatLabel}
                </dt>
                <dd className="font-medium">{formatPrice(booking.subtotal)}</dd>
              </div>
              {booking.discountAmount > 0 ? (
                <div className="flex justify-between gap-4 border-b border-line pb-3 text-terracotta">
                  <dt>Discount{booking.promotion ? ` (${booking.promotion.code})` : ""}</dt>
                  <dd className="font-medium">−{formatPrice(booking.discountAmount)}</dd>
                </div>
              ) : null}
              <div className="flex justify-between gap-4 pt-1 text-base">
                <dt className="font-semibold">Total</dt>
                <dd className="font-semibold text-forest">
                  {formatPrice(booking.totalAmount)}
                </dd>
              </div>
            </dl>
            <p className="mt-4 border-t border-line pt-4 text-xs leading-5 text-muted">
              {booking.status === "PENDING"
                ? "Complete payment to secure this booking."
                : booking.status === "PAID"
                  ? "Payment received in full."
                  : booking.status === "CONFIRMED"
                    ? "Confirmed. Your seats are secured."
                    : booking.status === "COMPLETED"
                      ? "Trip completed."
                      : "This page shows the booking status on record."}
            </p>
          </section>

          {cancelConflict ? (
            <div
              role="alert"
              className="rounded-[4px] border border-terracotta/40 bg-terracotta/10 p-5"
            >
              <p className="font-medium text-terracotta">
                This booking can no longer be cancelled.
              </p>
              <p className="mt-1 text-sm leading-6 text-muted">{cancelConflict}</p>
              <p className="mt-2 text-sm leading-6 text-muted">
                The latest booking status is shown on this page.
              </p>
              {booking.status === "PENDING" ? (
                <button
                  type="button"
                  onClick={() => { void handleCheckPayment(); }}
                  disabled={checkingPayment || paying}
                  className="btn-secondary mt-3"
                >
                  {checkingPayment ? "Checking…" : "Check payment status"}
                </button>
              ) : null}
            </div>
          ) : null}

          {booking.status === "PENDING" ? (
            <section className="rounded-[4px] border border-terracotta/30 bg-white p-6">
              <p className="eyebrow text-terracotta">Payment pending</p>
              <p className="mt-2 text-sm leading-6 text-muted">
                Your seats are held. This booking stays pending until payment is confirmed.
              </p>
              {paymentError ? (
                <p role="alert" className="mt-3 text-sm text-charcoal">{paymentError}</p>
              ) : null}
              {reconcileNotice ? (
                <p
                  role={reconcileNotice.tone === "success" || reconcileNotice.tone === "clear"
                    ? "status" : "alert"}
                  className={`mt-3 text-sm ${
                    reconcileNotice.tone === "success" ? "text-forest"
                      : reconcileNotice.tone === "failed" ? "text-terracotta"
                        : "text-charcoal"
                  }`}
                >
                  {reconcileNotice.message}
                </p>
              ) : null}
              {booking.totalAmount > 0 && vnpayCheckoutEnabled() ? (
                <button
                  type="button"
                  onClick={handleVnpay}
                  disabled={paying || checkingPayment || paymentBlocked}
                  className="btn-primary mt-4 w-full"
                >
                  {paying ? "Opening VNPay…" : paymentBlocked ? "Waiting for VNPay" : "Pay with VNPay"}
                  <IconArrowRight className="h-4 w-4" />
                </button>
              ) : null}
              <button
                type="button"
                onClick={() => { void handleCheckPayment(); }}
                disabled={checkingPayment || paying}
                className="btn-secondary mt-2 w-full"
              >
                {checkingPayment ? "Checking…" : "Check payment status"}
              </button>
            </section>
          ) : booking.status === "PAID" ? (
            <section className="rounded-[4px] border border-forest/30 bg-white p-6">
              <p className="eyebrow text-forest">Payment received</p>
              <p className="mt-2 text-sm leading-6 text-muted">
                Paid in full. Confirm the booking to secure the trip.
              </p>
              {advanceError ? (
                <p role="alert" className="mt-3 text-sm text-terracotta">
                  {advanceError}
                </p>
              ) : null}
              <div className="mt-4 flex flex-col gap-2">
                <button
                  type="button"
                  onClick={() => handleLifecycle("confirm")}
                  disabled={advancing}
                  className="btn-primary w-full"
                >
                  {advancing ? "Confirming…" : "Confirm booking"}
                  <IconArrowRight className="h-4 w-4" />
                </button>
                {canViewInvoice(booking) ? (
                  <Link href={`/invoices/${booking.id}`} className="btn-secondary w-full text-center">
                    View invoice
                  </Link>
                ) : null}
              </div>
            </section>
          ) : booking.status === "CONFIRMED" ? (
            <section className="rounded-[4px] border border-forest/30 bg-white p-6">
              <p className="eyebrow text-forest">Booking confirmed</p>
              <p className="mt-2 text-sm leading-6 text-muted">
                Your seats are secured. Once the trip is over, you can mark the booking
                completed.
              </p>
              {advanceError ? (
                <p role="alert" className="mt-3 text-sm text-terracotta">
                  {advanceError}
                </p>
              ) : null}
              <div className="mt-4 flex flex-col gap-2">
                <button
                  type="button"
                  onClick={() => handleLifecycle("complete")}
                  disabled={advancing}
                  className="btn-primary w-full"
                >
                  {advancing ? "Completing…" : "Mark trip completed"}
                  <IconArrowRight className="h-4 w-4" />
                </button>
                {canViewInvoice(booking) ? (
                  <Link href={`/invoices/${booking.id}`} className="btn-secondary w-full text-center">
                    View invoice
                  </Link>
                ) : null}
              </div>
            </section>
          ) : booking.status === "COMPLETED" ? (
            <section className="rounded-[4px] border border-line bg-white p-6">
              <p className="eyebrow text-forest">Trip completed</p>
              <p className="mt-2 text-sm leading-6 text-muted">
                This journey is complete. Your invoice is available below.
              </p>
              <Link href={`/invoices/${booking.id}`} className="btn-secondary mt-4 w-full text-center">
                View invoice
              </Link>
            </section>
          ) : null}

          {canCancelBooking(booking) ? (
            <section className="rounded-[4px] border border-terracotta/30 bg-white p-6">
              <p className="eyebrow text-terracotta">Need to change plans?</p>
              {confirming ? (
                <div role="alert">
                  <p className="mt-2 text-sm leading-6 text-muted">
                    Cancel this booking? The {booking.guestCount} {seatLabel} will be released
                    and cannot be reserved again from here.
                  </p>
                  {cancelError ? (
                    <p className="mt-2 text-sm text-terracotta">{cancelError}</p>
                  ) : null}
                  <div className="mt-4 flex flex-wrap gap-2">
                    <button
                      ref={confirmRef}
                      type="button"
                      onClick={handleCancel}
                      disabled={cancelling}
                      className="inline-flex min-h-11 items-center justify-center rounded-[3px] bg-terracotta px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-terracotta/90 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {cancelling ? "Cancelling…" : "Yes, cancel booking"}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setConfirming(false);
                        setCancelError(null);
                      }}
                      disabled={cancelling}
                      className="btn-secondary"
                    >
                      Keep booking
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <p className="mt-2 text-sm leading-6 text-muted">
                    Pending bookings can be cancelled before payment. Your seats will be
                    released.
                  </p>
                  <button
                    type="button"
                    onClick={() => setConfirming(true)}
                    className="mt-4 inline-flex min-h-11 items-center justify-center rounded-[3px] border border-terracotta/40 px-5 py-2 text-sm font-medium text-terracotta transition-colors hover:bg-terracotta/10"
                  >
                    Cancel booking
                  </button>
                </>
              )}
            </section>
          ) : booking.status === "CANCELLED" ? (
            <section className="rounded-[4px] border border-line bg-white p-6">
              <p className="eyebrow text-muted">Cancelled</p>
              <p className="mt-2 text-sm leading-6 text-muted">
                This booking is closed and its seats have been released.
              </p>
              <Link href="/tours" className="btn-secondary mt-4 w-full">
                Find another journey
              </Link>
            </section>
          ) : (
            <section className="rounded-[4px] border border-line bg-white p-6">
              <p className="eyebrow text-forest">Need help?</p>
              <p className="mt-2 text-sm leading-6 text-muted">
                To change a paid or confirmed booking, talk to us and we will find the best
                option.
              </p>
              <a
                href="mailto:hello@hanhtrinh.example"
                className="mt-4 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-forest hover:underline"
              >
                Contact us
                <IconArrowRight className="h-4 w-4" />
              </a>
            </section>
          )}

          <Link href={basePath} className="btn-secondary w-full">
            Back to {backLabel.toLowerCase()}
          </Link>
        </aside>
      </section>
    </>
  );
}
