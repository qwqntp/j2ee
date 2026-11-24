import { describe, expect, it } from "vitest";
import { adminActionStyles } from "../components/admin/admin-action-styles";

describe("admin icon action styles", () => {
  it("uses consistent accessible-sized bordered actions with matching hover colors", () => {
    for (const style of Object.values(adminActionStyles)) {
      expect(style).toContain("size-11");
      expect(style).toContain("border-current");
      expect(style).toContain("hover:text-white");
      expect(style).toContain("hover:border-transparent");
    }
    expect(adminActionStyles.view).toContain("hover:bg-[#0084ff]");
    expect(adminActionStyles.edit).toContain("hover:bg-charcoal");
    expect(adminActionStyles.delete).toContain("hover:bg-red-800");
  });
});
