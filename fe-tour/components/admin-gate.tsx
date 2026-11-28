"use client";

import Link from "next/link";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { getAdminRecheckAt } from "@/lib/admin-session";
import { hasConsoleAccess, isConsoleRole } from "@/lib/admin-permissions";
import { ensureSession, getStoredSession, useSession } from "@/lib/auth";
import type { Account, AuthSession } from "@/lib/types";

type AdminAccess = {
  account: Account;
  getSession: () => Promise<AuthSession | null>;
};

const AdminAccessContext = createContext<AdminAccess | null>(null);

export function useOptionalAdminAccess() {
  return useContext(AdminAccessContext);
}

export function useAdminAccess() {
  const access = useOptionalAdminAccess();
  if (!access) throw new Error("Admin access requires AdminGate.");
  return access;
}

export function AdminGate({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthSession | null | undefined>(undefined);
  const stored = useSession();
  const storedToken = stored?.accessToken;
  const validation = useRef<{ session: AuthSession | null; recheckAt: number } | null>(null);

  const validate = useCallback(async () => {
    const cached = validation.current;
    const current = getStoredSession();
    if (cached && !current) {
      validation.current = { session: null, recheckAt: 0 };
      setSession(null);
      return null;
    }
    if (cached?.session && cached.session === current && Date.now() < cached.recheckAt) {
      return cached.session;
    }

    const next = await ensureSession();
    validation.current = {
      session: next,
      recheckAt: next ? getAdminRecheckAt(next.accessToken, Date.now()) : 0,
    };
    setSession(next);
    return next;
  }, []);

  useEffect(() => {
    void validate();
  }, [storedToken, validate]);

  const getSession = useCallback(async () => {
    const current = await validate();
    return current && hasConsoleAccess(current, getStoredSession()) ? current : null;
  }, [validate]);

  return <AdminGateContent session={session} stored={stored} getSession={getSession}>{children}</AdminGateContent>;
}

// Keep the permission states testable without a browser or new DOM-test dependencies.
export function AdminGateContent({ session, stored, getSession, children }: {
  session: AuthSession | null | undefined;
  stored: AuthSession | null;
  getSession: AdminAccess["getSession"];
  children: React.ReactNode;
}) {
  if (session === undefined || (session && stored && session.account.id !== stored.account.id)) {
    return (
      <main className="admin-access">
        <p className="admin-notice" role="status">Checking admin access…</p>
      </main>
    );
  }

  if (!session || !stored) {
    return (
      <main className="admin-access">
        <div className="admin-panel w-full max-w-md p-6">
          <p className="font-display text-lg text-forest">Hành Trình</p>
          <h1 className="mt-3 text-2xl font-semibold">Admin sign-in required</h1>
          <p className="mt-2 text-sm leading-6 text-muted">
            Sign in with an administrator or staff account to access the workspace.
          </p>
          <Link href="/login?next=/admin" className="btn-primary mt-4">
            Sign in
          </Link>
        </div>
      </main>
    );
  }

  if (!isConsoleRole(session.account.roleName) || !hasConsoleAccess(session, stored)) {
    return (
      <main className="admin-access">
        <div role="alert" className="admin-panel w-full max-w-md p-6">
          <h1 className="text-2xl font-semibold">Workspace access required</h1>
          <p className="mt-2 text-sm leading-6 text-muted">This account does not have permission to access the workspace.</p>
          <Link href="/" className="btn-secondary mt-4">Back to Hành Trình</Link>
        </div>
      </main>
    );
  }

  return (
    <AdminAccessContext value={{ account: session.account, getSession }}>
      {children}
    </AdminAccessContext>
  );
}
