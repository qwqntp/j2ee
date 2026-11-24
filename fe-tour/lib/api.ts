import type {
  Account,
  AccountCreateInput,
  AccountRoleResponse,
  AccountUpdateInput,
  AccommodationInput,
  AccommodationRow,
  AdminAccount,
  AdminBookingListItem,
  AdminInvoiceListItem,
  AdminStats,
  AdminTour,
  AuthSession,
  Booking,
  DepartureInput,
  DestinationInput,
  DestinationRow,
  GuideInput,
  GuideRow,
  ImageInput,
  ImageRow,
  Invoice,
  PageData,
  Payment,
  Profile,
  Promotion,
  PromotionInput,
  TourDestinationOption,
  TourDetail,
  TourDeparture,
  TourInput,
  TourSummary,
  VehicleInput,
  VehicleRow,
  VnpayReconcile,
  VnpayStart,
  VnpayStatus,
} from "./types";
import { buildBookingListQuery, type BookingListFilters } from "./booking-list";
import { buildInvoiceListQuery, type InvoiceListFilters } from "./admin-invoices";
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
  signal?: AbortSignal;
  cache?: RequestCache;
};

// Server renders fill a `use cache` entry and can wait on a cold database pool.
const TIMEOUT_MS = 30_000;

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
      signal: options.signal ? AbortSignal.any([options.signal, controller.signal]) : controller.signal,
      ...(options.cache ? { cache: options.cache } : {}),
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

export function getAdminPassengers(token: string, id: number): Promise<import("./admin-passengers").PassengerManifest> {
  return apiRequest(`/api/admin/departures/${id}/passengers`, { token });
}

export function listAdminPayments(token: string, query: string): Promise<PageData<import("./admin-payments").AdminPayment>> {
  return apiRequest(`/api/admin/payments?${query}`, { token });
}

export function reconcileAdminPayment(token: string, id: number): Promise<import("./types").VnpayReconcile> {
  return apiRequest(`/api/admin/payments/${id}/reconcile`, { method: "POST", token });
}

export function listAdminCustomers(token: string, page: number, q: string): Promise<PageData<import("./admin-customers").AdminCustomer>> {
  return apiRequest(`/api/admin/customers?${new URLSearchParams({ page: String(page), size: "10", q })}`, { token });
}

export function getAdminCustomer(token: string, id: number): Promise<import("./admin-customers").AdminCustomer> {
  return apiRequest(`/api/customers/${id}`, { token });
}

