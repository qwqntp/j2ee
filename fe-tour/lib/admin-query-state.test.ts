import { describe, expect, it } from "vitest";
import { normalizeAdminQuery, updateAdminQuery } from "./admin-query-state";

describe("normalizeAdminQuery", () => {
  const schema = {
    page: { kind: "page" as const },
    size: { kind: "number" as const, allowed: [10, 20, 50], defaultValue: 10 },
    status: { kind: "enum" as const, allowed: ["PAID", "PENDING"] },
    from: { kind: "date" as const },
    to: { kind: "date" as const },
  };

  it("keeps unrelated parameters and removes invalid, duplicated and default values", () => {
    const result = normalizeAdminQuery(
      new URLSearchParams("other=ok&page=-2&size=999&status=PAID&status=PENDING&from=2025-02-30"),
      schema,
    );
    expect(result.toString()).toBe("other=ok");
  });

  it("accepts valid one-based pages and filters", () => {
    const result = normalizeAdminQuery(
      new URLSearchParams("page=3&size=20&status=PAID&from=2025-02-28"),
      schema,
    );
    expect(result.toString()).toBe("page=3&size=20&status=PAID&from=2025-02-28");
  });

  it("drops invalid reversed date ranges", () => {
    const result = normalizeAdminQuery(new URLSearchParams("from=2025-03-01&to=2025-02-01"), schema);
    expect(result.toString()).toBe("");
  });

  it("updates individual parameters without discarding unrelated values", () => {
    const result = updateAdminQuery(new URLSearchParams("page=3&other=ok"), schema, { page: "2" });
    expect(result.toString()).toBe("page=2&other=ok");
  });

  it("omits a return to the first page", () => {
    const result = updateAdminQuery(new URLSearchParams("page=3"), schema, { page: "1" });
    expect(result.toString()).toBe("");
  });

  it("removes sensitive search keys rather than copying them to another URL", () => {
    const privateSchema = { page: { kind: "page" as const }, q: { kind: "enum" as const, allowed: [] } };
    const result = updateAdminQuery(new URLSearchParams("page=2&q=private"), privateSchema, { page: "3" });
    expect(result.toString()).toBe("page=3");
  });

  it("omits default sorting while preserving a valid non-default direction", () => {
    const sortSchema = {
      sort: { kind: "enum" as const, allowed: ["createdAt", "totalAmount"], defaultValue: "createdAt" },
      direction: { kind: "enum" as const, allowed: ["asc", "desc"], defaultValue: "desc" },
    };
    expect(normalizeAdminQuery(new URLSearchParams("sort=createdAt&direction=asc"), sortSchema).toString())
      .toBe("direction=asc");
  });
});
