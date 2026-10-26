import Link from "next/link";
import { formatDate, formatPrice, seatsLabel } from "@/lib/format";
import { tourFallbackImage } from "@/lib/tour-images";
import type { TourSummary } from "@/lib/types";
import { IconArrowRight, IconCalendar } from "./icons";
import { TourImage } from "./tour-image";

export function TourCard({ tour }: { tour: TourSummary }) {
  const noDates = tour.departureCount === 0;
  const soldOut = !noDates && tour.seatsAvailable <= 0;
  const status = noDates ? "New dates soon" : soldOut ? "Sold out" : seatsLabel(tour.seatsAvailable);

  return (
    <article className="group">
      <Link href={`/tours/${tour.id}`} className="block">
        <div className="relative aspect-[4/3] overflow-hidden rounded-[3px] bg-sage/20">
          <TourImage
            src={tour.imageUrl}
            alt={tour.name}
            fallbackSrc={tourFallbackImage(tour.name)}
            className="absolute inset-0 h-full w-full transition-transform duration-700 group-hover:scale-[1.03]"
          />
          <span
            className={`absolute left-3 top-3 rounded-[2px] px-2.5 py-1 text-[11px] font-semibold tracking-wide ${
              soldOut ? "bg-charcoal text-ivory" : "bg-ivory/95 text-forest"
            }`}
          >
            {status}
          </span>
          {tour.departureCount > 0 ? (
            <span className="absolute right-3 top-3 rounded-[2px] bg-forest/85 px-2.5 py-1 text-[11px] font-medium text-ivory">
              {tour.departureCount} {tour.departureCount === 1 ? "date" : "dates"}
            </span>
          ) : null}
        </div>

        <div className="pt-5">
          <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-muted">
            Vietnam · Small group
          </p>
          <div className="mt-2 flex items-start justify-between gap-4">
            <h3 className="font-display text-2xl leading-tight tracking-tight text-charcoal transition-colors group-hover:text-forest">
              {tour.name}
            </h3>
            <IconArrowRight className="mt-1.5 h-4 w-4 shrink-0 text-forest opacity-0 transition-opacity group-hover:opacity-100" />
          </div>
          <p className="mt-2 line-clamp-2 min-h-10 text-sm leading-6 text-muted">
            {tour.description ?? "A small-group journey through Vietnam, planned with care."}
          </p>

          <div className="mt-5 flex items-end justify-between gap-4 border-t border-line pt-4">
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-muted">
                Next departure
              </p>
              <p className="mt-1.5 flex items-center gap-1.5 text-sm font-medium text-charcoal">
                <IconCalendar className="h-4 w-4 text-forest" />
                {formatDate(tour.nextDepartureDate)}
              </p>
            </div>
            <div className="text-right">
              <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-muted">
                From
              </p>
              <p className="mt-1 text-base font-semibold text-forest">
                {formatPrice(tour.priceFrom)}
              </p>
            </div>
          </div>
        </div>
      </Link>
    </article>
  );
}
