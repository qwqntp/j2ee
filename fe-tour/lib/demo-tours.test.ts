import { describe, expect, it } from "vitest";
import { demoFeaturedTours, demoTour, demoToursPage } from "./demo-tours";

describe("demoToursPage", () => {
  it("searches name, description and destinations case-insensitively", () => {
    const page = demoToursPage(0, "LAN HA");

    expect(page.totalElements).toBe(1);
    expect(page.content[0]?.name).toBe("Lan Ha Bay Overnight");
  });

  it("returns the same page shape as the API", () => {
    const page = demoToursPage(0, "");

    expect(page.size).toBe(9);
    expect(page.page).toBe(0);
    expect(page.totalPages).toBeGreaterThan(0);
  });
});

describe("demoFeaturedTours", () => {
  it("caps the featured list", () => {
    expect(demoFeaturedTours(2)).toHaveLength(2);
  });
});

describe("demoTour", () => {
  it("returns a known tour and null for an unknown id", () => {
    expect(demoTour(9001)?.name).toBe("Lan Ha Bay Overnight");
    expect(demoTour(1)).toBeNull();
  });
});
