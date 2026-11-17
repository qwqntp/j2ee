import { describe, expect, it } from "vitest";
import { catalogInput, catalogResourceFromPath, catalogTabs } from "./admin-catalog";

describe("catalogInput", () => {
  it("converts numeric catalog fields to API numbers", () => {
    expect(catalogInput("vehicles", { type: "Bus", capacity: "40", rentalPricePerDay: "3000000" })).toEqual({
      type: "Bus", capacity: 40, rentalPricePerDay: 3000000,
    });
  });

  it("trims strings and omits blank optional fields", () => {
    expect(catalogInput("destinations", {
      name: "  Ha Long  ",
      location: " Quang Ninh ",
      entryFee: "  ",
    })).toEqual({ name: "Ha Long", location: "Quang Ninh" });
  });

  it("rejects empty mandatory fields with the field name", () => {
    expect(() => catalogInput("destinations", { name: "  " })).toThrow("name is required");
    expect(() => catalogInput("accommodations", { name: "Hotel", location: "  " })).toThrow("location is required");
    expect(() => catalogInput("guides", { name: "Guide", experienceYears: " " })).toThrow("experienceYears is required");
    expect(() => catalogInput("images", { imageUrl: "", tourId: "1" })).toThrow("imageUrl is required");
  });

  it("requires numeric values for number fields and converts them", () => {
    expect(() => catalogInput("vehicles", { type: "Bus", capacity: "abc", rentalPricePerDay: "10" })).toThrow(
      "capacity must be a number",
    );
    expect(catalogInput("images", { imageUrl: " https://x/y.jpg ", tourId: " 7 " })).toEqual({
      imageUrl: "https://x/y.jpg", tourId: 7,
    });
  });
});

describe("catalogTabs", () => {
  it("returns only supported resources in tab order", () => {
    expect(catalogTabs().map((tab) => tab.resource)).toEqual([
      "destinations", "accommodations", "vehicles", "guides", "images",
    ]);
  });

  it("labels each tab for display", () => {
    expect(catalogTabs().map((tab) => tab.label)).toEqual([
      "Destinations", "Accommodations", "Travel vehicles", "Tour guides", "Tour images",
    ]);
  });
});

describe("catalogResourceFromPath", () => {
  it("maps resource names to API paths", () => {
    expect(catalogResourceFromPath("destinations")).toEqual({ path: "/api/tourist-destinations", idField: "id" });
    expect(catalogResourceFromPath("images")).toEqual({ path: "/api/tour-images", idField: "id" });
  });

  it("maps the remaining resources to their API paths", () => {
    expect(catalogResourceFromPath("accommodations").path).toBe("/api/accommodations");
    expect(catalogResourceFromPath("vehicles").path).toBe("/api/travel-vehicles");
    expect(catalogResourceFromPath("guides").path).toBe("/api/tour-guides");
  });
});
