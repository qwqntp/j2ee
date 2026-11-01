"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, createBooking } from "@/lib/api";
import { clearSession, ensureSession } from "@/lib/auth";
import { formatDateRange, formatDuration, formatPrice } from "@/lib/format";
import type { Booking, TourDeparture } from "@/lib/types";
import { IconArrowRight, IconCheck, IconShield, IconUsers } from "./icons";
import { TourImage } from "./tour-image";

type Props = {
  tourId: number;
  tourName: string;
  departure: TourDeparture;
  imageUrl: string | null;
  fallbackImage: string | null;
};

export function PaymentForm({ tourId, tourName, departure, imageUrl, fallbackImage }: Props) {
  const router = useRouter();
  const maxGuests = Math.max(1, Math.min(departure.seatsAvailable, 20));
  const [guests, setGuests] = useState(Math.min(2, maxGuests));
  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [signedIn, setSignedIn] = useState<boolean | null>(null);
  const total = Math.round(departure.price * guests);
  const loginHref = `/login?next=${encodeURIComponent(
    `/payment?tourId=${tourId}&departureId=${departure.id}`,
  )}`;
  const registerHref = `/register?next=${encodeURIComponent(
    `/payment?tourId=${tourId}&departureId=${departure.id}`,
  )}`;

  useEffect(() => {
    let active = true;
    ensureSession().then((session) => {
      if (active) setSignedIn(session !== null);
    });
    return () => {
      active = false;
    };
  }, []);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setPending(true);

    const data = new FormData(event.currentTarget);
    const contact = ["name", "email", "phone"]
      .map((key) => String(data.get(key) ?? "").trim())
      .filter(Boolean)
      .join(" | ");
    const notes = String(data.get("notes") ?? "").trim();
    const note = [contact, notes].filter(Boolean).join(" — ");

    try {
      const session = await ensureSession();
      if (!session) {
        router.push(loginHref);
        return;
      }

      const created = await createBooking(session.accessToken, {
        departureId: departure.id,
        guestCount: guests,
        note: note || undefined,
      });
      setBooking(created);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearSession();
        router.push(loginHref);
        return;
      }
      setError(
        err instanceof ApiError ? err.message : "Could not reach the booking service. Try again.",
      );
    } finally {
      setPending(false);
    }
  }

  if (booking) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="mx-auto max-w-2xl rounded-[4px] border border-line bg-white p-8 text-center sm:p-12"
      >
        <span className="mx-auto grid h-14 w-14 place-items-center rounded-[3px] bg-sage/25 text-forest">
          <IconCheck className="h-7 w-7" />
        </span>
        <p className="eyebrow mt-6 text-terracotta">Seats reserved</p>
        <h2 className="mt-3 font-display text-3xl tracking-tight sm:text-4xl">
          Your booking is {booking.bookingCode}.
        </h2>
        <p className="mt-3 leading-7 text-muted">
          {booking.guestCount} {booking.guestCount === 1 ? "traveler" : "travelers"} on{" "}
          {formatDateRange(departure.startDate, departure.endDate)}. Status: {booking.status}.
          Payment is collected separately.
        </p>
        <dl className="mx-auto mt-6 max-w-sm space-y-2 text-left text-sm">
          <div className="flex justify-between gap-4 border-b border-line pb-2">
            <dt className="text-muted">Booking code</dt>
            <dd className="font-semibold">{booking.bookingCode}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-muted">Total</dt>
            <dd className="font-semibold text-forest">{formatPrice(booking.totalAmount)}</dd>
          </div>
        </dl>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Link href={`/tours/${tourId}`} className="btn-primary">
            Back to the journey
          </Link>
          <Link href="/tours" className="btn-secondary">
            Browse more journeys
          </Link>
        </div>
      </div>
    );
  }

  if (signedIn === false) {
    return (
      <div className="mx-auto max-w-2xl rounded-[4px] border border-line bg-white p-8 text-center sm:p-12">
        <p className="eyebrow text-terracotta">Sign in required</p>
        <h2 className="mt-3 font-display text-3xl tracking-tight sm:text-4xl">
          Sign in to reserve these seats.
        </h2>
        <p className="mt-3 leading-7 text-muted">
          Bookings are tied to your account so you can find them again. Choose the dates after
          signing in and we will hold the seats.
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Link href={loginHref} className="btn-primary">
            Sign in
            <IconArrowRight className="h-4 w-4" />
          </Link>
          <Link href={registerHref} className="btn-secondary">
            Create an account
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="grid gap-10 lg:grid-cols-[1.35fr_0.65fr] lg:items-start">
      <form
        onSubmit={onSubmit}
        className="space-y-6"
      >
        <section className="rounded-[4px] border border-line bg-white p-6 sm:p-8">
          <div className="flex flex-wrap items-start justify-between gap-4 border-b border-line pb-5">
            <div>
              <p className="eyebrow text-terracotta">Step 01</p>
              <h2 className="mt-2 font-display text-2xl tracking-tight">Who is traveling?</h2>
            </div>
            <span className="tag">
              <IconUsers className="h-4 w-4 text-forest" />
              {guests} {guests === 1 ? "traveler" : "travelers"}
            </span>
          </div>

          <div className="mt-6 grid gap-5 sm:grid-cols-2">
            <div>
              <label htmlFor="contact-name" className="label">
                Full name
              </label>
              <input
                id="contact-name"
                name="name"
                autoComplete="name"
                required
                className="field"
                placeholder="Your name"
              />
            </div>
            <div>
              <label htmlFor="contact-email" className="label">
                Email
              </label>
              <input
                id="contact-email"
                name="email"
                type="email"
                autoComplete="email"
                required
                className="field"
                placeholder="you@example.com"
              />
            </div>
            <div>
              <label htmlFor="contact-phone" className="label">
                Phone
              </label>
              <input
                id="contact-phone"
                name="phone"
                type="tel"
                autoComplete="tel"
                required
                className="field"
                placeholder="+84 ..."
              />
            </div>
            <div>
              <span className="label">Travelers</span>
              <div className="mt-2 flex min-h-12 items-center justify-between rounded-[3px] border border-line bg-white px-2">
                <button
                  type="button"
                  onClick={() => setGuests((current) => Math.max(1, current - 1))}
                  disabled={guests <= 1}
                  aria-label="Remove a traveler"
                  className="grid h-9 w-9 place-items-center rounded-[2px] border border-line text-lg text-forest transition-colors hover:bg-sage/20 disabled:cursor-not-allowed disabled:border-line/60 disabled:text-muted/35"
                >
                  −
                </button>
                <span aria-live="polite" className="text-sm font-semibold">
                  {guests}
                </span>
                <button
                  type="button"
                  onClick={() => setGuests((current) => Math.min(maxGuests, current + 1))}
                  disabled={guests >= maxGuests}
                  aria-label="Add a traveler"
                  className="grid h-9 w-9 place-items-center rounded-[2px] border border-line text-lg text-forest transition-colors hover:bg-sage/20 disabled:cursor-not-allowed disabled:border-line/60 disabled:text-muted/35"
                >
                  +
                </button>
              </div>
              <p className="mt-1.5 text-xs text-muted">
                This departure allows up to {maxGuests} on this date.
              </p>
            </div>
          </div>

          <div className="mt-5">
            <label htmlFor="notes" className="label">
              Anything we should know?{" "}
              <span className="font-normal text-muted">(optional)</span>
            </label>
            <textarea
              id="notes"
              name="notes"
              rows={3}
              className="field resize-y"
              placeholder="Dietary needs, arrival time, a celebration…"
            />
          </div>
        </section>

        <section className="rounded-[4px] border border-line bg-white p-6 sm:p-8">
          <p className="eyebrow text-terracotta">Step 02</p>
          <h2 className="mt-2 font-display text-2xl tracking-tight">
            How would you like to pay?
          </h2>
          <p className="mt-2 text-sm leading-6 text-muted">
            Seats are reserved now and payment is arranged separately. No card details are collected
            here.
          </p>
          <div className="mt-6 space-y-3">
            <label className="flex cursor-pointer items-start gap-3 rounded-[3px] border border-line bg-ivory/60 p-4 transition has-[:checked]:border-forest has-[:checked]:bg-sage/15">
              <input
                type="radio"
                name="payment"
                value="office"
                defaultChecked
                className="mt-1 accent-forest"
              />
              <span>
                <span className="block text-sm font-semibold">Pay at the office</span>
                <span className="mt-0.5 block text-sm text-muted">
                  Hold the seats now, settle in person later.
                </span>
              </span>
            </label>
            <label className="flex cursor-pointer items-start gap-3 rounded-[3px] border border-line bg-ivory/60 p-4 transition has-[:checked]:border-forest has-[:checked]:bg-sage/15">
              <input type="radio" name="payment" value="transfer" className="mt-1 accent-forest" />
              <span>
                <span className="block text-sm font-semibold">Bank transfer</span>
                <span className="mt-0.5 block text-sm text-muted">
                  We would send the account details after a real booking.
                </span>
              </span>
            </label>
          </div>
        </section>

        <div className="flex flex-col gap-3 border-t border-line pt-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="flex items-center gap-2 text-xs text-muted">
            <IconShield className="h-4 w-4 text-forest" />
            {error ? <span role="alert" className="text-terracotta">{error}</span> : "Reserves seats now. Payment is arranged separately."}
          </p>
          <button type="submit" disabled={pending} className="btn-primary">
            {pending ? "Reserving…" : "Reserve these seats"}
            <IconArrowRight className="h-4 w-4" />
          </button>
        </div>
      </form>

      <aside className="lg:sticky lg:top-28">
        <div className="overflow-hidden rounded-[4px] border border-line bg-white shadow-panel">
          <div className="relative aspect-[16/10] bg-sage/20">
            <TourImage
              src={imageUrl}
              alt={tourName}
              fallbackSrc={fallbackImage}
              className="absolute inset-0 h-full w-full"
            />
            <span className="absolute left-3 top-3 rounded-[2px] bg-ivory/95 px-2.5 py-1 text-[11px] font-semibold text-forest">
              Checkout
            </span>
          </div>
          <div className="p-6">
            <p className="eyebrow text-forest">Your journey</p>
            <h2 className="mt-2 font-display text-2xl leading-tight tracking-tight">{tourName}</h2>
            <dl className="mt-5 space-y-3 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-muted">Dates</dt>
                <dd className="text-right font-medium">
                  {formatDateRange(departure.startDate, departure.endDate)}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted">Duration</dt>
                <dd className="font-medium">
                  {formatDuration(departure.startDate, departure.endDate)}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted">Travelers</dt>
                <dd className="font-medium">{guests}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted">Price each</dt>
                <dd className="font-medium">{formatPrice(departure.price)}</dd>
              </div>
            </dl>
            <div className="mt-5 flex items-end justify-between border-t border-line pt-4">
              <span className="text-sm font-medium">Total</span>
              <span className="text-xl font-semibold text-forest">{formatPrice(total)}</span>
            </div>
            <p className="mt-3 text-xs leading-5 text-muted">
              Total follows the live departure price. Seats are checked again when you reserve.
            </p>
          </div>
        </div>
      </aside>
    </div>
  );
}
