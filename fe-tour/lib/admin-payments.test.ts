import { describe, expect, it } from "vitest";
import { buildPaymentQuery } from "./admin-payments";

describe("buildPaymentQuery", () => {
  it("serializes filters and omits empty values", () => {
    expect(buildPaymentQuery({ page: 0, status: "PENDING", method: "VNPAY", from: "2026-01-01", to: "", q: " B 1 " }))
      .toBe("page=0&size=10&status=PENDING&method=VNPAY&from=2026-01-01&q=B+1");
  });
  it("rejects a reversed range", () => {
    expect(() => buildPaymentQuery({ page: 0, status: "", method: "", from: "2026-02-01", to: "2026-01-01", q: "" })).toThrow();
  });
});
