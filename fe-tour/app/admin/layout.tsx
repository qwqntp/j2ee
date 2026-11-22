import { Suspense } from "react";
import { AdminLayout } from "@/components/admin/admin-layout";
import { AdminGate } from "@/components/admin-gate";
import "./admin.css";

export default function AdminRouteLayout({ children }: LayoutProps<"/admin">) {
  return (
    <AdminGate>
      <Suspense fallback={<main className="admin-access" aria-busy="true">Loading admin workspace…</main>}>
        <AdminLayout>{children}</AdminLayout>
      </Suspense>
    </AdminGate>
  );
}
