import type { Metadata } from "next";
import { AdminPromotions } from "@/components/admin-promotions";

export const metadata: Metadata = {
  title: "Admin promotions",
  description: "Manage promotions, discounts and usage limits.",
};

export default function AdminPromotionsPage() {
  return <AdminPromotions />;
}
