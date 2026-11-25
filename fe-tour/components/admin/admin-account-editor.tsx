"use client";

import { useState, type FormEvent } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { accountStatusLabel } from "@/lib/admin-accounts";
import { ApiError, createAdminAccount, updateAdminAccount } from "@/lib/api";
import type { AccountRoleResponse, AdminAccount } from "@/lib/types";
import { AdminFormDialog } from "./admin-form-dialog";
import { AdminFormActions, AdminFormSection } from "./admin-ui";

function generateTemporaryPassword(): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (value) => alphabet[value % alphabet.length]).join("");
}

export function AdminAccountEditor({ account, roles, protectedAdmin, onDismiss, onSaved }: {
  account?: AdminAccount;
  roles: AccountRoleResponse[];
  protectedAdmin: boolean;
  onDismiss: () => void;
  onSaved: (message: string) => void;
}) {
  const { getSession } = useAdminAccess();
  const [roleId, setRoleId] = useState(() => String(account
    ? (account.roleId ?? roles.find((role) => role.roleName === account.roleName)?.id ?? "")
    : (roles.find((role) => role.roleName === "ROLE_USER")?.id ?? "")));
  const [status, setStatus] = useState(String(account ? (account.status ?? 0) : 1));
  const [emailVerified, setEmailVerified] = useState(String(account?.emailVerified ?? 0));
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);

  const dirty = account && (
    Number(roleId) !== account.roleId ||
    Number(status) !== (account.status ?? 0) ||
    Number(emailVerified) !== (account.emailVerified ?? 0)
  );

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !roleId || (account && !dirty)) return;
    if (account && !confirming) {
      setError(null);
      setConfirming(true);
      return;
    }

    const data = new FormData(event.currentTarget);
    const access = { roleId: Number(roleId), status: Number(status), emailVerified: Number(emailVerified) };
    setBusy(true);
    setError(null);
    try {
      const session = await getSession();
      if (!session) return;
      if (account) {
        await updateAdminAccount(session.accessToken, account.id, {
          ...access, username: account.username, email: account.email,
        });
        onSaved(`Account "${account.username}" updated.`);
      } else {
        const username = String(data.get("username") ?? "").trim();
        await createAdminAccount(session.accessToken, {
          ...access, username, email: String(data.get("email") ?? "").trim() || null,
          password: String(data.get("password") ?? ""),
        });
        onSaved(`Account "${username}" created.`);
      }
      onDismiss();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the account.");
    } finally {
      setBusy(false);
    }
  }

  async function resetPassword() {
    if (!account || busy) return;
    const currentRoleId = account.roleId ?? roles.find((role) => role.roleName === account.roleName)?.id;
    if (currentRoleId == null || account.status == null) {
      setError("Account role or status is unavailable. Reload the page and try again.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const session = await getSession();
      if (!session) return;
      const password = generateTemporaryPassword();
      await updateAdminAccount(session.accessToken, account.id, {
        username: account.username, email: account.email, roleId: currentRoleId,
        status: account.status, emailVerified: account.emailVerified ?? 0, password,
      });
      setTemporaryPassword(password);
      setCopied(false);
      setCopyError(false);
      onSaved(`Temporary password set for "${account.username}".`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not set a temporary password.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AdminFormDialog open narrow title={account ? `Edit ${account.username}` : "Add user"} busy={busy} onDismiss={onDismiss}>
      <form onSubmit={save} className="space-y-4" aria-busy={busy}>
        {!account && <AdminFormSection title="Account details">
          <div className="grid gap-3">
            <label className="label">Username<input name="username" className="field" required minLength={3} maxLength={50} autoComplete="off" /></label>
            <label className="label">Email <span className="text-muted">(optional)</span><input name="email" className="field" type="email" maxLength={255} autoComplete="off" /></label>
            <label className="label">Initial password<input name="password" className="field" type="password" required minLength={8} maxLength={100} autoComplete="new-password" /></label>
          </div>
        </AdminFormSection>}
        <AdminFormSection title="Role and access">
          <div className="grid gap-3">
            <label className="label">Role
              <select className="field" required value={roleId} onChange={(event) => setRoleId(event.target.value)} disabled={protectedAdmin || busy || confirming}>
                <option value="">Select role</option>
                {roles.map((role) => <option key={role.id} value={role.id}>{role.roleName === "ROLE_STAFF" ? "Staff" : role.roleName}</option>)}
              </select>
            </label>
            <label className="label">Status
              <select className="field" value={status} onChange={(event) => setStatus(event.target.value)} disabled={protectedAdmin || busy || confirming}>
                <option value="1">Active</option><option value="0">Locked</option>
              </select>
            </label>
            <label className="label">Email verified
              <select className="field" value={emailVerified} onChange={(event) => setEmailVerified(event.target.value)} disabled={busy || confirming}>
                <option value="0">Unverified</option><option value="1">Verified</option>
              </select>
            </label>
            {protectedAdmin && <p className="text-sm text-terracotta">The final active administrator cannot be disabled or demoted.</p>}
          </div>
        </AdminFormSection>
        {account && !confirming && (
          <AdminFormSection title="Password">
            <button type="button" className="btn-secondary" disabled={busy || temporaryPassword !== null} onClick={() => void resetPassword()}>
              Set temporary password
            </button>
            {temporaryPassword && <div className="admin-success mt-3">
              <p role="status">Temporary password created. Copy it now; it won&apos;t be shown after you close this dialog.</p>
              <code className="mt-2 block break-all font-semibold">{temporaryPassword}</code>
              <button type="button" className="btn-secondary mt-2" onClick={async () => {
                try { await navigator.clipboard.writeText(temporaryPassword); setCopied(true); setCopyError(false); }
                catch { setCopyError(true); }
              }}>{copied ? "Copied" : "Copy password"}</button>
              {copyError && <p role="alert" className="mt-2">Clipboard unavailable. Copy the password manually.</p>}
            </div>}
          </AdminFormSection>
        )}
        {confirming && <div className="admin-confirmation text-sm">
          <p className="font-semibold">Confirm changes</p>
          <p className="mt-1 text-muted">{roles.find((role) => role.id === Number(roleId))?.roleName} · {accountStatusLabel(Number(status))} · {emailVerified === "1" ? "Verified" : "Unverified"}</p>
        </div>}
        {error && <p className="admin-notice" role="alert">{error}</p>}
        <AdminFormActions>
          <button type="button" className="btn-secondary" disabled={busy}
            onClick={confirming ? () => { setConfirming(false); setError(null); } : onDismiss}>
            {confirming ? "Back" : temporaryPassword ? "Close" : "Cancel"}
          </button>
          <button type="submit" className="btn-primary" disabled={busy || !roleId || (account ? !dirty : false)}>
            {confirming ? (busy ? "Saving…" : "Confirm changes") :
              account ? "Apply changes" : busy ? "Creating…" : "Create user"}
          </button>
        </AdminFormActions>
      </form>
    </AdminFormDialog>
  );
}
