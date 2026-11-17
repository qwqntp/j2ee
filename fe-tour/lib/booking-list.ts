export type BookingListFilters = {
  status?: "" | "PENDING" | "PAID" | "CONFIRMED" | "COMPLETED" | "CANCELLED";
  from?: string;
  to?: string;
  q?: string;
};

const BOOKING_STATUSES = new Set(["PENDING", "PAID", "CONFIRMED", "COMPLETED", "CANCELLED"]);

type BookingListQuery = { page: number; size: number } & BookingListFilters;

/** API query for the admin booking list; keeps the backend parameter names exactly. */
export function buildBookingListQuery({ page, size, ...filters }: BookingListQuery): string {
  if (filters.status && !BOOKING_STATUSES.has(filters.status)) {
    throw new Error("Invalid status");
  }
  if (filters.from && filters.to && filters.from > filters.to) {
    throw new Error("from must be on or before to");
  }

  const params = new URLSearchParams();
  params.set("page", String(page));
  params.set("size", String(size));
  if (filters.status) params.set("status", filters.status);
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  const query = filters.q?.trim();
  if (query) params.set("q", query);
  return params.toString();
}
