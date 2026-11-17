import Link from "next/link";
import { AdminGate } from "@/components/admin-gate";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <section className="border-b border-line bg-white/50">
        <div className="shell py-12 sm:py-16">
          <p className="eyebrow text-terracotta">Operations</p>
          <h1 className="mt-3 font-display text-4xl leading-[1] tracking-tight text-charcoal sm:text-5xl">
            Admin console
          </h1>
          <nav aria-label="Admin" className="mt-6 flex flex-wrap gap-6 text-sm font-medium">
            <Link href="/admin" className="text-forest underline-offset-4 hover:underline">
              Dashboard
            </Link>
            <Link href="/admin/bookings" className="text-forest underline-offset-4 hover:underline">
              Bookings &amp; payments
            </Link>
            <Link href="/admin/accounts" className="text-forest underline-offset-4 hover:underline">
              Accounts
            </Link>
            <Link href="/admin/promotions" className="text-forest underline-offset-4 hover:underline">
              Promotions
            </Link>
            <Link href="/admin/tours" className="text-forest underline-offset-4 hover:underline">
              Tours &amp; departures
            </Link>
          </nav>
        </div>
      </section>

      <section className="shell py-10 sm:py-14">
        <AdminGate>{children}</AdminGate>
      </section>
    </>
  );
}
