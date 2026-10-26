import type { PageData, TourDetail, TourSummary } from "./types";

type Envelope<T> = {
  success: boolean;
  message: string;
  data: T | null;
  timestamp: string;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const BASE_URL = (
  process.env.API_URL ||
  process.env.NEXT_PUBLIC_API_URL ||
  "http://localhost:8080"
).replace(/\/$/, "");

async function getJson<T>(path: string): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5_000);

  try {
    const response = await fetch(`${BASE_URL}${path}`, {
      headers: { Accept: "application/json" },
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new ApiError(response.status, `Request failed (${response.status})`);
    }

    const envelope = (await response.json()) as Envelope<T>;
    if (!envelope.success || envelope.data === null) {
      throw new ApiError(response.status, envelope.message || "Unexpected API response");
    }
    return envelope.data;
  } finally {
    clearTimeout(timeout);
  }
}

export function getTours(page: number, keyword: string): Promise<PageData<TourSummary>> {
  const query = new URLSearchParams({ page: String(page), size: "9" });
  if (keyword) query.set("keyword", keyword);
  return getJson<PageData<TourSummary>>(`/api/tours?${query.toString()}`);
}

export function getFeaturedTours(): Promise<PageData<TourSummary>> {
  return getJson<PageData<TourSummary>>("/api/tours?page=0&size=6");
}

export function getTour(id: number): Promise<TourDetail> {
  return getJson<TourDetail>(`/api/tours/${id}`);
}
