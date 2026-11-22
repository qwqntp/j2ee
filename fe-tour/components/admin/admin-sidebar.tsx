import Link from "next/link";
import { ADMIN_SECTIONS } from "@/lib/admin-navigation";
import { IconCompass } from "@/components/icons";
import { AdminIcon } from "./admin-icons";

type Props = {
  pathname: string;
  activeHref: string | null;
  collapsed?: boolean;
  signingOut: boolean;
  onToggle?: () => void;
  onClose?: () => void;
  onSignOut: () => void;
};

export function AdminSidebar({
  pathname, activeHref, collapsed, signingOut, onToggle, onClose, onSignOut,
}: Props) {
  return (
    <div className="admin-sidebar flex h-full min-h-0 flex-col bg-forest-deep text-ivory">
      <div className="flex min-h-16 shrink-0 items-center gap-2 border-b border-white/15 px-3">
        <Link href="/admin" onNavigate={onClose} aria-label="Hành Trình admin dashboard"
          className="flex min-h-11 min-w-0 items-center gap-3 rounded-md px-2">
          <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-white/10">
            <IconCompass className="size-5" />
          </span>
          {!collapsed && <span className="truncate font-display text-xl">Hành Trình</span>}
        </Link>
        {onClose && (
          <button type="button" onClick={onClose} aria-label="Close navigation"
            className="ml-auto grid size-11 shrink-0 cursor-pointer place-items-center rounded-md hover:bg-white/10">
            <AdminIcon name="close" />
          </button>
        )}
      </div>

      <nav aria-label="Admin" className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-3 py-5">
        {!collapsed && <p className="mb-2 px-3 text-xs font-medium text-ivory/75">Management</p>}
        <ul className="space-y-1">
          {ADMIN_SECTIONS.map(({ href, label, icon }) => {
            const active = activeHref === href;
            return (
              <li key={href}>
                <Link href={href} onNavigate={onClose} title={collapsed ? label : undefined}
                  aria-current={pathname === href ? "page" : active ? "location" : undefined}
                  className={`flex min-h-11 items-center gap-3 rounded-lg border px-3 text-sm transition-colors ${
                    collapsed ? "justify-center" : ""
                  } ${
                    active ? "border-white/20 bg-white/15 font-semibold text-white"
                      : "border-transparent text-ivory/85 hover:bg-white/10 hover:text-white"
                  }`}>
                  <AdminIcon name={icon} className="size-5 shrink-0" />
                  <span className={collapsed ? "sr-only" : ""}>{label}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="space-y-1 border-t border-white/15 p-3">
        {onToggle && (
          <button type="button" onClick={onToggle} aria-expanded={!collapsed}
            aria-controls="admin-desktop-sidebar"
            title={collapsed ? "Expand sidebar" : undefined}
            className={`flex min-h-11 w-full cursor-pointer items-center gap-3 rounded-lg px-3 text-sm hover:bg-white/10 ${collapsed ? "justify-center" : ""}`}>
            <AdminIcon name="panel" className="size-5 shrink-0" />
            <span className={collapsed ? "sr-only" : ""}>
              {collapsed ? "Expand sidebar" : "Collapse sidebar"}
            </span>
          </button>
        )}
        <button type="button" onClick={onSignOut} disabled={signingOut}
          title={collapsed ? "Sign out" : undefined}
          className={`flex min-h-11 w-full cursor-pointer items-center gap-3 rounded-lg px-3 text-sm hover:bg-white/10 disabled:opacity-60 ${collapsed ? "justify-center" : ""}`}>
          <AdminIcon name="logout" className="size-5 shrink-0" />
          <span className={collapsed ? "sr-only" : ""}>{signingOut ? "Signing out…" : "Sign out"}</span>
        </button>
      </div>
    </div>
  );
}
