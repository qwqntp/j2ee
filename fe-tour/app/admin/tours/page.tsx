import type { Metadata } from "next";
import { Suspense } from "react";
import { AdminTours } from "@/components/admin-tours";

export const metadata: Metadata = {
  title: "Admin tours",
  description: "Manage tours and their dated departures.",
};

export default function AdminToursPage() {
  return <Suspense fallback={null}><AdminTours /></Suspense>;
}
