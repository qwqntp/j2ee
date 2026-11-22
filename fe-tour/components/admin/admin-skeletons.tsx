import type { ReactNode } from "react";

function Shape({ className }: { className: string }) {
  return <span className={`admin-skeleton ${className}`} />;
}

function LoadingRegion({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div role="status">
      <span className="sr-only">{label}</span>
      <div aria-hidden="true" data-admin-skeleton="">{children}</div>
    </div>
  );
}

function Heading({ action = false }: { action?: boolean }) {
  return (
    <div className="admin-page-heading flex flex-wrap items-center justify-between gap-4">
      <div className="grid gap-3">
        <Shape className="h-7 w-44" />
        <Shape className="h-4 w-55 max-w-full" />
      </div>
      {action && <Shape className="h-11 w-32" />}
    </div>
  );
}

function Rows({ rows = 5 }: { rows?: number }) {
  return (
    <div className="divide-y divide-line/65">
      <div className="grid grid-cols-[2fr_1fr_1fr] gap-4 bg-ivory/80 px-5 py-4">
        {[0, 1, 2].map((key) => <Shape key={key} className="h-3 w-3/4" />)}
      </div>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="grid min-h-14 grid-cols-[2fr_1fr_1fr] items-center gap-4 px-5 py-3">
          <Shape className={`h-3.5 ${index % 2 === 0 ? "w-4/5" : "w-3/5"}`} />
          <Shape className="h-5 w-3/4 rounded-full" />
          <Shape className="ml-auto h-6 w-2/3" />
        </div>
      ))}
    </div>
  );
}

export function AdminTableSkeleton({ label, filters = 0, pagination = false, action = false, embedded = false }: {
  label: string;
  filters?: number;
  pagination?: boolean;
  action?: boolean;
  embedded?: boolean;
}) {
  return (
    <LoadingRegion label={label}>
      {!embedded && <Heading action={action} />}
      <div className={embedded ? "" : "admin-panel admin-list-panel"}>
        {filters > 0 && (
          <div className={`admin-filter-bar grid gap-4 ${filters === 3 ? "sm:grid-cols-3" :
            filters === 5 ? "sm:grid-cols-2 xl:grid-cols-5" : "sm:grid-cols-2"}`}>
            {Array.from({ length: filters }, (_, index) => (
              <div key={index} className="grid gap-2">
                <Shape className="h-3 w-16" />
                <Shape className="h-11 w-full" />
              </div>
            ))}
          </div>
        )}
        <Rows />
        {pagination && <div className="flex justify-between gap-4 border-t border-line/60 p-4">
          <Shape className="h-9 w-20" /><Shape className="h-4 w-16 self-center" /><Shape className="h-9 w-20" />
        </div>}
      </div>
    </LoadingRegion>
  );
}

export function AdminDashboardSkeleton() {
  return (
    <LoadingRegion label="Loading dashboard…">
      <Heading />
      <div className="admin-kpi-grid mt-5">
        {[0, 1, 2, 3].map((key) => (
          <div key={key} className="admin-panel admin-stat-card">
            <Shape className="size-10" /><Shape className="mt-3 h-4 w-4/5" /><Shape className="mt-2 h-7 w-3/4" />
          </div>
        ))}
      </div>
      <div className="admin-analytics-grid mt-5">
        {[0, 1].map((key) => (
          <div key={key} className="admin-panel admin-analytics-panel grid content-start gap-4 p-5">
            <Shape className="h-5 w-2/5" /><Shape className="h-3 w-3/5" />
            {[0, 1, 2, 3, 4].map((row) => (
              <div key={row} className="grid gap-2">
                <Shape className="h-3 w-1/3" /><Shape className={`h-2 ${row % 2 ? "w-2/3" : "w-full"}`} />
              </div>
            ))}
          </div>
        ))}
      </div>
      <div className="admin-panel mt-5 overflow-hidden"><Rows rows={3} /></div>
    </LoadingRegion>
  );
}

export function AdminFormSkeleton({ label }: { label: string }) {
  return (
    <LoadingRegion label={label}>
      <div className="admin-panel mt-5 p-4 sm:p-5">
        <Shape className="h-5 w-36" />
        <div className="admin-form-grid">
          {[0, 1].map((key) => (
            <div key={key} className="admin-form-section grid gap-3">
              <Shape className="h-4 w-28" />
              <Shape className="h-3 w-20" /><Shape className="h-11 w-full" />
            </div>
          ))}
        </div>
        <div className="admin-form-actions"><Shape className="h-11 w-24" /><Shape className="h-11 w-24" /></div>
      </div>
    </LoadingRegion>
  );
}

export function AdminInvoiceSkeleton() {
  return (
    <LoadingRegion label="Loading invoice details…">
      <Heading />
      <div className="mx-auto mt-5 grid max-w-3xl gap-4">
        <div className="flex justify-between"><Shape className="h-11 w-32" /><Shape className="h-11 w-32" /></div>
        <div className="admin-panel overflow-hidden">
          <div className="grid gap-4 p-5"><Shape className="h-6 w-32" /><Shape className="h-4 w-44" /></div>
          <Rows rows={3} />
        </div>
      </div>
    </LoadingRegion>
  );
}
