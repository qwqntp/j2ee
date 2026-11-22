"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, getBooking, getVnpayStatus, verifyVnpayReturn } from "@/lib/api";
import { clearSession, ensureSession, getStoredSession } from "@/lib/auth";
import type { VnpayStatus } from "@/lib/types";
import {
  forgetVnpayReturn,
  readVnpayReturn,
  restoreVnpayReturn,
  stashVnpayReturn,
  vnpayResultState,
  vnpayReturnHref,
  vnpayReturnLoginHref,
  withVnpaySession,
} from "@/lib/vnpay";
import { pollVnpayBooking } from "@/lib/vnpay-polling";

const CARD = "mx-auto max-w-xl rounded-[4px] border border-line bg-white p-6 sm:p-9";
const TITLES = {
  success: "Payment confirmed",
  failed: "Payment was not completed",
  cancelled: "VNPay reported a cancellation",
  expired: "The payment link expired",
  pending: "Confirming your payment…",
};
const CONFIRMATION_WAIT = "Confirmation may take a few seconds. Please do not pay again.";
const CONFIRMATION_TIMEOUT = "Your payment is still being confirmed. Please do not pay again.";

type Check =
  | { kind: "status"; status: VnpayStatus }
  | { kind: "booking-cancelled"; bookingId: number }
  | { kind: "sign-in" }
  | { kind: "invalid" }
  | { kind: "network" };

function tabStorage() {
  try {
    return window.sessionStorage;
  } catch {
    return undefined;
  }
}

export function VnpayReturnLoading() {
  return (
    <section className={CARD} role="status">
      <h1 className="font-display text-3xl tracking-tight">{TITLES.pending}</h1>
      <p className="mt-3 text-sm leading-6 text-muted">{CONFIRMATION_WAIT}</p>
    </section>
  );
}

export function VnpayReturn() {
  const params = useSearchParams();
  const context = readVnpayReturn(new URLSearchParams(params.toString()));
  if (!context) {
    return (
      <section className={CARD}>
        <h1 className="font-display text-3xl tracking-tight">Payment link not recognized</h1>
        <p className="mt-3 text-sm leading-6 text-muted">Open your booking to check its status.</p>
        <Link href="/account/bookings" className="btn-primary mt-6">View booking history</Link>
      </section>
    );
  }
  return <VnpayReturnResult key={context.reference} reference={context.reference} initialQuery={context.query} />;
}

