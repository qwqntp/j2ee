import type { AdminAccount } from "./types";

type RoleStatus = Pick<AdminAccount, "roleName" | "status">;

export function accountStatusLabel(status: number | null): string {
  return status === 1 ? "Active" : "Locked";
}

export function canChangeAdminRole(accounts: RoleStatus[], nextRoleName: string): boolean {
  if (nextRoleName === "ROLE_ADMIN") {
    return true;
  }

  const activeAdmins = accounts.filter(
    (account) => account.roleName === "ROLE_ADMIN" && account.status === 1,
  );
  return activeAdmins.length !== 1;
}

export type AdminAccountFilter = { query: string; roleName: string; status: "all" | "0" | "1" };

export function filterAccounts(accounts: AdminAccount[], filter: AdminAccountFilter): AdminAccount[] {
  const query = filter.query.trim().toLowerCase();
  return accounts.filter((account) => {
    if (filter.roleName && account.roleName !== filter.roleName) return false;
    if (filter.status !== "all" && String(account.status) !== filter.status) return false;
    if (!query) return true;
    return [account.username, account.email ?? "", account.roleName ?? ""].some((value) =>
      value.toLowerCase().includes(query),
    );
  });
}

// True when this account is the only active ROLE_ADMIN in the loaded list.
// Reuses the last-admin rule from canChangeAdminRole.
export function isFinalActiveAdmin(accounts: AdminAccount[], accountId: number): boolean {
  const target = accounts.find((account) => account.id === accountId);
  if (!target || target.roleName !== "ROLE_ADMIN" || target.status !== 1) return false;
  return !canChangeAdminRole(accounts, "ROLE_USER");
}
