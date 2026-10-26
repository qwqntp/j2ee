import { cacheLife, cacheTag } from "next/cache";
import { getFeaturedTours, getTour, getTours } from "./api";
import type { PageData, TourDetail, TourSummary } from "./types";

export async function loadTours(page: number, keyword: string): Promise<PageData<TourSummary>> {
  "use cache";
  cacheLife("minutes");
  cacheTag("tours");
  return getTours(page, keyword);
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
