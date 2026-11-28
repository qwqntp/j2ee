import { Suspense } from "react";
import { notFound } from "next/navigation";
import { AdminPassengers } from "@/components/admin-passengers";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";

export default function PassengerPage({ params }: { params: Promise<{ id: string }> }) {
  return <Suspense fallback={<AdminTableSkeleton label="Loading passengers…" />}><PassengerRoute params={params} /></Suspense>;
}

async function PassengerRoute({ params }: { params: Promise<{ id: string }> }) {
  const id = Number((await params).id);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();
  return <AdminPassengers id={id} />;
}
