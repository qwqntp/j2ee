"use client";

import { useEffect, useRef, useState } from "react";
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
import { AdminEmptyState, AdminFormActions, AdminFormSection, AdminListPanel, AdminPageHeading, AdminPagination, AdminTableRegion } from "@/components/admin/admin-ui";
import { lastAvailablePage } from "@/lib/admin-pagination";
import { useAdminQueryState } from "@/lib/use-admin-query-state";
import { AdminFormSkeleton, AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { AdminConfirmDialog } from "@/components/admin/admin-confirm-dialog";
import { AdminFormDialog } from "@/components/admin/admin-form-dialog";
import { AdminIcon } from "@/components/admin/admin-icons";
import { adminActionStyles } from "@/components/admin/admin-action-styles";
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
  PageData,
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

async function fetchRows(resource: CatalogResource, token: string, page: number): Promise<PageData<CatalogRow>> {
  switch (resource) {
    case "destinations":
      return listDestination(token, page, 10);
    case "accommodations":
      return listAccommodation(token, page, 10);
    case "vehicles":
      return listVehicle(token, page, 10);
    case "guides":
      return listGuide(token, page, 10);
    case "images":
      return listImage(token, page, 10);
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

const QUERY_SCHEMA = {
  page: { kind: "page" },
  tab: { kind: "enum", allowed: ["destinations", "accommodations", "vehicles", "guides", "images"], defaultValue: "destinations" },
} as const;

export function AdminCatalog() {
  const { getSession } = useAdminAccess();
  const tabs = catalogTabs();
  const { params, update } = useAdminQueryState(QUERY_SCHEMA);
  const resource = (params.get("tab") ?? "destinations") as CatalogResource;
  const page = Number(params.get("page") ?? "1") - 1;
  const updateRef = useRef(update);
  useEffect(() => { updateRef.current = update; }, [update]);
  const [rows, setRows] = useState<CatalogRow[]>([]);
  const [pagination, setPagination] = useState({ totalPages: 0, totalElements: 0, hasNext: false });
  const [tours, setTours] = useState<AdminTour[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadedResource, setLoadedResource] = useState<CatalogResource | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [form, setForm] = useState<Record<string, string> | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [rowToDelete, setRowToDelete] = useState<CatalogRow | null>(null);
  const [deleting, setDeleting] = useState(false);

  const activeLabel = tabs.find((tab) => tab.resource === resource)?.label ?? resource;
  const initialLoading = loadedResource !== resource;
  const fieldGroups = resource === "images"
    ? [
        { title: "Image details", fields: FIELDS.images.filter((field) => !field.fromTours) },
        { title: "Related tour", fields: FIELDS.images.filter((field) => field.fromTours) },
      ]
    : [{ title: activeLabel, fields: FIELDS[resource] }];

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const [loadedRows, loadedTours] = await Promise.all([
          fetchRows(resource, session.accessToken, page),
          resource === "images"
            ? listAdminTours(session.accessToken, 0, 100).then((page) => page.content)
            : Promise.resolve<AdminTour[]>([]),
        ]);
        if (!active) return;
        const validPage = lastAvailablePage(page, loadedRows.totalPages);
        if (page !== validPage) { updateRef.current({ page: String(validPage + 1) }, "replace"); return; }
        setRows(loadedRows.content);
        setPagination(loadedRows);
        setTours(loadedTours);
        setError(null);
      } catch (err) {
        if (!active) return;
        setError(displayError(err, "Could not load catalog rows."));
      } finally {
        if (active) {
          setLoading(false);
          setLoadedResource(resource);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [resource, page, reloadKey, getSession]);

  function selectTab(next: CatalogResource) {
    if (next === resource) return;
    update({ tab: next, page: null });
    setLoadedResource(null);
    setLoading(true);
    setRows([]);
    setTours([]);
    setForm(null);
    setEditingId(null);
    setRowToDelete(null);
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
    setDeleting(true);
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
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div>
      <AdminPageHeading title="Catalog" description={`Managing ${activeLabel.toLowerCase()}.`}
        action={<button type="button" onClick={startCreate} className="btn-primary"><AdminIcon name="plus" className="size-4" /> Add record</button>}
      />

      {error && !form ? (
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

      <AdminFormDialog open={form !== null} title={editingId === null ? "Add record" : "Edit record"}
        busy={saving} narrow={resource !== "images"}
        onDismiss={() => { setForm(null); setEditingId(null); setError(null); }}>
      {form && resource === "images" && initialLoading ? <AdminFormSkeleton label="Loading tour options…" /> : form ? (
        <form onSubmit={(event) => void save(event)}>
          <div className="admin-form-grid">
          {fieldGroups.map(({ title, fields }) => (
          <AdminFormSection key={title} title={title}>
          <div className="grid gap-4 sm:grid-cols-2">
            {fields.map((field) => (
              <div
                key={field.name}
                className={fields.length === 1 || field.kind === "textarea" ? "sm:col-span-2" : undefined}
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
          </AdminFormSection>
          ))}
          </div>
          {error && <p className="admin-notice mt-4" role="alert">{error}</p>}
          <AdminFormActions>
            <button type="submit" disabled={saving} className="btn-primary">
              {saving ? "Saving…" : "Save"}
            </button>
            <button
              type="button"
              disabled={saving}
              onClick={() => {
                setForm(null);
                setEditingId(null);
                setError(null);
              }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </AdminFormActions>
        </form>
      ) : null}
      </AdminFormDialog>

      <AdminListPanel busy={loading && !initialLoading}>
      <div className="admin-filter-bar flex flex-wrap gap-2" role="group" aria-label="Catalog sections">
        {tabs.map((tab) => (
          <button key={tab.resource} type="button" aria-pressed={resource === tab.resource}
            onClick={() => selectTab(tab.resource)} className={resource === tab.resource ? "btn-primary" : "btn-secondary"}>
            {tab.label}
          </button>
        ))}
      </div>
      {initialLoading ? (
        <AdminTableSkeleton label={`Loading ${activeLabel.toLowerCase()}…`} embedded />
      ) : rows.length === 0 ? (
        !error && <AdminEmptyState title={`No ${activeLabel.toLowerCase()} yet`} description="Use Add record to create an entry." />
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
                        className={adminActionStyles.edit}
                        aria-label={`Edit record ${row.id}`} title="Edit"
                      >
                         <AdminIcon name="edit" className="size-4" />
                      </button>
                      <button
                        type="button"
                         disabled={deleting}
                         onClick={() => setRowToDelete(row)}
                         className={adminActionStyles.delete}
                         aria-label={`Delete record ${row.id}`} title="Delete"
                      >
                         <AdminIcon name="delete" className="size-4" />
                       </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </AdminTableRegion>
      )}
      {!initialLoading && <AdminPagination page={page} {...pagination} loading={loading} onPageChange={(next) => update({ page: String(next + 1) })} />}
      </AdminListPanel>
      <AdminConfirmDialog title="Delete catalog record?"
        target={rowToDelete ? ("name" in rowToDelete ? rowToDelete.name : `${activeLabel} #${rowToDelete.id}`) : null}
        consequence="Referenced records may be protected from deletion. If deletion succeeds, it cannot be undone here."
        confirmLabel="Delete record" onDismiss={() => setRowToDelete(null)}
        onConfirm={() => { if (rowToDelete) void remove(rowToDelete); }}
      />
    </div>
  );
}
