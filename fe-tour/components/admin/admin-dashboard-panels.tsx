import { AdminIcon } from "./admin-icons";
import { buildBookingBars, type DashboardCard } from "@/lib/admin-stats";

export function AdminStatCard({ card }: { card: DashboardCard }) {
  return (
    <div className="admin-panel admin-stat-card">
      <dt className="text-sm font-medium text-muted">
        <span aria-hidden="true" className={`admin-stat-icon admin-stat-${card.tone}`}><AdminIcon name={card.icon} /></span>
        {card.label}
      </dt>
      <dd className="mt-1 break-words text-2xl font-semibold tracking-tight tabular-nums">{card.value}</dd>
    </div>
  );
}

export function AdminBarPanel({ title, description, rows, empty }: {
  title: string;
  description: string;
  rows: { key: string | number; label: string; count: number }[];
  empty: string;
}) {
  const bars = buildBookingBars(rows.map(({ count }) => count));
  return (
    <section className="admin-panel admin-analytics-panel min-w-0 p-5">
      <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
      <p className="mt-1 text-sm text-muted">{description}</p>
      {rows.length === 0 ? <p className="mt-5 rounded-lg bg-ivory p-4 text-sm text-muted">{empty}</p> : (
        <ul className="mt-5 space-y-4">
          {rows.map(({ key, label, count }, index) => (
            <li key={key}>
              <div className="flex items-start justify-between gap-3 text-sm">
                <span className="min-w-0 wrap-anywhere text-charcoal">{label}</span>
                <span className="shrink-0 font-semibold tabular-nums">{count}</span>
              </div>
              <div aria-hidden="true" className="admin-bar-track mt-1.5">
                <div className="admin-bar-fill" style={{ width: `${bars[index].width}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
