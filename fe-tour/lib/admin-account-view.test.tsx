import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { AdminAccountRow, AdminAccountDetails } from "../components/admin/admin-account-view";
import type { AdminAccount } from "./types";

const account: AdminAccount = {
  id: 7, username: "traveler", email: "traveler@example.com", emailVerified: 1,
  status: 1, roleId: 2, roleName: "ROLE_USER", createdAt: "2025-01-02T00:00:00Z",
};

describe("admin account presentation", () => {
  it("shows compact account columns and labeled icon-only actions", () => {
    const html = renderToStaticMarkup(<AdminAccountRow account={account} protectedAdmin={false}
      deleting={false} onDetails={() => {}} onEdit={() => {}} onDelete={() => {}} />);
    expect(html).not.toContain(account.email);
    expect(html).not.toContain("Verified");
    expect(html).toContain('aria-label="View traveler details"');
    expect(html.indexOf("View traveler details")).toBeLessThan(html.indexOf("Edit traveler"));
    expect(html).toContain('aria-label="Delete traveler"');
    expect(html).not.toContain("</svg> Edit");
    expect(html).not.toContain("</svg> Delete");
  });

  it("retains removed account information in the details view", () => {
    const html = renderToStaticMarkup(<AdminAccountDetails account={account} />);
    expect(html).toContain("traveler@example.com");
    expect(html).toContain("Verified");
    expect(html).toContain("Created");
    expect(html).toContain("2025");
  });
});
