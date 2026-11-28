import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { buildDepartureQuery } from "./admin-departures";
import { AdminDepartureTable } from "../components/admin-departure-table";

describe("read-only departures", () => {
  it("defaults to upcoming departures and translates one-based UI pages", () => {
    expect(buildDepartureQuery(new URLSearchParams())).toBe("page=0&size=10&upcomingOnly=true");
    const result = new URLSearchParams(buildDepartureQuery(new URLSearchParams(
      "page=3&upcomingOnly=false&status=FULL&q=Bay+tour")));
    expect(Object.fromEntries(result)).toEqual({
      page: "2", size: "10", upcomingOnly: "false", status: "FULL", q: "Bay tour",
    });
  });

  it("renders capacity and a passenger link without management controls", () => {
    const html = renderToStaticMarkup(<AdminDepartureTable departures={[{
      id: 7, tourId: 2, tourName: "Bay & mountains", startDate: "2026-12-01T08:00:00",
      endDate: "2026-12-02T17:00:00", status: "FULL", capacity: 20, booked: 20, remaining: 0,
    }]} />);
    expect(html).toContain("Bay &amp; mountains");
    expect(html).toContain("1 Dec 2026");
    expect(html).toContain("2 Dec 2026");
    expect(html).toContain('href="/admin/departures/7/passengers"');
    expect(html).toContain("FULL");
    expect(html).not.toMatch(/Cancel|Edit|Delete|Refund/);
  });
});
