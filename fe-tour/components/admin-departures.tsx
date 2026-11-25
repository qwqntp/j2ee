"use client";

import { useEffect, useState } from "react";
import { useAdminAccess } from "@/components/admin-gate";
import { AdminEmptyState, AdminListPanel, AdminPageHeading, AdminPagination } from "@/components/admin/admin-ui";
import { AdminTableSkeleton } from "@/components/admin/admin-skeletons";
import { AdminDepartureTable } from "@/components/admin-departure-table";
import { buildDepartureQuery, DEPARTURE_QUERY_SCHEMA, listAdminDepartures, type AdminDeparture } from "@/lib/admin-departures";
import { useAdminQueryState } from "@/lib/use-admin-query-state";
import type { PageData } from "@/lib/types";

export function AdminDepartures() {
  const { getSession } = useAdminAccess();
  const { params, update } = useAdminQueryState(DEPARTURE_QUERY_SCHEMA);
  const navigationKey = params.toString();
  const query = buildDepartureQuery(params);
  const page = Number(params.get("page") ?? "1") - 1;
  const [result, setResult] = useState<{ query: string; data: PageData<AdminDeparture> } | null>(null);
  const [failure, setFailure] = useState<{ query: string; message: string } | null>(null);
  const [reload, setReload] = useState(0);
  const data = result?.query === query ? result.data : null;
  const error = failure?.query === query ? failure.message : null;

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const session = await getSession();
        if (!session || !active) return;
        const next = await listAdminDepartures(session.accessToken, query);
        if (active) { setResult({ query, data: next }); setFailure(null); }
      } catch (err) {
        if (active) setFailure({ query, message: err instanceof Error ? err.message : "Could not load departures." });
      }
    }
    void load();
    return () => { active = false; };
  }, [query, getSession, reload]);

  return <div>
    <AdminPageHeading title="Departures" description="Read-only departure availability and passenger manifests." />
    <AdminListPanel>
      <form key={navigationKey} className="admin-filter-bar admin-toolbar" aria-label="Filter departures"
        onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          update({ page: null, q: String(form.get("q") ?? "").trim(),
            status: String(form.get("status") ?? ""), upcomingOnly: String(form.get("upcomingOnly") ?? "true") });
        }}>
        <label className="admin-toolbar-search"><span className="sr-only">Tour name</span>
          <input name="q" className="field" type="search" maxLength={100} placeholder="Tour name" defaultValue={params.get("q") ?? ""} />
        </label>
        <div className="admin-toolbar-row">
          <label className="admin-toolbar-control">Status<select name="status" className="field" defaultValue={params.get("status") ?? ""}>
            <option value="">All statuses</option>{["OPEN", "FULL", "CLOSED", "CANCELLED"].map((s) => <option key={s}>{s}</option>)}
          </select></label>
          <label className="admin-toolbar-control">Dates<select name="upcomingOnly" className="field" defaultValue={params.get("upcomingOnly") ?? "true"}>
            <option value="true">Upcoming only</option><option value="false">All dates</option>
          </select></label>
          <div className="admin-filter-actions"><button type="submit" className="btn-primary admin-toolbar-primary">Search</button>
            <button type="reset" className="admin-toolbar-ghost" onClick={() => update({ page: null, q: null, status: null, upcomingOnly: null })}>Reset</button></div>
        </div>
      </form>
      {error ? <p role="alert" className="admin-notice">{error} <button type="button" className="admin-row-action" onClick={() => setReload((n) => n + 1)}>Try again</button></p>
        : !data ? <AdminTableSkeleton label="Loading departures…" embedded />
        : data.content.length === 0 ? <AdminEmptyState title="No departures found" description="Try another tour name, status or date filter." />
        : <AdminDepartureTable departures={data.content} />}
      {data && !error && <AdminPagination page={page} totalPages={data.totalPages} totalElements={data.totalElements}
        hasNext={data.hasNext} loading={false} onPageChange={(next) => update({ page: String(next + 1) })} />}
    </AdminListPanel>
  </div>;
}
