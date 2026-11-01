import type {
  Account,
  AuthSession,
  Booking,
  PageData,
  TourDetail,
  TourSummary,
} from "./types";

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

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  token?: string;
  allowEmpty?: boolean;
};

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10_000);

  try {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: options.method ?? "GET",
      headers: {
        Accept: "application/json",
        ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: "include",
      signal: controller.signal,
    });

    const envelope = (await response.json().catch(() => null)) as Envelope<T> | null;

    if (!response.ok) {
      throw new ApiError(
        response.status,
        envelope?.message || `Request failed (${response.status})`,
      );
    }

    if (!envelope || !envelope.success) {
      throw new ApiError(response.status, envelope?.message || "Unexpected API response");
    }

    if (envelope.data === null && !options.allowEmpty) {
      throw new ApiError(response.status, envelope.message || "Unexpected API response");
    }

    return envelope.data as T;
  } finally {
    clearTimeout(timeout);
  }
}

export function getTours(page: number, keyword: string): Promise<PageData<TourSummary>> {
  const query = new URLSearchParams({ page: String(page), size: "9" });
  if (keyword) query.set("keyword", keyword);
  return apiRequest<PageData<TourSummary>>(`/api/tours?${query.toString()}`);
}

export function getFeaturedTours(): Promise<PageData<TourSummary>> {
  return apiRequest<PageData<TourSummary>>("/api/tours?page=0&size=6");
}

export function getTour(id: number): Promise<TourDetail> {
  return apiRequest<TourDetail>(`/api/tours/${id}`);
}

export function register(input: {
  username: string;
  password: string;
  email: string;
}): Promise<AuthSession> {
  return apiRequest<AuthSession>("/api/auth/register", { method: "POST", body: input });
}

export function login(input: {
  username: string;
  password: string;
}): Promise<AuthSession> {
  return apiRequest<AuthSession>("/api/auth/login", { method: "POST", body: input });
}

export function refresh(): Promise<AuthSession> {
  return apiRequest<AuthSession>("/api/auth/refresh", { method: "POST" });
}

export function logout(token?: string): Promise<void> {
  return apiRequest<void>("/api/auth/logout", {
    method: "POST",
    token,
    allowEmpty: true,
  });
}

export function getMe(token: string): Promise<Account> {
  return apiRequest<Account>("/api/auth/me", { token });
}

export function createBooking(
  token: string,
  input: { departureId: number; guestCount: number; note?: string },
): Promise<Booking> {
  return apiRequest<Booking>("/api/bookings", {
    method: "POST",
    token,
    body: input,
  });
}

export function getBooking(token: string, id: number): Promise<Booking> {
  return apiRequest<Booking>(`/api/bookings/${id}`, { token });
}

export function listBookings(token: string, page = 0, size = 10): Promise<PageData<Booking>> {
  return apiRequest<PageData<Booking>>(`/api/bookings?page=${page}&size=${size}`, { token });
}
