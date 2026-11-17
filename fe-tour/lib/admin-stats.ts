import { formatPrice } from "./format";
import type { AdminStats } from "./types";

export type DashboardCard = {
  label: string;
  value: string;
  tone: "forest" | "terracotta" | "charcoal";
};

export function shouldShowSignIn(stats: AdminStats | null, error: string | null): boolean {
  return !stats && !error;
}

export function buildDashboardCards(stats: AdminStats): DashboardCard[] {
  return [
    { label: "Open tours", value: String(stats.openTours), tone: "forest" },
    {
      label: "Pending bookings",
      value: String(stats.bookingsByStatus.PENDING),
      tone: "terracotta",
    },
    { label: "Revenue (SUCCESS)", value: formatPrice(stats.successfulRevenue), tone: "forest" },
    {
      label: "Avg. successful payment",
      value: formatPrice(stats.averageSuccessfulPayment),
      tone: "charcoal",
    },
    { label: "Upcoming departures", value: String(stats.upcomingDepartures), tone: "forest" },
    { label: "At-risk capacity", value: String(stats.lowCapacityDepartures), tone: "terracotta" },
    { label: "Sold out", value: String(stats.soldOutDepartures), tone: "terracotta" },
  ];
}
