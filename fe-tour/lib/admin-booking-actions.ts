import { cancelBooking, completeBooking, confirmBooking, updateBookingStatus } from "./api";
import type { Booking } from "./types";

export function changeOperationalBookingStatus(
  token: string, id: number, status: string, role: string | null,
): Promise<Booking> {
  if (role === "ROLE_ADMIN") return updateBookingStatus(token, id, status);
  if (role === "ROLE_STAFF") {
    if (status === "CONFIRMED") return confirmBooking(token, id);
    if (status === "COMPLETED") return completeBooking(token, id);
    if (status === "CANCELLED") return cancelBooking(token, id);
  }
  throw new Error("This booking action is not permitted for your role.");
}
