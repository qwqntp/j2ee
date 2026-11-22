import { ApiError } from "./api";
import type { Booking, VnpayStatus } from "./types";

export type VnpayPollResult = "paid" | "cancelled" | "timeout" | "sign-in" | "invalid";

export function pollVnpayBooking(
  initial: Pick<VnpayStatus, "bookingId" | "status">,
  readBookingStatus: (bookingId: number, signal: AbortSignal) => Promise<Booking["status"] | null>,
  onFinish: (result: VnpayPollResult) => void,
): () => void {
  if (initial.status !== "PENDING") return () => {};

  const controller = new AbortController();
  const { signal } = controller;
  let next: ReturnType<typeof setTimeout>;
  const deadline = setTimeout(() => finish("timeout"), 30_000);

  function stop() {
    clearTimeout(deadline);
    clearTimeout(next);
    controller.abort();
  }

  function finish(result: VnpayPollResult) {
    if (signal.aborted) return;
    stop();
    onFinish(result);
  }

  async function check() {
    try {
      const status = await readBookingStatus(initial.bookingId, signal);
      if (status === "PAID" || status === "CONFIRMED" || status === "COMPLETED") return finish("paid");
      if (status === "CANCELLED") return finish("cancelled");
      if (status === null) return finish("sign-in");
    } catch (error) {
      if (error instanceof ApiError && (error.status === 403 || error.status === 404)) return finish("invalid");
      // A failed read does not say anything about the payment. Try again within the deadline.
    }
    if (!signal.aborted) next = setTimeout(check, 2_500);
  }

  next = setTimeout(check, 2_500);
  return stop;
}
