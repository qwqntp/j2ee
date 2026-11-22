"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, listBookings } from "@/lib/api";
import { clearSession, ensureSession, useSession } from "@/lib/auth";
import { registerUserRequest } from "@/lib/user-state";
import type { Booking } from "@/lib/types";
import { EmptyState } from "./empty-state";
import { IconChevronLeft, IconChevronRight } from "./icons";
import { MyTripsSkeleton } from "./skeleton";
import { TripCard } from "./trip-card";

const BASE_PATH = "/account/bookings";
const PAGE_SIZE = 10;
const loginHref = `/login?next=${encodeURIComponent(BASE_PATH)}`;

export function AccountBookings() {
  const router = useRouter();
  const session = useSession();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const accountId = session?.account.id ?? null;

  useEffect(() => {
    const controller = new AbortController();
    const untrack = registerUserRequest(controller);
    let active = true;

    async function load() {
      setLoading(true);
      setError(null);
      const current = await ensureSession();
      if (!active || controller.signal.aborted) return;
      if (!current) {
        setLoading(false);
        router.replace(loginHref);
        return;
      }

      try {
        const result = await listBookings(
          current.accessToken,
          page,
          PAGE_SIZE,
          {},
          controller.signal,
        );
        if (!active || controller.signal.aborted) return;
        setBookings(result.content);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
        setError(null);
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        if (err instanceof ApiError && err.status === 401) {
          clearSession();
          router.replace(loginHref);
          return;
        }
        setBookings([]);
        setTotalPages(0);
        setTotalElements(0);
        setError(
          err instanceof ApiError
            ? err.message
            : "Could not reach the booking service. Try again.",
        );
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
      untrack();
      controller.abort();
    };
  }, [accountId, page, reloadKey, router]);

  useEffect(() => {
    if (!loading && session === null) {
      router.replace(loginHref);
    }
  }, [loading, session, router]);

  if (loading) {
    return <MyTripsSkeleton />;
  }

  if (error) {
    return (
      <div role="alert" className="rounded-[4px] border border-terracotta/40 bg-terracotta/10 p-6">
        <p className="font-medium text-terracotta">We could not load your orders.</p>
        <p className="mt-1 text-sm leading-6 text-muted">{error}</p>
        <button
          type="button"
          onClick={() => setReloadKey((key) => key + 1)}
          className="btn-secondary mt-4"
        >
          Try again
        </button>
      </div>
    );
  }

  if (totalElements === 0) {
    return (
      <EmptyState
        title="No orders yet."
        description="When you reserve seats on a departure, the booking will appear here with its travelers, status and order total."
        action={{ href: "/tours", label: "Browse journeys" }}
      />
    );
  }

  const outOfRange = bookings.length === 0 && totalElements > 0;
  const countLabel =
    totalElements === 1 ? "1 order in total" : `${totalElements} orders in total`;

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow text-forest">Newest first</p>
          <h2 className="mt-2 font-display text-3xl tracking-tight">{countLabel}</h2>
        </div>
        <Link href="/tours" className="btn-secondary">
          Find another journey
        </Link>
      </div>

      {outOfRange ? (
        <div className="mt-8">
          <EmptyState
            title="No orders on this page."
            description="This page is past the end of your order history."
            action={{ href: BASE_PATH, label: "First page" }}
          />
        </div>
      ) : (
        <div className="mt-8 space-y-5">
          {bookings.map((booking) => (
            <TripCard key={booking.id} booking={booking} basePath={BASE_PATH} />
          ))}
        </div>
      )}

      {totalPages > 1 ? (
        <nav
          aria-label="Order pages"
          className="mt-10 flex flex-wrap items-center justify-between gap-4 border-t border-line pt-6"
        >
          <p className="text-sm text-muted" aria-live="polite">
            Page {page + 1} of {totalPages}
          </p>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              disabled={page === 0}
              className="btn-secondary min-h-11 px-4 py-2 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <IconChevronLeft className="h-4 w-4" />
              Previous
            </button>
            <button
              type="button"
              onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
              disabled={page + 1 >= totalPages}
              className="btn-secondary min-h-11 px-4 py-2 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Next
              <IconChevronRight className="h-4 w-4" />
            </button>
          </div>
        </nav>
      ) : null}
    </div>
  );
}
