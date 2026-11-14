import { describe, expect, it } from "vitest";
import {
  profilePayload,
  validatePasswordChange,
  validateProfile,
  type ProfileFormValues,
} from "./profile";

const values: ProfileFormValues = {
  name: "Lan Nguyen",
  email: "lan@example.com",
  phone: "0901234567",
  address: "Hoi An",
  dateOfBirth: "1990-05-12",
};

describe("profilePayload", () => {
  it("trims values and omits blank optional fields", () => {
    expect(
      profilePayload({
        name: "  Lan Nguyen ",
        email: " ",
        phone: "",
        address: " Hoi An ",
        dateOfBirth: "",
      }),
    ).toEqual({
      name: "Lan Nguyen",
      email: undefined,
      phone: undefined,
      address: "Hoi An",
      dateOfBirth: undefined,
    });
  });
});

describe("validateProfile", () => {
  it("accepts a complete profile", () => {
    expect(validateProfile(values, new Date(2026, 10, 14))).toBeNull();
  });

  it("requires a name", () => {
    expect(validateProfile({ ...values, name: "  " })).toBe("Name is required.");
  });

  it("rejects an invalid email", () => {
    expect(validateProfile({ ...values, email: "nope" })).toBe("Email must be valid.");
  });

  it("rejects optional fields over the backend limits", () => {
    expect(validateProfile({ ...values, phone: "1".repeat(21) })).toContain("Phone");
    expect(validateProfile({ ...values, address: "1".repeat(256) })).toContain("Address");
  });

  it("rejects a future or malformed date of birth", () => {
    expect(
      validateProfile({ ...values, dateOfBirth: "2026-11-15" }, new Date(2026, 10, 14)),
    ).toBe("Date of birth must not be in the future.");
    expect(validateProfile({ ...values, dateOfBirth: "1990-02-31" })).toBe(
      "Date of birth must be a valid date.",
    );
  });
});

describe("validatePasswordChange", () => {
  it("accepts a valid change", () => {
    expect(validatePasswordChange("old-password", "new-password", "new-password")).toBeNull();
  });

  it("requires the current password", () => {
    expect(validatePasswordChange("", "new-password", "new-password")).toBe(
      "Current password is required.",
    );
  });

  it("enforces length, confirmation and difference", () => {
    expect(validatePasswordChange("old-password", "short", "short")).toBe(
      "Password must be between 8 and 100 characters.",
    );
    expect(validatePasswordChange("old-password", "new-password", "other-password")).toBe(
      "New passwords do not match.",
    );
    expect(validatePasswordChange("same-password", "same-password", "same-password")).toBe(
      "New password must be different from the current password.",
    );
  });

  it("enforces the BCrypt byte limit for multibyte passwords", () => {
    const multibyte = "ế".repeat(40);

    expect(validatePasswordChange("old-password", multibyte, multibyte)).toBe(
      "Password must be at most 72 bytes.",
    );
  });
});
