import { describe, expect, it } from "vitest";
import { isDialogBackdropClick } from "./admin-dialog";

describe("dialog backdrop clicks", () => {
  const rect = { left: 20, right: 220, top: 30, bottom: 130 };

  it("recognizes clicks outside the dialog bounds", () => {
    expect(isDialogBackdropClick(rect, 19, 50)).toBe(true);
    expect(isDialogBackdropClick(rect, 100, 131)).toBe(true);
  });

  it("does not dismiss for a click inside or on the dialog edge", () => {
    expect(isDialogBackdropClick(rect, 100, 80)).toBe(false);
    expect(isDialogBackdropClick(rect, 20, 30)).toBe(false);
  });
});
