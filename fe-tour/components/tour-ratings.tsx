"use client";

import { useEffect, useState } from "react";
import { ensureSession } from "@/lib/auth";
import { ApiError, createTourRating, getTourRatings } from "@/lib/api";
import { formatDate } from "@/lib/format";
import type { TourRatingPage } from "@/lib/types";

type Props = {
  tourId: number;
  bookingId?: number;
};

export function TourRatings({ tourId, bookingId }: Props) {
  const [page, setPage] = useState<TourRatingPage | null>(null);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getTourRatings(tourId)
      .then((result) => { if (active) setPage(result); })
      .catch(() => { if (active) setPage(null); });
    return () => { active = false; };
  }, [tourId]);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (bookingId == null || busy) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const session = await ensureSession();
      if (!session) {
        setError("Sign in to rate this journey.");
        return;
      }
      await createTourRating(session.accessToken, tourId, {
        bookingId,
        rating,
        comment: comment.trim() || null,
      });
      setComment("");
      setNotice("Thanks — your rating is waiting for review.");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setNotice("You have already rated this booking. It is waiting for review.");
      } else {
        setError(err instanceof ApiError ? err.message : "Could not submit your rating. Try again.");
      }
    } finally {
      setBusy(false);
    }
  }

  const ratings = page?.content ?? [];

  return (
    <section className="rounded-[4px] border border-line bg-white p-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="eyebrow text-forest">Traveller reviews</p>
          <h2 className="mt-2 font-display text-2xl tracking-tight">
            {page && page.ratingCount > 0
              ? `${page.averageRating.toFixed(2)} out of 5`
              : "No reviews yet"}
          </h2>
        </div>
        {page && page.ratingCount > 0 ? (
          <p className="text-sm text-muted">
            {page.ratingCount} {page.ratingCount === 1 ? "review" : "reviews"}
          </p>
        ) : null}
      </div>

      {ratings.length ? (
        <ul className="mt-5 divide-y divide-line border-t border-line">
          {ratings.map((ratingItem) => (
            <li key={ratingItem.id} className="py-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium">{ratingItem.accountUsername}</span>
                <span aria-label={`${ratingItem.rating} out of 5 stars`} className="text-terracotta">
                  {"★".repeat(ratingItem.rating)}{"☆".repeat(5 - ratingItem.rating)}
                </span>
              </div>
              <p className="mt-1 text-xs text-muted">{formatDate(ratingItem.createdAt)}</p>
              {ratingItem.comment ? (
                <p className="mt-2 whitespace-pre-line text-sm leading-7 text-muted">
                  {ratingItem.comment}
                </p>
              ) : null}
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-4 text-sm leading-6 text-muted">
          Reviews appear here once approved.
        </p>
      )}

      {bookingId != null ? (
        <form onSubmit={submit} className="mt-6 border-t border-line pt-5">
          <p className="eyebrow text-forest">Rate this journey</p>
          <label htmlFor={`tour-rating-${tourId}`} className="mt-3 block text-sm text-muted">
            Score
          </label>
          <select
            id={`tour-rating-${tourId}`}
            value={rating}
            onChange={(event) => setRating(Number(event.target.value))}
            className="mt-1 min-h-11 w-full rounded-[3px] border border-line bg-white px-3 text-sm"
          >
            {[5, 4, 3, 2, 1].map((value) => (
              <option key={value} value={value}>{value} stars</option>
            ))}
          </select>
          <label htmlFor={`tour-comment-${tourId}`} className="mt-3 block text-sm text-muted">
            Comment (optional)
          </label>
          <textarea
            id={`tour-comment-${tourId}`}
            value={comment}
            maxLength={2000}
            onChange={(event) => setComment(event.target.value)}
            rows={3}
            className="mt-1 w-full rounded-[3px] border border-line bg-white px-3 py-2 text-sm"
          />
          {error ? <p role="alert" className="mt-3 text-sm text-terracotta">{error}</p> : null}
          {notice ? <p role="status" className="mt-3 text-sm text-forest">{notice}</p> : null}
          <button type="submit" disabled={busy} className="btn-primary mt-4">
            {busy ? "Submitting…" : "Submit rating"}
          </button>
        </form>
      ) : null}
    </section>
  );
}
