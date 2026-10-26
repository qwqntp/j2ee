import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import { Suspense } from "react";
import { DemoNotice } from "@/components/demo-notice";
import { EmptyState } from "@/components/empty-state";
import { IconSearch } from "@/components/icons";
import { Pagination } from "@/components/pagination";
import { TourGridSkeleton } from "@/components/skeleton";
import { TourCard } from "@/components/tour-card";
import { demoToursPage } from "@/lib/demo-tours";
import { loadTours } from "@/lib/tours";
import type { PageData, TourSummary } from "@/lib/types";

export const metadata: Metadata = {
  title: "Journeys",
  description:
    "Small-group journeys across Vietnam — bays, lantern towns, mountain valleys and slow coasts.",
};

type SearchParams = Promise<Record<string, string | string[] | undefined>>;
type Props = { searchParams: SearchParams };

function first(value: string | string[] | undefined): string {
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function pageHref(page: number, keyword: string): string {
  const query = new URLSearchParams();
  if (page > 0) query.set("page", String(page));
  if (keyword) query.set("keyword", keyword);
  const search = query.toString();
  return search ? `/tours?${search}` : "/tours";
}

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
                A small collection of routes we know well. Search by place or mood, then pick a date
                that leaves a little room to wander.
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

async function TourResults({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const keyword = first(params.keyword).trim().slice(0, 100);
  const parsedPage = Number.parseInt(first(params.page), 10);
  const page = Number.isInteger(parsedPage) && parsedPage > 0 ? parsedPage : 0;

  await connection();

  let result: PageData<TourSummary>;
  let usedDemo = false;

  try {
    result = await loadTours(page, keyword);
  } catch {
    // Kept outside the cached read so sample data is never cached as live data.
    result = demoToursPage(page, keyword);
    usedDemo = true;
  }

  const outOfRange = result.content.length === 0 && result.totalElements > 0;
  const countLabel =
    result.totalElements === 1 ? "1 journey" : `${result.totalElements} journeys`;

  return (
    <>
      <div className="flex flex-col gap-5 border-b border-line pb-6 md:flex-row md:items-end md:justify-between">
        <form
          action="/tours"
          method="get"
          role="search"
          className="flex w-full max-w-xl flex-col gap-2 sm:flex-row"
        >
          <div className="relative flex-1">
            <label htmlFor="keyword" className="sr-only">
              Search journeys
            </label>
            <IconSearch className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
            <input
              id="keyword"
              name="keyword"
              type="search"
              maxLength={100}
              defaultValue={keyword}
              placeholder="Search a place or journey"
              className="min-h-12 w-full rounded-[3px] border border-line bg-white py-3 pl-11 pr-4 text-sm text-charcoal outline-none transition placeholder:text-muted/60 focus:border-forest focus:ring-1 focus:ring-forest/20"
            />
          </div>
          <button type="submit" className="btn-primary shrink-0">
            Search
          </button>
          {keyword ? (
            <Link href="/tours" className="btn-secondary shrink-0">
              Clear
            </Link>
          ) : null}
        </form>

        <p className="text-sm text-muted" aria-live="polite">
          {countLabel}
          {keyword ? ` for “${keyword}”` : ""}
        </p>
      </div>

      {usedDemo ? (
        <div className="mt-6">
          <DemoNotice />
        </div>
      ) : null}

      {result.content.length === 0 ? (
        <div className="mt-8">
          <EmptyState
            title={outOfRange ? "No journeys on this page." : "No journeys found."}
            description={
              outOfRange
                ? "That page is past the end of the collection. Head back to the first page to keep browsing."
                : keyword
                  ? `Nothing matched “${keyword}”. Try a broader search, or browse the full collection.`
                  : "There are no published journeys just yet. Check back soon, or write to us and we will help you plan."
            }
            action={
              outOfRange
                ? { href: pageHref(0, keyword), label: "First page" }
                : keyword
                  ? { href: "/tours", label: "Clear search" }
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
        hrefFor={(target) => pageHref(target, keyword)}
      />
    </>
  );
}
