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
