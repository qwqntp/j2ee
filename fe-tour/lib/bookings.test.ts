import { describe, expect, it } from "vitest";
import type { Booking } from "./types";
import {
  bookingTab,
  canCancelBooking,
  canCompleteBooking,
  canConfirmBooking,
  canViewInvoice,
  groupBookings,
  statusDescription,
  statusLabel,
} from "./bookings";

const baseTour: NonNullable<Booking["tour"]> = {
  tourId: 1,
  tourName: "Hoi An Slow Days",
  imageUrl: null,
  startDate: "2026-12-01T09:00:00",
  endDate: "2026-12-03T18:00:00",
  meetingPoint: "Hoi An old town",
  departureStatus: "OPEN",
};

function booking(
  overrides: Partial<Booking> & Pick<Booking, "id" | "status">,
): Booking {
  return {
    bookingCode: `BK-${overrides.id}`,
    departureId: 1,
    accountId: 1,
    createdAt: "2026-10-01T10:00:00",
    guestCount: 2,
    unitPrice: 500,
    subtotal: 1000,
    discountAmount: 0,
    totalAmount: 1000,
    note: null,
    travelers: [],
    promotion: null,
    tour: baseTour,
    ...overrides,
  };
}

const now = new Date(2026, 10, 7, 12, 0);

describe("bookingTab", () => {
  it("puts a future active booking in upcoming", () => {
    expect(bookingTab(booking({ id: 1, status: "PENDING" }), now)).toBe("upcoming");
  });

  it("puts a booking whose departure ended in past", () => {
    const trip = booking({
      id: 1,
      status: "CONFIRMED",
      tour: { ...baseTour, endDate: "2026-11-06T18:00:00" },
    });

    expect(bookingTab(trip, now)).toBe("past");
  });

  it("puts completed bookings in past even without dates", () => {
    const trip = booking({ id: 1, status: "COMPLETED", tour: null });

    expect(bookingTab(trip, now)).toBe("past");
  });

  it("puts cancelled bookings in cancelled regardless of dates", () => {
    const trip = booking({ id: 1, status: "CANCELLED" });

    expect(bookingTab(trip, now)).toBe("cancelled");
  });

  it("keeps bookings without dates in upcoming unless they are closed", () => {
    expect(bookingTab(booking({ id: 1, status: "PAID", tour: null }), now)).toBe("upcoming");
  });
});

describe("groupBookings", () => {
  it("sorts upcoming trips by the soonest departure", () => {
    const later = booking({
      id: 1,
      status: "PENDING",
      tour: { ...baseTour, startDate: "2026-12-10T09:00:00" },
    });
    const sooner = booking({
      id: 2,
      status: "PENDING",
      tour: { ...baseTour, startDate: "2026-11-20T09:00:00" },
    });

    const groups = groupBookings([later, sooner], now);

    expect(groups.upcoming.map((item) => item.id)).toEqual([2, 1]);
  });

  it("sorts past trips by the most recent departure", () => {
    const older = booking({
      id: 1,
      status: "COMPLETED",
      tour: { ...baseTour, endDate: "2026-09-03T18:00:00" },
    });
    const newer = booking({
      id: 2,
      status: "COMPLETED",
      tour: { ...baseTour, endDate: "2026-10-03T18:00:00" },
    });

    const groups = groupBookings([older, newer], now);

    expect(groups.past.map((item) => item.id)).toEqual([2, 1]);
  });

  it("sorts cancelled trips by the newest booking", () => {
    const older = booking({
      id: 1,
      status: "CANCELLED",
      createdAt: "2026-09-01T10:00:00",
    });
    const newer = booking({
      id: 2,
      status: "CANCELLED",
      createdAt: "2026-10-01T10:00:00",
    });

    const groups = groupBookings([older, newer], now);

    expect(groups.cancelled.map((item) => item.id)).toEqual([2, 1]);
  });
});

describe("canCancelBooking", () => {
  it("allows cancellation only for pending bookings", () => {
    expect(canCancelBooking(booking({ id: 1, status: "PENDING" }))).toBe(true);
    expect(canCancelBooking(booking({ id: 2, status: "PAID" }))).toBe(false);
    expect(canCancelBooking(booking({ id: 3, status: "CONFIRMED" }))).toBe(false);
    expect(canCancelBooking(booking({ id: 4, status: "CANCELLED" }))).toBe(false);
  });
});

describe("canConfirmBooking", () => {
  it("allows confirmation only for paid bookings", () => {
    expect(canConfirmBooking(booking({ id: 1, status: "PENDING" }))).toBe(false);
    expect(canConfirmBooking(booking({ id: 2, status: "PAID" }))).toBe(true);
    expect(canConfirmBooking(booking({ id: 3, status: "CONFIRMED" }))).toBe(false);
    expect(canConfirmBooking(booking({ id: 4, status: "COMPLETED" }))).toBe(false);
  });
});

describe("canCompleteBooking", () => {
  it("allows completion only for confirmed bookings", () => {
    expect(canCompleteBooking(booking({ id: 1, status: "PAID" }))).toBe(false);
    expect(canCompleteBooking(booking({ id: 2, status: "CONFIRMED" }))).toBe(true);
    expect(canCompleteBooking(booking({ id: 3, status: "COMPLETED" }))).toBe(false);
  });
});

describe("canViewInvoice", () => {
  it("allows viewing once payment is received", () => {
    expect(canViewInvoice(booking({ id: 1, status: "PENDING" }))).toBe(false);
    expect(canViewInvoice(booking({ id: 2, status: "PAID" }))).toBe(true);
    expect(canViewInvoice(booking({ id: 3, status: "CONFIRMED" }))).toBe(true);
    expect(canViewInvoice(booking({ id: 4, status: "COMPLETED" }))).toBe(true);
    expect(canViewInvoice(booking({ id: 5, status: "CANCELLED" }))).toBe(false);
  });
});

describe("statusLabel", () => {
  it("describes each booking status", () => {
    expect(statusLabel("PENDING")).toBe("Payment pending");
    expect(statusLabel("PAID")).toBe("Paid");
    expect(statusLabel("CONFIRMED")).toBe("Confirmed");
    expect(statusLabel("COMPLETED")).toBe("Completed");
    expect(statusLabel("CANCELLED")).toBe("Cancelled");
  });

  it("falls back to a readable label for unknown statuses", () => {
    expect(statusLabel("WAITLISTED")).toBe("Waitlisted");
  });
});

describe("statusDescription", () => {
  it("explains the next step for each status", () => {
    expect(statusDescription("PENDING")).toContain("Payment");
    expect(statusDescription("CANCELLED")).toContain("released");
  });
});
