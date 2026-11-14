export const TOUR_SORT_VALUES = ["newest", "priceAsc", "priceDesc", "dateAsc"] as const;
export type TourSort = (typeof TOUR_SORT_VALUES)[number];

export const TOUR_SORT_OPTIONS: ReadonlyArray<{ value: TourSort; label: string }> = [
  { value: "newest", label: "Newest journeys" },
  { value: "priceAsc", label: "Price: low to high" },
  { value: "priceDesc", label: "Price: high to low" },
  { value: "dateAsc", label: "Next departure" },
];

export type TourFilters = {
  keyword: string;
  destinationId: string;
  minPrice: string;
  maxPrice: string;
  startFrom: string;
  startTo: string;
  minDays: string;
  maxDays: string;
  sort: TourSort | "";
};

export type TourSearchParams = Record<string, string | string[] | undefined>;

export const EMPTY_TOUR_FILTERS: TourFilters = {
  keyword: "",
  destinationId: "",
  minPrice: "",
  maxPrice: "",
  startFrom: "",
  startTo: "",
  minDays: "",
  maxDays: "",
  sort: "",
};

function first(value: string | string[] | undefined): string {
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function integer(value: string, minimum: number): string {
  if (!/^\d+$/.test(value)) return "";
  const parsed = Number.parseInt(value, 10);
  return Number.isSafeInteger(parsed) && parsed >= minimum ? String(parsed) : "";
}

function decimal(value: string): string {
  if (!/^\d+(?:\.\d{1,2})?$/.test(value)) return "";
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? value : "";
}

function isoDate(value: string): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return "";
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
    ? value
    : "";
}

function sort(value: string): TourSort | "" {
  return TOUR_SORT_VALUES.find((option) => option === value) ?? "";
}

/** Normalise a raw query string/FormData record into the shared filter shape. */
export function readTourFilters(values: TourSearchParams): TourFilters {
  return {
    keyword: first(values.keyword).trim().slice(0, 100),
    destinationId: integer(first(values.destinationId).trim(), 1),
    minPrice: decimal(first(values.minPrice).trim()),
    maxPrice: decimal(first(values.maxPrice).trim()),
    startFrom: isoDate(first(values.startFrom).trim()),
    startTo: isoDate(first(values.startTo).trim()),
    minDays: integer(first(values.minDays).trim(), 1),
    maxDays: integer(first(values.maxDays).trim(), 1),
    sort: sort(first(values.sort).trim()),
  };
}

/** Same parity rules as `TourService.validate`; the backend remains the final authority. */
export function validateTourFilters(filters: TourFilters): string | null {
  if (filters.minPrice && filters.maxPrice && Number(filters.minPrice) > Number(filters.maxPrice)) {
    return "minPrice must be less than or equal to maxPrice";
  }
  if (filters.startFrom && filters.startTo && filters.startFrom > filters.startTo) {
    return "startFrom must be on or before startTo";
  }
  if (filters.minDays && filters.maxDays && Number(filters.minDays) > Number(filters.maxDays)) {
    return "minDays must be less than or equal to maxDays";
  }
  return null;
}

export function hasActiveTourFilters(filters: TourFilters): boolean {
  return (
    filters.keyword.length > 0 ||
    filters.destinationId.length > 0 ||
    filters.minPrice.length > 0 ||
    filters.maxPrice.length > 0 ||
    filters.startFrom.length > 0 ||
    filters.startTo.length > 0 ||
    filters.minDays.length > 0 ||
    filters.maxDays.length > 0 ||
    filters.sort.length > 0
  );
}

/** URL state for the catalogue page. Empty values and page 0 are omitted. */
export function buildTourSearchParams(filters: TourFilters, page = 0): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.keyword) params.set("keyword", filters.keyword);
  if (filters.destinationId) params.set("destinationId", filters.destinationId);
  if (filters.minPrice) params.set("minPrice", filters.minPrice);
  if (filters.maxPrice) params.set("maxPrice", filters.maxPrice);
  if (filters.startFrom) params.set("startFrom", filters.startFrom);
  if (filters.startTo) params.set("startTo", filters.startTo);
  if (filters.minDays) params.set("minDays", filters.minDays);
  if (filters.maxDays) params.set("maxDays", filters.maxDays);
  if (filters.sort) params.set("sort", filters.sort);
  if (page > 0) params.set("page", String(page));
  return params;
}

export function buildTourHref(filters: TourFilters, page = 0): string {
  const search = buildTourSearchParams(filters, page).toString();
  return search ? `/tours?${search}` : "/tours";
}

/** API query, including paging; keeps the backend parameter names exactly. */
export function buildTourApiQuery(filters: TourFilters, page: number, size: number): string {
  const params = buildTourSearchParams(filters, page);
  params.set("page", String(page));
  params.set("size", String(size));
  return params.toString();
}

export function parseTourPage(values: TourSearchParams): number {
  const raw = Number.parseInt(first(values.page), 10);
  return Number.isInteger(raw) && raw > 0 ? raw : 0;
}

/** FormData is a record of strings; this keeps the form and URL parser in one place. */
export function tourFiltersFromFormData(data: FormData): TourFilters {
  const values: TourSearchParams = {};
  for (const [key, value] of data.entries()) {
    if (typeof value === "string") values[key] = value;
  }
  return readTourFilters(values);
}
