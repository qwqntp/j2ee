import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { pollVnpayBooking } from "./vnpay-polling";

describe("VNPay confirmation polling", () => {
  const pending = { bookingId: 42, status: "PENDING" } as const;

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("does not poll after immediate payment success or failure", async () => {
    const read = vi.fn();
    const finish = vi.fn();
    for (const status of ["SUCCESS", "FAILED"] as const) {
      pollVnpayBooking({ ...pending, status }, read, finish);
    }
    await vi.advanceTimersByTimeAsync(30_000);
    expect(read).not.toHaveBeenCalled();
    expect(finish).not.toHaveBeenCalled();
  });

  it("waits for delayed IPN after QueryDr code 99 and stops on paid", async () => {
    const check = { ...pending, reason: "PROVIDER_REJECTED", providerCode: "99" };
    const read = vi.fn().mockResolvedValueOnce("PENDING").mockResolvedValueOnce("PAID");
    const finish = vi.fn();
    pollVnpayBooking(check, read, finish);

    await vi.advanceTimersByTimeAsync(2_500);
    expect(finish).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(2_500);
    expect(finish).toHaveBeenCalledExactlyOnceWith("paid");
    await vi.advanceTimersByTimeAsync(30_000);
    expect(read).toHaveBeenCalledTimes(2);
    expect(read).toHaveBeenCalledWith(42, expect.any(AbortSignal));
  });

  it("stops on cancellation and never reports payment success", async () => {
    const read = vi.fn().mockResolvedValue("CANCELLED");
    const finish = vi.fn();
    pollVnpayBooking(pending, read, finish);
    await vi.advanceTimersByTimeAsync(30_000);
    expect(finish).toHaveBeenCalledExactlyOnceWith("cancelled");
    expect(read).toHaveBeenCalledTimes(1);
  });

  it("recovers from a transient network failure", async () => {
    const read = vi.fn().mockRejectedValueOnce(new TypeError("Offline")).mockResolvedValueOnce("CONFIRMED");
    const finish = vi.fn();
    pollVnpayBooking(pending, read, finish);
    await vi.advanceTimersByTimeAsync(5_000);
    expect(finish).toHaveBeenCalledExactlyOnceWith("paid");
  });

  it("times out after 30 seconds, aborting a slow request without starting another", async () => {
    const read = vi.fn(() => new Promise<"PAID">(() => {}));
    const finish = vi.fn();
    pollVnpayBooking(pending, read, finish);
    await vi.advanceTimersByTimeAsync(29_999);
    expect(finish).not.toHaveBeenCalled();
    expect(read).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(finish).toHaveBeenCalledExactlyOnceWith("timeout");
    expect(read).toHaveBeenCalledWith(42, expect.objectContaining({ aborted: true }));
  });

  it("cleans up on unmount and ignores results that arrive after cleanup", async () => {
    let resolve!: (status: "PAID") => void;
    const read = vi.fn(() => new Promise<"PAID">((done) => { resolve = done; }));
    const finish = vi.fn();
    const stop = pollVnpayBooking(pending, read, finish);
    await vi.advanceTimersByTimeAsync(2_500);
    stop();
    resolve("PAID");
    await vi.advanceTimersByTimeAsync(30_000);
    expect(read).toHaveBeenCalledTimes(1);
    expect(read).toHaveBeenCalledWith(42, expect.objectContaining({ aborted: true }));
    expect(finish).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });
});
