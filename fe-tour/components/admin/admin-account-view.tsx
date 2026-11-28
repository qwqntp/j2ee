"use client";

import { useEffect, useId, useRef } from "react";
import { AdminIcon } from "./admin-icons";
import { adminActionStyles } from "./admin-action-styles";
import { AdminStatusBadge } from "./admin-ui";
import { accountStatusLabel } from "../../lib/admin-accounts";
import { formatDate } from "../../lib/format";
import type { AdminAccount } from "../../lib/types";

export function AdminAccountRow({ account, protectedAdmin, deleting, onDetails, onEdit, onDelete }: {
  account: AdminAccount;
  protectedAdmin: boolean;
  deleting: boolean;
  onDetails: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return <tr>
    <td className="font-medium">{account.username}</td>
    <td className="text-muted">{account.roleName ?? "—"}</td>
    <td><AdminStatusBadge tone={account.status === 1 ? "positive" : account.status === 0 ? "danger" : "neutral"}>
      {accountStatusLabel(account.status)}
    </AdminStatusBadge></td>
    <td><div className="flex items-center justify-center gap-2 whitespace-nowrap">
      <button type="button" aria-label={`View ${account.username} details`} title="View details"
        className={adminActionStyles.view} onClick={onDetails}>
        <AdminIcon name="eye" className="size-4" />
      </button>
      <button type="button" aria-label={`Edit ${account.username}`} title="Edit"
        className={adminActionStyles.edit} onClick={onEdit}>
        <AdminIcon name="edit" className="size-4" />
      </button>
      <button type="button" aria-label={`Delete ${account.username}`}
        className={adminActionStyles.delete}
        disabled={protectedAdmin || deleting}
        title={protectedAdmin ? "The final active administrator cannot be deleted." : "Delete"} onClick={onDelete}>
        <AdminIcon name="delete" className="size-4" />
      </button>
    </div></td>
  </tr>;
}

export function AdminAccountDetails({ account }: { account: AdminAccount }) {
  const details = [
    ["Username", account.username],
    ["Email", account.email ?? "—"],
    ["Role", account.roleName ?? "—"],
    ["Status", accountStatusLabel(account.status)],
    ["Email verified", account.emailVerified === 1 ? "Verified" : "Unverified"],
    ["Created", formatDate(account.createdAt)],
  ];
  return <dl className="mt-4 grid gap-3 text-sm">
    {details.map(([label, value]) => <div key={label} className="grid gap-1 sm:grid-cols-2">
      <dt className="font-semibold">{label}</dt>
      <dd className="text-muted wrap-anywhere">{value}</dd>
    </div>)}
  </dl>;
}

export function AdminAccountDetailsDialog({ account, onDismiss }: {
  account: AdminAccount;
  onDismiss: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  return <dialog ref={dialog} aria-labelledby={titleId} onClose={onDismiss}
    className="admin-panel m-auto w-[calc(100%_-_2rem)] max-w-md p-5 text-charcoal shadow-panel backdrop:bg-charcoal/55">
    <h2 id={titleId} className="text-xl font-semibold tracking-tight">Account details</h2>
    <AdminAccountDetails account={account} />
    <div className="admin-form-actions">
      <button type="button" className="btn-secondary" onClick={() => dialog.current?.close()}>Close</button>
    </div>
  </dialog>;
}
