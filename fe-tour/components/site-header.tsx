"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ensureSession, getStoredSession, signOut, useSession } from "@/lib/auth";
import { IconArrowRight, IconChevronRight, IconMenu } from "./icons";

const links = [
  { href: "/tours", label: "Journeys" },
  { href: "/#destinations", label: "Destinations" },
  { href: "/#stories", label: "Travel notes" },
  { href: "/#why", label: "Why us" },
];

export function SiteHeader() {
  const menu = useRef<HTMLDetailsElement>(null);
  const accountMenu = useRef<HTMLDetailsElement>(null);
  const session = useSession();
  const account = session?.account ?? null;
  const initial = account?.username.slice(0, 1).toUpperCase() ?? "";
  const router = useRouter();
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    if (!getStoredSession()) return;
    void ensureSession();
  }, []);

  async function handleSignOut() {
    if (signingOut) return;
    setSigningOut(true);
    accountMenu.current?.removeAttribute("open");
    menu.current?.removeAttribute("open");
    try {
      await signOut();
    } catch {
      // The local session is cleared even when the server call fails.
    } finally {
      setSigningOut(false);
      router.refresh();
    }
  }

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-ivory">
      <div className="shell flex h-20 items-center justify-between gap-6">
        <Link href="/" className="group flex items-baseline gap-3" aria-label="Hành Trình home">
          <span className="font-display text-[1.65rem] leading-none tracking-tight text-forest">
            Hành Trình
          </span>
          <span className="hidden border-l border-line pl-3 text-[10px] font-medium uppercase tracking-[0.22em] text-muted sm:block">
            Vietnam travel atelier
          </span>
        </Link>

        <nav className="hidden items-center gap-7 text-sm md:flex" aria-label="Main">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-muted transition-colors hover:text-forest"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="hidden items-center gap-6 md:flex">
          {account ? (
            <details ref={accountMenu} className="relative">
              <summary className="flex min-h-11 cursor-pointer list-none items-center gap-2 rounded-[3px] border border-line bg-white/60 py-1 pl-1 pr-3 text-sm transition-colors hover:border-forest [&::-webkit-details-marker]:hidden">
                <span
                  aria-hidden="true"
                  className="grid h-8 w-8 place-items-center rounded-full bg-forest text-xs font-semibold text-white"
                >
                  {initial}
                </span>
                <span className="max-w-32 truncate font-medium text-charcoal">
                  {account.username}
                </span>
                <IconChevronRight className="h-3.5 w-3.5 rotate-90 text-muted" />
              </summary>
              <div className="absolute right-0 top-12 w-72 rounded-[3px] border border-line bg-ivory p-4 shadow-panel">
                <p className="eyebrow text-terracotta">Signed in</p>
                <p className="mt-2 truncate font-medium text-charcoal">{account.username}</p>
                {account.email ? (
                  <p className="truncate text-sm text-muted">{account.email}</p>
                ) : null}
                <div className="mt-4 border-t border-line pt-2">
                  <button
                    type="button"
                    onClick={handleSignOut}
                    disabled={signingOut}
                    className="w-full rounded-[2px] px-3 py-2 text-left text-sm font-medium text-terracotta transition-colors hover:bg-terracotta/10 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {signingOut ? "Signing out…" : "Sign out"}
                  </button>
                </div>
              </div>
            </details>
          ) : (
            <Link
              href="/login"
              className="text-sm font-medium text-muted transition-colors hover:text-forest"
            >
              Sign in
            </Link>
          )}

          <Link href="/tours" className="btn-primary">
            Find a journey
            <IconArrowRight className="h-4 w-4" />
          </Link>
        </div>

        <details ref={menu} className="relative md:hidden">
          <summary className="grid h-11 w-11 cursor-pointer list-none place-items-center rounded-[3px] border border-line text-charcoal transition-colors hover:border-forest hover:text-forest [&::-webkit-details-marker]:hidden">
            <IconMenu />
            <span className="sr-only">Open menu</span>
          </summary>
          <div className="absolute right-0 top-14 w-64 rounded-[3px] border border-line bg-ivory p-2 shadow-panel">
            <nav
              aria-label="Mobile"
              className="flex flex-col"
              onClick={() => menu.current?.removeAttribute("open")}
            >
              {links.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className="rounded-[2px] px-4 py-3 text-sm font-medium text-muted transition-colors hover:bg-sage/20 hover:text-forest"
                >
                  {link.label}
                </Link>
              ))}
              <Link href="/tours" className="btn-primary mt-2">
                Find a journey
                <IconArrowRight className="h-4 w-4" />
              </Link>

              <div className="mt-2 border-t border-line pt-2">
                {account ? (
                  <>
                    <div className="flex items-center gap-3 px-4 py-3">
                      <span
                        aria-hidden="true"
                        className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-forest text-xs font-semibold text-white"
                      >
                        {initial}
                      </span>
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-charcoal">
                          {account.username}
                        </p>
                        {account.email ? (
                          <p className="truncate text-xs text-muted">{account.email}</p>
                        ) : null}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={handleSignOut}
                      disabled={signingOut}
                      className="w-full rounded-[2px] px-4 py-3 text-left text-sm font-medium text-terracotta transition-colors hover:bg-terracotta/10 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {signingOut ? "Signing out…" : "Sign out"}
                    </button>
                  </>
                ) : (
                  <Link
                    href="/login"
                    className="block rounded-[2px] px-4 py-3 text-sm font-medium text-muted transition-colors hover:bg-sage/20 hover:text-forest"
                  >
                    Sign in
                  </Link>
                )}
              </div>
            </nav>
          </div>
        </details>
      </div>
    </header>
  );
}
