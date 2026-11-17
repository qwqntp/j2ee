import { describe, expect, it } from "vitest";
import type { Promotion } from "./types";
import { filterPromotions, promotionLifecycle } from "./promotions";

describe("promotionLifecycle", () => {
  it("classifies lifecycle deterministically", () => {
    const now = new Date("2026-11-17T00:00:00");
    expect(
      promotionLifecycle(
        { active: true, startDate: "2026-11-01", endDate: "2026-11-30", code: "SALE" },
        now,
      ),
    ).toBe("Active");
    expect(
      promotionLifecycle(
        { active: false, startDate: "2026-11-01", endDate: "2026-11-30", code: "SALE" },
        now,
      ),
    ).toBe("Inactive");
    expect(
      promotionLifecycle(
        { active: true, startDate: "2026-11-18", endDate: "2026-11-30", code: "SALE" },
        now,
      ),
    ).toBe("Scheduled");
    expect(
      promotionLifecycle(
        { active: true, startDate: "2026-10-01", endDate: "2026-10-31", code: "SALE" },
        now,
      ),
    ).toBe("Expired");
  });

  it("ignores time of day", () => {
    const now = new Date("2026-11-17T23:59:59");
    expect(
      promotionLifecycle(
        { active: true, startDate: "2026-11-17", endDate: "2026-11-30", code: "SALE" },
        now,
      ),
    ).toBe("Active");
    expect(
      promotionLifecycle(
        { active: true, startDate: "2026-11-18", endDate: "2026-11-30", code: "SALE" },
        now,
      ),
    ).toBe("Scheduled");
  });
});

describe("filterPromotions", () => {
  it("filters promotions by lifecycle and case-insensitive code/name", () => {
    const rows = [
      {
        id: 1,
        name: "Winter Sale",
        code: "WIN2026",
        active: true,
        startDate: "2026-11-01",
        endDate: "2026-12-01",
        usedCount: 3,
      },
      {
        id: 2,
        name: "Expired Deal",
        code: "OLD",
        active: true,
        startDate: "2026-10-01",
        endDate: "2026-10-31",
        usedCount: 0,
      },
    ] as Promotion[];
    expect(
      filterPromotions(rows, { lifecycle: "Active", query: "win" }, new Date("2026-11-17T00:00:00")),
    ).toHaveLength(1);
  });
});
