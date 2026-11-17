import { expect, it } from "vitest";
import { buildDashboardCards } from "./admin-stats";
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

it("builds seven dashboard cards with exact labels", () => {
  expect(buildDashboardCards(stats).map((card) => card.label)).toEqual([
    "Open tours", "Pending bookings", "Revenue (SUCCESS)", "Avg. successful payment",
    "Upcoming departures", "At-risk capacity", "Sold out",
  ]);
});
