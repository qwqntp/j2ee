import { expect, it } from "vitest";
import { buildBookingBars, buildDashboardCards, shouldShowSignIn } from "./admin-stats";
import type { AdminStats } from "./types";

const stats: AdminStats = {
  generatedAt: "2026-11-17T08:00:00",
  totalTours: 12,
  openTours: 4,
  bookingsByStatus: { PENDING: 2, PAID: 3, CONFIRMED: 1, COMPLETED: 5, CANCELLED: 1 },
  successfulRevenue: 1500,
  successfulPayments: 3,
  averageSuccessfulPayment: 500,
  upcomingDepartures: 6,
  lowCapacityDepartures: 2,
  soldOutDepartures: 1,
  topTours: [],
  capacityRisks: [],
};

it("prioritizes four metrics without dropping the secondary readings", () => {
  const { primary, secondary } = buildDashboardCards(stats);
  expect(primary.map((card) => card.label)).toEqual([
    "Successful revenue", "Pending bookings", "Open tours", "Upcoming departures",
  ]);
  expect(primary.map((card) => card.value)).toEqual([expect.stringContaining("1,500"), "2", "4", "6"]);
  expect(secondary.map(({ label, value }) => [label, value])).toEqual([
    ["Avg. successful payment", expect.stringContaining("500")],
    ["At-risk capacity", "2"], ["Sold out", "1"],
  ]);
});

it("scales booking counts without changing their values or inventing history", () => {
  expect(buildBookingBars([8, 4, 0])).toEqual([
    { count: 8, width: 100 }, { count: 4, width: 50 }, { count: 0, width: 0 },
  ]);
  expect(buildBookingBars([0])).toEqual([{ count: 0, width: 0 }]);
  expect(buildBookingBars([])).toEqual([]);
});

it("shows sign-in only when stats are absent without an error", () => {
  expect(shouldShowSignIn(null, null)).toBe(true);
  expect(shouldShowSignIn(null, "Service unavailable")).toBe(false);
  expect(shouldShowSignIn(stats, null)).toBe(false);
});
