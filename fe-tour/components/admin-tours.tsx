"use client";

import { useEffect, useRef, useState } from "react";
import {
  ApiError,
  createAdminTour,
  listAdminTours,
  updateAdminTour,
  uploadImage,
} from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { TourDeparturesDialog } from "@/components/admin/tour-departures-dialog";
import { AdminFormDialog } from "@/components/admin/admin-form-dialog";
import { AdminEmptyState, AdminFormActions, AdminFormSection, AdminListPanel, AdminPageHeading, AdminPagination, AdminStatusBadge, AdminTableRegion } from "@/components/admin/admin-ui";
import { lastAvailablePage } from "@/lib/admin-pagination";
import { useAdminQueryState } from "@/lib/use-admin-query-state";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { AdminIcon } from "@/components/admin/admin-icons";
import { adminActionStyles } from "@/components/admin/admin-action-styles";
import type {
  AdminTour,
  TourInput,
} from "@/lib/types";

type TourForm = {
  name: string;
  description: string;
  durationDays: string;
  status: string;
  imageUrls: string;
  // Carried through unchanged so saving a tour does not wipe its relations.
  accommodationId: number | null;
  tourGuideId: number | null;
  promotionId: number | null;
  destinationIds: number[];
  vehicleIds: number[];
};

const TOUR_STATUSES = ["DRAFT", "OPEN", "CLOSED", "CANCELLED"];
const QUERY_SCHEMA = { page: { kind: "page" } } as const;

const EMPTY_TOUR: TourForm = {
  name: "",
  description: "",
  durationDays: "",
  status: "OPEN",
  imageUrls: "",
  accommodationId: null,
  tourGuideId: null,
  promotionId: null,
  destinationIds: [],
  vehicleIds: [],
};

