import { describe, expect, it } from "vitest";
import {
  buildBookingRequest,
  emptyTravelerDraft,
  resizeTravelers,
  travelerPayload,
  validateTravelerDrafts,
  type TravelerDraft,
} from "./booking-form";

function traveler(overrides: Partial<TravelerDraft> = {}): TravelerDraft {
  return { ...emptyTravelerDraft(), fullName: "Lan Nguyen", ...overrides };
}

describe("resizeTravelers", () => {
  it("adds blank rows and keeps existing values", () => {
    const existing = [traveler({ fullName: "Lan" })];

    const next = resizeTravelers(existing, 3);

    expect(next).toHaveLength(3);
    expect(next[0]?.fullName).toBe("Lan");
    expect(next[1]).toEqual(emptyTravelerDraft());
    expect(next[2]).toEqual(emptyTravelerDraft());
  });

  it("drops rows above the new guest count", () => {
    expect(resizeTravelers([traveler(), traveler(), traveler()], 1)).toHaveLength(1);
  });
});

describe("travelerPayload", () => {
  it("omits blank optional fields and trims values", () => {
    expect(
      travelerPayload(
        traveler({
          fullName: "  Lan Nguyen ",
          phone: " 0901234567 ",
          email: "",
          dateOfBirth: "",
          gender: "",
          idDocument: "",
        }),
      ),
    ).toEqual({
      fullName: "Lan Nguyen",
      phone: "0901234567",
      email: undefined,
      dateOfBirth: undefined,
      gender: undefined,
      idDocument: undefined,
    });
  });

  it("keeps a selected gender", () => {
    expect(travelerPayload(traveler({ gender: "FEMALE" })).gender).toBe("FEMALE");
  });
});

describe("buildBookingRequest", () => {
  it("sends exactly one traveler per guest and omits empty note/promotion", () => {
    const request = buildBookingRequest(
      12,
      2,
      "  ",
      [traveler({ fullName: "Lan" }), traveler({ fullName: "Minh" })],
      "",
    );

    expect(request.departureId).toBe(12);
    expect(request.guestCount).toBe(2);
    expect(request.travelers.map((item) => item.fullName)).toEqual(["Lan", "Minh"]);
    expect(request.note).toBeUndefined();
    expect(request.promotionCode).toBeUndefined();
  });

  it("trims and keeps a promotion code and note", () => {
    const request = buildBookingRequest(1, 1, " Late arrival ", [traveler()], " SPRING10 ");

    expect(request.note).toBe("Late arrival");
    expect(request.promotionCode).toBe("SPRING10");
  });
});

describe("validateTravelerDrafts", () => {
  it("accepts a complete traveler", () => {
    expect(validateTravelerDrafts([traveler()])).toBeNull();
  });

  it("requires every full name", () => {
    expect(validateTravelerDrafts([traveler({ fullName: "   " })])).toBe(
      "Traveler 1: full name is required.",
    );
  });

  it("rejects an invalid optional email", () => {
    expect(validateTravelerDrafts([traveler({ email: "not-an-email" })])).toBe(
      "Traveler 1: enter a valid email address.",
    );
  });

  it("rejects overlong optional fields", () => {
    expect(validateTravelerDrafts([traveler({ phone: "1".repeat(21) })])).toContain(
      "phone must be at most 20 characters",
    );
    expect(validateTravelerDrafts([traveler({ idDocument: "1".repeat(65) })])).toContain(
      "ID document must be at most 64 characters",
    );
  });
});
