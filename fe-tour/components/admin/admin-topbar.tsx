import Link from "next/link";
import { useRef } from "react";
import { IconChevronRight, IconMenu, IconUsers } from "@/components/icons";
import type { getAdminLocation } from "@/lib/admin-navigation";
import type { Account } from "@/lib/types";
import { AdminIcon } from "./admin-icons";

type Props = {
  location: ReturnType<typeof getAdminLocation>;
  account: Account;
  drawerOpen: boolean;
  onOpenNavigation: () => void;
  signingOut: boolean;
  onSignOut: () => void;
};

export function AdminTopbar({
  location, account, drawerOpen, onOpenNavigation, signingOut, onSignOut,
}: Props) {
  const menu = useRef<HTMLDetailsElement>(null);
  const closeMenu = () => { if (menu.current) menu.current.open = false; };

  return (
    <header className="flex min-h-16 shrink-0 items-center justify-between gap-2 border-b border-line/75 bg-white px-3 sm:px-5 print:hidden">
      <div className="flex min-w-0 items-center gap-2 sm:gap-3">
        <button type="button" onClick={onOpenNavigation} aria-label="Open navigation"
          aria-expanded={drawerOpen} aria-controls="admin-navigation-drawer" aria-haspopup="dialog"
          className="grid size-11 shrink-0 cursor-pointer place-items-center rounded-lg border border-line/80 text-forest hover:bg-ivory min-[768px]:hidden">
          <IconMenu className="size-5" />
        </button>
        <nav aria-label="Current location" className="flex min-w-0 items-center gap-2 text-sm">
          {location.parent && (
            <>
              <Link href={location.parent.href} className="hidden text-muted hover:text-forest hover:underline sm:inline">
                {location.parent.label}
              </Link>
              <IconChevronRight className="hidden size-3.5 shrink-0 text-muted sm:block" />
            </>
          )}
          <span aria-current="page" className="font-semibold leading-tight text-charcoal">
            {location.title}
          </span>
        </nav>
      </div>
      <details ref={menu} className="relative z-20 shrink-0"
        onBlur={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget)) closeMenu();
        }}
        onKeyDown={(event) => {
          if (event.key === "Escape" && menu.current?.open) {
            event.stopPropagation();
            closeMenu();
            menu.current.querySelector("summary")?.focus();
          }
        }}>
        <summary aria-label={`Account menu: ${account.username}, Administrator`}
          className="flex min-h-11 cursor-pointer list-none items-center gap-2 rounded-lg border border-line/80 px-1.5 hover:bg-ivory sm:pr-2.5 [&::-webkit-details-marker]:hidden">
          <span aria-hidden="true" className="grid size-8 shrink-0 place-items-center rounded-md bg-forest text-sm font-semibold text-white">
            {account.username.slice(0, 1).toUpperCase()}
          </span>
          <span className="hidden max-w-32 truncate text-left text-sm leading-tight sm:block">
            <span className="block truncate font-semibold">{account.username}</span>
            <span className="block text-xs text-muted">Administrator</span>
          </span>
          <IconChevronRight className="size-3.5 rotate-90 text-muted" />
        </summary>
        <div className="absolute top-full right-0 mt-2 w-60 rounded-xl border border-line bg-white p-2 shadow-panel">
          <div className="border-b border-line/70 px-3 py-2">
            <p className="break-words text-sm font-semibold">{account.username}</p>
            <p className="break-words text-xs text-muted">{account.email ?? "Administrator"}</p>
          </div>
          <Link href="/account/profile" onNavigate={closeMenu}
            className="flex min-h-11 items-center gap-2 rounded-md px-3 text-sm hover:bg-ivory">
            <IconUsers className="size-4 text-forest" /> Profile
          </Link>
          <Link href="/account/bookings" onNavigate={closeMenu}
            className="flex min-h-11 items-center gap-2 rounded-md px-3 text-sm hover:bg-ivory">
            <AdminIcon name="bookings" className="size-4 text-forest" /> My orders
          </Link>
          <button type="button" onClick={onSignOut} disabled={signingOut}
            className="flex min-h-11 w-full cursor-pointer items-center gap-2 rounded-md px-3 text-left text-sm font-medium text-red-800 hover:bg-red-50 disabled:opacity-60">
            <AdminIcon name="logout" className="size-4" />
            {signingOut ? "Signing out…" : "Sign out"}
          </button>
        </div>
      </details>
    </header>
  );
}
