"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, createBooking } from "@/lib/api";
import { clearSession, ensureSession } from "@/lib/auth";
import {
  buildBookingRequest,
  resizeTravelers,
  validateTravelerDrafts,
  type TravelerDraft,
} from "@/lib/booking-form";
import { publishFlash } from "@/lib/flash";
import { formatDateRange, formatDuration, formatPrice } from "@/lib/format";
import type { TourDeparture } from "@/lib/types";
import { IconArrowRight, IconShield, IconUsers } from "./icons";
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
  const maxGuests = Math.max(1, departure.seatsAvailable);
  const initialGuests = Math.min(2, maxGuests);
  const [guests, setGuests] = useState(initialGuests);
  const [travelers, setTravelers] = useState<TravelerDraft[]>(() =>
    resizeTravelers([], initialGuests),
  );
  const [note, setNote] = useState("");
  const [promotionCode, setPromotionCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [signedIn, setSignedIn] = useState<boolean | null>(null);

  const returnTo = `/payment?tourId=${tourId}&departureId=${departure.id}`;
  const loginHref = `/login?next=${encodeURIComponent(returnTo)}`;
  const registerHref = `/register?next=${encodeURIComponent(returnTo)}`;
  const estimate = departure.price * guests;

  useEffect(() => {
    let active = true;

    ensureSession().then((session) => {
      if (!active) return;
      if (session) {
        setSignedIn(true);
      } else {
        setSignedIn(false);
        router.replace(loginHref);
      }
    });

    return () => {
      active = false;
    };
  }, [loginHref, router]);

  function changeGuests(next: number) {
    const clamped = Math.max(1, Math.min(maxGuests, next));
    setGuests(clamped);
    setTravelers((current) => resizeTravelers(current, clamped));
  }

  function updateTraveler<K extends keyof TravelerDraft>(
    index: number,
    field: K,
    value: TravelerDraft[K],
  ) {
    setTravelers((current) =>
      current.map((traveler, position) =>
        position === index ? { ...traveler, [field]: value } : traveler,
      ),
    );
  }

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;

    setError(null);
    setConflict(null);

    const validation = validateTravelerDrafts(travelers);
    if (validation) {
      setError(validation);
      return;
    }
    if (promotionCode.trim().length > 64) {
      setError("Promotion code must be at most 64 characters.");
      return;
    }

    setPending(true);
    try {
      const session = await ensureSession();
      if (!session) {
        router.replace(loginHref);
        return;
      }

      const created = await createBooking(
        session.accessToken,
        buildBookingRequest(departure.id, guests, note, travelers, promotionCode),
      );

      publishFlash("Seats reserved. Your booking is pending.");
      router.push(`/account/bookings/${created.id}`);
      router.refresh();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearSession();
        router.replace(loginHref);
        return;
      }
      if (err instanceof ApiError && err.status === 409) {
        setConflict(
          err.message ||
            "This departure is no longer open for booking. Choose another date.",
        );
        return;
      }
      setError(
        err instanceof ApiError ? err.message : "Could not reach the booking service. Try again.",
      );
    } finally {
      setPending(false);
    }
  }

  if (signedIn !== true) {
    return (
      <div className="mx-auto max-w-2xl rounded-[4px] border border-line bg-white p-8 text-center sm:p-12">
        <p className="eyebrow text-terracotta">Sign in required</p>
        <h2 className="mt-3 font-display text-3xl tracking-tight sm:text-4xl">
          Taking you to sign in.
        </h2>
        <p className="mt-3 leading-7 text-muted">
          Bookings are tied to your account so you can find them again. You will come straight
          back to these seats.
        </p>
        {signedIn === false ? (
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <Link href={loginHref} className="btn-primary">
              Sign in
              <IconArrowRight className="h-4 w-4" />
            </Link>
            <Link href={registerHref} className="btn-secondary">
              Create an account
            </Link>
          </div>
        ) : (
          <p className="mt-6 text-sm text-muted">Checking your session…</p>
        )}
      </div>
    );
  }

  return (
    <div className="grid gap-10 lg:grid-cols-[1.35fr_0.65fr] lg:items-start">
      <form onSubmit={onSubmit} className="space-y-6">
        {conflict ? (
          <div
            role="alert"
            className="rounded-[4px] border border-terracotta/40 bg-terracotta/10 p-5"
          >
            <p className="font-medium text-terracotta">These seats could not be reserved.</p>
            <p className="mt-1 text-sm leading-6 text-muted">{conflict}</p>
            <Link href={`/tours/${tourId}#departures`} className="btn-secondary mt-4">
              Choose another date
            </Link>
          </div>
        ) : null}

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

          <div className="mt-6 flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium">Seats on this departure</p>
              <p className="mt-1 text-xs leading-5 text-muted">
                This date allows up to {maxGuests} travelers in one booking.
              </p>
            </div>
            <div className="flex min-h-12 items-center gap-4 rounded-[3px] border border-line bg-white px-2">
              <button
                type="button"
                onClick={() => changeGuests(guests - 1)}
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
                onClick={() => changeGuests(guests + 1)}
                disabled={guests >= maxGuests}
                aria-label="Add a traveler"
                className="grid h-9 w-9 place-items-center rounded-[2px] border border-line text-lg text-forest transition-colors hover:bg-sage/20 disabled:cursor-not-allowed disabled:border-line/60 disabled:text-muted/35"
              >
                +
              </button>
            </div>
          </div>

          <div className="mt-6 space-y-5">
            {travelers.map((traveler, index) => (
              <fieldset
                key={index}
                className="rounded-[3px] border border-line bg-ivory/50 p-4 sm:p-5"
              >
                <legend className="px-1 text-sm font-semibold">
                  Traveler {index + 1}
                  {index === 0 ? (
                    <span className="ml-2 text-xs font-normal text-muted">Lead traveler</span>
                  ) : null}
                </legend>
                <div className="mt-3 grid gap-4 sm:grid-cols-2">
                  <div className="sm:col-span-2">
                    <label htmlFor={`traveler-${index}-name`} className="label">
                      Full name <span className="font-normal text-muted">(required)</span>
                    </label>
                    <input
                      id={`traveler-${index}-name`}
                      name={`traveler-${index}-name`}
                      type="text"
                      required
                      maxLength={255}
                      autoComplete={index === 0 ? "name" : "off"}
                      value={traveler.fullName}
                      onChange={(event) =>
                        updateTraveler(index, "fullName", event.target.value)
                      }
                      className="field"
                      placeholder="As it appears on the ID document"
                    />
                  </div>

                  <div>
                    <label htmlFor={`traveler-${index}-phone`} className="label">
                      Phone <span className="font-normal text-muted">(optional)</span>
                    </label>
                    <input
                      id={`traveler-${index}-phone`}
                      type="tel"
                      maxLength={20}
                      autoComplete={index === 0 ? "tel" : "off"}
                      value={traveler.phone}
                      onChange={(event) => updateTraveler(index, "phone", event.target.value)}
                      className="field"
                      placeholder="+84 ..."
                    />
                  </div>

                  <div>
                    <label htmlFor={`traveler-${index}-email`} className="label">
                      Email <span className="font-normal text-muted">(optional)</span>
                    </label>
                    <input
                      id={`traveler-${index}-email`}
                      type="email"
                      maxLength={255}
                      autoComplete={index === 0 ? "email" : "off"}
                      value={traveler.email}
                      onChange={(event) => updateTraveler(index, "email", event.target.value)}
                      className="field"
                      placeholder="you@example.com"
                    />
                  </div>

                  <div>
                    <label htmlFor={`traveler-${index}-birth`} className="label">
                      Date of birth <span className="font-normal text-muted">(optional)</span>
                    </label>
                    <input
                      id={`traveler-${index}-birth`}
                      type="date"
                      value={traveler.dateOfBirth}
                      onChange={(event) =>
                        updateTraveler(index, "dateOfBirth", event.target.value)
                      }
                      className="field"
                    />
                  </div>

                  <div>
                    <label htmlFor={`traveler-${index}-gender`} className="label">
                      Gender <span className="font-normal text-muted">(optional)</span>
                    </label>
                    <select
                      id={`traveler-${index}-gender`}
                      value={traveler.gender}
                      onChange={(event) =>
                        updateTraveler(
                          index,
                          "gender",
                          event.target.value as TravelerDraft["gender"],
                        )
                      }
                      className="field"
                    >
                      <option value="">Prefer not to say</option>
                      <option value="MALE">Male</option>
                      <option value="FEMALE">Female</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </div>

                  <div className="sm:col-span-2">
                    <label htmlFor={`traveler-${index}-document`} className="label">
                      ID document <span className="font-normal text-muted">(optional)</span>
                    </label>
                    <input
                      id={`traveler-${index}-document`}
                      type="text"
                      maxLength={64}
                      value={traveler.idDocument}
                      onChange={(event) =>
                        updateTraveler(index, "idDocument", event.target.value)
                      }
                      className="field"
                      placeholder="Passport or national ID number"
                    />
                  </div>
                </div>
              </fieldset>
            ))}
          </div>
        </section>

        <section className="rounded-[4px] border border-line bg-white p-6 sm:p-8">
          <p className="eyebrow text-terracotta">Step 02</p>
          <h2 className="mt-2 font-display text-2xl tracking-tight">Promotion and notes</h2>
          <p className="mt-2 text-sm leading-6 text-muted">
            A promotion code is optional. The server checks it and calculates the discount and
            final total when the booking is created.
          </p>

          <div className="mt-6 grid gap-5 sm:grid-cols-2">
            <div>
              <label htmlFor="promotion-code" className="label">
                Promotion code <span className="font-normal text-muted">(optional)</span>
              </label>
              <input
                id="promotion-code"
                type="text"
                maxLength={64}
                value={promotionCode}
                onChange={(event) => setPromotionCode(event.target.value)}
                className="field uppercase"
                placeholder="SPRING10"
                autoComplete="off"
              />
            </div>
            <div className="sm:col-span-2">
              <label htmlFor="booking-note" className="label">
                Anything we should know? <span className="font-normal text-muted">(optional)</span>
              </label>
              <textarea
                id="booking-note"
                rows={3}
                value={note}
                onChange={(event) => setNote(event.target.value)}
                className="field resize-y"
                placeholder="Dietary needs, arrival time, a celebration…"
              />
            </div>
          </div>
        </section>

        <div className="flex flex-col gap-3 border-t border-line pt-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="text-xs leading-5 text-muted">
            {error ? (
              <p role="alert" className="text-terracotta">
                {error}
              </p>
            ) : (
              <p className="flex items-center gap-2">
                <IconShield className="h-4 w-4 shrink-0 text-forest" />
                Your booking is created as pending. No payment is taken here.
              </p>
            )}
          </div>
          <button type="submit" disabled={pending} className="btn-primary">
            {pending ? "Reserving seats…" : "Reserve seats"}
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
              <span className="text-sm font-medium">Estimated subtotal</span>
              <span className="text-xl font-semibold text-forest">{formatPrice(estimate)}</span>
            </div>
            <p className="mt-3 text-xs leading-5 text-muted">
              This is an estimate before any promotion. The server confirms the subtotal,
              discount and total when you reserve; you will see the final amounts on your
              booking.
            </p>
          </div>
        </div>
      </aside>
    </div>
  );
}