function VnpayReturnResult({ reference, initialQuery }: { reference: string; initialQuery: string | null }) {
  const [result, setResult] = useState<Check | null>(null);
  const [checking, setChecking] = useState(false);
  const [timedOutBooking, setTimedOutBooking] = useState<number | null>(null);
  const pendingQuery = useRef(initialQuery);
  const returnOutcome = useRef<VnpayStatus["returnOutcome"]>(null);
  const inFlight = useRef<Promise<Check> | null>(null);
  const mounted = useRef(false);

  const checkPayment = useCallback(() => {
    // Both Strict Mode effect passes, and any click while active, share the same session check.
    if (inFlight.current) return inFlight.current;
    const task = (async (): Promise<Check> => {
      const storage = tabStorage();
      const query = pendingQuery.current ?? restoreVnpayReturn(storage, reference, Date.now());
      const cleanHref = vnpayReturnHref(reference);
      function stripProviderQuery() {
        if (!mounted.current) return;
        try {
          if (window.location.search !== `?ref=${reference}`) window.history.replaceState(null, "", cleanHref);
        } catch {
          // A browser history restriction must not discard the result or block sign-in.
        }
      }
      try {
        const status = await withVnpaySession(
          async () => (await ensureSession())?.accessToken ?? null,
          clearSession,
          (token) => query ? verifyVnpayReturn(token, query) : getVnpayStatus(token, reference),
        );
        if (!status) {
          if (query) stashVnpayReturn(storage, reference, query, Date.now());
          stripProviderQuery();
          return { kind: "sign-in" };
        }
        pendingQuery.current = null;
        forgetVnpayReturn(storage, reference);
        stripProviderQuery();
        returnOutcome.current = status.returnOutcome ?? returnOutcome.current;
        return { kind: "status", status: { ...status, returnOutcome: returnOutcome.current } };
      } catch (error) {
        if (error instanceof ApiError && (error.status === 400 || error.status === 404)) {
          pendingQuery.current = null;
          forgetVnpayReturn(storage, reference);
          stripProviderQuery();
          return { kind: "invalid" };
        }
        stripProviderQuery();
        return { kind: "network" };
      }
    })();
    inFlight.current = task;
    void task.then(() => { if (inFlight.current === task) inFlight.current = null; });
    return task;
  }, [reference]);

  useEffect(() => {
    mounted.current = true;
    void checkPayment().then((next) => { if (mounted.current) setResult(next); });
    return () => { mounted.current = false; };
  }, [checkPayment]);

  const status = result?.kind === "status" ? result.status : null;
  const pendingBooking = status?.status === "PENDING" ? status.bookingId : null;
  const polling = pendingBooking !== null && pendingBooking !== timedOutBooking;

  useEffect(() => {
    if (!polling || pendingBooking === null) return;
    // The VNPay status endpoint also calls QueryDr; only read the booking while waiting for IPN.
    return pollVnpayBooking({ bookingId: pendingBooking, status: "PENDING" }, async (id, signal) => {
      const booking = await withVnpaySession(
        async () => {
          signal.throwIfAborted();
          return (getStoredSession() ?? await ensureSession())?.accessToken ?? null;
        },
        () => { if (!signal.aborted) clearSession(); },
        (token) => getBooking(token, id, signal),
      );
      return booking?.status ?? null;
    }, (outcome) => {
      switch (outcome) {
        case "paid":
          setResult((current) => current?.kind === "status" && current.status.bookingId === pendingBooking
            ? { kind: "status", status: { ...current.status, status: "SUCCESS" } }
            : current);
          break;
        case "cancelled":
          setResult({ kind: "booking-cancelled", bookingId: pendingBooking });
          break;
        case "timeout":
          setTimedOutBooking(pendingBooking);
          break;
        default:
          setResult({ kind: outcome });
      }
    });
  }, [pendingBooking, polling]);

  async function recheck() {
    if (polling || inFlight.current) return;
    setChecking(true);
    const next = await checkPayment();
    if (!mounted.current) return;
    // Leave the last known pending state and booking link intact if a manual read fails.
    if (next.kind !== "network" || pendingBooking === null) setResult(next);
    setChecking(false);
  }

  if (!result) return <VnpayReturnLoading />;
  const unresolved = pendingBooking !== null;
  const state = status && !unresolved ? vnpayResultState(status) : null;
  const bookingId = status?.bookingId ?? (result.kind === "booking-cancelled" ? result.bookingId : null);
  const title = unresolved ? (polling ? TITLES.pending : "Payment confirmation pending")
    : state ? TITLES[state] : result.kind === "booking-cancelled" ? "Booking cancelled"
    : result.kind === "sign-in" ? "Sign in to check your payment"
    : result.kind === "invalid" ? "We couldn’t verify this payment link" : "Payment status is unavailable";
  const description = state === "success" ? "The payment is recorded on your booking."
    : unresolved ? (polling ? CONFIRMATION_WAIT : CONFIRMATION_TIMEOUT)
    : result.kind === "booking-cancelled" ? "Open your booking for its current status. If you were charged, contact support."
    : state ? "This attempt did not pay the booking. Return to your booking for the available options."
    : result.kind === "sign-in" ? "Sign in with the account that made the booking to continue this payment check."
    : result.kind === "invalid" ? "Check your booking history for its current status."
    : "No payment was confirmed by this check. Try again or open your booking history.";

  return (
    <section className={CARD} aria-busy={checking}>
      <div role={result.kind === "invalid" || result.kind === "network" ? "alert" : "status"}>
        <h1 className="font-display text-3xl tracking-tight sm:text-4xl">{title}</h1>
        <p className="mt-3 text-sm leading-6 text-muted">{description}</p>
      </div>
      <div className="mt-6 flex flex-wrap gap-3">
        {result.kind === "sign-in" && (
          <Link className="btn-primary" href={vnpayReturnLoginHref(reference)}>Sign in</Link>
        )}
        {((unresolved && !polling) || result.kind === "network") && (
          <button type="button" className="btn-primary" onClick={recheck} disabled={checking}>
            {checking ? "Checking…" : "Check payment status"}
          </button>
        )}
        {bookingId !== null && (
          <Link className="btn-secondary" href={`/account/bookings/${bookingId}`}>View booking</Link>
        )}
        <Link className="btn-secondary" href="/account/bookings">Booking history</Link>
      </div>
    </section>
  );
}
