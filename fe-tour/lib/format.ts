const MONTHS = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

type DateParts = { year: number; month: number; day: number };

// The API sends LocalDateTime without an offset, so read the wall-clock parts
// directly instead of letting the browser shift them into another timezone.
function parts(value: string | null | undefined): DateParts | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value ?? "");
  if (!match) return null;
  return {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
  };
}

export function formatDate(value: string | null | undefined): string {
  const date = parts(value);
  if (!date) return "Dates to be announced";
  return `${date.day} ${MONTHS[date.month - 1]} ${date.year}`;
}

export function formatDateRange(
  start: string | null | undefined,
  end: string | null | undefined,
): string {
  const from = parts(start);
  const to = parts(end);
  if (!from) return "Dates to be announced";
  if (!to) return formatDate(start);

  if (from.year !== to.year) {
    return `${formatDate(start)} – ${formatDate(end)}`;
  }
  if (from.month !== to.month) {
    return `${from.day} ${MONTHS[from.month - 1]} – ${to.day} ${MONTHS[to.month - 1]} ${to.year}`;
  }
  if (from.day !== to.day) {
    return `${from.day}–${to.day} ${MONTHS[to.month - 1]} ${to.year}`;
  }
  return `${from.day} ${MONTHS[from.month - 1]} ${from.year}`;
}

export function durationDays(
  start: string | null | undefined,
  end: string | null | undefined,
): number | null {
  const from = parts(start);
  const to = parts(end);
  if (!from || !to) return null;
  const startUtc = Date.UTC(from.year, from.month - 1, from.day);
  const endUtc = Date.UTC(to.year, to.month - 1, to.day);
  const days = Math.round((endUtc - startUtc) / 86_400_000) + 1;
  return days > 0 ? days : null;
}

export function formatDuration(
  start: string | null | undefined,
  end: string | null | undefined,
): string {
  const days = durationDays(start, end);
  if (!days) return "Flexible";
  return days === 1 ? "1 day" : `${days} days`;
}

export function formatPrice(value: number | null | undefined): string {
  if (value == null) return "Price on request";
  return `₫${new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 0,
  }).format(value)}`;
}

export function seatsLabel(seats: number | null | undefined): string {
  if (!seats) return "Sold out";
  return seats === 1 ? "1 seat left" : `${seats} seats left`;
}

export function isSoldOut(status: string, seats: number): boolean {
  return status === "FULL" || seats <= 0;
}
