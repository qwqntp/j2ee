"use client";

import { useEffect, useState } from "react";
import {
  ApiError,
  createAdminTour,
  createDeparture,
  deleteDeparture,
  listAdminTours,
  updateAdminTour,
  updateDeparture,
} from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminConfirmDialog } from "@/components/admin/admin-confirm-dialog";
import { AdminFormDialog } from "@/components/admin/admin-form-dialog";
import { AdminEmptyState, AdminFormActions, AdminFormSection, AdminListPanel, AdminPageHeading, AdminStatusBadge, AdminTableRegion } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { AdminIcon } from "@/components/admin/admin-icons";
import { formatDateRange, formatPrice } from "@/lib/format";
import type {
  AdminTour,
  DepartureInput,
  TourDeparture,
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

type DepartureForm = {
  id: number | null;
  tourId: number;
  startDate: string;
  endDate: string;
  capacity: string;
  price: string;
  meetingPoint: string;
  status: string;
};

const TOUR_STATUSES = ["DRAFT", "OPEN", "CLOSED", "CANCELLED"];
const DEPARTURE_STATUSES = ["OPEN", "FULL", "CLOSED", "CANCELLED"];

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
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [tourForm, setTourForm] = useState<TourForm | null>(null);
  const [editingTourId, setEditingTourId] = useState<number | null>(null);
  const [savingTour, setSavingTour] = useState(false);

  const [selectedTourId, setSelectedTourId] = useState<number | null>(null);
  const [departureForm, setDepartureForm] = useState<DepartureForm | null>(null);
  const [savingDeparture, setSavingDeparture] = useState(false);
  const [deletingDepartureId, setDeletingDepartureId] = useState<number | null>(null);
  const [departureToDelete, setDepartureToDelete] = useState<TourDeparture | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const result = await listAdminTours(session.accessToken, 0, 100);
        if (!active) return;
        setTours(result.content);
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
  }, [reloadKey, getSession]);

  const selectedTour = tours.find((tour) => tour.id === selectedTourId) ?? null;

  function startCreate() {
    setEditingTourId(null);
    setTourForm({ ...EMPTY_TOUR });
    setNotice(null);
  }

  function startEdit(tour: AdminTour) {
    setEditingTourId(tour.id);
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

  function startCreateDeparture(tour: AdminTour) {
    setDepartureForm({
      id: null,
      tourId: tour.id,
      startDate: "",
      endDate: "",
      capacity: "10",
      price: "",
      meetingPoint: "",
      status: "OPEN",
    });
  }

  function startEditDeparture(tour: AdminTour, departure: TourDeparture) {
    setDepartureForm({
      id: departure.id,
      tourId: tour.id,
      startDate: departure.startDate.slice(0, 16),
      endDate: departure.endDate.slice(0, 16),
      capacity: String(departure.capacity),
      price: String(departure.price),
      meetingPoint: departure.meetingPoint ?? "",
      status: departure.status,
    });
  }

  async function saveDeparture(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!departureForm) return;
    setSavingDeparture(true);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      const input: DepartureInput = {
        tourId: departureForm.tourId,
        startDate: departureForm.startDate,
        endDate: departureForm.endDate,
        capacity: Number(departureForm.capacity),
        price: Number(departureForm.price),
        meetingPoint: departureForm.meetingPoint.trim() || undefined,
        status: departureForm.status,
      };
      if (departureForm.id === null) {
        await createDeparture(session.accessToken, input);
        setNotice("Departure created.");
      } else {
        await updateDeparture(session.accessToken, departureForm.id, input);
        setNotice(
          departureForm.status === "CANCELLED"
            ? "Departure cancelled. Live bookings were cancelled and refunds reserved."
            : "Departure saved.",
        );
      }
      setDepartureForm(null);
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the departure.");
    } finally {
      setSavingDeparture(false);
    }
  }

  async function removeDeparture(departure: TourDeparture) {
    setDeletingDepartureId(departure.id);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      await deleteDeparture(session.accessToken, departure.id);
      setNotice("Departure deleted.");
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete the departure.");
    } finally {
      setDeletingDepartureId(null);
    }
  }

  if (!loaded) {
    return <AdminTableSkeleton label="Loading tours…" action />;
  }

  return (
    <div>
      <AdminPageHeading title="Tours & departures"
        description={`Manage itineraries and departure schedules. ${tours.length} ${tours.length === 1 ? "tour" : "tours"} loaded.`}
        action={<button type="button" onClick={startCreate} className="btn-primary"><AdminIcon name="plus" className="size-4" /> New tour</button>}
      />

      {error && !tourForm && !departureForm ? (
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
                        className="admin-row-action"
                      >
                        <AdminIcon name="edit" className="size-4" /> Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setSelectedTourId(tour.id);
                          setDepartureForm(null);
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
      </AdminListPanel>

      {selectedTour ? (
        <div className="admin-panel mt-5 p-4 sm:p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-lg font-semibold tracking-tight">
              Departures — {selectedTour.name}
            </h2>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => startCreateDeparture(selectedTour)}
                className="btn-secondary min-h-0 px-4 py-2 text-xs"
              >
                New departure
              </button>
              <button
                type="button"
                onClick={() => {
                  setSelectedTourId(null);
                  setDepartureForm(null);
                }}
                className="btn-secondary min-h-0 px-4 py-2 text-xs"
              >
                Close
              </button>
            </div>
          </div>

          <AdminFormDialog open={departureForm !== null} title={departureForm?.id == null ? "New departure" : "Edit departure"}
            busy={savingDeparture} onDismiss={() => { setDepartureForm(null); setError(null); }}>
          {departureForm && (
            <form onSubmit={saveDeparture}>
               <div className="admin-form-grid">
               <AdminFormSection title="Schedule and capacity" description="Required fields are marked with an asterisk.">
               <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label htmlFor="departure-start" className="label">
                    Start
                  </label>
                  <input
                    id="departure-start"
                    type="datetime-local"
                    required
                    className="field"
                    value={departureForm.startDate}
                    onChange={(event) =>
                      setDepartureForm({
                        ...departureForm,
                        startDate: event.target.value,
                      })
                    }
                  />
                </div>
                <div>
                  <label htmlFor="departure-end" className="label">
                    End
                  </label>
                  <input
                    id="departure-end"
                    type="datetime-local"
                    required
                    className="field"
                    value={departureForm.endDate}
                    onChange={(event) =>
                      setDepartureForm({
                        ...departureForm,
                        endDate: event.target.value,
                      })
                    }
                  />
                </div>
                <div>
                  <label htmlFor="departure-capacity" className="label">
                    Capacity
                  </label>
                  <input
                    id="departure-capacity"
                    type="number"
                    min={1}
                    required
                    className="field"
                    value={departureForm.capacity}
                    onChange={(event) =>
                      setDepartureForm({
                        ...departureForm,
                        capacity: event.target.value,
                      })
                    }
                  />
                </div>
                <div>
                  <label htmlFor="departure-price" className="label">
                    Price per seat
                  </label>
                  <input
                    id="departure-price"
                    type="number"
                    min={0}
                    step="0.01"
                    required
                    className="field"
                    value={departureForm.price}
                    onChange={(event) =>
                      setDepartureForm({
                        ...departureForm,
                        price: event.target.value,
                      })
                    }
                  />
                </div>
                 <div className="sm:col-span-2">
                   <label htmlFor="departure-meeting" className="label">
                    Meeting point
                  </label>
                  <input
                    id="departure-meeting"
                    maxLength={255}
                    className="field"
                    value={departureForm.meetingPoint}
                    onChange={(event) =>
                      setDepartureForm({
                        ...departureForm,
                        meetingPoint: event.target.value,
                      })
                    }
                  />
                 </div>
               </div>
               </AdminFormSection>
               <AdminFormSection title="Departure status">
                 <div>
                  <label htmlFor="departure-status" className="label">
                    Status
                  </label>
                   <select
                     id="departure-status"
                     aria-describedby="departure-status-help"
                    className="field"
                    value={departureForm.status}
                    onChange={(event) =>
                      setDepartureForm({
                        ...departureForm,
                        status: event.target.value,
                      })
                    }
                  >
                    {DEPARTURE_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {status}
                      </option>
                    ))}
                  </select>
                   <p id="departure-status-help" className="mt-2 text-xs leading-5 text-muted">
                    CANCELLED cancels the departure&apos;s live bookings and reserves refunds
                    for successful payments.
                  </p>
                 </div>
               </AdminFormSection>
               </div>
               {error && <p className="admin-notice mt-4" role="alert">{error}</p>}
               <AdminFormActions>
                <button
                  type="submit"
                  disabled={savingDeparture}
                  className="btn-primary min-h-0 px-4 py-2 text-xs"
                >
                  {savingDeparture ? "Saving…" : "Save departure"}
                </button>
                 <button
                   type="button"
                   disabled={savingDeparture}
                   onClick={() => { setDepartureForm(null); setError(null); }}
                  className="btn-secondary min-h-0 px-4 py-2 text-xs"
                >
                  Cancel
                </button>
               </AdminFormActions>
             </form>
           )}
           </AdminFormDialog>

          {selectedTour.departures.length === 0 ? (
            <p className="mt-5 text-sm text-muted">No departures for this tour yet.</p>
          ) : (
            <AdminTableRegion label="Departures">
              <table className="w-full min-w-[44rem] text-left text-sm">
                <thead className="border-b border-line text-[11px] uppercase tracking-[0.12em] text-muted">
                  <tr>
                    <th className="px-3 py-2 font-semibold">Dates</th>
                    <th className="px-3 py-2 font-semibold">Seats</th>
                    <th className="px-3 py-2 font-semibold">Price</th>
                    <th className="px-3 py-2 font-semibold">Status</th>
                    <th className="px-3 py-2 font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {selectedTour.departures.map((departure) => (
                    <tr key={departure.id}>
                      <td className="px-3 py-3 text-muted">
                        {formatDateRange(departure.startDate, departure.endDate)}
                      </td>
                      <td className="px-3 py-3 text-muted">
                        {departure.bookedSlots} / {departure.capacity}
                      </td>
                      <td className="px-3 py-3 text-muted">{formatPrice(departure.price)}</td>
                         <td className="px-3 py-3">
                           <AdminStatusBadge tone={departure.status === "CANCELLED" ? "danger" : departure.status === "OPEN" ? "positive" : "neutral"}>
                             {departure.status}
                           </AdminStatusBadge>
                         </td>
                      <td className="px-3 py-3">
                        <div className="flex flex-wrap gap-2">
                          <a className="admin-row-action" href={`/admin/departures/${departure.id}/passengers`}>Passengers</a>
                          <button
                            type="button"
                            onClick={() => startEditDeparture(selectedTour, departure)}
                            className="admin-row-action"
                          >
                            <AdminIcon name="edit" className="size-4" /> Edit
                          </button>
                          <button
                            type="button"
                            disabled={deletingDepartureId === departure.id}
                            onClick={() => setDepartureToDelete(departure)}
                            className="admin-danger-action"
                          >
                            <AdminIcon name="delete" className="size-4" /> Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableRegion>
          )}
        </div>
      ) : null}
      <AdminConfirmDialog
        title="Delete departure?"
        target={departureToDelete ? `${selectedTour?.name ?? "Departure"} · ${formatDateRange(departureToDelete.startDate, departureToDelete.endDate)}` : null}
        consequence="This removes the departure from the schedule. The system will reject deletion when the departure cannot be removed."
        confirmLabel="Delete departure"
        onDismiss={() => setDepartureToDelete(null)}
        onConfirm={() => { if (departureToDelete) void removeDeparture(departureToDelete); }}
      />
    </div>
  );
}
