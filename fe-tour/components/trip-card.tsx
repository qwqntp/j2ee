import Link from "next/link";
import { statusLabel, statusTone } from "@/lib/bookings";
import { formatDate, formatDateRange, formatPrice } from "@/lib/format";
import { tourFallbackImage } from "@/lib/tour-images";
import type { Booking } from "@/lib/types";
import { IconArrowRight, IconCalendar, IconMapPin, IconUsers } from "./icons";
import { TourImage } from "./tour-image";

export function TripCard({ booking }: { booking: Booking }) {
  const tour = booking.tour;
  const tourName = tour?.tourName ?? `Booking ${booking.bookingCode}`;
  const fallback = tourFallbackImage(tourName);
  const seatNote =
    booking.status === "CANCELLED"
      ? "Seats released"
      : booking.status === "COMPLETED"
        ? "Journey completed"
        : "Seats held";

  return (
    <article className="overflow-hidden rounded-[4px] border border-line bg-white transition-colors hover:border-forest/40">
      <div className="grid sm:grid-cols-[15rem_1fr]">
        <Link
          href={`/my-trips/${booking.id}`}
          aria-label={`View ${tourName}`}
          className="group relative block aspect-[4/3] overflow-hidden bg-sage/20 sm:aspect-auto sm:min-h-full"
        >
          <TourImage
            src={tour?.imageUrl ?? null}
            alt={tourName}
            fallbackSrc={fallback}
            className="absolute inset-0 h-full w-full transition-transform duration-700 group-hover:scale-[1.03]"
          />
          <span
            className={`absolute left-3 top-3 rounded-[2px] border px-2.5 py-1 text-[11px] font-semibold tracking-wide ${statusTone(booking.status)}`}
          >
            {statusLabel(booking.status)}
          </span>
        </Link>

        <div className="flex flex-col gap-4 p-5 sm:p-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-muted">
                {booking.bookingCode}
              </p>
              <h3 className="mt-2 font-display text-2xl leading-tight tracking-tight">
                <Link href={`/my-trips/${booking.id}`} className="transition-colors hover:text-forest">
                  {tourName}
                </Link>
              </h3>
            </div>
            <p className="shrink-0 text-right">
              <span className="block text-[10px] font-semibold uppercase tracking-[0.16em] text-muted">
                Total
              </span>
              <span className="mt-1 block text-lg font-semibold text-forest">
                {formatPrice(booking.totalAmount)}
              </span>
            </p>
          </div>

          <div className="grid gap-2 text-sm text-muted sm:grid-cols-2">
            <p className="flex items-center gap-2">
              <IconCalendar className="h-4 w-4 shrink-0 text-forest" />
              {formatDateRange(tour?.startDate, tour?.endDate)}
            </p>
            {tour?.meetingPoint ? (
              <p className="flex items-center gap-2">
                <IconMapPin className="h-4 w-4 shrink-0 text-forest" />
                <span className="truncate">{tour.meetingPoint}</span>
              </p>
            ) : null}
            <p className="flex items-center gap-2">
              <IconUsers className="h-4 w-4 shrink-0 text-forest" />
              {booking.guestCount} {booking.guestCount === 1 ? "traveller" : "travellers"} ·{" "}
              {booking.guestCount} {booking.guestCount === 1 ? "seat" : "seats"}
            </p>
          </div>

          <div className="mt-auto flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
            <p className="text-xs text-muted">
              {seatNote} · Booked {formatDate(booking.createdAt)}
            </p>
            <Link
              href={`/my-trips/${booking.id}`}
              className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-forest transition-colors hover:text-forest-deep hover:underline"
            >
              View trip
              <IconArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </div>
      </div>
    </article>
  );
}
