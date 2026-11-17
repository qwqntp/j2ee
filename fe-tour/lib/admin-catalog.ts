import type {
  CatalogInput,
  CatalogInputFor,
  CatalogResource,
} from "./types";

export type {
  AccommodationRow,
  CatalogInput,
  CatalogResource,
  CatalogRow,
  DestinationRow,
  GuideRow,
  ImageRow,
  VehicleRow,
} from "./types";

export type CatalogTab = {
  resource: CatalogResource;
  label: string;
};

export function catalogTabs(): CatalogTab[] {
  return [
    { resource: "destinations", label: "Destinations" },
    { resource: "accommodations", label: "Accommodations" },
    { resource: "vehicles", label: "Travel vehicles" },
    { resource: "guides", label: "Tour guides" },
    { resource: "images", label: "Tour images" },
  ];
}

const RESOURCE_PATHS: Record<CatalogResource, string> = {
  destinations: "/api/tourist-destinations",
  accommodations: "/api/accommodations",
  vehicles: "/api/travel-vehicles",
  guides: "/api/tour-guides",
  images: "/api/tour-images",
};

export function catalogResourceFromPath(resource: CatalogResource): { path: string; idField: "id" } {
  return { path: RESOURCE_PATHS[resource], idField: "id" };
}

function trimmed(form: Record<string, string>, field: string): string {
  return (form[field] ?? "").trim();
}

function requiredString(form: Record<string, string>, field: string): string {
  const value = trimmed(form, field);
  if (!value) throw new Error(`${field} is required`);
  return value;
}

function optionalString(form: Record<string, string>, field: string): string | undefined {
  return trimmed(form, field) || undefined;
}

function numberValue(
  form: Record<string, string>,
  field: string,
  required: boolean,
): number | undefined {
  const raw = trimmed(form, field);
  if (!raw) {
    if (required) throw new Error(`${field} is required`);
    return undefined;
  }
  const value = Number(raw);
  if (!Number.isFinite(value)) throw new Error(`${field} must be a number`);
  return value;
}

export function catalogInput<R extends CatalogResource>(
  resource: R,
  form: Record<string, string>,
): CatalogInputFor<R> {
  let result: CatalogInput;

  switch (resource) {
    case "destinations":
      result = {
        name: requiredString(form, "name"),
        ...(optionalString(form, "location") !== undefined && { location: optionalString(form, "location") }),
        ...(optionalString(form, "description") !== undefined && { description: optionalString(form, "description") }),
        ...(numberValue(form, "entryFee", false) !== undefined && { entryFee: numberValue(form, "entryFee", false) }),
      };
      break;
    case "accommodations":
      result = {
        name: requiredString(form, "name"),
        location: requiredString(form, "location"),
        ...(numberValue(form, "rating", false) !== undefined && { rating: numberValue(form, "rating", false) }),
        ...(numberValue(form, "pricePerNight", false) !== undefined && {
          pricePerNight: numberValue(form, "pricePerNight", false),
        }),
        ...(optionalString(form, "type") !== undefined && { type: optionalString(form, "type") as
          "HOTEL" | "RESORT" | "HOMESTAY" }),
      };
      break;
    case "vehicles":
      result = {
        type: requiredString(form, "type"),
        capacity: numberValue(form, "capacity", true) as number,
        rentalPricePerDay: numberValue(form, "rentalPricePerDay", true) as number,
      };
      break;
    case "guides":
      result = {
        name: requiredString(form, "name"),
        ...(optionalString(form, "email") !== undefined && { email: optionalString(form, "email") }),
        ...(optionalString(form, "phone") !== undefined && { phone: optionalString(form, "phone") }),
        experienceYears: numberValue(form, "experienceYears", true) as number,
      };
      break;
    case "images":
      result = {
        imageUrl: requiredString(form, "imageUrl"),
        tourId: numberValue(form, "tourId", true) as number,
      };
      break;
  }

  return result as CatalogInputFor<R>;
}
