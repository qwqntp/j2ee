import type { AdminSection } from "@/lib/admin-navigation";

const paths = {
  dashboard: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  bookings: "M4 7h16a1 1 0 0 1 1 1v11H3V8a1 1 0 0 1 1-1z M8 7V5a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2 M3 12h18 M10 12v2h4v-2",
  accounts: "M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M14 7a4 4 0 1 1-8 0 4 4 0 1 1 8 0 M20 21v-2a4 4 0 0 0-3-3.9 M17 3.2a4 4 0 0 1 0 7.6",
  promotions: "M3 4h8l10 10-7 7L4 11z M7.5 7.5h.01",
  invoices: "M6 3h12v18l-3-2-3 2-3-2-3 2z M9 8h6 M9 12h6",
  catalog: "M12 3 3 8l9 5 9-5-9-5z M3 12l9 5 9-5 M3 16l9 5 9-5",
  tours: "M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z M9 3v15 M15 6v15",
  panel: "M3 5h18v14H3z M9 5v14",
  logout: "M10 17l5-5-5-5 M15 12H3 M14 3h6v18h-6",
  close: "m6 6 12 12 M18 6 6 18",
  revenue: "M3 6h18v12H3z M3 10h18 M7 14h3",
  calendar: "M4 6h16v15H4z M4 10h16 M8 3v5 M16 3v5 M8 14h2 M14 14h2",
  alert: "M12 3 2.5 20h19L12 3z M12 9v4 M12 16h.01",
  plus: "M12 5v14 M5 12h14",
  edit: "m4 16 11-11 4 4L8 20H4v-4z M13 7l4 4",
  delete: "M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13 M10 11v5 M14 11v5",
  search: "M15.5 15.5 21 21 M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0z",
} satisfies Record<AdminSection["icon"], string> & Record<string, string>;

export function AdminIcon({ name, className = "size-5" }: {
  name: keyof typeof paths;
  className?: string;
}) {
  return (
    <svg className={className} aria-hidden="true" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d={paths[name]} />
    </svg>
  );
}
