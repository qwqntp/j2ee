import type { ReactNode } from "react";

export function AdminPageHeading({ title, description, action }: {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-x-5 gap-y-3">
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

export function AdminLoading({ children }: { children: ReactNode }) {
  return (
    <div className="admin-notice flex items-center gap-3" role="status" aria-busy="true">
      <span aria-hidden="true" className="size-4 shrink-0 animate-spin rounded-full border-2 border-forest/25 border-t-forest motion-reduce:animate-none" />
      {children}
    </div>
  );
}
