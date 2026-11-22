import type { Metadata } from "next";
import { Suspense } from "react";
import { MyTrips } from "@/components/my-trips";
import { MyTripsSkeleton } from "@/components/skeleton";

export const metadata: Metadata = {
  title: "My trips",
  description: "Your booked journeys, seats and order totals in one place.",
};

export default function MyTripsPage() {
  return (
    <>
      <section className="border-b border-line bg-white/50">
        <div className="shell py-14 sm:py-20">
          <div className="grid gap-8 lg:grid-cols-[1fr_0.8fr] lg:items-end">
            <div>
              <p className="eyebrow text-terracotta">Your account</p>
              <h1 className="mt-3 font-display text-5xl leading-[0.98] tracking-tight text-charcoal sm:text-6xl">
                My trips
              </h1>
              <p className="mt-5 max-w-2xl text-lg leading-8 text-muted">
                Every journey you have reserved, with its dates, seats and booking code. Open a
                trip to see the full order or cancel a pending booking.
              </p>
            </div>
            <p className="max-w-sm text-sm leading-6 text-muted lg:justify-self-end">
              Seats are held against your account. Only you can see these bookings while signed
              in.
            </p>
          </div>
        </div>
      </section>

      <section className="shell py-10 sm:py-14">
        <Suspense fallback={<MyTripsSkeleton />}>
          <MyTrips />
        </Suspense>
      </section>
    </>
  );
}
