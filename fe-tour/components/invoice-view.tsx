"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, getBooking, listMyInvoices } from "@/lib/api";
import { clearSession, ensureSession } from "@/lib/auth";
import { formatDate, formatDateRange, formatPrice } from "@/lib/format";
import type { Booking, Invoice } from "@/lib/types";
import { EmptyState } from "./empty-state";
import { IconArrowRight } from "./icons";

export function InvoiceView({ bookingId }: { bookingId: number }) {
  const router = useRouter();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const loginHref = `/login?next=${encodeURIComponent(`/invoices/${bookingId}`)}`;

  useEffect(() => {
    let active = true;

    async function load() {
      const current = await ensureSession();
      if (!current) {
        router.replace(loginHref);
        return;
      }

      try {
        const [bookingResult, invoicesResult] = await Promise.all([
          getBooking(current.accessToken, bookingId),
          listMyInvoices(current.accessToken, 0, 100),
        ]);
        if (!active) return;
        setBooking(bookingResult);
        setInvoice(
          invoicesResult.content.find((item) => item.bookingId === bookingId) ?? null,
        );
        setLoading(false);
      } catch (err) {
        if (!active) return;
        if (err instanceof ApiError && err.status === 401) {
          clearSession();
          router.replace(loginHref);
          return;
        }
        if (err instanceof ApiError && err.status === 404) {
          setMissing(true);
          setLoading(false);
          return;
        }
        setError(err instanceof ApiError ? err.message : "Could not load the invoice.");
        setLoading(false);
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [bookingId, loginHref, router]);

  if (loading) {
    return (
      <div className="shell py-16">
        <p className="text-sm text-muted">Loading your invoice…</p>
      </div>
    );
  }

  if (missing || error) {
    return (
      <div className="shell py-16">
        <EmptyState
          title={missing ? "Invoice not found" : "Could not load the invoice"}
          description={
            missing
              ? "We could not find an invoice for this booking yet. Invoices are created once payment is received."
              : (error ?? "Please try again.")
          }
          action={{ label: "Back to trip details", href: `/my-trips/${bookingId}` }}
        />
      </div>
    );
  }

  if (!booking) {
    return null;
  }

  const tour = booking.tour;

  return (
    <div className="shell py-10 sm:py-14">
      <div className="mx-auto max-w-3xl">
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3 print:hidden">
          <Link href={`/my-trips/${bookingId}`} className="btn-secondary">
            Back to trip details
          </Link>
          <button type="button" onClick={() => window.print()} className="btn-primary">
            Print invoice
            <IconArrowRight className="h-4 w-4" />
          </button>
        </div>

        <div className="rounded-[4px] border border-line bg-white p-6 shadow-panel sm:p-10 print:border-0 print:bg-white print:p-0 print:shadow-none">
          <header className="flex flex-wrap items-start justify-between gap-6 border-b border-line pb-6">
            <div>
              <p className="eyebrow text-terracotta">Hành Trình</p>
              <h1 className="mt-2 font-display text-3xl tracking-tight">Invoice</h1>
              <p className="mt-1 text-sm text-muted">
                Invoice #{invoice?.id ?? "pending"} · Booking {booking.bookingCode}
              </p>
            </div>
            <div className="text-right text-sm">
              <p className="font-semibold">{invoice?.customerName ?? "Guest"}</p>
              {invoice?.customerEmail ? <p className="text-muted">{invoice.customerEmail}</p> : null}
              {invoice?.customerPhone ? <p className="text-muted">{invoice.customerPhone}</p> : null}
              <p className="mt-2 text-xs text-muted">
                Issued {invoice ? formatDate(invoice.createdAt) : "on payment"}
              </p>
            </div>
          </header>

          {tour ? (
            <section className="border-b border-line py-6">
              <p className="eyebrow text-forest">Journey</p>
              <p className="mt-2 font-display text-xl tracking-tight">{tour.tourName}</p>
              <p className="mt-1 text-sm text-muted">
                {formatDateRange(tour.startDate, tour.endDate)}
                {tour.meetingPoint ? ` · ${tour.meetingPoint}` : ""}
              </p>
            </section>
          ) : null}

          <section className="py-6">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-[0.18em] text-muted">
                  <th className="pb-3">Description</th>
                  <th className="pb-3 text-right">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                <tr>
                  <td className="py-3">
                    {booking.guestCount} × seat{booking.guestCount === 1 ? "" : "s"}
                    {tour ? ` — ${tour.tourName}` : ""}
                    <span className="block text-xs text-muted">
                      {formatPrice(booking.unitPrice)} per seat
                    </span>
                  </td>
                  <td className="py-3 text-right font-medium">{formatPrice(booking.subtotal)}</td>
                </tr>
                {booking.discountAmount > 0 ? (
                  <tr>
                    <td className="py-3 text-terracotta">
                      Discount{booking.promotion ? ` (${booking.promotion.code})` : ""}
                    </td>
                    <td className="py-3 text-right text-terracotta">
                      −{formatPrice(booking.discountAmount)}
                    </td>
                  </tr>
                ) : null}
                {invoice && invoice.serviceFee > 0 ? (
                  <tr>
                    <td className="py-3">Service fee</td>
                    <td className="py-3 text-right">{formatPrice(invoice.serviceFee)}</td>
                  </tr>
                ) : null}
                {invoice && invoice.taxAmount > 0 ? (
                  <tr>
                    <td className="py-3">Tax</td>
                    <td className="py-3 text-right">{formatPrice(invoice.taxAmount)}</td>
                  </tr>
                ) : null}
              </tbody>
              <tfoot>
                <tr>
                  <td className="pt-4 text-base font-semibold">Total</td>
                  <td className="pt-4 text-right text-base font-semibold text-forest">
                    {formatPrice(invoice ? invoice.totalAmount : booking.totalAmount)}
                  </td>
                </tr>
              </tfoot>
            </table>
          </section>

          <footer className="border-t border-line pt-6 text-xs leading-6 text-muted">
            <div className="flex flex-wrap justify-between gap-4">
              <div>
                <p className="eyebrow text-forest">Payment</p>
                <p className="mt-1">
                  {invoice?.paymentMethod ?? "CARD"} · {invoice?.status ?? booking.status}
                </p>
              </div>
              <div className="text-right">
                <p className="eyebrow text-forest">Questions</p>
                <p className="mt-1">hello@hanhtrinh.example · +84 28 0000 0000</p>
              </div>
            </div>
            <p className="mt-4">
              This is a demo invoice for a demo booking experience. No real payment was processed.
            </p>
          </footer>
        </div>
      </div>
    </div>
  );
}
