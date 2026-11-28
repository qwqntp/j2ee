import { beforeEach, describe, expect, it, vi } from "vitest";
import { changeOperationalBookingStatus } from "./admin-booking-actions";

const api = vi.hoisted(() => ({
  confirmBooking: vi.fn(), completeBooking: vi.fn(), cancelBooking: vi.fn(), updateBookingStatus: vi.fn(),
}));
vi.mock("./api", () => api);

describe("staff booking actions", () => {
  beforeEach(() => vi.clearAllMocks());
  it("uses dedicated operations rather than generic status writes for staff", async () => {
    await changeOperationalBookingStatus("token", 7, "CONFIRMED", "ROLE_STAFF");
    await changeOperationalBookingStatus("token", 7, "COMPLETED", "ROLE_STAFF");
    await changeOperationalBookingStatus("token", 7, "CANCELLED", "ROLE_STAFF");
    expect(api.confirmBooking).toHaveBeenCalledWith("token", 7);
    expect(api.completeBooking).toHaveBeenCalledWith("token", 7);
    expect(api.cancelBooking).toHaveBeenCalledWith("token", 7);
    expect(api.updateBookingStatus).not.toHaveBeenCalled();
  });
  it("preserves admin behavior and rejects unlisted staff mutations", async () => {
    await changeOperationalBookingStatus("token", 7, "CONFIRMED", "ROLE_ADMIN");
    expect(api.updateBookingStatus).toHaveBeenCalledWith("token", 7, "CONFIRMED");
    expect(() => changeOperationalBookingStatus("token", 7, "PAID", "ROLE_STAFF")).toThrow("not permitted");
    expect(() => changeOperationalBookingStatus("token", 7, "CONFIRMED", "ROLE_USER")).toThrow("not permitted");
  });
});
