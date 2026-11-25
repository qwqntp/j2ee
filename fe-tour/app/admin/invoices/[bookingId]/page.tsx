import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { InvoiceView } from "@/components/invoice-view";
import { AdminInvoiceSkeleton } from "@/components/admin/admin-skeletons";

export const metadata: Metadata = {
  title: "Admin invoice",
  description: "Printable customer invoice for a booking.",
};

type Props = { params: Promise<{ bookingId: string }>; searchParams: Promise<Record<string, string | string[] | undefined>> };

export default function AdminInvoicePage({ params, searchParams }: Props) {
  return (
    <Suspense fallback={<AdminInvoiceSkeleton />}>
      <AdminInvoiceRoute params={params} searchParams={searchParams} />
    </Suspense>
  );
}

async function AdminInvoiceRoute({ params, searchParams }: Props) {
  const { bookingId } = await params;
  const id = Number(bookingId);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();

  const supplied = await searchParams;
  const backParams = new URLSearchParams();
  for (const key of ["page", "size", "status", "sort", "direction"]) {
    const value = supplied[key];
    if (typeof value === "string" && value.length <= 50) backParams.set(key, value);
  }
  const query = backParams.toString();
  return <InvoiceView bookingId={id} mode="admin" backHref={query ? `/admin/invoices?${query}` : "/admin/invoices"} />;
}