export function listAdminCustomerBookings(token: string, id: number, page: number): Promise<PageData<import("./admin-customers").AdminCustomerBooking>> {
  return apiRequest(`/api/admin/customers/${id}/bookings?page=${page}&size=10`, { token });
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

export function googleSignIn(idToken: string): Promise<AuthSession> {
  return apiRequest<AuthSession>("/api/auth/google", {
    method: "POST",
    body: { idToken },
  });
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
  return apiRequest<Account>("/api/auth/me", { token, cache: "no-store" });
}

export function listAdminAccounts(
  token: string,
  page = 0,
  size = 100,
): Promise<PageData<AdminAccount>> {
  return apiRequest<PageData<AdminAccount>>(`/api/accounts?page=${page}&size=${size}`, { token });
}

export function listAccountRoles(token: string): Promise<AccountRoleResponse[]> {
  return apiRequest<PageData<AccountRoleResponse>>(
    "/api/account-roles?page=0&size=100",
    { token },
  ).then((page) => page.content);
}

export function updateAdminAccount(
  token: string,
  id: number,
  input: AccountUpdateInput,
): Promise<AdminAccount> {
  return apiRequest<AdminAccount>(`/api/accounts/${id}`, { method: "PUT", token, body: input });
}

export function createAdminAccount(token: string, input: AccountCreateInput): Promise<AdminAccount> {
  return apiRequest<AdminAccount>("/api/accounts", { method: "POST", token, body: input });
}

export function deleteAdminAccount(token: string, id: number): Promise<void> {
  return apiRequest<void>(`/api/accounts/${id}`, { method: "DELETE", token, allowEmpty: true });
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

export function getBooking(token: string, id: number, signal?: AbortSignal): Promise<Booking> {
  return apiRequest<Booking>(`/api/bookings/${id}`, { token, signal, cache: "no-store" });
}

export function listBookings(
  token: string,
  page = 0,
  size = 10,
  filters: BookingListFilters = {},
  signal?: AbortSignal,
): Promise<PageData<Booking>> {
  const query = buildBookingListQuery({ page, size, ...filters });
  return apiRequest<PageData<Booking>>(`/api/bookings?${query}`, {
    token,
    signal,
    cache: "no-store",
  });
}

export function listAdminBookings(
  token: string,
  page = 0,
  size = 10,
  filters: BookingListFilters = {},
  signal?: AbortSignal,
): Promise<PageData<AdminBookingListItem>> {
  const query = buildBookingListQuery({ page, size, ...filters });
  return apiRequest<PageData<AdminBookingListItem>>(`/api/admin/bookings?${query}`, {
    token,
    signal,
    cache: "no-store",
  });
}

export function listBookingPayments(token: string, bookingId: number): Promise<Payment[]> {
  return apiRequest<Payment[]>(`/api/bookings/${bookingId}/payments`, { token });
}

export function cancelBooking(token: string, id: number): Promise<Booking> {
  return apiRequest<Booking>(`/api/bookings/${id}/cancel`, { method: "POST", token });
}

export function payBooking(token: string, id: number): Promise<Payment> {
  return apiRequest<Payment>(`/api/bookings/${id}/payments/mock`, { method: "POST", token });
}

export function startVnpay(token: string, bookingId: number): Promise<VnpayStart> {
  return apiRequest<VnpayStart>(`/api/bookings/${bookingId}/payments/vnpay`, { method: "POST", token });
}

export function verifyVnpayReturn(token: string, query: string): Promise<VnpayStatus> {
  return apiRequest<VnpayStatus>("/api/payments/vnpay/return", { method: "POST", token, body: { query } });
}

export function getVnpayStatus(token: string, reference: string): Promise<VnpayStatus> {
  return apiRequest<VnpayStatus>(`/api/payments/vnpay/status/${encodeURIComponent(reference)}`, { token });
}

export function reconcileVnpayBooking(token: string, bookingId: number): Promise<VnpayReconcile> {
  return apiRequest<VnpayReconcile>(`/api/bookings/${bookingId}/payments/vnpay/reconcile`, {
    method: "POST",
    token,
  });
}

export function confirmBooking(token: string, id: number): Promise<Booking> {
  return apiRequest<Booking>(`/api/bookings/${id}/confirm`, { method: "POST", token });
}

export function completeBooking(token: string, id: number): Promise<Booking> {
  return apiRequest<Booking>(`/api/bookings/${id}/complete`, { method: "POST", token });
}

export function listMyInvoices(
  token: string,
  page = 0,
  size = 100,
  signal?: AbortSignal,
): Promise<PageData<Invoice>> {
  return apiRequest<PageData<Invoice>>(`/api/invoices/me?page=${page}&size=${size}`, {
    token,
    signal,
    cache: "no-store",
  });
}

export function listInvoices(
  token: string,
  page = 0,
  size = 10,
  filters: InvoiceListFilters = {},
  signal?: AbortSignal,
): Promise<PageData<AdminInvoiceListItem>> {
  const query = buildInvoiceListQuery({ page, size, ...filters });
  return apiRequest<PageData<AdminInvoiceListItem>>(`/api/invoices?${query}`, {
    token,
    signal,
    cache: "no-store",
  });
}

export function getInvoiceByBooking(
  token: string,
  bookingId: number,
  signal?: AbortSignal,
): Promise<Invoice> {
  return apiRequest<Invoice>(`/api/invoices/by-booking/${bookingId}`, {
    token,
    signal,
    cache: "no-store",
  });
}

export type ProfileInput = {
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  dateOfBirth?: string;
};

export function getProfile(token: string, signal?: AbortSignal): Promise<Profile> {
  return apiRequest<Profile>("/api/profile", { token, signal, cache: "no-store" });
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

export function getAdminStats(token: string): Promise<AdminStats> {
  return apiRequest<AdminStats>("/api/admin/stats", { token });
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

export function listPromotions(
  token: string,
  page = 0,
  size = 100,
): Promise<PageData<Promotion>> {
  return apiRequest<PageData<Promotion>>(`/api/promotions?page=${page}&size=${size}`, { token });
}

export function createPromotion(token: string, input: PromotionInput): Promise<Promotion> {
  return apiRequest<Promotion>("/api/promotions", { method: "POST", token, body: input });
}

export function updatePromotion(
  token: string,
  id: number,
  input: PromotionInput,
): Promise<Promotion> {
  return apiRequest<Promotion>(`/api/promotions/${id}`, {
    method: "PUT",
    token,
    body: input,
  });
}

export function deletePromotion(token: string, id: number): Promise<void> {
  return apiRequest<void>(`/api/promotions/${id}`, {
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

export function listDestination(token: string, page = 0, size = 100): Promise<PageData<DestinationRow>> {
  return apiRequest<PageData<DestinationRow>>(`/api/tourist-destinations?page=${page}&size=${size}`, { token });
}

export function createDestination(token: string, input: DestinationInput): Promise<DestinationRow> {
  return apiRequest<DestinationRow>("/api/tourist-destinations", { method: "POST", token, body: input });
}

export function updateDestination(
  token: string,
  id: number,
  input: DestinationInput,
): Promise<DestinationRow> {
  return apiRequest<DestinationRow>(`/api/tourist-destinations/${id}`, {
    method: "PUT", token, body: input,
  });
}

export function deleteDestination(token: string, id: number): Promise<void> {
  return apiRequest<void>(`/api/tourist-destinations/${id}`, {
    method: "DELETE", token, allowEmpty: true,
  });
}

export function listAccommodation(token: string, page = 0, size = 100): Promise<PageData<AccommodationRow>> {
  return apiRequest<PageData<AccommodationRow>>(`/api/accommodations?page=${page}&size=${size}`, { token });
}

export function createAccommodation(token: string, input: AccommodationInput): Promise<AccommodationRow> {
  return apiRequest<AccommodationRow>("/api/accommodations", { method: "POST", token, body: input });
}

export function updateAccommodation(
  token: string,
  id: number,
  input: AccommodationInput,
): Promise<AccommodationRow> {
  return apiRequest<AccommodationRow>(`/api/accommodations/${id}`, {
    method: "PUT", token, body: input,
  });
}

export function deleteAccommodation(token: string, id: number): Promise<void> {
  return apiRequest<void>(`/api/accommodations/${id}`, {
    method: "DELETE", token, allowEmpty: true,
  });
}

export function listVehicle(token: string, page = 0, size = 100): Promise<PageData<VehicleRow>> {
  return apiRequest<PageData<VehicleRow>>(`/api/travel-vehicles?page=${page}&size=${size}`, { token });
}

export function createVehicle(token: string, input: VehicleInput): Promise<VehicleRow> {
  return apiRequest<VehicleRow>("/api/travel-vehicles", { method: "POST", token, body: input });
}

export function updateVehicle(token: string, id: number, input: VehicleInput): Promise<VehicleRow> {
  return apiRequest<VehicleRow>(`/api/travel-vehicles/${id}`, {
    method: "PUT", token, body: input,
  });
}

export function deleteVehicle(token: string, id: number): Promise<void> {
  return apiRequest<void>(`/api/travel-vehicles/${id}`, {
    method: "DELETE", token, allowEmpty: true,
  });
}

export function listGuide(token: string, page = 0, size = 100): Promise<PageData<GuideRow>> {
  return apiRequest<PageData<GuideRow>>(`/api/tour-guides?page=${page}&size=${size}`, { token });
}

export function createGuide(token: string, input: GuideInput): Promise<GuideRow> {
  return apiRequest<GuideRow>("/api/tour-guides", { method: "POST", token, body: input });
}

export function updateGuide(token: string, id: number, input: GuideInput): Promise<GuideRow> {
  return apiRequest<GuideRow>(`/api/tour-guides/${id}`, {
    method: "PUT", token, body: input,
  });
}

export function deleteGuide(token: string, id: number): Promise<void> {
  return apiRequest<void>(`/api/tour-guides/${id}`, {
    method: "DELETE", token, allowEmpty: true,
  });
}

export function listImage(token: string, page = 0, size = 100): Promise<PageData<ImageRow>> {
  return apiRequest<PageData<ImageRow>>(`/api/tour-images?page=${page}&size=${size}`, { token });
}

export function createImage(token: string, input: ImageInput): Promise<ImageRow> {
  return apiRequest<ImageRow>("/api/tour-images", { method: "POST", token, body: input });
}

export function updateImage(token: string, id: number, input: ImageInput): Promise<ImageRow> {
  return apiRequest<ImageRow>(`/api/tour-images/${id}`, {
    method: "PUT", token, body: input,
  });
}

export function deleteImage(token: string, id: number): Promise<void> {
  return apiRequest<void>(`/api/tour-images/${id}`, {
    method: "DELETE", token, allowEmpty: true,
  });
}
