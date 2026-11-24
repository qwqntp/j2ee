import type { Metadata } from "next";
import { AdminRefunds } from "@/components/admin-refunds";

export const metadata: Metadata = {
  title: "Admin refunds",
  description: "Review and update refund requests.",
};

export default function AdminRefundsPage() {
  return <AdminRefunds />;
}
