"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { getDrawerFocusIndex } from "@/lib/admin-drawer";
import { getAdminLocation } from "@/lib/admin-navigation";
import { signOut } from "@/lib/auth";
import { AdminSidebar } from "./admin-sidebar";
import { AdminTopbar } from "./admin-topbar";

export function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const location = getAdminLocation(pathname);
  const { account } = useAdminAccess();
  const content = useRef<HTMLElement>(null);
  const drawer = useRef<HTMLDialogElement>(null);
  const opener = useRef<HTMLElement | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    content.current?.scrollTo({ top: 0, left: 0, behavior: "instant" });
    drawer.current?.close();
  }, [pathname]);

  useEffect(() => {
    const desktop = window.matchMedia("(min-width: 768px)");
    const closeOnResize = () => { if (desktop.matches) drawer.current?.close(); };
    desktop.addEventListener("change", closeOnResize);
    return () => desktop.removeEventListener("change", closeOnResize);
  }, []);

  function openNavigation() {
    const dialog = drawer.current;
    if (!dialog || dialog.open) return;
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog.showModal();
    dialog.querySelector<HTMLElement>('[aria-label="Close navigation"]')?.focus();
    setDrawerOpen(true);
  }

  async function handleSignOut() {
    if (signingOut) return;
    setSigningOut(true);
    drawer.current?.close();
    try {
      await signOut();
    } catch {
      // signOut clears the local session even if the backend call fails.
    } finally {
      setSigningOut(false);
    }
  }

  const sidebarProps = { pathname, activeHref: location.activeHref, signingOut, onSignOut: handleSignOut };

  return (
    <div className="admin-workspace grid h-dvh min-w-0 grid-cols-[minmax(0,1fr)] overflow-hidden text-charcoal min-[768px]:grid-cols-[auto_minmax(0,1fr)]">
      <a href="#admin-main"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-30 focus:rounded-lg focus:bg-white focus:px-4 focus:py-3">
        Skip to admin content
      </a>
      <aside id="admin-desktop-sidebar" className={`hidden min-h-0 min-[768px]:block print:hidden ${collapsed ? "w-[4.5rem]" : "w-60"}`}>
        <AdminSidebar {...sidebarProps} collapsed={collapsed} onToggle={() => setCollapsed((value) => !value)} />
      </aside>
      <div className="flex min-h-0 min-w-0 flex-col">
        <AdminTopbar location={location} account={account} drawerOpen={drawerOpen}
          onOpenNavigation={openNavigation} signingOut={signingOut} onSignOut={handleSignOut} />
        <main id="admin-main" tabIndex={-1} ref={content}
          className="admin-main min-h-0 min-w-0 flex-1 overflow-auto overscroll-contain">
          <div className="admin-content mx-auto w-full min-w-0 max-w-[100rem] p-4 sm:p-6 xl:p-8">
            {children}
          </div>
        </main>
      </div>
      <dialog id="admin-navigation-drawer" ref={drawer} aria-label="Admin navigation"
        className="fixed inset-y-0 left-0 m-0 h-dvh max-h-none w-[min(18rem,88vw)] max-w-none overflow-hidden bg-forest-deep p-0 backdrop:bg-charcoal/50"
        onClose={() => {
          setDrawerOpen(false);
          if (opener.current?.getClientRects().length) opener.current.focus();
          else content.current?.focus();
          opener.current = null;
        }}
        onClick={(event) => { if (event.target === event.currentTarget) event.currentTarget.close(); }}
        onKeyDown={(event) => {
          if (event.key !== "Tab") return;
          const controls = [...event.currentTarget.querySelectorAll<HTMLElement>("a[href], button:not(:disabled)")];
          const index = getDrawerFocusIndex(controls.findIndex((control) => control === document.activeElement), controls.length, event.shiftKey);
          event.preventDefault();
          if (index !== null) controls[index]?.focus();
        }}>
        <AdminSidebar {...sidebarProps} onClose={() => drawer.current?.close()} />
      </dialog>
    </div>
  );
}
