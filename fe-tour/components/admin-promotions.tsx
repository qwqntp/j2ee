"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ApiError,
  createPromotion,
  deletePromotion,
  listPromotions,
  updatePromotion,
} from "@/lib/api";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminLoading, AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import { formatPrice } from "@/lib/format";
import {
  filterPromotions,
  promotionLifecycle,
  type PromotionFilter,
} from "@/lib/promotions";
import type { Promotion, PromotionInput } from "@/lib/types";

type PromotionForm = {
  name: string;
  code: string;
  discountPercentage: string;
  startDate: string;
  endDate: string;
  active: boolean;
  usageLimit: string;
  minOrderAmount: string;
  description: string;
};

const EMPTY_FORM: PromotionForm = {
  name: "",
  code: "",
  discountPercentage: "",
  startDate: "",
  endDate: "",
  active: true,
  usageLimit: "",
  minOrderAmount: "",
  description: "",
};

const LIFECYCLES: PromotionFilter["lifecycle"][] = [
  "All",
  "Active",
  "Scheduled",
  "Expired",
  "Inactive",
];

function draftFrom(promotion: Promotion): PromotionForm {
  return {
    name: promotion.name,
    code: promotion.code ?? "",
    discountPercentage: String(promotion.discountPercentage),
    startDate: promotion.startDate.slice(0, 10),
    endDate: promotion.endDate.slice(0, 10),
    active: promotion.active === true,
    usageLimit: promotion.usageLimit == null ? "" : String(promotion.usageLimit),
    minOrderAmount: promotion.minOrderAmount == null ? "" : String(promotion.minOrderAmount),
    description: promotion.description ?? "",
  };
}

// PromotionInput has no id or usedCount; the backend owns both.
function inputFromForm(form: PromotionForm): PromotionInput {
  return {
    name: form.name.trim(),
    code: form.code.trim() || null,
    discountPercentage: Number(form.discountPercentage),
    startDate: form.startDate,
    endDate: form.endDate,
    description: form.description.trim() || null,
    active: form.active,
    usageLimit: form.usageLimit === "" ? null : Number(form.usageLimit),
    minOrderAmount: form.minOrderAmount === "" ? null : Number(form.minOrderAmount),
  };
}

function inputWithActive(promotion: Promotion, active: boolean): PromotionInput {
  return {
    name: promotion.name,
    code: promotion.code,
    discountPercentage: promotion.discountPercentage,
    startDate: promotion.startDate.slice(0, 10),
    endDate: promotion.endDate.slice(0, 10),
    description: promotion.description,
    active,
    usageLimit: promotion.usageLimit,
    minOrderAmount: promotion.minOrderAmount,
  };
}

