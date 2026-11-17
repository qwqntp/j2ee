import type { Metadata } from "next";
import { AdminInvoices } from "@/components/admin-invoices";

export const metadata: Metadata = {
  title: "Admin invoices",
  description: "Look up and print customer invoices.",
};

export default function AdminInvoicesPage() {
  return <AdminInvoices />;
}
