import { describe, expect, it } from "vitest";
import { buildPassengerCsv } from "./admin-passengers";

describe("buildPassengerCsv", () => {
  it("quotes commas, quotes and newlines and neutralizes spreadsheet formulas", () => {
    const csv = buildPassengerCsv([{
      fullName: '=HYPERLINK("x")', phone: "+123", email: "a,b@example.com",
      dateOfBirth: null, gender: null, idDocument: null, bookingId: 1,
      bookingCode: "B1", bookingStatus: "PAID", contactNote: "one\ntwo",
    }]);
    expect(csv).toContain(`"'=HYPERLINK(""x"")"`);
    expect(csv).toContain("\"'+123\"");
    expect(csv).toContain('"a,b@example.com"');
    expect(csv).toContain('"one\ntwo"');
  });
});
