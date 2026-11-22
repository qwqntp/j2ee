import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { TripDetailSkeleton } from "@/components/skeleton";
import { AccountSessionBoundary } from "@/components/account-session-boundary";
import { TripDetail } from "@/components/trip-detail";

export const metadata: Metadata = {
  title: "Order details",
  description: "Travelers, promotion, status and totals for one of your bookings.",
};

type Props = { params: Promise<{ id: string }> };

function bookingId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const parsed = Number.parseInt(value, 10);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

export default function AccountBookingDetailPage({ params }: Props) {
  return (
    <Suspense fallback={<TripDetailSkeleton />}>
      <AccountBookingDetailRoute params={params} />
    </Suspense>
  );
}

async function AccountBookingDetailRoute({ params }: Props) {
  const { id } = await params;
  const value = bookingId(id);
  if (value === null) notFound();

  return (
    <AccountSessionBoundary fallback={<TripDetailSkeleton />}>
      <TripDetail key={value} bookingId={value} basePath="/account/bookings" />
    </AccountSessionBoundary>
  );
}
