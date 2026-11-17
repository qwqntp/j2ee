"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ApiError,
  listAccountRoles,
  listAdminAccounts,
  updateAdminAccount,
} from "@/lib/api";
import {
  accountStatusLabel,
  filterAccounts,
  isFinalActiveAdmin,
  type AdminAccountFilter,
} from "@/lib/admin-accounts";
import { ensureSession } from "@/lib/auth";
import { formatDate } from "@/lib/format";
import type { AccountRoleResponse, AccountUpdateInput, AdminAccount } from "@/lib/types";

type Draft = { roleId: string; status: string; emailVerified: string };

function draftFrom(account: AdminAccount): Draft {
  return {
    roleId: account.roleId == null ? "" : String(account.roleId),
    status: String(account.status ?? 0),
    emailVerified: String(account.emailVerified ?? 0),
  };
}

function generateTemporaryPassword(): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
  const values = new Uint8Array(16);
  crypto.getRandomValues(values);
  return Array.from(values, (value) => alphabet[value % alphabet.length]).join("");
}

export function AdminAccounts() {
  const [accounts, setAccounts] = useState<AdminAccount[]>([]);
  const [roles, setRoles] = useState<AccountRoleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [filter, setFilter] = useState<AdminAccountFilter>({
    query: "",
    roleName: "",
    status: "all",
  });
  const [drafts, setDrafts] = useState<Record<number, Draft>>({});
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [passwordBanner, setPasswordBanner] = useState<{
    username: string;
    password: string;
  } | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const session = await ensureSession();
        if (!active || !session) return;
        const [page, roleList] = await Promise.all([
          listAdminAccounts(session.accessToken, 0, 100),
          listAccountRoles(session.accessToken),
        ]);
        if (!active) return;
        setAccounts(page.content);
        setRoles(roleList);
        setError(null);
      } catch (err) {
        if (!active) return;
        setError(err instanceof ApiError ? err.message : "Could not load accounts. Try again.");
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [reloadKey]);

  const visibleAccounts = useMemo(() => filterAccounts(accounts, filter), [accounts, filter]);
  const roleNames = useMemo(
    () => new Map(roles.map((role) => [role.id, role.roleName])),
    [roles],
  );

  function getDraft(account: AdminAccount): Draft {
    return drafts[account.id] ?? draftFrom(account);
  }

  function updateDraft(account: AdminAccount, patch: Partial<Draft>) {
    setDrafts((current) => ({
      ...current,
      [account.id]: { ...getDraft(account), ...patch },
    }));
  }

  function isDirty(account: AdminAccount): boolean {
    const draft = getDraft(account);
    const current = draftFrom(account);
    return (
      draft.roleId !== current.roleId ||
      draft.status !== current.status ||
      draft.emailVerified !== current.emailVerified
    );
  }

  async function runUpdate(
    account: AdminAccount,
    input: AccountUpdateInput,
    message: string,
  ): Promise<boolean> {
    setBusyId(account.id);
    setError(null);
    setNotice(null);
    try {
      const session = await ensureSession();
      if (!session) return false;
      await updateAdminAccount(session.accessToken, account.id, input);
      setNotice(message);
      setReloadKey((key) => key + 1);
      return true;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the account.");
      return false;
    } finally {
      setBusyId(null);
    }
  }

  async function confirmChanges(account: AdminAccount) {
    const draft = getDraft(account);
    const input: AccountUpdateInput = {
      username: account.username,
      email: account.email,
      emailVerified: Number(draft.emailVerified),
      status: Number(draft.status),
      roleId: Number(draft.roleId),
    };
    const saved = await runUpdate(account, input, `Account "${account.username}" updated.`);
    if (saved) {
      setDrafts((current) => {
        const next = { ...current };
        delete next[account.id];
        return next;
      });
      setConfirmingId(null);
    }
  }

  async function setTemporaryPassword(account: AdminAccount) {
    const password = generateTemporaryPassword();
    const input: AccountUpdateInput = {
      username: account.username,
      email: account.email,
      emailVerified: account.emailVerified ?? 0,
      status: account.status ?? 0,
      roleId: account.roleId ?? 0,
      password,
    };
    const saved = await runUpdate(
      account,
      input,
      `Temporary password set for "${account.username}".`,
    );
    if (saved) {
      setCopied(false);
      setPasswordBanner({ username: account.username, password });
    }
  }

  function renderConfirmPanel(account: AdminAccount) {
    const draft = getDraft(account);
    return (
      <div className="mt-3 rounded-[3px] border border-line bg-ivory/60 p-3">
        <p className="text-xs font-medium text-charcoal">Confirm changes for {account.username}</p>
        <ul className="mt-2 space-y-1 text-xs text-muted">
          <li>Role: {roleNames.get(Number(draft.roleId)) ?? draft.roleId}</li>
          <li>Status: {accountStatusLabel(Number(draft.status))}</li>
          <li>Email verified: {Number(draft.emailVerified) === 1 ? "Verified" : "Unverified"}</li>
        </ul>
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            disabled={busyId === account.id}
            onClick={() => void confirmChanges(account)}
            className="btn-primary min-h-0 px-3 py-1.5 text-xs disabled:opacity-50"
          >
            {busyId === account.id ? "Saving…" : "Click again to confirm"}
          </button>
          <button
            type="button"
            disabled={busyId === account.id}
            onClick={() => setConfirmingId(null)}
            className="btn-secondary min-h-0 px-3 py-1.5 text-xs"
          >
            Cancel
          </button>
        </div>
      </div>
    );
  }

  if (loading && accounts.length === 0) {
    return <p className="text-sm text-muted">Loading accounts…</p>;
  }

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow text-forest">Access control</p>
          <h2 className="mt-2 font-display text-3xl tracking-tight">
            {accounts.length === 1 ? "1 account" : `${accounts.length} accounts`}
          </h2>
        </div>
      </div>

      {error ? (
        <p
          role="alert"
          className="mt-6 rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-4 text-sm text-terracotta"
        >
          {error}
        </p>
      ) : null}
      {notice ? (
        <p className="mt-6 rounded-[3px] border border-forest/30 bg-sage/15 p-4 text-sm text-forest">
          {notice}
        </p>
      ) : null}

      {passwordBanner ? (
        <div
          role="status"
          className="mt-6 rounded-[3px] border border-forest/30 bg-sage/15 p-4"
        >
          <p className="text-sm font-medium text-charcoal">
            Temporary password for {passwordBanner.username}
          </p>
          <p className="mt-2 text-sm text-charcoal">
            <code className="rounded-[2px] bg-white px-2 py-1 text-xs">{passwordBanner.password}</code>
          </p>
          <p className="mt-2 text-xs text-muted">Copy it now; it will not be shown again.</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => {
                void navigator.clipboard.writeText(passwordBanner.password).then(() => setCopied(true));
              }}
              className="btn-secondary min-h-0 px-3 py-1.5 text-xs"
            >
              {copied ? "Copied" : "Copy"}
            </button>
            <button
              type="button"
              onClick={() => {
                setPasswordBanner(null);
                setCopied(false);
              }}
              className="btn-secondary min-h-0 px-3 py-1.5 text-xs"
            >
              Dismiss
            </button>
          </div>
        </div>
      ) : null}

      <div className="mt-6 grid gap-4 rounded-[4px] border border-line bg-white p-4 sm:grid-cols-3">
        <div>
          <label htmlFor="account-search" className="label">
            Search
          </label>
          <input
            id="account-search"
            className="field"
            placeholder="Username, email or role"
            value={filter.query}
            onChange={(event) => setFilter({ ...filter, query: event.target.value })}
          />
        </div>
        <div>
          <label htmlFor="account-role-filter" className="label">
            Role
          </label>
          <select
            id="account-role-filter"
            className="field"
            value={filter.roleName}
            onChange={(event) => setFilter({ ...filter, roleName: event.target.value })}
          >
            <option value="">All roles</option>
            {roles.map((role) => (
              <option key={role.id} value={role.roleName}>
                {role.roleName}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="account-status-filter" className="label">
            Status
          </label>
          <select
            id="account-status-filter"
            className="field"
            value={filter.status}
            onChange={(event) =>
              setFilter({
                ...filter,
                status: event.target.value as AdminAccountFilter["status"],
              })
            }
          >
            <option value="all">All</option>
            <option value="1">Active</option>
            <option value="0">Locked</option>
          </select>
        </div>
      </div>

      {visibleAccounts.length === 0 ? (
        <div className="mt-6 rounded-[4px] border border-line bg-white p-6 text-sm text-muted">
          No accounts match the current filters.
        </div>
      ) : (
        <div className="mt-6 overflow-x-auto rounded-[4px] border border-line bg-white">
          <table className="w-full min-w-[68rem] text-left text-sm">
            <thead className="border-b border-line bg-sage/10 text-[11px] uppercase tracking-[0.12em] text-muted">
              <tr>
                <th className="px-4 py-3 font-semibold">Username</th>
                <th className="px-4 py-3 font-semibold">Email</th>
                <th className="px-4 py-3 font-semibold">Role</th>
                <th className="px-4 py-3 font-semibold">Status</th>
                <th className="px-4 py-3 font-semibold">Email verified</th>
                <th className="px-4 py-3 font-semibold">Created</th>
                <th className="px-4 py-3 font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {visibleAccounts.map((account) => {
                const draft = getDraft(account);
                const protectedAdmin = isFinalActiveAdmin(accounts, account.id);
                const dirty = isDirty(account);
                const busy = busyId === account.id;
                const confirming = confirmingId === account.id;
                return (
                  <tr key={account.id} className="align-top">
                    <td className="px-4 py-4 font-medium text-charcoal">{account.username}</td>
                    <td className="px-4 py-4 text-muted">{account.email ?? "—"}</td>
                    <td className="px-4 py-4 text-muted">{account.roleName ?? "—"}</td>
                    <td className="px-4 py-4 text-muted">{accountStatusLabel(account.status)}</td>
                    <td className="px-4 py-4 text-muted">
                      {account.emailVerified === 1 ? "Verified" : "Unverified"}
                    </td>
                    <td className="px-4 py-4 text-muted">{formatDate(account.createdAt)}</td>
                    <td className="px-4 py-4">
                      <div className="grid gap-2">
                        <div>
                          <label className="text-[11px] text-muted">Role</label>
                          <select
                            aria-label={`Role for ${account.username}`}
                            className="field min-h-0 py-1 text-xs"
                            disabled={protectedAdmin || busy}
                            value={draft.roleId}
                            onChange={(event) => updateDraft(account, { roleId: event.target.value })}
                          >
                            <option value="">Select role</option>
                            {roles.map((role) => (
                              <option key={role.id} value={String(role.id)}>
                                {role.roleName}
                              </option>
                            ))}
                          </select>
                        </div>
                        <div>
                          <label className="text-[11px] text-muted">Status</label>
                          <select
                            aria-label={`Status for ${account.username}`}
                            className="field min-h-0 py-1 text-xs"
                            disabled={protectedAdmin || busy}
                            value={draft.status}
                            onChange={(event) => updateDraft(account, { status: event.target.value })}
                          >
                            <option value="1">Active</option>
                            <option value="0">Locked</option>
                          </select>
                        </div>
                        <div>
                          <label className="text-[11px] text-muted">Email verified</label>
                          <select
                            aria-label={`Email verified for ${account.username}`}
                            className="field min-h-0 py-1 text-xs"
                            disabled={busy}
                            value={draft.emailVerified}
                            onChange={(event) =>
                              updateDraft(account, { emailVerified: event.target.value })
                            }
                          >
                            <option value="0">Unverified</option>
                            <option value="1">Verified</option>
                          </select>
                        </div>
                        {protectedAdmin ? (
                          <p className="text-xs text-terracotta">
                            Final active admin cannot be disabled or demoted.
                          </p>
                        ) : null}
                        <div className="flex flex-wrap gap-2">
                          <button
                            type="button"
                            disabled={!dirty || busy || confirming}
                            onClick={() => {
                              setError(null);
                              setNotice(null);
                              setConfirmingId(account.id);
                            }}
                            className="rounded-[2px] border border-forest/40 px-3 py-1.5 text-xs font-medium text-forest transition-colors hover:bg-sage/15 disabled:opacity-50"
                          >
                            Apply changes
                          </button>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => void setTemporaryPassword(account)}
                            className="rounded-[2px] border border-charcoal/25 px-3 py-1.5 text-xs font-medium text-charcoal transition-colors hover:border-forest hover:text-forest disabled:opacity-50"
                          >
                            Set temporary password
                          </button>
                        </div>
                        {confirming ? renderConfirmPanel(account) : null}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
