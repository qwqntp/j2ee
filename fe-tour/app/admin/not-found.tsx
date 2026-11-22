import Link from "next/link";

export default function AdminNotFound() {
  return (
    <section className="admin-panel p-5 sm:p-6">
      <h1 className="text-2xl font-semibold tracking-tight">Admin page not found</h1>
      <p className="mt-2 text-sm text-muted">This page may have moved or the record may no longer exist.</p>
      <Link href="/admin" className="btn-primary mt-4">Back to dashboard</Link>
    </section>
  );
}
