import type { Metadata } from "next";
import { AdminTours } from "@/components/admin-tours";

export const metadata: Metadata = {
  title: "Admin tours",
  description: "Manage tours and their dated departures.",
};

export default function AdminToursPage() {
  return <AdminTours />;
}
