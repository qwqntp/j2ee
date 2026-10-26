import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import { Suspense } from "react";
import { DemoNotice } from "@/components/demo-notice";
import { EmptyState } from "@/components/empty-state";
import {
  IconArrowRight,
  IconCalendar,
  IconClock,
  IconMapPin,
  IconPhone,
  IconStar,
  IconUsers,
} from "@/components/icons";
import { TourDetailSkeleton } from "@/components/skeleton";
import { TourImage } from "@/components/tour-image";
import { ApiError } from "@/lib/api";
import { demoTour } from "@/lib/demo-tours";
import {
  formatDate,
  formatDateRange,
  formatDuration,
  formatPrice,
  isSoldOut,
  seatsLabel,
} from "@/lib/format";
import { tourFallbackImage } from "@/lib/tour-images";
import { loadTour } from "@/lib/tours";
import type { TourDetail } from "@/lib/types";

export const metadata: Metadata = {
  title: "Journey details",
  description: "A small-group journey through Vietnam, with dates, prices and local guides.",
};

type Props = { params: Promise<{ id: string }> };

export default function TourDetailPage({ params }: Props) {
  return (
    <Suspense fallback={<TourDetailSkeleton />}>
      <TourDetailContent params={params} />
    </Suspense>
  );
}

async function TourDetailContent({ params }: Props) {
  const { id: rawId } = await params;
  const id = Number(rawId);

  if (!Number.isInteger(id) || id <= 0) {
    notFound();
  }

  await connection();

  let tour: TourDetail | null = null;
  let usedDemo = false;

  try {
    tour = await loadTour(id);
  } catch (error) {
    // Kept outside the cached read so sample data is never cached as live data.
    tour = demoTour(id);
    usedDemo = tour !== null;
    if (!tour && error instanceof ApiError && error.status === 404) {
      notFound();
    }
  }

  if (!tour) {
    return (
      <section className="shell py-20">
        <EmptyState
          title="We could not load this journey."
          description="The booking service may be taking a short break. Try again, or browse the rest of the collection."
          action={{ href: "/tours", label: "Back to journeys" }}
        />
      </section>
    );
  }

  const nextDeparture = tour.departures[0] ?? null;
  const priceFrom = tour.departures.length
    ? Math.min(...tour.departures.map((departure) => departure.price))
    : null;
  const duration = nextDeparture
    ? formatDuration(nextDeparture.startDate, nextDeparture.endDate)
    : null;
  const images = tour.imageUrls.slice(0, 3);
  const fallback = tourFallbackImage(tour.name, ...tour.destinationNames);
  const leadDestination = tour.destinationNames[0] ?? "Vietnam";

  return (
    <>
      <section className="border-b border-line">
        <div className="shell py-8 sm:py-10">
          <nav aria-label="Breadcrumb" className="text-xs text-muted">
            <ol className="flex flex-wrap items-center gap-2">
              <li>
                <Link href="/" className="transition-colors hover:text-forest">
                  Home
                </Link>
              </li>
              <li aria-hidden="true">/</li>
              <li>
                <Link href="/tours" className="transition-colors hover:text-forest">
                  Journeys
                </Link>
              </li>
              <li aria-hidden="true">/</li>
              <li aria-current="page" className="text-charcoal">
                {tour.name}
              </li>
            </ol>
          </nav>

          {usedDemo ? (
            <div className="mt-6">
              <DemoNotice />
            </div>
          ) : null}

          <div className="mt-7 grid gap-8 lg:grid-cols-[1.4fr_0.6fr] lg:items-end">
            <div>
              <p className="eyebrow text-terracotta">
                {tour.destinationNames.length ? tour.destinationNames.join(" · ") : "Vietnam"}
              </p>
              <h1 className="mt-3 max-w-3xl font-display text-4xl leading-[1.02] tracking-tight text-charcoal sm:text-5xl lg:text-6xl">
                {tour.name}
              </h1>
              <p className="mt-5 max-w-2xl text-lg leading-8 text-muted">
                {tour.description ?? "A small-group journey through Vietnam, planned with care."}
              </p>
              <div className="mt-6 flex flex-wrap gap-2">
                {duration ? (
                  <span className="tag">
                    <IconClock className="h-4 w-4 text-forest" />
                    {duration}
                  </span>
                ) : null}
                {tour.accommodationName ? (
                  <span className="tag">
                    <IconStar className="h-4 w-4 text-terracotta" />
                    {tour.accommodationName}
                  </span>
                ) : null}
                {tour.guideName ? (
                  <span className="tag">
                    <IconUsers className="h-4 w-4 text-forest" />
                    Guide {tour.guideName}
                  </span>
                ) : null}
                {tour.vehicleTypes.map((vehicle) => (
                  <span key={vehicle} className="tag">
                    {vehicle}
                  </span>
                ))}
              </div>
            </div>

            <div className="rounded-[4px] border border-line bg-white p-5">
              <p className="eyebrow text-muted">From</p>
              <p className="mt-1 text-3xl font-semibold text-forest">{formatPrice(priceFrom)}</p>
              <p className="mt-1 text-sm text-muted">per traveler</p>
              <Link href="#departures" className="btn-primary mt-4 w-full">
                Choose a date
                <IconArrowRight className="h-4 w-4" />
              </Link>
              {nextDeparture ? (
                <p className="mt-3 flex items-center justify-center gap-1.5 text-xs text-muted">
                  <IconCalendar className="h-3.5 w-3.5" />
                  Next: {formatDate(nextDeparture.startDate)}
                </p>
              ) : null}
            </div>
          </div>
        </div>
      </section>

      <section className="shell py-8 sm:py-10">
        {images.length >= 3 ? (
          <div className="grid gap-3 sm:grid-cols-3 sm:grid-rows-2">
            <div className="relative aspect-[16/10] overflow-hidden rounded-[3px] bg-sage/20 sm:col-span-2 sm:row-span-2 sm:aspect-auto">
              <TourImage
                src={images[0] ?? null}
                alt={tour.name}
                fallbackSrc={fallback}
                eager
                className="absolute inset-0 h-full w-full"
              />
            </div>
            {images.slice(1, 3).map((src, index) => (
              <div
                key={`${src}-${index}`}
                className="relative hidden aspect-[16/10] overflow-hidden rounded-[3px] bg-sage/20 sm:block"
              >
                <TourImage
                  src={src}
                  alt={`${tour.name}, view ${index + 2}`}
                  fallbackSrc={fallback}
                  className="absolute inset-0 h-full w-full"
                />
              </div>
            ))}
          </div>
        ) : images.length === 2 ? (
          <div className="grid gap-3 sm:grid-cols-2">
            {images.map((src, index) => (
              <div
                key={`${src}-${index}`}
                className="relative aspect-[16/10] overflow-hidden rounded-[3px] bg-sage/20"
              >
                <TourImage
                  src={src}
                  alt={index === 0 ? tour.name : `${tour.name}, view ${index + 1}`}
                  fallbackSrc={fallback}
                  eager={index === 0}
                  className="absolute inset-0 h-full w-full"
                />
              </div>
            ))}
          </div>
        ) : (
          <div className="relative aspect-[16/9] overflow-hidden rounded-[3px] bg-sage/20">
            <TourImage
              src={images[0] ?? null}
              alt={tour.name}
              fallbackSrc={fallback}
              eager
              className="absolute inset-0 h-full w-full"
            />
          </div>
        )}
      </section>

      <section className="shell grid gap-12 pb-16 lg:grid-cols-[1.6fr_1fr] lg:items-start lg:gap-16">
        <div className="space-y-12">
          <div>
            <p className="eyebrow text-forest">The journey</p>
            <h2 className="mt-2 font-display text-3xl tracking-tight sm:text-4xl">
              A slower way through {leadDestination}.
            </h2>
            <p className="mt-4 leading-8 text-muted">
              {tour.description ?? "A small-group journey through Vietnam, planned with care."}
            </p>
            {tour.destinationNames.length ? (
              <div className="mt-6 flex flex-wrap gap-2">
                {tour.destinationNames.map((destination) => (
                  <span key={destination} className="tag">
                    <IconMapPin className="h-4 w-4 text-forest" />
                    {destination}
                  </span>
                ))}
              </div>
            ) : null}
          </div>

          {tour.itinerary.length ? (
            <div>
              <p className="eyebrow text-forest">Day by day</p>
              <h2 className="mt-2 font-display text-3xl tracking-tight sm:text-4xl">
                What the days look like.
              </h2>
              <ol className="mt-6 border-t border-line">
                {tour.itinerary.map((item, index) => (
                  <li
                    key={index}
                    className="grid gap-3 border-b border-line py-5 sm:grid-cols-[3rem_1fr]"
                  >
                    <span className="text-xs font-semibold tracking-[0.16em] text-terracotta">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    <p className="text-sm leading-7 text-muted">{item}</p>
                  </li>
                ))}
              </ol>
            </div>
          ) : null}

          <div className="grid gap-5 sm:grid-cols-2">
            <div className="rounded-[4px] border border-line bg-white p-5">
              <p className="eyebrow text-forest">Stay</p>
              <p className="mt-2 font-display text-xl">
                {tour.accommodationName ?? "Handpicked stays"}
              </p>
              {tour.accommodationType ? (
                <p className="mt-1 text-sm text-muted">{tour.accommodationType}</p>
              ) : null}
              {tour.accommodationRating ? (
                <p className="mt-2 flex items-center gap-1.5 text-sm font-medium">
                  <IconStar className="h-4 w-4 text-terracotta" />
                  {tour.accommodationRating} / 5
                </p>
              ) : null}
            </div>

            <div className="rounded-[4px] border border-line bg-white p-5">
              <p className="eyebrow text-forest">Your guide</p>
              <p className="mt-2 font-display text-xl">{tour.guideName ?? "A local guide"}</p>
              <p className="mt-1 text-sm leading-6 text-muted">
                A familiar face for the length of the journey, from first pickup to last drop-off.
              </p>
              {tour.guidePhone ? (
                <a
                  href={`tel:${tour.guidePhone.replace(/\s/g, "")}`}
                  className="mt-3 inline-flex items-center gap-2 text-sm font-medium text-forest hover:underline"
                >
                  <IconPhone className="h-4 w-4" />
                  {tour.guidePhone}
                </a>
              ) : null}
            </div>
          </div>
        </div>

        <aside className="lg:sticky lg:top-28">
          <div className="rounded-[4px] border border-line bg-white p-6 shadow-panel">
            <p className="eyebrow text-forest">At a glance</p>
            <dl className="mt-5 space-y-3 text-sm">
              <div className="flex justify-between gap-4 border-b border-line pb-3">
                <dt className="text-muted">Duration</dt>
                <dd className="font-medium">{duration ?? "Flexible"}</dd>
              </div>
              <div className="flex justify-between gap-4 border-b border-line pb-3">
                <dt className="text-muted">Group size</dt>
                <dd className="font-medium">
                  {nextDeparture ? `Up to ${nextDeparture.capacity}` : "Small group"}
                </dd>
              </div>
              <div className="flex justify-between gap-4 border-b border-line pb-3">
                <dt className="text-muted">Meeting point</dt>
                <dd className="max-w-[11rem] text-right font-medium">
                  {nextDeparture?.meetingPoint ?? "Confirmed on booking"}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted">Price from</dt>
                <dd className="font-medium">{formatPrice(priceFrom)}</dd>
              </div>
            </dl>
            <Link href="#departures" className="btn-primary mt-6 w-full">
              See departure dates
            </Link>
            <p className="mt-3 text-center text-xs leading-5 text-muted">
              No online payment is taken in this demo.
            </p>
          </div>
        </aside>
      </section>

      <section id="departures" className="border-t border-line bg-white/50">
        <div className="shell py-16">
          <p className="eyebrow text-terracotta">Dates</p>
          <h2 className="mt-3 font-display text-4xl tracking-tight">Choose your departure.</h2>
          <p className="mt-3 max-w-2xl text-muted">
            Prices are per traveler. Sold-out dates are shown for reference.
          </p>

          {tour.departures.length === 0 ? (
            <div className="mt-8">
              <EmptyState
                title="No dates open just yet."
                description="This journey is between scheduled departures. Ask us about private dates or the next window."
                action={{ href: "mailto:hello@hanhtrinh.example", label: "Ask about dates" }}
              />
            </div>
          ) : (
            <ul className="mt-8 border-t border-line">
              {tour.departures.map((departure) => {
                const soldOut = isSoldOut(departure.status, departure.seatsAvailable);
                return (
                  <li
                    key={departure.id}
                    className="grid gap-4 border-b border-line py-5 sm:grid-cols-[1.2fr_0.7fr_0.7fr_auto] sm:items-center"
                  >
                    <div>
                      <p className="font-medium">
                        {formatDateRange(departure.startDate, departure.endDate)}
                      </p>
                      <p className="mt-1 text-sm text-muted">
                        {formatDuration(departure.startDate, departure.endDate)}
                        {departure.meetingPoint ? ` · ${departure.meetingPoint}` : ""}
                      </p>
                    </div>
                    <div>
                      <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-muted">
                        Per traveler
                      </p>
                      <p className="mt-1 text-lg font-semibold text-forest">
                        {formatPrice(departure.price)}
                      </p>
                    </div>
                    <div>
                      <span
                        className={`inline-flex items-center gap-1.5 text-xs font-semibold ${
                          soldOut ? "text-muted" : "text-forest"
                        }`}
                      >
                        <span
                          className={`h-1.5 w-1.5 rounded-full ${
                            soldOut ? "bg-muted" : "bg-forest"
                          }`}
                        />
                        {soldOut ? "Sold out" : seatsLabel(departure.seatsAvailable)}
                      </span>
                    </div>
                    <div className="sm:justify-self-end">
                      {soldOut ? (
                        <span className="inline-flex min-h-11 items-center rounded-[3px] border border-line px-5 text-sm text-muted/60">
                          Unavailable
                        </span>
                      ) : (
                        <Link
                          href={`/payment?tourId=${tour.id}&departureId=${departure.id}`}
                          className="btn-primary"
                          aria-label={`Reserve ${tour.name} on ${formatDateRange(departure.startDate, departure.endDate)}`}
                        >
                          Reserve
                          <IconArrowRight className="h-4 w-4" />
                        </Link>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </section>
    </>
  );
}
