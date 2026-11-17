import { describe, expect, it } from "vitest";
import { promotionLifecycle } from "./promotions";

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
