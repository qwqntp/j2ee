import type { Metadata } from "next";
import { AdminCatalog } from "@/components/admin-catalog";

export const metadata: Metadata = {
  title: "Admin catalog",
  description: "Manage destinations, accommodations, vehicles, guides and tour images.",
};

export default function AdminCatalogPage() {
  return <AdminCatalog />;
}
