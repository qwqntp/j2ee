import Link from "next/link";
import { connection } from "next/server";
import { Suspense } from "react";
import { DemoNotice } from "@/components/demo-notice";
import { EmptyState } from "@/components/empty-state";
import { IconArrowRight, IconSearch } from "@/components/icons";
import { Photo } from "@/components/photo";
import { SectionHeading } from "@/components/section-heading";
import { TourGridSkeleton } from "@/components/skeleton";
import { TourCard } from "@/components/tour-card";
import { demoFeaturedTours } from "@/lib/demo-tours";
import { destinations, travelNotes, trustReasons } from "@/lib/editorial";
import { loadFeaturedTours } from "@/lib/tours";
import type { TourSummary } from "@/lib/types";

export default function HomePage() {
  return (
    <>
      <section className="relative isolate overflow-hidden bg-forest">
        <Photo
          src="/images/hero-lan-ha.jpg"
          alt="Limestone islands rising from the green water of Lan Ha Bay"
          className="absolute inset-0 h-full w-full"
          eager
        />
        <div className="absolute inset-0 bg-forest/55" />
        <div className="absolute inset-0 bg-linear-to-t from-charcoal/80 via-charcoal/20 to-transparent" />

        <div className="shell relative flex min-h-[620px] flex-col justify-end py-16 sm:min-h-[680px] sm:py-20 lg:min-h-[740px]">
          <div className="max-w-3xl">
            <p className="eyebrow flex items-center gap-3 text-ivory/80">
              <span className="h-px w-10 bg-ivory/50" />
              Vietnam, unhurried
            </p>
            <h1 className="mt-6 font-display text-5xl leading-[0.98] tracking-tight text-white sm:text-6xl lg:text-7xl">
              Travel deeper than the guidebook.
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-8 text-ivory/85">
              Small-group journeys through Vietnam&apos;s quiet corners — limestone bays,
              lantern-lit lanes and mountain villages — with time to actually be there.
            </p>

            <form
              action="/tours"
              method="get"
              role="search"
              className="mt-8 flex w-full max-w-xl flex-col gap-2 sm:flex-row"
            >
              <label htmlFor="hero-keyword" className="sr-only">
                Search journeys
              </label>
              <div className="relative flex-1">
                <IconSearch className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
                <input
                  id="hero-keyword"
                  name="keyword"
                  type="search"
                  maxLength={100}
                  placeholder="Where would you like to go?"
                  className="min-h-12 w-full rounded-[3px] border border-transparent bg-ivory py-3 pl-11 pr-4 text-sm text-charcoal outline-none placeholder:text-muted/70 focus:border-forest focus:ring-1 focus:ring-forest/20"
                />
              </div>
              <button type="submit" className="btn-light shrink-0">
                Find a journey
                <IconArrowRight className="h-4 w-4" />
              </button>
            </form>

            <ul className="mt-8 flex flex-wrap gap-x-6 gap-y-2 text-xs font-medium text-ivory/75">
              <li>Small groups</li>
              <li>Local guides</li>
              <li>Thoughtful pacing</li>
            </ul>
          </div>

          <p className="mt-16 text-xs text-ivory/60 sm:mt-20">
            Lan Ha Bay, Quảng Ninh · A quieter half of the bay
          </p>
        </div>
      </section>

      <section id="destinations" className="shell py-20 sm:py-24">
        <SectionHeading
          eyebrow="Destinations"
          title="Four places worth slowing down for."
          description="Start with the landscape. Each of these regions rewards a little more time than the guidebook allows."
          action={{ href: "/tours", label: "See all journeys" }}
        />

        <div className="mt-10 grid gap-6 md:grid-cols-12">
          {destinations.map((destination, index) => (
            <Link
              key={destination.name}
              href="/tours"
              className={`group block ${index === 0 || index === 3 ? "md:col-span-7" : "md:col-span-5"}`}
            >
              <div className="relative h-[280px] overflow-hidden rounded-[3px] bg-sage/20 sm:h-[340px] lg:h-[380px]">
                <Photo
                  src={destination.image}
                  alt={destination.alt}
                  className="absolute inset-0 h-full w-full transition-transform duration-700 group-hover:scale-[1.03]"
                />
                <span className="absolute left-3 top-3 rounded-[2px] bg-ivory/95 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-forest">
                  {destination.region}
                </span>
              </div>
              <div className="mt-4 flex items-start justify-between gap-4">
                <div>
                  <h3 className="font-display text-2xl tracking-tight text-charcoal transition-colors group-hover:text-forest">
                    {destination.name}
                  </h3>
                  <p className="mt-1.5 max-w-md text-sm leading-6 text-muted">
                    {destination.description}
                  </p>
                </div>
                <IconArrowRight className="mt-1.5 h-4 w-4 shrink-0 text-forest opacity-0 transition-opacity group-hover:opacity-100" />
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section id="experiences" className="shell py-20 sm:py-24">
        <SectionHeading
          eyebrow="Curated experiences"
          title="Journeys we know by heart."
          description="A small collection of routes we run ourselves, each with room left in the schedule."
          action={{ href: "/tours", label: "View all journeys" }}
        />

        <div className="mt-10">
          <Suspense fallback={<TourGridSkeleton />}>
            <FeaturedJourneys />
          </Suspense>
        </div>
      </section>

      <section id="stories" className="border-y border-line bg-white/50">
        <div className="shell py-20 sm:py-24">
          <SectionHeading
            eyebrow="Travel notes"
            title="Notes from the road."
            description="A few things we have learned about moving slowly through Vietnam."
            action={{ href: "/tours", label: "Explore the collection" }}
          />

          <div className="mt-10 grid gap-8 md:grid-cols-3">
            {travelNotes.map((note) => (
              <Link key={note.title} href="/tours" className="group flex h-full flex-col">
                <div className="relative aspect-[3/2] overflow-hidden rounded-[3px] bg-sage/20">
                  <Photo
                    src={note.image}
                    alt={note.alt}
                    className="absolute inset-0 h-full w-full transition-transform duration-700 group-hover:scale-[1.03]"
                  />
                </div>
                <p className="mt-4 text-[10px] font-semibold uppercase tracking-[0.18em] text-terracotta">
                  {note.category}
                </p>
                <h3 className="mt-2 font-display text-2xl leading-tight tracking-tight text-charcoal transition-colors group-hover:text-forest">
                  {note.title}
                </h3>
                <p className="mt-2 text-sm leading-6 text-muted">{note.excerpt}</p>
                <span className="mt-auto inline-flex items-center gap-2 pt-4 text-sm font-medium text-forest">
                  Find journeys
                  <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
                </span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      <section id="why" className="border-t border-line bg-white/50">
        <div className="shell py-20 sm:py-24">
          <div className="grid gap-12 lg:grid-cols-[0.9fr_1.1fr] lg:gap-20">
            <div>
              <p className="eyebrow text-forest">Why Hành Trình</p>
              <h2 className="mt-3 font-display text-4xl leading-[1.05] tracking-tight text-charcoal sm:text-5xl">
                The details are the trip.
              </h2>
              <p className="mt-5 max-w-md text-base leading-7 text-muted">
                We plan around people and places, not a checklist. Fewer stops, longer stays, and a
                guide who knows when to tell a story — and when to let the view speak.
              </p>
              <p className="mt-8 border-l-2 border-terracotta pl-4 text-sm leading-6 text-charcoal">
                Every journey is planned and run by our own team in Vietnam. No call centres, no
                invented reviews.
              </p>
            </div>

            <div className="grid gap-x-10 gap-y-8 sm:grid-cols-2">
              {trustReasons.map((reason) => (
                <div key={reason.title} className="border-t border-line pt-5">
                  <p className="text-xs font-semibold tracking-[0.16em] text-terracotta">
                    {reason.number}
                  </p>
                  <h3 className="mt-2 font-display text-xl tracking-tight">{reason.title}</h3>
                  <p className="mt-2 text-sm leading-6 text-muted">{reason.copy}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="shell py-20 sm:py-28">
        <figure className="mx-auto max-w-4xl text-center">
          <div className="mx-auto h-px w-16 bg-terracotta" />
          <blockquote className="mt-8 text-balance font-display text-3xl leading-[1.15] tracking-tight text-charcoal sm:text-5xl">
            “The best moments in Vietnam are the ones no itinerary can schedule.”
          </blockquote>
          <figcaption className="mt-6 text-[10px] font-semibold uppercase tracking-[0.18em] text-muted">
            The Hành Trình team
          </figcaption>
        </figure>
      </section>

      <section className="shell pb-20 sm:pb-24">
        <div className="relative isolate overflow-hidden rounded-[4px] bg-forest">
          <Photo
            src="/images/cta-phu-quoc.jpg"
            alt="Aerial view of the pale sand and turquoise water at Kem Beach, Phú Quốc"
            className="absolute inset-0 h-full w-full"
          />
          <div className="absolute inset-0 bg-forest/65" />
          <div className="relative max-w-2xl px-6 py-16 sm:px-12 sm:py-20">
            <p className="eyebrow text-sage">Start with a conversation</p>
            <h2 className="mt-3 font-display text-4xl leading-[1.05] tracking-tight text-white sm:text-5xl">
              Tell us how you like to travel.
            </h2>
            <p className="mt-4 max-w-xl leading-7 text-ivory/80">
              Browse the collection for inspiration, or write to us with the kind of trip you have
              in mind. We will help you find the right pace.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link href="/tours" className="btn-light">
                Browse journeys
                <IconArrowRight className="h-4 w-4" />
              </Link>
              <a href="mailto:hello@hanhtrinh.example" className="btn-ghost-light">
                Email us
              </a>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}

async function FeaturedJourneys() {
  await connection();

  let tours: TourSummary[];
  let usedDemo = false;

  try {
    tours = await loadFeaturedTours();
  } catch {
    // Kept outside the cached read so sample data is never cached as live data.
    tours = demoFeaturedTours();
    usedDemo = true;
  }

  if (tours.length === 0) {
    return (
      <EmptyState
        title="New journeys are being mapped."
        description="There are no published journeys just yet. Check back soon, or write to us and we will help you plan."
        action={{ href: "/tours", label: "Browse all journeys" }}
      />
    );
  }

  return (
    <>
      {usedDemo ? <DemoNotice /> : null}
      <div className="grid gap-x-6 gap-y-12 sm:grid-cols-2 lg:grid-cols-3">
        {tours.map((tour) => (
          <TourCard key={tour.id} tour={tour} />
        ))}
      </div>
    </>
  );
}
