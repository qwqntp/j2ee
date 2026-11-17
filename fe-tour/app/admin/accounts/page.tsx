import type { Metadata } from "next";
import { AdminAccounts } from "@/components/admin-accounts";

export const metadata: Metadata = {
  title: "Admin accounts",
  description: "Manage admin accounts, roles, status and credentials.",
};

export default function AdminAccountsPage() {
  return <AdminAccounts />;
}
