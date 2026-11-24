import type { Metadata } from "next";
import { AdminRatings } from "@/components/admin-ratings";

export const metadata: Metadata = {
  title: "Admin ratings",
  description: "Review and moderate tour ratings.",
};

export default function AdminRatingsPage() {
  return <AdminRatings />;
}
