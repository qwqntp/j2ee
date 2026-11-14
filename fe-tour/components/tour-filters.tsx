"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import {
  TOUR_SORT_OPTIONS,
  buildTourHref,
  hasActiveTourFilters,
  tourFiltersFromFormData,
  validateTourFilters,
  type TourFilters,
} from "@/lib/tour-filters";
import type { TourDestinationOption } from "@/lib/types";
import { IconSearch } from "./icons";

type Props = {
  filters: TourFilters;
  destinations: TourDestinationOption[];
};

export function TourFiltersForm({ filters, destinations }: Props) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const active = hasActiveTourFilters(filters);

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = tourFiltersFromFormData(new FormData(event.currentTarget));
    const message = validateTourFilters(next);
    if (message) {
      setError(message);
      return;
    }
    setError(null);
    startTransition(() => router.push(buildTourHref(next)));
  }

  return (
    <form
      onSubmit={onSubmit}
      method="get"
      action="/tours"
      aria-label="Filter journeys"
      className="rounded-[4px] border border-line bg-white p-5 sm:p-6"
    >
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="eyebrow text-terracotta">Refine the collection</p>
          <h2 className="mt-2 font-display text-2xl tracking-tight">Find your kind of journey.</h2>
        </div>
        {active ? (
          <Link href="/tours" className="btn-secondary min-h-10 px-4 py-2 text-xs">
            Clear all
          </Link>
        ) : null}
      </div>

      <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="sm:col-span-2">
          <label htmlFor="filter-keyword" className="label">
            Search
          </label>
          <div className="relative">
            <IconSearch className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
            <input
              id="filter-keyword"
              name="keyword"
              type="search"
              maxLength={100}
              defaultValue={filters.keyword}
              placeholder="A place or a mood"
              className="field pl-11"
            />
          </div>
        </div>

        <div>
          <label htmlFor="filter-destination" className="label">
            Destination
          </label>
          <select
            id="filter-destination"
            name="destinationId"
            defaultValue={filters.destinationId}
            className="field"
          >
            <option value="">Anywhere</option>
            {destinations.map((destination) => (
              <option key={destination.id} value={destination.id}>
                {destination.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="filter-sort" className="label">
            Sort by
          </label>
          <select
            id="filter-sort"
            name="sort"
            defaultValue={filters.sort}
            className="field"
          >
            <option value="">Default order</option>
            {TOUR_SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="filter-min-price" className="label">
            Min price (₫)
          </label>
          <input
            id="filter-min-price"
            name="minPrice"
            type="number"
            min={0}
            step={1000}
            inputMode="numeric"
            defaultValue={filters.minPrice}
            placeholder="0"
            className="field"
          />
        </div>

        <div>
          <label htmlFor="filter-max-price" className="label">
            Max price (₫)
          </label>
          <input
            id="filter-max-price"
            name="maxPrice"
            type="number"
            min={0}
            step={1000}
            inputMode="numeric"
            defaultValue={filters.maxPrice}
            placeholder="No limit"
            className="field"
          />
        </div>

        <div>
          <label htmlFor="filter-min-days" className="label">
            Min days
          </label>
          <input
            id="filter-min-days"
            name="minDays"
            type="number"
            min={1}
            step={1}
            inputMode="numeric"
            defaultValue={filters.minDays}
            placeholder="1"
            className="field"
          />
        </div>

        <div>
          <label htmlFor="filter-max-days" className="label">
            Max days
          </label>
          <input
            id="filter-max-days"
            name="maxDays"
            type="number"
            min={1}
            step={1}
            inputMode="numeric"
            defaultValue={filters.maxDays}
            placeholder="Any length"
            className="field"
          />
        </div>

        <div>
          <label htmlFor="filter-start-from" className="label">
            Departing from
          </label>
          <input
            id="filter-start-from"
            name="startFrom"
            type="date"
            defaultValue={filters.startFrom}
            className="field"
          />
        </div>

        <div>
          <label htmlFor="filter-start-to" className="label">
            Departing until
          </label>
          <input
            id="filter-start-to"
            name="startTo"
            type="date"
            defaultValue={filters.startTo}
            className="field"
          />
        </div>
      </div>

      {error ? (
        <p
          role="alert"
          className="mt-4 rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-3 text-sm text-terracotta"
        >
          {error}
        </p>
      ) : null}

      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
        <p className="text-xs leading-5 text-muted">
          All supplied price and date filters must match the same upcoming departure.
        </p>
        <button type="submit" disabled={pending} className="btn-primary">
          {pending ? "Filtering…" : "Apply filters"}
        </button>
      </div>
    </form>
  );
}
