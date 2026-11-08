import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { TripDetailSkeleton } from "@/components/skeleton";
import { TripDetail } from "@/components/trip-detail";

export const metadata: Metadata = {
  title: "Trip details",
  description: "Dates, travellers, seats and the order total for one of your booked journeys.",
};

type Props = { params: Promise<{ id: string }> };

function bookingId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const parsed = Number.parseInt(value, 10);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

export default function TripDetailPage({ params }: Props) {
  return (
    <Suspense fallback={<TripDetailSkeleton />}>
      <TripDetailRoute params={params} />
    </Suspense>
  );
}

async function TripDetailRoute({ params }: Props) {
  const { id } = await params;
  const bookingIdValue = bookingId(id);
  if (bookingIdValue === null) notFound();

  return <TripDetail key={bookingIdValue} bookingId={bookingIdValue} />;
}