export function AdminPromotions() {
  const { getSession } = useAdminAccess();
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [filter, setFilter] = useState<PromotionFilter>({ lifecycle: "All", query: "" });
  const [form, setForm] = useState<PromotionForm | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<number | null>(null);
  const [busyDeleteId, setBusyDeleteId] = useState<number | null>(null);
  const [togglingId, setTogglingId] = useState<number | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const page = await listPromotions(session.accessToken, 0, 100);
        if (!active) return;
        setPromotions(page.content);
        setError(null);
      } catch (err) {
        if (!active) return;
        setError(err instanceof ApiError ? err.message : "Could not load promotions. Try again.");
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [reloadKey, getSession]);

  const visiblePromotions = useMemo(
    () => filterPromotions(promotions, filter),
    [promotions, filter],
  );

  function startCreate() {
    setEditingId(null);
    setForm({ ...EMPTY_FORM });
    setNotice(null);
    setError(null);
  }

  function startEdit(promotion: Promotion) {
    setEditingId(promotion.id);
    setForm(draftFrom(promotion));
    setNotice(null);
    setError(null);
  }

  async function savePromotion(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      const input = inputFromForm(form);
      if (editingId === null) {
        await createPromotion(session.accessToken, input);
      } else {
        await updatePromotion(session.accessToken, editingId, input);
      }
      setNotice("Promotion saved.");
      setForm(null);
      setEditingId(null);
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the promotion.");
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive(promotion: Promotion) {
    setTogglingId(promotion.id);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      await updatePromotion(
        session.accessToken,
        promotion.id,
        inputWithActive(promotion, promotion.active !== true),
      );
      setNotice("Promotion saved.");
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the promotion.");
    } finally {
      setTogglingId(null);
    }
  }

  function clickDelete(promotion: Promotion) {
    setError(null);
    setNotice(null);
    if (confirmingDeleteId === promotion.id) {
      void removePromotion(promotion);
      return;
    }
    setConfirmingDeleteId(promotion.id);
  }

  async function removePromotion(promotion: Promotion) {
    setBusyDeleteId(promotion.id);
    try {
      const session = await getSession();
      if (!session) return;
      await deletePromotion(session.accessToken, promotion.id);
      setNotice("Promotion deleted.");
      setConfirmingDeleteId(null);
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete the promotion.");
      setConfirmingDeleteId(null);
    } finally {
      setBusyDeleteId(null);
    }
  }

  if (loading && promotions.length === 0) {
    return <AdminLoading>Loading promotions…</AdminLoading>;
  }

  return (
    <div>
      <AdminPageHeading title="Promotions"
        description={`${promotions.length} loaded. Search and filters apply to the first 100 promotions.`}
        action={<button type="button" onClick={startCreate} className="btn-primary">New promotion</button>}
      />

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
          onSubmit={savePromotion}
          className="admin-panel mt-5 p-4 sm:p-5"
        >
          <h2 className="text-lg font-semibold tracking-tight">
            {editingId === null ? "New promotion" : "Edit promotion"}
          </h2>
          <div className="mt-5 grid gap-5 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label htmlFor="promotion-name" className="label">
                Name
              </label>
              <input
                id="promotion-name"
                required
                maxLength={255}
                className="field"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
              />
            </div>
            <div>
              <label htmlFor="promotion-code" className="label">
                Code
              </label>
              <input
                id="promotion-code"
                maxLength={64}
                className="field"
                value={form.code}
                onChange={(event) => setForm({ ...form, code: event.target.value })}
              />
            </div>
            <div>
              <label htmlFor="promotion-discount" className="label">
                Discount (%)
              </label>
              <input
                id="promotion-discount"
                type="number"
                min={0}
                max={100}
                required
                className="field"
                value={form.discountPercentage}
                onChange={(event) =>
                  setForm({ ...form, discountPercentage: event.target.value })
                }
              />
            </div>
            <div>
              <label htmlFor="promotion-start" className="label">
                Start date
              </label>
              <input
                id="promotion-start"
                type="date"
                required
                className="field"
                value={form.startDate}
                onChange={(event) => setForm({ ...form, startDate: event.target.value })}
              />
            </div>
            <div>
              <label htmlFor="promotion-end" className="label">
                End date
              </label>
              <input
                id="promotion-end"
                type="date"
                required
                className="field"
                value={form.endDate}
                onChange={(event) => setForm({ ...form, endDate: event.target.value })}
              />
            </div>
            <div>
              <label htmlFor="promotion-usage-limit" className="label">
                Usage limit (optional)
              </label>
              <input
                id="promotion-usage-limit"
                type="number"
                min={0}
                className="field"
                value={form.usageLimit}
                onChange={(event) => setForm({ ...form, usageLimit: event.target.value })}
              />
            </div>
            <div>
              <label htmlFor="promotion-min-order" className="label">
                Min order amount (optional)
              </label>
              <input
                id="promotion-min-order"
                type="number"
                min={0}
                step="0.01"
                className="field"
                value={form.minOrderAmount}
                onChange={(event) => setForm({ ...form, minOrderAmount: event.target.value })}
              />
            </div>
            <div className="flex items-end">
              <label htmlFor="promotion-active" className="flex items-center gap-2 text-sm text-charcoal">
                <input
                  id="promotion-active"
                  type="checkbox"
                  checked={form.active}
                  onChange={(event) => setForm({ ...form, active: event.target.checked })}
                />
                Active
              </label>
            </div>
            <div className="sm:col-span-2">
              <label htmlFor="promotion-description" className="label">
                Description
              </label>
              <textarea
                id="promotion-description"
                rows={3}
                className="field"
                value={form.description}
                onChange={(event) => setForm({ ...form, description: event.target.value })}
              />
            </div>
          </div>
          <div className="mt-5 flex flex-wrap gap-3">
            <button type="submit" disabled={saving} className="btn-primary">
              {saving ? "Saving…" : "Save promotion"}
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

      <div className="admin-panel mt-5 grid gap-4 p-4 sm:grid-cols-2">
        <div>
          <label htmlFor="promotion-search" className="label">
            Search
          </label>
          <input
            id="promotion-search"
            className="field"
            placeholder="Name, code or description"
            value={filter.query}
            onChange={(event) => setFilter({ ...filter, query: event.target.value })}
          />
        </div>
        <div>
          <label htmlFor="promotion-lifecycle-filter" className="label">
            Lifecycle
          </label>
          <select
            id="promotion-lifecycle-filter"
            className="field"
            value={filter.lifecycle}
            onChange={(event) =>
              setFilter({
                ...filter,
                lifecycle: event.target.value as PromotionFilter["lifecycle"],
              })
            }
          >
            {LIFECYCLES.map((lifecycle) => (
              <option key={lifecycle} value={lifecycle}>
                {lifecycle}
              </option>
            ))}
          </select>
        </div>
      </div>

      {promotions.length === 0 ? (
        <div className="admin-notice mt-5">
          No promotions yet.
        </div>
      ) : visiblePromotions.length === 0 ? (
        <div className="admin-notice mt-5">
          No promotions match the current filters.
        </div>
      ) : (
        <AdminTableRegion label="Promotions">
          <table className="w-full min-w-[60rem] text-left text-sm">
            <thead className="border-b border-line bg-sage/10 text-[11px] uppercase tracking-[0.12em] text-muted">
              <tr>
                <th className="px-4 py-3 font-semibold">Name</th>
                <th className="px-4 py-3 font-semibold">Code</th>
                <th className="px-4 py-3 font-semibold">Discount</th>
                <th className="px-4 py-3 font-semibold">Usage</th>
                <th className="px-4 py-3 font-semibold">Lifecycle</th>
                <th className="px-4 py-3 font-semibold">Active</th>
                <th className="px-4 py-3 font-semibold">Min order</th>
                <th className="px-4 py-3 font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {visiblePromotions.map((promotion) => {
                const confirming = confirmingDeleteId === promotion.id;
                const busy = busyDeleteId === promotion.id;
                return (
                  <tr key={promotion.id} className="align-top">
                    <td className="px-4 py-4 font-medium text-charcoal">{promotion.name}</td>
                    <td className="px-4 py-4 text-muted">{promotion.code ?? "—"}</td>
                    <td className="px-4 py-4 text-muted">{promotion.discountPercentage}%</td>
                    <td className="px-4 py-4 text-muted">
                      {promotion.usedCount}/{promotion.usageLimit ?? "∞"}
                    </td>
                    <td className="px-4 py-4 text-muted">{promotionLifecycle(promotion)}</td>
                    <td className="px-4 py-4 text-muted">
                      <input
                        type="checkbox"
                        aria-label={`Active for ${promotion.name}`}
                        checked={promotion.active === true}
                        disabled={togglingId === promotion.id}
                        onChange={() => void toggleActive(promotion)}
                      />
                    </td>
                    <td className="px-4 py-4 text-muted">
                      {promotion.minOrderAmount == null
                        ? "—"
                        : formatPrice(promotion.minOrderAmount)}
                    </td>
                    <td className="px-4 py-4">
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => startEdit(promotion)}
                          className="admin-row-action"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          disabled={busy || togglingId === promotion.id}
                          onClick={() => clickDelete(promotion)}
                          className={`admin-danger-action ${confirming ? "bg-red-50" : ""}`}
                        >
                          {confirming ? "Click again to delete" : "Delete"}
                        </button>
                        {confirming ? (
                          <button
                            type="button"
                            onClick={() => setConfirmingDeleteId(null)}
                            className="admin-row-action"
                          >
                            Cancel
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </AdminTableRegion>
      )}
    </div>
  );
}
