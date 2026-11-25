"use client";

import { useEffect, useMemo, useState } from "react";
import { AdminAccountEditor } from "@/components/admin/admin-account-editor";
import { AdminAccountDetailsDialog, AdminAccountRow } from "@/components/admin/admin-account-view";
import { AdminConfirmDialog } from "@/components/admin/admin-confirm-dialog";
import { AdminIcon } from "@/components/admin/admin-icons";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminTableRegion } from "@/components/admin/admin-ui";
import { useAdminAccess } from "@/components/admin-gate";
import { filterAccounts, isFinalActiveAdmin, type AdminAccountFilter } from "@/lib/admin-accounts";
import { ApiError, deleteAdminAccount, listAccountRoles, listAdminAccounts } from "@/lib/api";
import { useAdminQueryState } from "@/lib/use-admin-query-state";
import { useAdminPrivateSearch } from "@/lib/use-admin-private-search";
import { useAdminTextDraft } from "@/lib/use-admin-text-draft";
import type { AccountRoleResponse, AdminAccount } from "@/lib/types";

const PAGE_SIZE = 10;
const BATCH_SIZE = 100;
const QUERY_SCHEMA = {
  page: { kind: "page" }, q: { kind: "enum", allowed: [] },
  role: { kind: "text", maxLength: 100 },
  status: { kind: "enum", allowed: ["0", "1"] },
} as const;
type Editor = { mode: "add" } | { mode: "edit"; account: AdminAccount };

