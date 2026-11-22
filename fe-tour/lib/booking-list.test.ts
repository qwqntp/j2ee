import { afterEach, describe, expect, it, vi } from "vitest";
import {
  buildBookingListQuery,
  createDebouncedCommit,
  createKeyedRequestTracker,
  createRequestTracker,
  resetAdminPage,
} from "./booking-list";

afterEach(() => {
  vi.useRealTimers();
});

describe("buildBookingListQuery", () => {
  it("omits empty filters and trims query", () => {
    expect(buildBookingListQuery({ page: 0, size: 20 })).toBe("page=0&size=20");
    expect(
      buildBookingListQuery({ page: 1, size: 20, status: "PAID", q: "  han-01  " }),
    ).toBe("page=1&size=20&status=PAID&q=han-01");
  });

  it("rejects reversed date ranges", () => {
    expect(() =>
      buildBookingListQuery({
        page: 0,
        size: 20,
        from: "2026-11-20",
        to: "2026-11-01",
      }),
    ).toThrow("from must be on or before to");
  });

  it("keeps every filter in a stable order", () => {
    expect(
      buildBookingListQuery({
        page: 2,
        size: 10,
        status: "COMPLETED",
        from: "2026-11-01",
        to: "2026-11-30",
        q: "Ha Long",
        sort: "totalAmount,asc",
      }),
    ).toBe(
      "page=2&size=10&status=COMPLETED&from=2026-11-01&to=2026-11-30&q=Ha+Long&sort=totalAmount%2Casc",
    );
  });

  it("rejects an unknown status or sort", () => {
    expect(() =>
      buildBookingListQuery({ page: 0, size: 20, status: "UNKNOWN" as never }),
    ).toThrow("Invalid status");
    expect(() =>
      buildBookingListQuery({ page: 0, size: 20, sort: "bookingNote,asc" as never }),
    ).toThrow("Invalid sort");
  });
});

describe("createRequestTracker", () => {
  it("only accepts the latest request token", () => {
    const tracker = createRequestTracker();
    const first = tracker.begin();
    const second = tracker.begin();

    expect(tracker.isCurrent(first)).toBe(false);
    expect(tracker.isCurrent(second)).toBe(true);

    tracker.cancel();
    expect(tracker.isCurrent(second)).toBe(false);
  });
});

describe("createKeyedRequestTracker", () => {
  it("invalidates a closed row without touching other rows", () => {
    const tracker = createKeyedRequestTracker();
    const rowOne = tracker.begin(1);
    const rowTwo = tracker.begin(2);

    tracker.cancel(1);

    expect(tracker.isCurrent(1, rowOne)).toBe(false);
    expect(tracker.isCurrent(2, rowTwo)).toBe(true);
  });

  it("only accepts the newest read for the same row", () => {
    const tracker = createKeyedRequestTracker();
    const first = tracker.begin(7);
    const second = tracker.begin(7);

    expect(tracker.isCurrent(7, first)).toBe(false);
    expect(tracker.isCurrent(7, second)).toBe(true);
  });
});

describe("resetAdminPage", () => {
  it("resets to page 0 with the new filters and keeps the chosen size", () => {
    const current = { page: 2, size: 20, filters: { q: "old" } };
    expect(resetAdminPage(current, { filters: { q: "new" } })).toEqual({
      page: 0, size: 20, filters: { q: "new" },
    });
    expect(resetAdminPage(current, { filters: { q: "new" }, size: 50 })).toEqual({
      page: 0, size: 50, filters: { q: "new" },
    });
  });
});

describe("createDebouncedCommit", () => {
  it("commits a burst exactly once with the newest value at 400 ms", () => {
    vi.useFakeTimers();
    const commit = vi.fn();
    const debounced = createDebouncedCommit(commit);

    debounced.schedule("a");
    vi.advanceTimersByTime(200);
    debounced.schedule("ab");
    vi.advanceTimersByTime(399);
    expect(commit).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(commit).toHaveBeenCalledExactlyOnceWith("ab");
  });

  it("drops a pending value when an explicit action cancels it", () => {
    vi.useFakeTimers();
    const commit = vi.fn();
    const debounced = createDebouncedCommit(commit);

    debounced.schedule("pending");
    debounced.cancel();
    vi.advanceTimersByTime(400);

    expect(commit).not.toHaveBeenCalled();
  });
});
