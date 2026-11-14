import { describe, expect, it } from "vitest";
import {
  EMPTY_TOUR_FILTERS,
  buildTourApiQuery,
  buildTourHref,
  buildTourSearchParams,
  hasActiveTourFilters,
  parseTourPage,
  readTourFilters,
  tourFiltersFromFormData,
  validateTourFilters,
} from "./tour-filters";

describe("readTourFilters", () => {
  it("keeps valid server parameters", () => {
    const filters = readTourFilters({
      keyword: "  Ha Long  ",
      destinationId: "5",
      minPrice: "1000000",
      maxPrice: "4500000.50",
      startFrom: "2026-12-01",
      startTo: "2026-12-31",
      minDays: "2",
      maxDays: "7",
      sort: "priceAsc",
    });

    expect(filters).toEqual({
      keyword: "Ha Long",
      destinationId: "5",
      minPrice: "1000000",
      maxPrice: "4500000.50",
      startFrom: "2026-12-01",
      startTo: "2026-12-31",
      minDays: "2",
      maxDays: "7",
      sort: "priceAsc",
    });
  });

  it("drops malformed numbers, dates and sort values", () => {
    const filters = readTourFilters({
      keyword: "x".repeat(120),
      destinationId: "0",
      minPrice: "-1",
      maxPrice: "abc",
      startFrom: "2026-02-31",
      startTo: "31/12/2026",
      minDays: "0",
      maxDays: "1.5",
      sort: "cheapest",
    });

    expect(filters.keyword).toHaveLength(100);
    expect(filters).toMatchObject({
      destinationId: "",
      minPrice: "",
      maxPrice: "",
      startFrom: "",
      startTo: "",
      minDays: "",
      maxDays: "",
      sort: "",
    });
  });

  it("uses the first value when a parameter repeats", () => {
    expect(readTourFilters({ destinationId: ["2", "3"] }).destinationId).toBe("2");
  });
});

describe("validateTourFilters", () => {
  it("accepts empty filters", () => {
    expect(validateTourFilters(EMPTY_TOUR_FILTERS)).toBeNull();
  });

  it("rejects a price range in reverse order", () => {
    expect(
      validateTourFilters({ ...EMPTY_TOUR_FILTERS, minPrice: "900", maxPrice: "100" }),
    ).toBe("minPrice must be less than or equal to maxPrice");
  });

  it("rejects a reversed date range", () => {
    expect(
      validateTourFilters({
        ...EMPTY_TOUR_FILTERS,
        startFrom: "2026-12-31",
        startTo: "2026-12-01",
      }),
    ).toBe("startFrom must be on or before startTo");
  });

  it("rejects a reversed duration range", () => {
    expect(
      validateTourFilters({ ...EMPTY_TOUR_FILTERS, minDays: "8", maxDays: "3" }),
    ).toBe("minDays must be less than or equal to maxDays");
  });
});

describe("URL and API query builders", () => {
  const filters = {
    ...EMPTY_TOUR_FILTERS,
    keyword: "lan ha",
    destinationId: "2",
    minDays: "2",
    sort: "dateAsc" as const,
  };

  it("omits empty values and page 0 from the page URL", () => {
    const params = buildTourSearchParams(filters, 0);

    expect(params.toString()).toBe(
      "keyword=lan+ha&destinationId=2&minDays=2&sort=dateAsc",
    );
    expect(buildTourHref(filters)).toBe(
      `/tours?${params.toString()}`,
    );
  });

  it("returns the bare catalogue path for no filters", () => {
    expect(buildTourHref(EMPTY_TOUR_FILTERS)).toBe("/tours");
  });

  it("adds page to links and API queries", () => {
    expect(buildTourHref(filters, 2)).toContain("page=2");
    expect(buildTourApiQuery(filters, 2, 9)).toBe(
      "keyword=lan+ha&destinationId=2&minDays=2&sort=dateAsc&page=2&size=9",
    );
  });

  it("round-trips through the parser", () => {
    const parsed = readTourFilters(
      Object.fromEntries(buildTourSearchParams(filters, 1)),
    );

    expect(parsed).toEqual(filters);
  });
});

describe("hasActiveTourFilters", () => {
  it("detects empty and active states", () => {
    expect(hasActiveTourFilters(EMPTY_TOUR_FILTERS)).toBe(false);
    expect(hasActiveTourFilters({ ...EMPTY_TOUR_FILTERS, sort: "priceAsc" })).toBe(true);
  });
});

describe("parseTourPage", () => {
  it("normalises invalid and negative pages to zero", () => {
    expect(parseTourPage({ page: "2" })).toBe(2);
    expect(parseTourPage({ page: "-2" })).toBe(0);
    expect(parseTourPage({ page: "abc" })).toBe(0);
    expect(parseTourPage({})).toBe(0);
  });
});

describe("tourFiltersFromFormData", () => {
  it("reads the form fields through the same parser", () => {
    const data = new FormData();
    data.set("keyword", " hue ");
    data.set("minPrice", "500000");
    data.set("sort", "priceDesc");

    expect(tourFiltersFromFormData(data)).toEqual({
      ...EMPTY_TOUR_FILTERS,
      keyword: "hue",
      minPrice: "500000",
      sort: "priceDesc",
    });
  });
});
