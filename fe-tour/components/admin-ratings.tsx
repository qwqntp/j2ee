"use client";

import { useEffect, useState } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import {
  AdminEmptyState,
  AdminListPanel,
  AdminPageHeading,
  AdminStatusBadge,
  AdminTableRegion,
} from "@/components/admin/admin-ui";
import { ApiError, deleteRating, listAdminRatings, moderateRating } from "@/lib/api";
import type { TourRating } from "@/lib/types";

export function AdminRatings() {
  const { getSession } = useAdminAccess();
  const [ratings, setRatings] = useState<TourRating[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const page = await listAdminRatings(session.accessToken, 0, 100);
        if (!active) return;
        setRatings(page.content);
        setError(null);
      } catch (err) {
        if (active) {
          setError(err instanceof ApiError ? err.message : "Could not load ratings. Try again.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [reloadKey, getSession]);

  async function change(id: number, action: "approve" | "reject" | "delete") {
    setBusyId(id);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      if (action === "delete") {
        await deleteRating(session.accessToken, id);
        setNotice("Rating deleted.");
      } else {
        await moderateRating(session.accessToken, id, {
          status: action === "approve" ? "APPROVED" : "REJECTED",
        });
        setNotice(action === "approve" ? "Rating approved." : "Rating rejected.");
      }
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the rating. Try again.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <AdminPageHeading
        title="Tour ratings"
        description="Approve reviews before they appear on public tour pages."
        action={<button type="button" onClick={() => setReloadKey((key) => key + 1)} className="btn-secondary min-h-0 px-4 py-2 text-xs">Refresh</button>}
      />

      {error ? <p role="alert" className="admin-notice mt-5 text-sm text-terracotta">{error}</p> : null}
      {notice ? <p role="status" className="admin-notice mt-5 text-sm text-forest">{notice}</p> : null}

      <AdminListPanel busy={loading} >
        {!loading && ratings.length === 0 ? (
          <AdminEmptyState title="No ratings yet" description="Ratings appear here after travellers submit them." />
        ) : (
          <AdminTableRegion label="Tour ratings">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-line text-muted">
                <tr>
                  <th scope="col" className="font-semibold">Tour</th>
                  <th scope="col" className="font-semibold">Author</th>
                  <th scope="col" className="font-semibold">Score</th>
                  <th scope="col" className="font-semibold">Comment</th>
                  <th scope="col" className="font-semibold">Status</th>
                  <th scope="col" className="font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {ratings.map((rating) => (
                  <tr key={rating.id}>
                    <td className="px-3 py-3">Tour {rating.tourId}</td>
                    <td className="px-3 py-3">{rating.accountUsername}</td>
                    <td className="px-3 py-3 tabular-nums">{rating.rating}/5</td>
                    <td className="max-w-xs px-3 py-3 text-muted">{rating.comment ?? "—"}</td>
                    <td className="px-3 py-3">
                      <AdminStatusBadge
                        tone={rating.status === "APPROVED" ? "positive"
                          : rating.status === "REJECTED" ? "danger" : "warning"}
                      >
                        {rating.status}
                      </AdminStatusBadge>
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex flex-wrap gap-2">
                        <button type="button" disabled={busyId === rating.id}
                          onClick={() => void change(rating.id, "approve")}
                          className="btn-secondary min-h-0 px-3 py-1 text-xs">Approve</button>
                        <button type="button" disabled={busyId === rating.id}
                          onClick={() => void change(rating.id, "reject")}
                          className="btn-secondary min-h-0 px-3 py-1 text-xs">Reject</button>
                        <button type="button" disabled={busyId === rating.id}
                          onClick={() => void change(rating.id, "delete")}
                          className="btn-secondary min-h-0 px-3 py-1 text-xs text-terracotta">Delete</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableRegion>
        )}
      </AdminListPanel>
    </div>
  );
}