export function AdminAccounts() {
  const { getSession, account } = useAdminAccess();
  const { params, update } = useAdminQueryState(QUERY_SCHEMA);
  const page = Number(params.get("page") ?? "1") - 1;
  const batch = Math.floor(page / (BATCH_SIZE / PAGE_SIZE));
  const [privateQuery, setPrivateQuery] = useAdminPrivateSearch(account.id, "accounts");
  const privateReady = privateQuery !== null;
  const [search, changeSearch] = useAdminTextDraft(privateQuery ?? "", (value) => {
    setPrivateQuery(value.trim());
    update({ page: null, q: null }, "replace");
  }, params.toString());
  const roleName = params.get("role") ?? "";
  const status = (params.get("status") ?? "all") as AdminAccountFilter["status"];
  const [accounts, setAccounts] = useState<AdminAccount[]>([]);
  const [roles, setRoles] = useState<AccountRoleResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const hasNext = (page + 1) * PAGE_SIZE < totalElements;
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const filter = useMemo<AdminAccountFilter>(() => ({ query: search, roleName, status }), [search, roleName, status]);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [accountToDelete, setAccountToDelete] = useState<AdminAccount | null>(null);
  const [accountDetails, setAccountDetails] = useState<AdminAccount | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (roles.length && roleName && !roles.some((role) => role.roleName === roleName)) {
      update({ role: null }, "replace");
    }
  }, [roles, roleName, update]);

  useEffect(() => {
    if (!privateReady) return;
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const session = await getSession();
        if (!active || !session) return;
        const [result, roleList] = await Promise.all([
          listAdminAccounts(session.accessToken, batch, BATCH_SIZE),
          listAccountRoles(session.accessToken),
        ]);
        if (!active) return;
        setAccounts(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(Math.ceil(result.totalElements / PAGE_SIZE));
        setRoles(roleList);
        setError(null);
      } catch (err) {
        if (active) setError(err instanceof ApiError ? err.message : "Could not load accounts. Try again.");
      } finally {
        if (active) {
          setLoading(false);
          setLoaded(true);
        }
      }
    }
    void load();
    return () => { active = false; };
  }, [batch, reloadKey, getSession, privateReady]);

  const currentAccounts = useMemo(() => accounts.slice((page % (BATCH_SIZE / PAGE_SIZE)) * PAGE_SIZE, (page % (BATCH_SIZE / PAGE_SIZE) + 1) * PAGE_SIZE), [accounts, page]);
  const visibleAccounts = useMemo(() => filterAccounts(currentAccounts, filter), [currentAccounts, filter]);
  const editedAccount = editor?.mode === "edit" ? editor.account : undefined;

  function accountSaved(message: string) {
    setError(null);
    setNotice(message);
    setReloadKey((key) => key + 1);
  }

  async function removeAccount(account: AdminAccount) {
    if (deleting || isFinalActiveAdmin(accounts, account.id)) return;
    setDeleting(true);
    setError(null);
    setNotice(null);
    try {
      const session = await getSession();
      if (!session) return;
      await deleteAdminAccount(session.accessToken, account.id);
      setAccounts((current) => current.filter(({ id }) => id !== account.id));
      if (page > 0 && currentAccounts.length === 1) update({ page: String(page) }, "replace");
      accountSaved(`Account "${account.username}" deleted.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete the account.");
    } finally {
      setDeleting(false);
    }
  }

  if (!loaded) return <AdminTableSkeleton label="Loading accounts…" filters={3} pagination action />;

  return (
    <div>
      <AdminPageHeading title="Accounts"
        description="Review account status, roles, and email verification. Search and filters apply to the current page."
        action={<button type="button" className="btn-primary" disabled={roles.length === 0}
          onClick={() => { setNotice(null); setEditor({ mode: "add" }); }}>
          <AdminIcon name="plus" className="size-4" /> Add user
        </button>}
      />
      {error && <p role="alert" className="admin-notice mt-5">{error}</p>}
      {notice && <p role="status" className="admin-success mt-5">{notice}</p>}

      <AdminListPanel busy={loading}>
        <div className="admin-filter-bar admin-toolbar">
          <label className="admin-toolbar-search"><span className="sr-only">Search accounts</span><AdminIcon name="search" className="size-4" />
            <input type="search" className="field" placeholder="Username, email or role" maxLength={100} value={filter.query}
              onChange={(event) => changeSearch(event.target.value)} />
          </label>
          <div className="admin-toolbar-row">
          <label className="admin-toolbar-control">Role
            <select className="field" value={filter.roleName} onChange={(event) => update({ role: event.target.value, page: null })}>
              <option value="">All roles</option>
              {roles.map((role) => <option key={role.id} value={role.roleName}>{role.roleName}</option>)}
            </select>
          </label>
          <label className="admin-toolbar-control">Status
            <select className="field" value={filter.status}
              onChange={(event) => update({ status: event.target.value === "all" ? null : event.target.value, page: null })}>
              <option value="all">All</option><option value="1">Active</option><option value="0">Locked</option>
            </select>
          </label>
          </div>
        </div>

        {visibleAccounts.length > 0 ? (
          <AdminTableRegion label="Accounts">
            <table className="w-full min-w-[36rem] text-left text-sm">
              <thead className="border-b border-line text-muted">
                <tr>
                  {["Username", "Role", "Status", "Actions"].map((column) => (
                    <th key={column} scope="col" className={`font-semibold${column === "Actions" ? " text-center" : ""}`}>{column}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {visibleAccounts.map((account) => <AdminAccountRow key={account.id} account={account}
                  protectedAdmin={isFinalActiveAdmin(accounts, account.id)} deleting={deleting}
                  onDetails={() => setAccountDetails(account)}
                  onEdit={() => { setNotice(null); setEditor({ mode: "edit", account }); }}
                  onDelete={() => setAccountToDelete(account)} />)}
              </tbody>
            </table>
          </AdminTableRegion>
        ) : !error && <AdminEmptyState
          title={accounts.length ? "No accounts match the current filters" : "No accounts on this page"}
          description={accounts.length ? "Try a different search, role, or status." : undefined}
        />}

        <div className="admin-pagination">
          <button type="button" className="btn-secondary" disabled={page === 0 || loading}
            onClick={() => update({ page: String(page) })}>Previous</button>
          <p className="text-center text-sm text-muted" aria-live="polite">
            Page {page + 1} of {totalPages} · {visibleAccounts.length} shown · {totalElements} total
          </p>
          <button type="button" className="btn-secondary" disabled={!hasNext || loading}
            onClick={() => update({ page: String(page + 2) })}>Next</button>
        </div>
      </AdminListPanel>

      {editor && <AdminAccountEditor
        key={editedAccount?.id ?? "add"} account={editedAccount} roles={roles}
        protectedAdmin={editedAccount ? isFinalActiveAdmin(accounts, editedAccount.id) : false}
        onDismiss={() => setEditor(null)} onSaved={accountSaved}
      />}
      {accountDetails && <AdminAccountDetailsDialog account={accountDetails} onDismiss={() => setAccountDetails(null)} />}
      <AdminConfirmDialog title="Delete account?" target={accountToDelete?.username ?? null}
        consequence="This permanently removes the account if the system permits deletion."
        confirmLabel="Delete account" onDismiss={() => setAccountToDelete(null)}
        onConfirm={() => { if (accountToDelete) void removeAccount(accountToDelete); }}
      />
    </div>
  );
}
