"use client";

import { useEffect, useState } from "react";
import { catalogInput, catalogTabs } from "@/lib/admin-catalog";
import {
  ApiError,
  createAccommodation,
  createDestination,
  createGuide,
  createImage,
  createVehicle,
  deleteAccommodation,
  deleteDestination,
  deleteGuide,
  deleteImage,
  deleteVehicle,
  listAccommodation,
  listAdminTours,
  listDestination,
  listGuide,
  listImage,
  listVehicle,
  updateAccommodation,
  updateDestination,
  updateGuide,
  updateImage,
  updateVehicle,
} from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminLoading, AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import type {
  AccommodationInput,
  AdminTour,
  CatalogInput,
  CatalogResource,
  CatalogRow,
  DestinationInput,
  GuideInput,
  ImageInput,
  VehicleInput,
} from "@/lib/types";

type Field = {
  name: string;
  label: string;
  kind: "text" | "number" | "textarea" | "select";
  options?: string[];
  fromTours?: boolean;
};

const FIELDS: Record<CatalogResource, Field[]> = {
  destinations: [
    { name: "name", label: "Name", kind: "text" },
    { name: "location", label: "Location", kind: "text" },
    { name: "description", label: "Description", kind: "textarea" },
    { name: "entryFee", label: "Entry fee", kind: "number" },
  ],
  accommodations: [
    { name: "name", label: "Name", kind: "text" },
    { name: "location", label: "Location", kind: "text" },
    { name: "rating", label: "Rating", kind: "number" },
    { name: "pricePerNight", label: "Price per night", kind: "number" },
    {
      name: "type",
      label: "Type",
      kind: "select",
      options: ["HOTEL", "RESORT", "HOMESTAY"],
    },
  ],
  vehicles: [
    { name: "type", label: "Type", kind: "text" },
    { name: "capacity", label: "Capacity", kind: "number" },
    { name: "rentalPricePerDay", label: "Rental price per day", kind: "number" },
  ],
  guides: [
    { name: "name", label: "Name", kind: "text" },
    { name: "email", label: "Email", kind: "text" },
    { name: "phone", label: "Phone", kind: "text" },
    { name: "experienceYears", label: "Experience (years)", kind: "number" },
  ],
  images: [
    { name: "imageUrl", label: "Image URL", kind: "text" },
    { name: "tourId", label: "Tour", kind: "select", fromTours: true },
  ],
};

const CONFLICT_MESSAGE = "This record is referenced elsewhere and cannot be deleted.";

function displayError(error: unknown, fallback: string, deletion = false): string {
  if (error instanceof ApiError) {
    return error.status === 409 && deletion
      ? `${error.message} ${CONFLICT_MESSAGE}`
      : error.message;
  }
  return fallback;
}

async function fetchRows(resource: CatalogResource, token: string): Promise<CatalogRow[]> {
  switch (resource) {
    case "destinations":
      return (await listDestination(token)).content;
    case "accommodations":
      return (await listAccommodation(token)).content;
    case "vehicles":
      return (await listVehicle(token)).content;
    case "guides":
      return (await listGuide(token)).content;
    case "images":
      return (await listImage(token)).content;
  }
}

async function saveRow(
  resource: CatalogResource,
  token: string,
  id: number | null,
  input: CatalogInput,
): Promise<void> {
  switch (resource) {
    case "destinations": {
      const value = input as DestinationInput;
      if (id === null) await createDestination(token, value);
      else await updateDestination(token, id, value);
      return;
    }
    case "accommodations": {
      const value = input as AccommodationInput;
      if (id === null) await createAccommodation(token, value);
      else await updateAccommodation(token, id, value);
      return;
    }
    case "vehicles": {
      const value = input as VehicleInput;
      if (id === null) await createVehicle(token, value);
      else await updateVehicle(token, id, value);
      return;
    }
    case "guides": {
      const value = input as GuideInput;
      if (id === null) await createGuide(token, value);
      else await updateGuide(token, id, value);
      return;
    }
    case "images": {
      const value = input as ImageInput;
      if (id === null) await createImage(token, value);
      else await updateImage(token, id, value);
      return;
    }
  }
}

