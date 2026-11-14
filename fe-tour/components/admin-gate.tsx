"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ensureSession } from "@/lib/auth";
import type { AuthSession } from "@/lib/types";

export function AdminGate({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthSession | null | undefined>(undefined);

  useEffect(() => {
    let active = true;
    void ensureSession().then((current) => {
      if (active) setSession(current);
    });
    return () => {
      active = false;
    };
  }, []);

  if (session === undefined) {
    return <p className="text-sm text-muted">Checking admin access…</p>;
  }

  if (!session) {
    return (
      <div className="rounded-[4px] border border-line bg-white p-6">
        <p className="font-medium text-charcoal">Admin sign-in required.</p>
        <p className="mt-1 text-sm leading-6 text-muted">
          Sign in with an administrator account to manage tours, departures, bookings and
          payments.
        </p>
        <Link href="/login?next=/admin" className="btn-primary mt-4">
          Sign in
        </Link>
      </div>
    );
  }

  if (session.account.roleName !== "ROLE_ADMIN") {
    return (
      <div
        role="alert"
        className="rounded-[4px] border border-terracotta/40 bg-terracotta/10 p-6"
      >
        <p className="font-medium text-terracotta">Admin access required.</p>
        <p className="mt-1 text-sm leading-6 text-muted">
          This account is not an administrator.
        </p>
      </div>
    );
  }

  return <>{children}</>;
}
