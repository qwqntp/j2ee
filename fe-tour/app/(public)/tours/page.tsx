import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { Pagination } from "@/components/pagination";
import { TourGridSkeleton } from "@/components/skeleton";
import { TourCard } from "@/components/tour-card";
import { TourFiltersForm } from "@/components/tour-filters";
import { ApiError } from "@/lib/api";
import {
  buildTourHref,
  hasActiveTourFilters,
  parseTourPage,
  readTourFilters,
  type TourSearchParams,
} from "@/lib/tour-filters";
import { loadTourDestinations, loadTours } from "@/lib/tours";

export const metadata: Metadata = {
  title: "Journeys",
  description:
    "Small-group journeys across Vietnam — filter by destination, price, dates and duration, then pick the departure that fits.",
};

type Props = { searchParams: Promise<TourSearchParams> };

export default function ToursPage({ searchParams }: Props) {
  return (
    <>
      <section className="border-b border-line bg-white/50">
        <div className="shell py-14 sm:py-20">
          <div className="grid gap-8 lg:grid-cols-[1fr_0.8fr] lg:items-end">
            <div>
              <p className="eyebrow text-terracotta">The collection</p>
              <h1 className="mt-3 font-display text-5xl leading-[0.98] tracking-tight text-charcoal sm:text-6xl">
                Journeys
              </h1>
              <p className="mt-5 max-w-2xl text-lg leading-8 text-muted">
                A small collection of routes we know well. Search by place or mood, narrow the
                dates and budget, then pick a departure that leaves a little room to wander.
              </p>
            </div>
            <p className="max-w-sm text-sm leading-6 text-muted lg:justify-self-end">
              Every journey is run by our own guides in Vietnam. If you cannot find the right fit,
              write to us and we will help you plan.
            </p>
          </div>
        </div>
      </section>

      <section className="shell py-12 sm:py-16">
        <Suspense fallback={<TourGridSkeleton count={6} />}>
          <TourResults searchParams={searchParams} />
        </Suspense>
      </section>
    </>
  );
}

async function TourResults({ searchParams }: { searchParams: Promise<TourSearchParams> }) {
  const params = await searchParams;
  const filters = readTourFilters(params);
  const page = parseTourPage(params);
  const active = hasActiveTourFilters(filters);

  await connection();

  const destinations = await loadTourDestinations();

  let result: Awaited<ReturnType<typeof loadTours>> | null = null;
  let backendError: string | null = null;

  try {
    result = await loadTours(filters, page);
  } catch (error) {
    if (error instanceof ApiError && error.status === 400) {
      backendError = error.message;
    } else {
      throw error;
    }
  }

  if (!result) {
    return (
      <>
        <TourFiltersForm filters={filters} destinations={destinations} />
        <div
          role="alert"
          className="mt-8 rounded-[4px] border border-terracotta/40 bg-terracotta/10 p-5"
        >
          <p className="font-medium text-terracotta">These filters could not be applied.</p>
          <p className="mt-1 text-sm leading-6 text-muted">{backendError}</p>
          <Link href="/tours" className="btn-secondary mt-4">
            Clear filters
          </Link>
        </div>
      </>
    );
  }

  const outOfRange = result.content.length === 0 && result.totalElements > 0;
  const countLabel =
    result.totalElements === 1 ? "1 journey" : `${result.totalElements} journeys`;
  const keyword = filters.keyword;

  return (
    <>
      <TourFiltersForm filters={filters} destinations={destinations} />

      <div className="mt-10 flex flex-col gap-3 border-b border-line pb-5 sm:flex-row sm:items-end sm:justify-between">
        <p className="text-sm text-muted" aria-live="polite">
          {countLabel}
          {active ? " match your filters" : ""}
        </p>
        {active ? (
          <Link href="/tours" className="text-sm font-medium text-forest hover:underline">
            View the full collection
          </Link>
        ) : null}
      </div>

      {result.content.length === 0 ? (
        <div className="mt-8">
          <EmptyState
            title={outOfRange ? "No journeys on this page." : "No journeys found."}
            description={
              outOfRange
                ? "That page is past the end of the collection. Head back to the first page to keep browsing."
                : active
                  ? keyword
                    ? `Nothing matched these filters, including “${keyword}”. Try a broader search or remove a filter.`
                    : "Nothing matched these filters. Try widening the dates, price or duration."
                  : "There are no published journeys just yet. Check back soon, or write to us and we will help you plan."
            }
            action={
              outOfRange
                ? { href: buildTourHref(filters, 0), label: "First page" }
                : active
                  ? { href: "/tours", label: "Clear filters" }
                  : undefined
            }
          />
        </div>
      ) : (
        <div className="mt-10 grid gap-x-6 gap-y-12 sm:grid-cols-2 lg:grid-cols-3">
          {result.content.map((tour) => (
            <TourCard key={tour.id} tour={tour} />
          ))}
        </div>
      )}

      <Pagination
        page={result.page}
        totalPages={result.totalPages}
        hasPrevious={result.hasPrevious}
        hasNext={result.hasNext}
        hrefFor={(target) => buildTourHref(filters, target)}
      />
    </>
  );
}
