import { describe, expect, it } from "vitest";
import {
  durationDays,
  formatDate,
  formatDateRange,
  formatDuration,
  formatPrice,
  isSoldOut,
  seatsLabel,
} from "./format";

describe("formatDate", () => {
  it("reads wall-clock parts without a timezone shift", () => {
    expect(formatDate("2026-11-01T23:30:00")).toBe("1 Nov 2026");
  });

  it("falls back when the value is missing", () => {
    expect(formatDate(null)).toBe("Dates to be announced");
  });
});

describe("formatDateRange", () => {
  it("formats a range in the same month", () => {
    expect(formatDateRange("2026-11-01T08:00:00", "2026-11-03T17:00:00")).toBe(
      "1–3 Nov 2026",
    );
  });

  it("formats a range across months", () => {
    expect(formatDateRange("2026-11-28", "2026-12-02")).toBe("28 Nov – 2 Dec 2026");
  });

  it("formats a range across years", () => {
    expect(formatDateRange("2026-12-30", "2027-01-02")).toBe("30 Dec 2026 – 2 Jan 2027");
  });
});

describe("durationDays", () => {
  it("counts both the start and end day", () => {
    expect(durationDays("2026-11-01T08:00:00", "2026-11-03T17:00:00")).toBe(3);
  });

  it("returns null for missing or reversed ranges", () => {
    expect(durationDays(null, "2026-11-03")).toBeNull();
    expect(durationDays("2026-11-03", "2026-11-01")).toBeNull();
  });

  it("formats durations", () => {
    expect(formatDuration("2026-11-01", "2026-11-01")).toBe("1 day");
    expect(formatDuration(null, null)).toBe("Flexible");
  });
});

describe("formatPrice", () => {
  it("groups VND without decimals", () => {
    expect(formatPrice(4500000)).toBe("₫4,500,000");
  });

  it("handles missing prices", () => {
    expect(formatPrice(undefined)).toBe("Price on request");
  });
});

describe("seats", () => {
  it("labels sold-out departures", () => {
    expect(seatsLabel(0)).toBe("Sold out");
    expect(isSoldOut("FULL", 5)).toBe(true);
    expect(isSoldOut("OPEN", 5)).toBe(false);
  });
});
