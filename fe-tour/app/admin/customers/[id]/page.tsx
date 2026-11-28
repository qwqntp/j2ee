import { Suspense } from "react";
import { notFound } from "next/navigation";
import { AdminCustomerDetail } from "@/components/admin-customer-detail";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";

export default function CustomerPage({ params }: { params: Promise<{ id: string }> }) {
  return <Suspense fallback={<AdminTableSkeleton label="Loading customer…" />}><CustomerRoute params={params} /></Suspense>;
}
async function CustomerRoute({ params }: { params: Promise<{ id: string }> }) {
  const id = Number((await params).id);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();
  return <AdminCustomerDetail id={id} />;
}
