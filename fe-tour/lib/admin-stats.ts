import { formatPrice } from "./format";
import type { AdminStats } from "./types";

export type DashboardCard = {
  icon: "revenue" | "bookings" | "tours" | "calendar" | "alert";
  label: string;
  value: string;
  tone: "forest" | "terracotta" | "charcoal";
};

export function shouldShowSignIn(stats: AdminStats | null, error: string | null): boolean {
  return !stats && !error;
}

export function buildDashboardCards(stats: AdminStats): { primary: DashboardCard[]; secondary: DashboardCard[] } {
  return {
    primary: [
      { label: "Successful revenue", value: formatPrice(stats.successfulRevenue), tone: "forest", icon: "revenue" },
      { label: "Pending bookings", value: String(stats.bookingsByStatus.PENDING), tone: "terracotta", icon: "bookings" },
      { label: "Open tours", value: String(stats.openTours), tone: "forest", icon: "tours" },
      { label: "Upcoming departures", value: String(stats.upcomingDepartures), tone: "charcoal", icon: "calendar" },
    ],
    secondary: [
      { label: "Avg. successful payment", value: formatPrice(stats.averageSuccessfulPayment), tone: "charcoal", icon: "revenue" },
      { label: "At-risk capacity", value: String(stats.lowCapacityDepartures), tone: "terracotta", icon: "alert" },
      { label: "Sold out", value: String(stats.soldOutDepartures), tone: "terracotta", icon: "alert" },
    ],
  };
}

export function buildBookingBars(counts: number[]) {
  const max = Math.max(1, ...counts);
  return counts.map((count) => ({ count, width: count / max * 100 }));
}
