import { describe, expect, it } from "vitest";
import { buildInvoiceListQuery, toInvoiceStatusParam } from "./admin-invoices";

describe("buildInvoiceListQuery", () => {
  it("sends page 0 and size 10 by default", () => {
    expect(buildInvoiceListQuery({ page: 0, size: 10 })).toBe("page=0&size=10");
  });

  it("encodes full-dataset filters and sort", () => {
    expect(
      buildInvoiceListQuery({
        page: 2,
        size: 20,
        status: "UNPAID",
        q: "  an@example.com  ",
        sort: "createdAt,asc",
      }),
    ).toBe(
      "page=2&size=20&status=UNPAID&q=an%40example.com&sort=createdAt%2Casc",
    );
  });

  it("omits the UI-only All status", () => {
    expect(toInvoiceStatusParam("All")).toBeUndefined();
    expect(toInvoiceStatusParam("PAID")).toBe("PAID");
    expect(buildInvoiceListQuery({ page: 0, size: 10, status: toInvoiceStatusParam("All") }))
      .toBe("page=0&size=10");
  });

  it("rejects an unknown status or sort", () => {
    expect(() =>
      buildInvoiceListQuery({ page: 0, size: 10, status: "CANCELLED" as never }),
    ).toThrow("Invalid status");
    expect(() =>
      buildInvoiceListQuery({ page: 0, size: 10, sort: "customerName,asc" as never }),
    ).toThrow("Invalid sort");
  });
});
