import type { Booking } from "./types";

export type BookingTab = "upcoming" | "past" | "cancelled";

export const BOOKING_TABS: ReadonlyArray<{ id: BookingTab; label: string }> = [
  { id: "upcoming", label: "Upcoming" },
  { id: "past", label: "Past" },
  { id: "cancelled", label: "Cancelled" },
];

const STATUS_LABELS: Record<string, string> = {
  PENDING: "Payment pending",
  PAID: "Paid",
  CONFIRMED: "Confirmed",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

const STATUS_DESCRIPTIONS: Record<string, string> = {
  PENDING: "Payment is pending. Your seats are held; complete payment to secure them.",
  PAID: "Payment received. Your seats are secured.",
  CONFIRMED: "Your booking is confirmed and your seats are secured.",
  COMPLETED: "This journey has finished. We hope it stayed with you.",
  CANCELLED: "This booking was cancelled and the seats were released.",
};

// The API sends LocalDateTime without an offset, so compare the wall-clock
// parts the same way the display formatters read them.
export function parseBookingDate(value: string | null | undefined): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/.exec(value ?? "");
  if (!match) return null;
  const [, year, month, day, hours = "0", minutes = "0", seconds = "0"] = match;
  return new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hours),
    Number(minutes),
    Number(seconds),
  ).getTime();
}

export function bookingTab(booking: Booking, now: Date = new Date()): BookingTab {
  if (booking.status === "CANCELLED") return "cancelled";
  if (booking.status === "COMPLETED") return "past";

  const end = parseBookingDate(booking.tour?.endDate);
  if (end !== null && end < now.getTime()) return "past";

  return "upcoming";
}

export function groupBookings(
  bookings: Booking[],
  now: Date = new Date(),
): Record<BookingTab, Booking[]> {
  const groups: Record<BookingTab, Booking[]> = {
    upcoming: [],
    past: [],
    cancelled: [],
  };

  for (const booking of bookings) {
    groups[bookingTab(booking, now)].push(booking);
  }

  groups.upcoming.sort((left, right) => {
    const byStart = compareAscNullsLast(
      parseBookingDate(left.tour?.startDate),
      parseBookingDate(right.tour?.startDate),
    );
    return byStart !== 0 ? byStart : byCreatedDesc(left, right);
  });

  groups.past.sort((left, right) => {
    const byEnd = compareDescNullsLast(
      parseBookingDate(left.tour?.endDate),
      parseBookingDate(right.tour?.endDate),
    );
    return byEnd !== 0 ? byEnd : byCreatedDesc(left, right);
  });

  groups.cancelled.sort(byCreatedDesc);

  return groups;
}

export function canConfirmBooking(booking: Booking): boolean {
  return booking.status === "PAID";
}

export function canCompleteBooking(booking: Booking): boolean {
  return booking.status === "CONFIRMED";
}

export function canViewInvoice(booking: Booking): boolean {
  return booking.status === "PAID" || booking.status === "CONFIRMED" || booking.status === "COMPLETED";
}

export function canCancelBooking(booking: Booking): boolean {
  return booking.status === "PENDING";
}

export function statusLabel(status: string): string {
  return (
    STATUS_LABELS[status] ?? status.charAt(0).toUpperCase() + status.slice(1).toLowerCase()
  );
}

export function statusDescription(status: string): string {
  return STATUS_DESCRIPTIONS[status] ?? "The booking status has been updated.";
}

export function statusTone(status: string): string {
  switch (status) {
    case "PENDING":
      return "border-terracotta/50 bg-white text-terracotta";
    case "PAID":
    case "CONFIRMED":
      return "border-forest/40 bg-white text-forest";
    case "COMPLETED":
      return "border-line bg-white text-charcoal";
    case "CANCELLED":
      return "border-line bg-white text-muted";
    default:
      return "border-line bg-white text-muted";
  }
}

function compareAscNullsLast(left: number | null, right: number | null): number {
  if (left === null && right === null) return 0;
  if (left === null) return 1;
  if (right === null) return -1;
  return left - right;
}

function compareDescNullsLast(left: number | null, right: number | null): number {
  if (left === null && right === null) return 0;
  if (left === null) return 1;
  if (right === null) return -1;
  return right - left;
}

function byCreatedDesc(left: Booking, right: Booking): number {
  return (parseBookingDate(right.createdAt) ?? 0) - (parseBookingDate(left.createdAt) ?? 0);
}
