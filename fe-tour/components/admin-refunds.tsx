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
import { ApiError, listAdminRefunds, updateAdminRefund } from "@/lib/api";
import { formatDate, formatPrice } from "@/lib/format";
import type { Refund } from "@/lib/types";

const NEXT_ACTIONS: Record<string, { label: string; status: "APPROVED" | "REJECTED" | "DONE" }[]> = {
  REQUESTED: [
    { label: "Approve", status: "APPROVED" },
    { label: "Reject", status: "REJECTED" },
  ],
  APPROVED: [{ label: "Mark done", status: "DONE" }],
};

export function AdminRefunds() {
  const { getSession } = useAdminAccess();
  const [refunds, setRefunds] = useState<Refund[]>([]);
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
        const page = await listAdminRefunds(session.accessToken, 0, 100);
        if (!active) return;
        setRefunds(page.content);
        setError(null);
      } catch (err) {
        if (active) {
          setError(err instanceof ApiError ? err.message : "Could not load refunds. Try again.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [reloadKey, getSession]);

  async function change(refund: Refund, status: "APPROVED" | "REJECTED" | "DONE") {
    setBusyId(refund.id);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      await updateAdminRefund(session.accessToken, refund.id, { status });
      setNotice(`Refund ${refund.id} is now ${status}.`);
      setReloadKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the refund. Try again.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <AdminPageHeading
        title="Refunds"
        description="Review refund requests and record their progress."
        action={<button type="button" onClick={() => setReloadKey((key) => key + 1)}
          className="btn-secondary min-h-0 px-4 py-2 text-xs">Refresh</button>}
      />

      {error ? <p role="alert" className="admin-notice mt-5 text-sm text-terracotta">{error}</p> : null}
      {notice ? <p role="status" className="admin-notice mt-5 text-sm text-forest">{notice}</p> : null}

      <AdminListPanel busy={loading}>
        {!loading && refunds.length === 0 ? (
          <AdminEmptyState title="No refunds yet" description="Requests appear here after a booking is refunded." />
        ) : (
          <AdminTableRegion label="Refunds">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-line text-muted">
                <tr>
                  <th scope="col" className="font-semibold">ID</th>
                  <th scope="col" className="font-semibold">Booking</th>
                  <th scope="col" className="font-semibold">Payment</th>
                  <th scope="col" className="font-semibold">Amount</th>
                  <th scope="col" className="font-semibold">Status</th>
                  <th scope="col" className="font-semibold">Requested</th>
                  <th scope="col" className="font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {refunds.map((refund) => (
                  <tr key={refund.id}>
                    <td className="px-3 py-3 tabular-nums">{refund.id}</td>
                    <td className="px-3 py-3 tabular-nums">{refund.bookingId}</td>
                    <td className="px-3 py-3 tabular-nums">{refund.paymentId}</td>
                    <td className="px-3 py-3 tabular-nums">{formatPrice(refund.amount)}</td>
                    <td className="px-3 py-3">
                      <AdminStatusBadge tone={refund.status === "REJECTED" ? "danger"
                        : refund.status === "DONE" ? "positive" : "warning"}>
                        {refund.status}
                      </AdminStatusBadge>
                    </td>
                    <td className="px-3 py-3 text-muted">{formatDate(refund.requestedAt)}</td>
                    <td className="px-3 py-3">
                      <div className="flex flex-wrap gap-2">
                        {(NEXT_ACTIONS[refund.status] ?? []).map((action) => (
                          <button
                            key={action.status}
                            type="button"
                            disabled={busyId === refund.id}
                            onClick={() => void change(refund, action.status)}
                            className="btn-secondary min-h-0 px-3 py-1 text-xs"
                          >
                            {action.label}
                          </button>
                        ))}
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
