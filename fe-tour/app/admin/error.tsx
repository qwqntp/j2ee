"use client";

import Link from "next/link";

export default function AdminError({ reset }: { reset: () => void }) {
  return (
    <section className="admin-panel p-5 sm:p-6" role="alert">
      <h1 className="text-2xl font-semibold tracking-tight">Could not load this page</h1>
      <p className="mt-2 text-sm text-muted">Try loading this page again, or go back to the dashboard.</p>
      <div className="mt-4 flex flex-wrap gap-2">
        <button type="button" onClick={reset} className="btn-primary">Try again</button>
        <Link href="/admin" className="btn-secondary">Dashboard</Link>
      </div>
    </section>
  );
}
