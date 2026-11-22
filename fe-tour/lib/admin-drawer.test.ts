import { describe, expect, it } from "vitest";
import { getDrawerFocusIndex } from "./admin-drawer";

describe("admin drawer focus", () => {
  it("does not select an index with no focusable items", () => {
    expect(getDrawerFocusIndex(-1, 0, false)).toBeNull();
  });

  it("keeps focus on the only control", () => {
    expect(getDrawerFocusIndex(0, 1, true)).toBe(0);
    expect(getDrawerFocusIndex(0, 1, false)).toBe(0);
  });

  it("wraps focus in both directions", () => {
    expect(getDrawerFocusIndex(3, 4, false)).toBe(0);
    expect(getDrawerFocusIndex(0, 4, true)).toBe(3);
    expect(getDrawerFocusIndex(1, 4, false)).toBe(2);
  });

  it("recovers focus when it is not on a drawer control", () => {
    expect(getDrawerFocusIndex(-1, 4, false)).toBe(0);
    expect(getDrawerFocusIndex(-1, 4, true)).toBe(3);
  });
});
