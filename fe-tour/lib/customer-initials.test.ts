import { describe, expect, it } from "vitest";
import { customerInitials } from "./customer-initials";

describe("customerInitials", () => {
  it("uses the first and last name initials", () => {
    expect(customerInitials("  Nguyễn  Văn An  ")).toBe("NA");
  });

  it("handles single-word and blank names", () => {
    expect(customerInitials("alice")).toBe("A");
    expect(customerInitials("  ")).toBe("?");
  });
});
