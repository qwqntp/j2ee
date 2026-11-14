import type { Metadata } from "next";
import { AdminBookings } from "@/components/admin-bookings";

export const metadata: Metadata = {
  title: "Admin bookings",
  description: "Manage bookings, confirmations and cash payments.",
};

export default function AdminBookingsPage() {
  return <AdminBookings />;
}
