import { describe, expect, it } from "vitest";
import { lastAvailablePage } from "./admin-pagination";

describe("lastAvailablePage", () => {
  it("keeps a valid page and returns to the last page after records are removed", () => {
    expect(lastAvailablePage(1, 3)).toBe(1);
    expect(lastAvailablePage(3, 2)).toBe(1);
    expect(lastAvailablePage(1, 0)).toBe(0);
  });
});
