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
    readonly retryAfterSeconds: number | null = null,
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
      const retryAfter = response.headers.get("Retry-After");
      throw new ApiError(
        response.status,
        envelope?.message || `Request failed (${response.status})`,
        retryAfter !== null && /^\d+$/.test(retryAfter) ? Number(retryAfter) : null,
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

export type ProfileInput = {
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  dateOfBirth?: string;
};

export function getProfile(token: string): Promise<Profile> {
  return apiRequest<Profile>("/api/profile", { token });
}

export function updateProfile(token: string, input: ProfileInput): Promise<Profile> {
  return apiRequest<Profile>("/api/profile", { method: "PUT", token, body: input });
}

export function changePassword(
  token: string,
  input: { currentPassword: string; newPassword: string },
): Promise<AuthSession> {
  return apiRequest<AuthSession>("/api/auth/change-password", {
    method: "POST",
    token,
    body: input,
  });
}

export function verifyEmail(token: string): Promise<void> {
  return apiRequest<void>("/api/auth/verify-email", {
    method: "POST",
    body: { token },
    allowEmpty: true,
  });
}

export async function forgotPassword(email: string): Promise<void> {
  await apiRequest<void>("/api/auth/forgot-password", {
    method: "POST",
    body: { email },
    allowEmpty: true,
  });
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await apiRequest<void>("/api/auth/reset-password", {
    method: "POST",
    body: { token, newPassword },
    allowEmpty: true,
  });
}

export function listAdminTours(
  token: string,
  page = 0,
  size = 50,
): Promise<PageData<AdminTour>> {
  return apiRequest<PageData<AdminTour>>(`/api/admin/tours?page=${page}&size=${size}`, {
    token,
  });
}

export function createAdminTour(token: string, input: TourInput): Promise<AdminTour> {
  return apiRequest<AdminTour>("/api/admin/tours", { method: "POST", token, body: input });
}

export function updateAdminTour(
  token: string,
  id: number,
  input: TourInput,
): Promise<AdminTour> {
  return apiRequest<AdminTour>(`/api/admin/tours/${id}`, {
    method: "PUT",
    token,
    body: input,
  });
}

export function listDepartures(
  token: string,
  page = 0,
  size = 100,
): Promise<PageData<TourDeparture>> {
  return apiRequest<PageData<TourDeparture>>(
    `/api/tour-departures?page=${page}&size=${size}`,
    { token },
  );
}

export function createDeparture(
  token: string,
  input: DepartureInput,
): Promise<TourDeparture> {
  return apiRequest<TourDeparture>("/api/tour-departures", {
    method: "POST",
    token,
    body: input,
  });
}

export function updateDeparture(
  token: string,
  id: number,
  input: DepartureInput,
): Promise<TourDeparture> {
  return apiRequest<TourDeparture>(`/api/tour-departures/${id}`, {
    method: "PUT",
    token,
    body: input,
  });
}

export function deleteDeparture(token: string, id: number): Promise<void> {
  return apiRequest<void>(`/api/tour-departures/${id}`, {
    method: "DELETE",
    token,
    allowEmpty: true,
  });
}

export function updateBookingStatus(
  token: string,
  id: number,
  status: string,
): Promise<Booking> {
  return apiRequest<Booking>(`/api/bookings/${id}`, {
    method: "PUT",
    token,
    body: { status },
  });
}

export function createPayment(
  token: string,
  input: { bookingId: number; paymentMethod: string; status?: string },
): Promise<Payment> {
  return apiRequest<Payment>("/api/payments", { method: "POST", token, body: input });
}
