"use client";

import { usePathname, useRouter } from "next/navigation";
import { Fragment, useEffect, useSyncExternalStore, type ReactNode } from "react";
import { useSession } from "@/lib/auth";
import { accountScope, useAccountSeen } from "@/lib/session";

function subscribeNever(): () => void {
  return () => {};
}

function getClientMounted(): boolean {
  return true;
}

function getServerMounted(): boolean {
  return false;
}

/**
 * Keeps account pages tied to the signed-in account.
 *
 * - A logout renders only the page skeleton and redirects to login instead of
 *   leaving the previous account's page on screen.
 * - A different account remounts the whole subtree, so no booking, invoice,
 *   order list, profile or form state from the previous account survives.
 * - The first client render waits for hydration, avoiding a server/client
 *   snapshot mismatch and a duplicate data effect.
 */
export function AccountSessionBoundary({
  children,
  fallback = null,
}: {
  children: ReactNode;
  fallback?: ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const scope = accountScope(useSession());
  const seenAccount = useAccountSeen();
  const mounted = useSyncExternalStore(subscribeNever, getClientMounted, getServerMounted);

  useEffect(() => {
    if (!mounted || scope !== null || !seenAccount) return;
    const next = pathname || "/account/bookings";
    router.replace(`/login?next=${encodeURIComponent(next)}`);
  }, [mounted, scope, seenAccount, pathname, router]);

  if (!mounted) return <>{fallback}</>;
  if (scope === null && seenAccount) return <>{fallback}</>;

  return <Fragment key={scope ?? "anonymous"}>{children}</Fragment>;
}
