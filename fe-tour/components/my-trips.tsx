"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { ApiError, listBookings } from "@/lib/api";
import { clearSession, ensureSession, useSession } from "@/lib/auth";
import { BOOKING_TABS, groupBookings, type BookingTab } from "@/lib/bookings";
import type { Booking } from "@/lib/types";
import { EmptyState } from "./empty-state";
import { MyTripsSkeleton } from "./skeleton";
import { TripCard } from "./trip-card";

const PAGE_SIZE = 20;

const EMPTY_COPY: Record<BookingTab, { title: string; description: string }> = {
  upcoming: {
    title: "No upcoming trips.",
    description:
      "When you reserve seats on a departure, the journey will appear here with its dates, seats and booking code.",
  },
  past: {
    title: "No past trips yet.",
    description:
      "Journeys you have taken will collect here, so you can look back at where you have been.",
  },
  cancelled: {
    title: "No cancelled bookings.",
    description:
      "If you cancel a pending booking, it will stay here for your records.",
  },
};

export function MyTrips() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const session = useSession();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      const current = await ensureSession();
      if (!active) return;
      if (!current) {
        router.replace("/login?next=/my-trips");
        return;
      }

      try {
        const result = await listBookings(current.accessToken, 0, PAGE_SIZE);
        if (!active) return;
        setBookings(result.content);
        setPage(result.page);
        setHasNext(result.hasNext);
        setTotalElements(result.totalElements);
        setError(null);
        setLoading(false);
      } catch (err) {
        if (!active) return;
        if (err instanceof ApiError && err.status === 401) {
          clearSession();
          router.replace("/login?next=/my-trips");
          return;
        }
        setBookings([]);
        setHasNext(false);
        setTotalElements(0);
        setError(
          err instanceof ApiError
            ? err.message
            : "Could not reach the booking service. Try again.",
        );
        setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [router, reloadKey]);

  useEffect(() => {
    if (!loading && session === null) {
      router.replace("/login?next=/my-trips");
    }
  }, [loading, session, router]);

  const activeTab: BookingTab =
    BOOKING_TABS.find((item) => item.id === searchParams.get("tab"))?.id ?? "upcoming";
  const groups = useMemo(() => groupBookings(bookings), [bookings]);
  const activeBookings = groups[activeTab];
  const showCounts = !hasNext;
  const totalLabel =
    totalElements === 1 ? "1 trip booked" : `${totalElements} trips booked`;

  function retry() {
    setLoading(true);
    setError(null);
    setReloadKey((key) => key + 1);
  }

  async function loadMore() {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    setError(null);

    try {
      const current = await ensureSession();
      if (!current) {
        router.replace("/login?next=/my-trips");
        return;
      }
      const result = await listBookings(current.accessToken, page + 1, PAGE_SIZE);
      setBookings((existing) => [...existing, ...result.content]);
      setPage(result.page);
      setHasNext(result.hasNext);
      setTotalElements(result.totalElements);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearSession();
        router.replace("/login?next=/my-trips");
        return;
      }
      setError(
        err instanceof ApiError ? err.message : "Could not load more trips. Try again.",
      );
    } finally {
      setLoadingMore(false);
    }
  }

  if (loading) {
    return <MyTripsSkeleton />;
  }

  if (error && bookings.length === 0) {
    return <TripsError message={error} onRetry={retry} />;
  }

  if (totalElements === 0) {
    return (
      <EmptyState
        title="No trips yet."
        description="When you reserve seats on a departure, your booking will appear here with its dates, seats and order total."
        action={{ href: "/tours", label: "Browse journeys" }}
      />
    );
  }

  const empty = EMPTY_COPY[activeTab];

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow text-forest">Booked journeys</p>
          <h2 className="mt-2 font-display text-3xl tracking-tight">{totalLabel}</h2>
        </div>
        <Link href="/tours" className="btn-secondary">
          Find another journey
        </Link>
      </div>

      <nav
        aria-label="Trip filters"
        className="mt-8 flex flex-wrap gap-6 border-b border-line"
      >
        {BOOKING_TABS.map((item) => {
          const active = item.id === activeTab;
          const count = groups[item.id].length;
          return (
            <Link
              key={item.id}
              href={item.id === "upcoming" ? "/my-trips" : `/my-trips?tab=${item.id}`}
              scroll={false}
              aria-current={active ? "page" : undefined}
              className={`-mb-px inline-flex min-h-11 items-center gap-2 border-b-2 px-1 text-sm font-medium transition-colors ${
                active
                  ? "border-forest text-forest"
                  : "border-transparent text-muted hover:text-forest"
              }`}
            >
              {item.label}
              {showCounts && count > 0 ? (
                <span
                  className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                    active ? "bg-forest/10 text-forest" : "bg-sage/25 text-charcoal"
                  }`}
                >
                  {count}
                </span>
              ) : null}
            </Link>
          );
        })}
      </nav>

      {error ? (
        <div
          role="alert"
          className="mt-6 rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-4 text-sm text-terracotta"
        >
          {error}
        </div>
      ) : null}

      <div className="mt-8 space-y-5">
        {activeBookings.length === 0 ? (
          <EmptyState
            title={empty.title}
            description={empty.description}
            action={{ href: "/tours", label: "Browse journeys" }}
          />
        ) : (
          activeBookings.map((booking) => <TripCard key={booking.id} booking={booking} />)
        )}
      </div>

      {hasNext ? (
        <div className="mt-8 text-center">
          <button
            type="button"
            onClick={loadMore}
            disabled={loadingMore}
            className="btn-secondary min-w-48"
          >
            {loadingMore ? "Loading…" : "Load more trips"}
          </button>
        </div>
      ) : null}
    </div>
  );
}

function TripsError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="rounded-[4px] border border-terracotta/40 bg-terracotta/10 p-6"
    >
      <p className="font-medium text-terracotta">We could not load your trips.</p>
      <p className="mt-1 text-sm leading-6 text-muted">{message}</p>
      <button type="button" onClick={onRetry} className="btn-secondary mt-4">
        Try again
      </button>
    </div>
  );
}
