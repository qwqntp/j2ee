import type { ReactNode } from "react";
import { AdminIcon } from "./admin-icons";

export function AdminPageHeading({ title, description, action }: {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="admin-page-heading flex flex-wrap items-center justify-between gap-x-5 gap-y-3">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight sm:text-[1.75rem]">{title}</h1>
        {description && <p className="mt-1 text-sm leading-6 text-muted">{description}</p>}
      </div>
      {action}
    </div>
  );
}

export function AdminTableRegion({ label, children, className = "mt-5" }: {
  label: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div role="region" aria-label={label} tabIndex={0} className={`admin-table ${className}`}>
      {children}
    </div>
  );
}

export function AdminListPanel({ children, busy = false }: { children: ReactNode; busy?: boolean }) {
  return (
    <section className="admin-panel admin-list-panel">
      {busy && <div className="admin-list-progress" role="status"><span className="sr-only">Updating results…</span></div>}
      <div aria-busy={busy}>{children}</div>
    </section>
  );
}

export function AdminFormSection({ title, description, children }: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <fieldset className="admin-form-section">
      <legend>{title}</legend>
      {description && <p className="mb-4 text-sm text-muted">{description}</p>}
      {children}
    </fieldset>
  );
}

export function AdminFormActions({ children }: { children: ReactNode }) {
  return <div className="admin-form-actions">{children}</div>;
}

export function AdminStatusBadge({ children, tone = "neutral" }: {
  children: ReactNode;
  tone?: "neutral" | "positive" | "warning" | "danger";
}) {
  return <span className={`admin-status admin-status-${tone}`}>{children}</span>;
}

export function AdminEmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="px-5 py-10 text-center" role="status">
      <span className="mx-auto mb-3 grid size-10 place-items-center rounded-lg bg-ivory text-forest">
        <AdminIcon name="search" />
      </span>
      <p className="font-semibold">{title}</p>
      {description && <p className="mt-1 text-sm text-muted">{description}</p>}
    </div>
  );
}
