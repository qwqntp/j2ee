import { cacheLife, cacheTag } from "next/cache";
import { getFeaturedTours, getTour, getTourDestinations, getTours } from "./api";
import {
  EMPTY_TOUR_FILTERS,
  hasActiveTourFilters,
  type TourFilters,
} from "./tour-filters";
import type { PageData, TourDestinationOption, TourDetail, TourSummary } from "./types";

// Filtered combinations are client-controlled and unbounded, so they deliberately
// bypass the cache. The plain catalogue (page 0, no filters) stays cached.
export async function loadTours(
  filters: TourFilters,
  page: number,
): Promise<PageData<TourSummary>> {
  if (hasActiveTourFilters(filters)) {
    return getTours(filters, page);
  }
  return loadCataloguePage(page);
}

async function loadCataloguePage(page: number): Promise<PageData<TourSummary>> {
  "use cache";
  cacheLife("minutes");
  cacheTag("tours");
  return getTours(EMPTY_TOUR_FILTERS, page);
}

export async function loadTourDestinations(): Promise<TourDestinationOption[]> {
  "use cache";
  cacheLife("minutes");
  cacheTag("tours");
  return getTourDestinations();
}

export async function loadFeaturedTours(): Promise<TourSummary[]> {
  "use cache";
  cacheLife("minutes");
  cacheTag("tours");
  const page = await getFeaturedTours();
  return page.content;
}

export async function loadTour(id: number): Promise<TourDetail> {
  "use cache";
  cacheLife("minutes");
  cacheTag("tours", `tour-${id}`);
  return getTour(id);
}
