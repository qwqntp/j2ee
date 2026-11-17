import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { InvoiceView } from "@/components/invoice-view";

export const metadata: Metadata = {
  title: "Admin invoice",
  description: "Printable customer invoice for a booking.",
};

type Props = { params: Promise<{ bookingId: string }> };

export default function AdminInvoicePage({ params }: Props) {
  return (
    <Suspense fallback={<p className="shell py-16 text-sm text-muted">Loading invoice…</p>}>
      <AdminInvoiceRoute params={params} />
    </Suspense>
  );
}

async function AdminInvoiceRoute({ params }: Props) {
  const { bookingId } = await params;
  const id = Number(bookingId);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();

  return <InvoiceView bookingId={id} mode="admin" backHref="/admin/invoices" />;
}
