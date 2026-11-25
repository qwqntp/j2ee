import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { AdminAccountEditor } from "../components/admin/admin-account-editor";
import type { ReactNode } from "react";

vi.mock("@/components/admin-gate", () => ({ useAdminAccess: () => ({ getSession: vi.fn() }) }));
vi.mock("@/lib/admin-accounts", () => ({ accountStatusLabel: () => "Active" }));
vi.mock("@/lib/api", () => ({ createAdminAccount: vi.fn(), updateAdminAccount: vi.fn() }));
vi.mock("../components/admin/admin-form-dialog", () => ({
  AdminFormDialog: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

describe("account role options", () => {
  it("renders DB-provided Staff with its assignable role ID", () => {
    const html = renderToStaticMarkup(<AdminAccountEditor roles={[
      { id: 1, roleName: "ROLE_ADMIN" }, { id: 2, roleName: "ROLE_USER" }, { id: 3, roleName: "ROLE_STAFF" },
    ]} protectedAdmin={false} onDismiss={() => {}} onSaved={() => {}} />);
    expect(html).toContain('<option value="3">Staff</option>');
    expect(html).toContain("Select role");
  });
});
