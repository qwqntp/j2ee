"use client";

import { useState } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminConfirmDialog } from "@/components/admin/admin-confirm-dialog";
import { AdminFormDialog } from "@/components/admin/admin-form-dialog";
import { AdminIcon } from "@/components/admin/admin-icons";
import { AdminFormActions, AdminFormSection, AdminStatusBadge, AdminTableRegion } from "@/components/admin/admin-ui";
import { ApiError, createDeparture, deleteDeparture, updateDeparture } from "@/lib/api";
import { formatDateRange, formatPrice } from "@/lib/format";
import type { AdminTour, DepartureInput, TourDeparture } from "@/lib/types";

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

const DEPARTURE_STATUSES = ["OPEN", "FULL", "CLOSED", "CANCELLED"];

export function TourDeparturesDialog({ tour, loading, loadError, onDismiss, onReload }: {
  tour: AdminTour;
  loading: boolean;
  loadError: string | null;
  onDismiss: () => void;
  onReload: () => void;
}) {
  const { getSession } = useAdminAccess();
  const [form, setForm] = useState<DepartureForm | null>(null);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [toDelete, setToDelete] = useState<TourDeparture | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function startCreate() {
    setForm({ id: null, tourId: tour.id, startDate: "", endDate: "", capacity: "10", price: "", meetingPoint: "", status: "OPEN" });
  }

  function startEdit(departure: TourDeparture) {
    setForm({
      id: departure.id, tourId: tour.id, startDate: departure.startDate.slice(0, 16),
      endDate: departure.endDate.slice(0, 16), capacity: String(departure.capacity),
      price: String(departure.price), meetingPoint: departure.meetingPoint ?? "", status: departure.status,
    });
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      const input: DepartureInput = {
        tourId: form.tourId, startDate: form.startDate, endDate: form.endDate,
        capacity: Number(form.capacity), price: Number(form.price),
        meetingPoint: form.meetingPoint.trim() || undefined, status: form.status,
      };
      if (form.id === null) {
        await createDeparture(session.accessToken, input);
        setNotice("Departure created.");
      } else {
        await updateDeparture(session.accessToken, form.id, input);
        setNotice(form.status === "CANCELLED"
          ? "Departure cancelled. Live bookings were cancelled and refunds reserved."
          : "Departure saved.");
      }
      setForm(null);
      onReload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the departure.");
    } finally {
      setSaving(false);
    }
  }

  async function remove(departure: TourDeparture) {
    setDeletingId(departure.id);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      await deleteDeparture(session.accessToken, departure.id);
      setNotice("Departure deleted.");
      onReload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete the departure.");
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <>
      <AdminFormDialog open title={`Departures — ${tour.name}`} onDismiss={onDismiss} backdropClose lockScroll>
        <button type="button" onClick={startCreate} className="btn-secondary min-h-0 px-4 py-2 text-xs">
          New departure
        </button>
        {error && !form && <p className="admin-notice mt-4" role="alert">{error}</p>}
        {loadError && <p className="admin-notice mt-4" role="alert">{loadError}</p>}
        {notice && <p className="admin-success mt-4" role="status">{notice}</p>}
        {loading && <p className="mt-4 text-sm text-muted" role="status">Loading tours…</p>}
        {tour.departures.length === 0 ? (
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
                {tour.departures.map((departure) => (
                  <tr key={departure.id}>
                    <td className="px-3 py-3 text-muted">{formatDateRange(departure.startDate, departure.endDate)}</td>
                    <td className="px-3 py-3 text-muted">
                      {departure.bookedSlots} / {departure.capacity} ({departure.capacity - departure.bookedSlots} remaining)
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
                        <button type="button" onClick={() => startEdit(departure)} className="admin-row-action">
                          <AdminIcon name="edit" className="size-4" /> Edit
                        </button>
                        <button type="button" disabled={deletingId === departure.id} onClick={() => setToDelete(departure)} className="admin-danger-action">
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
      </AdminFormDialog>
      <AdminFormDialog open={form !== null} title={form?.id == null ? "New departure" : "Edit departure"}
        busy={saving} onDismiss={() => { setForm(null); setError(null); }}>
        {form && (
          <form onSubmit={save}>
            <div className="admin-form-grid">
              <AdminFormSection title="Schedule and capacity" description="Required fields are marked with an asterisk.">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label htmlFor="departure-start" className="label">Start</label>
                    <input id="departure-start" type="datetime-local" required className="field" value={form.startDate} onChange={(event) => setForm({ ...form, startDate: event.target.value })} />
                  </div>
                  <div>
                    <label htmlFor="departure-end" className="label">End</label>
                    <input id="departure-end" type="datetime-local" required className="field" value={form.endDate} onChange={(event) => setForm({ ...form, endDate: event.target.value })} />
                  </div>
                  <div>
                    <label htmlFor="departure-capacity" className="label">Capacity</label>
                    <input id="departure-capacity" type="number" min={1} required className="field" value={form.capacity} onChange={(event) => setForm({ ...form, capacity: event.target.value })} />
                  </div>
                  <div>
                    <label htmlFor="departure-price" className="label">Price per seat</label>
                    <input id="departure-price" type="number" min={0} step="0.01" required className="field" value={form.price} onChange={(event) => setForm({ ...form, price: event.target.value })} />
                  </div>
                  <div className="sm:col-span-2">
                    <label htmlFor="departure-meeting" className="label">Meeting point</label>
                    <input id="departure-meeting" maxLength={255} className="field" value={form.meetingPoint} onChange={(event) => setForm({ ...form, meetingPoint: event.target.value })} />
                  </div>
                </div>
              </AdminFormSection>
              <AdminFormSection title="Departure status">
                <div>
                  <label htmlFor="departure-status" className="label">Status</label>
                  <select id="departure-status" aria-describedby="departure-status-help" className="field" value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}>
                    {DEPARTURE_STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
                  </select>
                  <p id="departure-status-help" className="mt-2 text-xs leading-5 text-muted">
                    CANCELLED cancels the departure&apos;s live bookings and reserves refunds for successful payments.
                  </p>
                </div>
              </AdminFormSection>
            </div>
            {error && <p className="admin-notice mt-4" role="alert">{error}</p>}
            <AdminFormActions>
              <button type="submit" disabled={saving} className="btn-primary min-h-0 px-4 py-2 text-xs">{saving ? "Saving…" : "Save departure"}</button>
              <button type="button" disabled={saving} onClick={() => { setForm(null); setError(null); }} className="btn-secondary min-h-0 px-4 py-2 text-xs">Cancel</button>
            </AdminFormActions>
          </form>
        )}
      </AdminFormDialog>
      <AdminConfirmDialog
        title="Delete departure?"
        target={toDelete ? `${tour.name} · ${formatDateRange(toDelete.startDate, toDelete.endDate)}` : null}
        consequence="This removes the departure from the schedule. The system will reject deletion when the departure cannot be removed."
        confirmLabel="Delete departure"
        onDismiss={() => setToDelete(null)}
        onConfirm={() => { if (toDelete) void remove(toDelete); }}
      />
    </>
  );
}