export function AdminTours() {
  const { getSession } = useAdminAccess();
  const [tours, setTours] = useState<AdminTour[]>([]);
  const { params, update } = useAdminQueryState(QUERY_SCHEMA);
  const page = Number(params.get("page") ?? "1") - 1;
  const updateRef = useRef(update);
  useEffect(() => { updateRef.current = update; }, [update]);
  const [pagination, setPagination] = useState({ totalPages: 0, totalElements: 0, hasNext: false });
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [tourForm, setTourForm] = useState<TourForm | null>(null);
  const [editingTourId, setEditingTourId] = useState<number | null>(null);
  const [savingTour, setSavingTour] = useState(false);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [imageError, setImageError] = useState<string | null>(null);

  const [selectedTourId, setSelectedTourId] = useState<number | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const result = await listAdminTours(session.accessToken, page, 10);
        if (!active) return;
        const validPage = lastAvailablePage(page, result.totalPages);
        if (page !== validPage) { updateRef.current({ page: String(validPage + 1) }, "replace"); return; }
        setTours(result.content);
        setPagination(result);
        setError(null);
      } catch (err) {
        if (!active) return;
        setError(err instanceof ApiError ? err.message : "Could not load tours. Try again.");
      } finally {
        if (active) {
          setLoading(false);
          setLoaded(true);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [page, reloadKey, getSession]);

  const selectedTour = tours.find((tour) => tour.id === selectedTourId) ?? null;

  function startCreate() {
    setEditingTourId(null);
    setTourForm({ ...EMPTY_TOUR });
    setImageError(null);
    setNotice(null);
  }

  function startEdit(tour: AdminTour) {
    setEditingTourId(tour.id);
    setImageError(null);
    setTourForm({
      name: tour.name,
      description: tour.description ?? "",
      durationDays: tour.durationDays == null ? "" : String(tour.durationDays),
      status: tour.status,
      imageUrls: tour.imageUrls.join("\n"),
      accommodationId: tour.accommodationId,
      tourGuideId: tour.tourGuideId,
      promotionId: tour.promotionId,
      destinationIds: tour.destinationIds,
      vehicleIds: tour.vehicleIds,
    });
    setNotice(null);
  }

  async function saveTour(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tourForm) return;
    setSavingTour(true);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      const input: TourInput = {
        name: tourForm.name.trim(),
        description: tourForm.description.trim() || undefined,
        durationDays: tourForm.durationDays ? Number(tourForm.durationDays) : undefined,
        status: tourForm.status,
        imageUrls: tourForm.imageUrls
          .split("\n")
          .map((url) => url.trim())
          .filter(Boolean),
        accommodationId: tourForm.accommodationId ?? undefined,
        tourGuideId: tourForm.tourGuideId ?? undefined,
        promotionId: tourForm.promotionId ?? undefined,
        destinationIds: tourForm.destinationIds,
        vehicleIds: tourForm.vehicleIds,
      };
      if (editingTourId === null) {
        const created = await createAdminTour(session.accessToken, input);
        setNotice(`Tour "${created.name}" created.`);
        setSelectedTourId(created.id);
      } else {
        const updated = await updateAdminTour(session.accessToken, editingTourId, input);
        setNotice(`Tour "${updated.name}" saved.`);
      }
      setTourForm(null);
      setEditingTourId(null);
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the tour.");
    } finally {
      setSavingTour(false);
    }
  }

  async function uploadTourImage(file: File | undefined) {
    if (!file) return;
    setUploadingImage(true);
    setImageError(null);
    try {
      const session = await getSession();
      if (!session) return;
      const uploaded = await uploadImage(session.accessToken, file);
      setTourForm((current) => current === null ? current : {
        ...current,
        imageUrls: [current.imageUrls.trim(), uploaded.url].filter(Boolean).join("\n"),
      });
    } catch (err) {
      setImageError(err instanceof ApiError ? err.message : "Could not upload the image.");
    } finally {
      setUploadingImage(false);
    }
  }

  if (!loaded) {
    return <AdminTableSkeleton label="Loading tours…" action />;
  }

  return (
    <div>
      <AdminPageHeading title="Tours & departures"
        description={`Manage itineraries and departure schedules. ${pagination.totalElements} tours total.`}
        action={<button type="button" onClick={startCreate} className="btn-primary"><AdminIcon name="plus" className="size-4" /> New tour</button>}
      />

      {error && !tourForm ? (
        <p
          role="alert"
          className="admin-notice mt-5"
        >
          {error}
        </p>
      ) : null}
      {notice ? (
        <p role="status" className="admin-success mt-5">
          {notice}
        </p>
      ) : null}

      <AdminFormDialog open={tourForm !== null} title={editingTourId === null ? "New tour" : "Edit tour"}
        busy={savingTour} onDismiss={() => { setTourForm(null); setEditingTourId(null); setError(null); }}>
      {tourForm && (
        <form onSubmit={saveTour}>
          <div className="admin-form-grid">
            <AdminFormSection title="Tour information" description="Describe the itinerary. Required fields are marked with an asterisk.">
            <div className="space-y-4">
            <div>
              <label htmlFor="tour-name" className="label">
                Name
              </label>
              <input
                id="tour-name"
                required
                maxLength={255}
                className="field"
                value={tourForm.name}
                onChange={(event) =>
                  setTourForm({ ...tourForm, name: event.target.value })
                }
              />
            </div>
            <div>
              <label htmlFor="tour-description" className="label">
                Description
              </label>
              <textarea
                id="tour-description"
                rows={3}
                className="field"
                value={tourForm.description}
                onChange={(event) =>
                  setTourForm({ ...tourForm, description: event.target.value })
                }
              />
            </div>
            <div>
              <label htmlFor="tour-duration" className="label">
                Duration (days)
              </label>
              <input
                id="tour-duration"
                type="number"
                min={0}
                className="field"
                value={tourForm.durationDays}
                onChange={(event) =>
                  setTourForm({ ...tourForm, durationDays: event.target.value })
                }
              />
            </div>
            </div>
            </AdminFormSection>
            <AdminFormSection title="Availability and media">
            <div className="space-y-4">
            <div>
              <label htmlFor="tour-status" className="label">
                Status
              </label>
              <select
                id="tour-status"
                className="field"
                value={tourForm.status}
                onChange={(event) =>
                  setTourForm({ ...tourForm, status: event.target.value })
                }
              >
                {TOUR_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="tour-images" className="label">
                Image URLs (one per line)
              </label>
              <textarea
                id="tour-images"
                rows={3}
                className="field"
                value={tourForm.imageUrls}
                onChange={(event) =>
                  setTourForm({ ...tourForm, imageUrls: event.target.value })
                }
              />
              <label htmlFor="tour-image-upload" className="label mt-3">
                Upload image
              </label>
              <input
                id="tour-image-upload"
                type="file"
                accept="image/*"
                className="field"
                disabled={savingTour || uploadingImage}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  event.target.value = "";
                  void uploadTourImage(file);
                }}
              />
              {uploadingImage ? (
                <p role="status" className="mt-1 text-xs text-muted">Uploading…</p>
              ) : null}
              {imageError ? (
                <p role="alert" className="mt-1 text-xs text-terracotta">{imageError}</p>
              ) : null}
            </div>
            </div>
            </AdminFormSection>
          </div>
          {error && <p className="admin-notice mt-4" role="alert">{error}</p>}
          <AdminFormActions>
            <button type="submit" disabled={savingTour} className="btn-primary">
              {savingTour ? "Saving…" : "Save tour"}
            </button>
            <button
              type="button"
              disabled={savingTour}
              onClick={() => {
                setTourForm(null);
                setEditingTourId(null);
                setError(null);
              }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </AdminFormActions>
        </form>
      )}
      </AdminFormDialog>

      <AdminListPanel busy={loading}>
      {tours.length === 0 ? (
        !error && <AdminEmptyState title="No tours yet" description="Create a tour before adding departures." />
      ) : (
        <AdminTableRegion label="Tours">
          <table className="w-full min-w-[48rem] text-left text-sm">
            <thead className="border-b border-line bg-sage/10 text-[11px] uppercase tracking-[0.12em] text-muted">
              <tr>
                <th className="px-4 py-3 font-semibold">Name</th>
                <th className="px-4 py-3 font-semibold">Status</th>
                <th className="px-4 py-3 font-semibold">Duration</th>
                <th className="px-4 py-3 font-semibold">Departures</th>
                <th className="px-4 py-3 font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {tours.map((tour) => (
                <tr key={tour.id}>
                  <td className="px-4 py-4 font-medium text-charcoal">{tour.name}</td>
                  <td className="px-4 py-4"><AdminStatusBadge tone={tour.status === "OPEN" ? "positive" : tour.status === "CANCELLED" ? "danger" : "neutral"}>{tour.status}</AdminStatusBadge></td>
                  <td className="px-4 py-4 text-muted">
                    {tour.durationDays == null ? "—" : `${tour.durationDays} days`}
                  </td>
                  <td className="px-4 py-4 text-muted">{tour.departures.length}</td>
                  <td className="px-4 py-4">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => startEdit(tour)}
                        className={adminActionStyles.edit}
                        aria-label={`Edit ${tour.name}`} title="Edit"
                      >
                        <AdminIcon name="edit" className="size-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setSelectedTourId(tour.id);
                        }}
                        className="admin-row-action"
                      >
                        Departures
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </AdminTableRegion>
      )}
      <AdminPagination page={page} {...pagination} loading={loading} onPageChange={(next) => update({ page: String(next + 1) })} />
      </AdminListPanel>

      {selectedTour ? (
        <TourDeparturesDialog tour={selectedTour} loading={loading} loadError={error} onDismiss={() => setSelectedTourId(null)}
          onReload={() => setReloadKey((key) => key + 1)} />
      ) : null}
    </div>
  );
}



