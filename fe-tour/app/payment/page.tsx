import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { PaymentForm } from "@/components/payment-form";
import { CheckoutSkeleton } from "@/components/skeleton";
import { ApiError } from "@/lib/api";
import { isSoldOut } from "@/lib/format";
import { tourFallbackImage } from "@/lib/tour-images";
import { loadTour } from "@/lib/tours";
import type { TourDetail } from "@/lib/types";

export const metadata: Metadata = {
  title: "Checkout",
  description: "Reserve seats on a departure. Payment is arranged separately.",
};

type SearchParams = Promise<Record<string, string | string[] | undefined>>;
type Props = { searchParams: SearchParams };

function first(value: string | string[] | undefined): string {
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function positiveInt(value: string): number | null {
  const parsed = Number.parseInt(value, 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export default function PaymentPage({ searchParams }: Props) {
  return (
    <>
      <section className="border-b border-line bg-white/50">
        <div className="shell py-12 sm:py-16">
          <div className="flex flex-wrap items-end justify-between gap-5">
            <div>
              <p className="eyebrow text-terracotta">Checkout</p>
              <h1 className="mt-3 max-w-3xl font-display text-4xl leading-[1.04] tracking-tight text-charcoal sm:text-5xl">
                A quiet moment before the journey.
              </h1>
              <p className="mt-4 max-w-xl text-sm leading-6 text-muted">
                Reserve seats on this departure. Payment is arranged separately, so no card details
                are collected here.
              </p>
            </div>
            <Link href="/tours" className="btn-secondary">
              Back to journeys
            </Link>
          </div>
        </div>
      </section>

      <section className="shell py-12 sm:py-16">
        <Suspense fallback={<CheckoutSkeleton />}>
          <PaymentContent searchParams={searchParams} />
        </Suspense>
      </section>
    </>
  );
}

async function PaymentContent({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const tourId = positiveInt(first(params.tourId));
  const departureId = positiveInt(first(params.departureId));

  await connection();

  if (!tourId) {
    return (
      <EmptyState
        title="Start with a journey."
        description="Pick a departure from any journey to reserve seats. Payment is arranged separately."
        action={{ href: "/tours", label: "Browse journeys" }}
      />
    );
  }

  let tour: TourDetail;

  try {
    tour = await loadTour(tourId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <EmptyState
          title="This journey is no longer available."
          description="It may have been unpublished since the link was shared. The rest of the collection is still here."
          action={{ href: "/tours", label: "Browse journeys" }}
        />
      );
    }
    throw error;
  }

  const departure = departureId
    ? tour.departures.find((item) => item.id === departureId)
    : tour.departures.length === 1
      ? tour.departures[0]
      : null;

  if (!departure) {
    return (
      <EmptyState
        title="Choose a departure first."
        description="Choose a specific date so the booking has the right price and seats."
        action={{ href: `/tours/${tour.id}#departures`, label: "See dates" }}
      />
    );
  }

  if (isSoldOut(departure.status, departure.seatsAvailable)) {
    return (
      <EmptyState
        title="That date is sold out."
        description="There are no seats left on this departure. Pick another date on the journey page."
        action={{ href: `/tours/${tour.id}#departures`, label: "Choose another date" }}
      />
    );
  }

  return (
    <>
      <PaymentForm
        tourId={tour.id}
        tourName={tour.name}
        departure={departure}
        imageUrl={tour.imageUrls[0] ?? null}
        fallbackImage={tourFallbackImage(tour.name, ...tour.destinationNames)}
      />
    </>
  );
}
