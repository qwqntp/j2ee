import Link from "next/link";
import { canAccessAdminPage, consoleHome } from "../lib/admin-permissions";

export function AdminPageAccess({ role, pathname, children }: {
  role: string | null; pathname: string; children: React.ReactNode;
}) {
  if (canAccessAdminPage(role, pathname)) return children;
  return (
    <div role="alert" className="admin-panel p-6">
      <h1 className="text-2xl font-semibold">No permission</h1>
      <p className="mt-2 text-sm text-muted">Your role does not have access to this page.</p>
      <Link className="btn-secondary mt-4" href={consoleHome(role)}>Back to bookings</Link>
    </div>
  );
}
