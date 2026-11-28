import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { AdminPageAccess } from "../components/admin-page-access";

function ForbiddenChild(): never { throw new Error("Forbidden page mounted"); }

describe("page permission boundary", () => {
  it("renders a useful denial without mounting forbidden page children", () => {
    const html = renderToStaticMarkup(<AdminPageAccess role="ROLE_STAFF" pathname="/admin">
      <ForbiddenChild />
    </AdminPageAccess>);
    expect(html).toContain("No permission");
    expect(html).toContain('href="/admin/bookings"');
  });

  it("renders shared pages for staff and the dashboard for admin", () => {
    expect(renderToStaticMarkup(<AdminPageAccess role="ROLE_STAFF" pathname="/admin/departures/7/passengers">
      <p>Manifest</p>
    </AdminPageAccess>)).toBe("<p>Manifest</p>");
    expect(renderToStaticMarkup(<AdminPageAccess role="ROLE_ADMIN" pathname="/admin">
      <p>Dashboard</p>
    </AdminPageAccess>)).toBe("<p>Dashboard</p>");
  });
});
