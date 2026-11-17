import { describe, expect, it } from "vitest";
import { buildBookingListQuery } from "./booking-list";

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
      }),
    ).toBe(
      "page=2&size=10&status=COMPLETED&from=2026-11-01&to=2026-11-30&q=Ha+Long",
    );
  });

  it("rejects an unknown status", () => {
    expect(() =>
      buildBookingListQuery({ page: 0, size: 20, status: "UNKNOWN" as never }),
    ).toThrow("Invalid status");
  });
});
