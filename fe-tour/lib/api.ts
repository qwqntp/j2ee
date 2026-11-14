import type {
  Account,
  AdminTour,
  AuthSession,
  Booking,
  DepartureInput,
  Invoice,
  PageData,
  Payment,
  Profile,
  TourDestinationOption,
  TourDetail,
  TourDeparture,
  TourInput,
  TourSummary,
} from "./types";
import { buildTourApiQuery, type TourFilters } from "./tour-filters";

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

// Server renders fill a `use cache` entry and can wait on a cold database pool.
const TIMEOUT_MS = typeof window === "undefined" ? 30_000 : 10_000;

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

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
  } catch (error) {
    if (controller.signal.aborted) {
      throw new ApiError(504, "The booking service took too long to respond.");
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export function getTours(
  filters: TourFilters,
  page: number,
  size = 9,
): Promise<PageData<TourSummary>> {
  const query = buildTourApiQuery(filters, page, size);
  return apiRequest<PageData<TourSummary>>(`/api/tours?${query}`);
}

export function getTourDestinations(): Promise<TourDestinationOption[]> {
  return apiRequest<TourDestinationOption[]>("/api/tours/destinations");
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

export type TravelerInput = {
  fullName: string;
  phone?: string;
  email?: string;
  dateOfBirth?: string;
  gender?: string;
  idDocument?: string;
};

export function createBooking(
  token: string,
  input: {
    departureId: number;
    guestCount: number;
    note?: string;
    travelers?: TravelerInput[];
    promotionCode?: string;
  },
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

export function cancelBooking(token: string, id: number): Promise<Booking> {
  return apiRequest<Booking>(`/api/bookings/${id}/cancel`, { method: "POST", token });
}

export function payBooking(token: string, id: number): Promise<Payment> {
  return apiRequest<Payment>(`/api/bookings/${id}/payments/mock`, { method: "POST", token });
}

export function confirmBooking(token: string, id: number): Promise<Booking> {
  return apiRequest<Booking>(`/api/bookings/${id}/confirm`, { method: "POST", token });
}

export function completeBooking(token: string, id: number): Promise<Booking> {
  return apiRequest<Booking>(`/api/bookings/${id}/complete`, { method: "POST", token });
}

export function listMyInvoices(token: string, page = 0, size = 100): Promise<PageData<Invoice>> {
  return apiRequest<PageData<Invoice>>(`/api/invoices/me?page=${page}&size=${size}`, { token });
}