async function removeRow(resource: CatalogResource, token: string, id: number): Promise<void> {
  switch (resource) {
    case "destinations":
      await deleteDestination(token, id);
      return;
    case "accommodations":
      await deleteAccommodation(token, id);
      return;
    case "vehicles":
      await deleteVehicle(token, id);
      return;
    case "guides":
      await deleteGuide(token, id);
      return;
    case "images":
      await deleteImage(token, id);
      return;
  }
}

export function AdminCatalog() {
  const { getSession } = useAdminAccess();
  const tabs = catalogTabs();
  const [resource, setResource] = useState<CatalogResource>("destinations");
  const [rows, setRows] = useState<CatalogRow[]>([]);
  const [tours, setTours] = useState<AdminTour[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [form, setForm] = useState<Record<string, string> | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

  const activeLabel = tabs.find((tab) => tab.resource === resource)?.label ?? resource;

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const [loadedRows, loadedTours] = await Promise.all([
          fetchRows(resource, session.accessToken),
          resource === "images"
            ? listAdminTours(session.accessToken, 0, 100).then((page) => page.content)
            : Promise.resolve<AdminTour[]>([]),
        ]);
        if (!active) return;
        setRows(loadedRows);
        setTours(loadedTours);
        setError(null);
      } catch (err) {
        if (!active) return;
        setError(displayError(err, "Could not load catalog rows."));
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [resource, reloadKey, getSession]);

  function selectTab(next: CatalogResource) {
    if (next === resource) return;
    setResource(next);
    setForm(null);
    setEditingId(null);
    setConfirmDeleteId(null);
    setError(null);
    setNotice(null);
  }

  function emptyForm(): Record<string, string> {
    const next: Record<string, string> = {};
    for (const field of FIELDS[resource]) {
      next[field.name] = field.kind === "select" && field.options?.length ? field.options[0] : "";
    }
    if (resource === "images" && tours.length > 0) next.tourId = String(tours[0].id);
    return next;
  }

  function startCreate() {
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
    setNotice(null);
  }

  function startEdit(row: CatalogRow) {
    const source = row as unknown as Record<string, unknown>;
    const next: Record<string, string> = {};
    for (const field of FIELDS[resource]) {
      const value = source[field.name];
      next[field.name] = value == null ? "" : String(value);
    }
    setEditingId(row.id);
    setForm(next);
    setError(null);
    setNotice(null);
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setError(null);
    setNotice(null);
    let input: CatalogInput;
    try {
      input = catalogInput(resource, form);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invalid input.");
      return;
    }
    setSaving(true);
    try {
      const session = await getSession();
      if (!session) return;
      await saveRow(resource, session.accessToken, editingId, input);
      setNotice("Saved.");
      setForm(null);
      setEditingId(null);
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(displayError(err, "Could not save the record."));
    } finally {
      setSaving(false);
    }
  }

  async function remove(row: CatalogRow) {
    if (confirmDeleteId !== row.id) {
      setConfirmDeleteId(row.id);
      return;
    }
    setConfirmDeleteId(null);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      await removeRow(resource, session.accessToken, row.id);
      setNotice("Deleted.");
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(displayError(err, "Could not delete the record.", true));
    }
  }

  return (
    <div>
      <AdminPageHeading title="Catalog" description={`Managing ${activeLabel.toLowerCase()}.`}
        action={<button type="button" onClick={startCreate} className="btn-primary">Add record</button>}
      />
      <div className="mt-5 flex flex-wrap gap-2" role="group" aria-label="Catalog sections">
        {tabs.map((tab) => (
          <button
            key={tab.resource}
            type="button"
            aria-pressed={resource === tab.resource}
            onClick={() => selectTab(tab.resource)}
            className={
              resource === tab.resource
                ? "btn-primary"
                : "btn-secondary"
            }
          >
            {tab.label}
          </button>
        ))}
      </div>

      {error ? (
        <p
          role="alert"
          className="admin-notice mt-5"
        >
          {error}
        </p>
      ) : null}
      {notice ? (
        <p
          role="status"
          className="admin-success mt-5"
        >
          {notice}
        </p>
      ) : null}

      {form ? (
        <form
          onSubmit={(event) => void save(event)}
          className="admin-panel mt-5 p-4 sm:p-5"
        >
          <h2 className="text-lg font-semibold tracking-tight">
            {editingId === null ? "Add record" : "Edit record"}
          </h2>
          <div className="mt-5 grid gap-5 sm:grid-cols-2">
            {FIELDS[resource].map((field) => (
              <div
                key={field.name}
                className={field.kind === "textarea" ? "sm:col-span-2" : undefined}
              >
                <label htmlFor={`catalog-${field.name}`} className="label">
                  {field.label}
                </label>
                {field.kind === "textarea" ? (
                  <textarea
                    id={`catalog-${field.name}`}
                    rows={3}
                    className="field"
                    value={form[field.name] ?? ""}
                    onChange={(event) =>
                      setForm({ ...form, [field.name]: event.target.value })
                    }
                  />
                ) : field.kind === "select" ? (
                  <select
                    id={`catalog-${field.name}`}
                    className="field"
                    value={form[field.name] ?? ""}
                    onChange={(event) =>
                      setForm({ ...form, [field.name]: event.target.value })
                    }
                  >
                    {field.fromTours ? (
                      <>
                        <option value="">Select a tour</option>
                        {tours.map((tour) => (
                          <option key={tour.id} value={String(tour.id)}>
                            {tour.name}
                          </option>
                        ))}
                      </>
                    ) : (
                      <>
                        <option value="">Select…</option>
                        {field.options?.map((option) => (
                          <option key={option} value={option}>
                            {option}
                          </option>
                        ))}
                      </>
                    )}
                  </select>
                ) : (
                  <input
                    id={`catalog-${field.name}`}
                    type={field.kind === "number" ? "number" : "text"}
                    className="field"
                    value={form[field.name] ?? ""}
                    onChange={(event) =>
                      setForm({ ...form, [field.name]: event.target.value })
                    }
                  />
                )}
                {field.fromTours ? (
                  <p className="mt-1 text-xs text-muted">
                    Dropdown shows the first 100 tours.
                  </p>
                ) : null}
              </div>
            ))}
          </div>
          <div className="mt-5 flex flex-wrap gap-3">
            <button type="submit" disabled={saving} className="btn-primary">
              {saving ? "Saving…" : "Save"}
            </button>
            <button
              type="button"
              onClick={() => {
                setForm(null);
                setEditingId(null);
              }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </form>
      ) : null}

      {loading ? (
        <div className="mt-5"><AdminLoading>Loading {activeLabel.toLowerCase()}…</AdminLoading></div>
      ) : rows.length === 0 ? (
        <div className="admin-notice mt-5">
          No {resource} yet.
        </div>
      ) : (
        <AdminTableRegion label={activeLabel}>
          <table className="w-full min-w-[48rem] text-left text-sm">
            <thead className="border-b border-line bg-sage/10 text-[11px] uppercase tracking-[0.12em] text-muted">
              <tr>
                {FIELDS[resource].map((field) => (
                  <th key={field.name} className="px-4 py-3 font-semibold">
                    {field.label}
                  </th>
                ))}
                <th className="px-4 py-3 font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((row) => (
                <tr key={row.id}>
                  {FIELDS[resource].map((field) => {
                    const value = (row as unknown as Record<string, unknown>)[field.name];
                    let display = value == null ? "—" : String(value);
                    if (field.fromTours && value != null) {
                      const tour = tours.find((item) => item.id === Number(value));
                      if (tour) display = tour.name;
                    }
                    return (
                      <td key={field.name} className="max-w-xs px-4 py-4 break-words text-muted">
                        {display}
                      </td>
                    );
                  })}
                  <td className="px-4 py-4">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => startEdit(row)}
                        className="admin-row-action"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => void remove(row)}
                         className={`admin-danger-action ${confirmDeleteId === row.id ? "bg-red-50" : ""}`}
                      >
                        {confirmDeleteId === row.id ? "Click again to delete" : "Delete"}
                       </button>
                       {confirmDeleteId === row.id && (
                         <button type="button" className="admin-row-action" onClick={() => setConfirmDeleteId(null)}>
                           Cancel
                         </button>
                       )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </AdminTableRegion>
      )}
    </div>
  );
}
