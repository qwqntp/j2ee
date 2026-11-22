"use client";

import Link from "next/link";

export default function ErrorPage({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <section className="shell py-20 sm:py-28">
      <div className="mx-auto max-w-2xl rounded-[4px] border border-line bg-white p-8 text-center sm:p-12">
        <p className="eyebrow text-terracotta">Something went sideways</p>
        <h1 className="mt-3 font-display text-3xl tracking-tight sm:text-4xl">
          We could not finish loading this page.
        </h1>
        <p className="mt-3 leading-7 text-muted">
          A short retry usually helps. If it keeps happening, the booking service may be taking a
          break.
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <button type="button" onClick={reset} className="btn-primary">
            Try again
          </button>
          <Link href="/tours" className="btn-secondary">
            Browse journeys
          </Link>
        </div>
      </div>
    </section>
  );
}
