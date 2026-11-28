import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { AdminGateContent, useAdminAccess } from "../components/admin-gate";
import type { AuthSession } from "./types";

vi.mock("@/lib/admin-session", () => ({ getAdminRecheckAt: vi.fn() }));
vi.mock("@/lib/admin-permissions", async () => import("./admin-permissions"));
vi.mock("@/lib/auth", () => ({ ensureSession: vi.fn(), getStoredSession: vi.fn(), useSession: vi.fn() }));

const session = (roleName: string) => ({ accessToken: "fixture", account: { id: 7, roleName } }) as AuthSession;
function Child() { return <p>{useAdminAccess().account.roleName}</p>; }
function render(validated: AuthSession | null | undefined, stored = validated ?? null) {
  return renderToStaticMarkup(<AdminGateContent session={validated} stored={stored}
    getSession={async () => validated ?? null}><Child /></AdminGateContent>);
}

describe("admin area gate", () => {
  it("keeps checking and sign-in states without mounting protected content", () => {
    expect(render(undefined)).toContain("Checking admin access");
    expect(render(null)).toContain("Admin sign-in required");
  });
  it("allows validated staff and admin sessions", () => {
    expect(render(session("ROLE_STAFF"))).toBe("<p>ROLE_STAFF</p>");
    expect(render(session("ROLE_ADMIN"))).toBe("<p>ROLE_ADMIN</p>");
  });
  it("blocks customers and forged stored privilege labels", () => {
    expect(render(session("ROLE_USER"))).toContain("Workspace access required");
    expect(render(session("ROLE_USER"), session("ROLE_ADMIN"))).toContain("Workspace access required");
    expect(render(session("ROLE_STAFF"), session("ROLE_ADMIN"))).toContain("Workspace access required");
  });
});
