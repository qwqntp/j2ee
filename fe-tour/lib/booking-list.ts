import type { AdminListSort } from "./types";

export type BookingListFilters = {
  status?: "" | "PENDING" | "PAID" | "CONFIRMED" | "COMPLETED" | "CANCELLED";
  from?: string;
  to?: string;
  q?: string;
  sort?: AdminListSort;
};

const BOOKING_STATUSES = new Set(["PENDING", "PAID", "CONFIRMED", "COMPLETED", "CANCELLED"]);
const ADMIN_SORTS = new Set<AdminListSort>([
  "createdAt,asc",
  "createdAt,desc",
  "totalAmount,asc",
  "totalAmount,desc",
]);

type BookingListQuery = { page: number; size: number } & BookingListFilters;

/** API query for the admin booking list; keeps the backend parameter names exactly. */
export function buildBookingListQuery({ page, size, ...filters }: BookingListQuery): string {
  if (filters.status && !BOOKING_STATUSES.has(filters.status)) {
    throw new Error("Invalid status");
  }
  if (filters.sort && !ADMIN_SORTS.has(filters.sort)) {
    throw new Error("Invalid sort");
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
  if (filters.sort) params.set("sort", filters.sort);
  return params.toString();
}

/**
 * Guards async list/detail loads: only the most recently started request may
 * commit state. Aborting a request also invalidates its token.
 */
export function createRequestTracker() {
  let latest = 0;
  return {
    begin(): number {
      latest += 1;
      return latest;
    },
    isCurrent(token: number): boolean {
      return token === latest;
    },
    cancel(): void {
      latest += 1;
    },
  };
}

/**
 * Per-row version tracker for expanded booking details. Closing a row or
 * starting a newer read invalidates the older completion without touching
 * other rows.
 */
export function createKeyedRequestTracker() {
  const versions = new Map<number, number>();
  return {
    begin(key: number): number {
      const next = (versions.get(key) ?? 0) + 1;
      versions.set(key, next);
      return next;
    },
    isCurrent(key: number, token: number): boolean {
      return (versions.get(key) ?? 0) === token;
    },
    cancel(key: number): void {
      versions.set(key, (versions.get(key) ?? 0) + 1);
    },
  };
}

/**
 * Commits paging changes atomically: a filter, sort or size change always
 * resets to page 0 in the same state object, so the list effect can never see
 * a new filter with the old page.
 */
export function resetAdminPage<Q extends { page: number }>(
  current: Q,
  patch?: Omit<Partial<Q>, "page">,
): Q {
  return { ...current, ...(patch ?? {}), page: 0 } as Q;
}

/**
 * Commits the newest value once input pauses. `cancel` also discards a pending
 * value, which an explicit Apply/Clear action uses to avoid a second fetch.
 */
export function createDebouncedCommit<T>(commit: (value: T) => void, delay = 400) {
  let timer: ReturnType<typeof setTimeout> | null = null;
  return {
    schedule(value: T): void {
      if (timer !== null) clearTimeout(timer);
      timer = setTimeout(() => {
        timer = null;
        commit(value);
      }, delay);
    },
    cancel(): void {
      if (timer !== null) clearTimeout(timer);
      timer = null;
    },
  };
}
