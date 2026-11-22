import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { InvoiceView } from "@/components/invoice-view";
import { AccountSessionBoundary } from "@/components/account-session-boundary";

export const metadata: Metadata = {
  title: "Invoice",
  description: "Printable invoice for one of your booked journeys.",
};

type Props = { params: Promise<{ bookingId: string }> };

function bookingId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const parsed = Number.parseInt(value, 10);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

export default function InvoicePage({ params }: Props) {
  return (
    <Suspense fallback={<p className="shell py-16 text-sm text-muted">Loading your invoice…</p>}>
      <InvoiceRoute params={params} />
    </Suspense>
  );
}

async function InvoiceRoute({ params }: Props) {
  const { bookingId: raw } = await params;
  const id = bookingId(raw);
  if (id === null) notFound();

  return (
    <AccountSessionBoundary
      fallback={<p className="shell py-16 text-sm text-muted">Loading your invoice…</p>}
    >
      <InvoiceView bookingId={id} />
    </AccountSessionBoundary>
  );
}
