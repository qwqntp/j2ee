import { describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import {
  checkoutRecoveryHref, checkoutWithBooking, forgetVnpayReturn, openVnpayCheckout, readVnpayReturn,
  restoreVnpayReturn, sandboxPaymentUrl, stashVnpayReturn, vnpayCheckoutEnabled, vnpayReconcileError,
  vnpayReconcileNotice, vnpayReasonDetail, vnpayResultState, vnpayReturnLoginHref, withVnpaySession,
} from "./vnpay";

const PAYMENT_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=VNPTEST";
const REFERENCE = `VNP${"A".repeat(32)}`;

describe("VNPay checkout", () => {
  it("permits only the HTTPS Sandbox payment destination", () => {
    expect(sandboxPaymentUrl(PAYMENT_URL)).toBe(PAYMENT_URL);
    for (const url of [
      PAYMENT_URL.replace("https:", "http:"),
      PAYMENT_URL.replace("sandbox.vnpayment.vn", "sandbox.vnpayment.vn.example.test"),
      PAYMENT_URL.replace("sandbox.vnpayment.vn", "user@sandbox.vnpayment.vn"),
      PAYMENT_URL.replace("vpcpay.html", "other.html"),
      PAYMENT_URL.replace("sandbox.vnpayment.vn", "sandbox.vnpayment.vn:444"),
      `${PAYMENT_URL}#fragment`,
      "javascript:void(0)",
    ]) expect(sandboxPaymentUrl(url)).toBeNull();
  });

  it("keeps the booking accessible when payment initiation times out", async () => {
    const navigate = vi.fn();
    const result = await openVnpayCheckout(42, async () => {
      throw new ApiError(504, "Timeout");
    }, navigate);
    expect(result).toMatchObject({ bookingHref: "/account/bookings/42", redirected: false });
    expect(result.error).toContain("timed out");
    expect(navigate).not.toHaveBeenCalled();
  });

  it("will not redirect to an unexpected destination after the booking has been created", async () => {
    const navigate = vi.fn();
    const result = await openVnpayCheckout(42, async () => ({
      paymentUrl: "https://example.test/payment",
      expiresAt: "2026-11-21T02:15:00Z", reference: REFERENCE,
    }), navigate);
    expect(result).toMatchObject({ bookingHref: "/account/bookings/42", redirected: false });
    expect(navigate).not.toHaveBeenCalled();
  });

  it("redirects once a confirmed booking supplies an allowed URL", async () => {
    const navigate = vi.fn();
    const result = await openVnpayCheckout(42, async () => ({
      paymentUrl: PAYMENT_URL, expiresAt: "2026-11-21T02:15:00Z", reference: REFERENCE,
    }), navigate);
    expect(result).toMatchObject({ bookingHref: "/account/bookings/42", redirected: true });
    expect(navigate).toHaveBeenCalledExactlyOnceWith(PAYMENT_URL);
  });

  it("only enables checkout outside the Next development server", () => {
    expect(vnpayCheckoutEnabled("development")).toBe(false);
    expect(vnpayCheckoutEnabled("production")).toBe(true);
  });

  it("recovers the completed reservation after checkout remounts", () => {
    const persisted = checkoutWithBooking(5, 7, 42);
    const reloaded = new URL(persisted, "http://localhost:3000");
    expect(reloaded.pathname).toBe("/payment");
    expect(checkoutRecoveryHref(reloaded.searchParams.get("reservedBooking") ?? undefined))
      .toBe("/account/bookings/42");
    expect(checkoutRecoveryHref("42abc")).toBeNull();
    expect(checkoutRecoveryHref(["42", "43"])).toBeNull();
  });
});

describe("VNPay browser return", () => {
  const query = `vnp_TxnRef=${REFERENCE}&vnp_OrderInfo=Tour+booking&vnp_SecureHash=synthetic`;

  it("keeps signed fields and duplicates for server-side validation", () => {
    const input = new URLSearchParams(`${query}&vnp_ResponseCode=00&vnp_ResponseCode=24`);
    const parsed = readVnpayReturn(input);
    expect(parsed?.reference).toBe(REFERENCE);
    expect(new URLSearchParams(parsed?.query ?? "").getAll("vnp_ResponseCode")).toEqual(["00", "24"]);
    expect(readVnpayReturn(new URLSearchParams(`${query}&vnp_TxnRef=${REFERENCE}`))).toBeNull();
    expect(readVnpayReturn(new URLSearchParams(`vnp_ResponseCode=00&ref=${REFERENCE}`))).toBeNull();
  });

  it("does not put signed fields into the reauthentication URL", () => {
    const url = new URL(vnpayReturnLoginHref(REFERENCE), "http://localhost:3000");
    expect(url.pathname).toBe("/login");
    expect(url.searchParams.get("next")).toBe(`/payment/vnpay-return?ref=${REFERENCE}`);
    expect(url.href).not.toContain("vnp_SecureHash");
    expect(readVnpayReturn(new URLSearchParams(`ref=${REFERENCE}`))).toEqual({
      reference: REFERENCE, query: null,
    });
  });

  it("allows only a matching tab context and deletes it after verification or expiry", () => {
    const entries = new Map<string, string>();
    const storage = {
      getItem: (key: string) => entries.get(key) ?? null,
      setItem: (key: string, value: string) => { entries.set(key, value); },
      removeItem: (key: string) => { entries.delete(key); },
    };
    const now = 100;
    stashVnpayReturn(storage, REFERENCE, query, now);
    expect(restoreVnpayReturn(storage, `VNP${"B".repeat(32)}`, now)).toBeNull();
    expect(restoreVnpayReturn(storage, REFERENCE, now + 10)).toBe(query);
    forgetVnpayReturn(storage, REFERENCE);
    expect(restoreVnpayReturn(storage, REFERENCE, now + 20)).toBeNull();
    stashVnpayReturn(storage, REFERENCE, query, now);
    stashVnpayReturn(storage, REFERENCE, query, now + 60_000);
    expect(restoreVnpayReturn(storage, REFERENCE, now + 15 * 60 * 1000)).toBeNull();
    expect(entries.size).toBe(0);
  });

  it("falls back to reference-only recovery when tab storage throws", () => {
    const deny = () => { throw new Error("Storage disabled"); };
    const storage = { getItem: deny, setItem: deny, removeItem: deny };
    expect(() => stashVnpayReturn(storage, REFERENCE, query, 100)).not.toThrow();
    expect(restoreVnpayReturn(storage, REFERENCE, 101)).toBeNull();
    expect(vnpayReturnLoginHref(REFERENCE)).toContain(REFERENCE);
  });

  it("tries fresh authentication once without dropping the transaction request", async () => {
    let token = "stale";
    const checked: string[] = [];
    const result = await withVnpaySession(
      async () => token,
      () => { token = "fresh"; },
      async (current) => {
        checked.push(current);
        if (current === "stale") throw new ApiError(401, "Expired");
        return REFERENCE;
      },
    );
    expect(result).toBe(REFERENCE);
    expect(checked).toEqual(["stale", "fresh"]);
  });

  it("returns a sign-in signal rather than retrying repeatedly after 401", async () => {
    const check = vi.fn().mockRejectedValue(new ApiError(401, "Expired"));
    await expect(withVnpaySession(async () => "stale", () => {}, check)).resolves.toBeNull();
    expect(check).toHaveBeenCalledTimes(2);
  });

  it("displays success only after recorded IPN and distinguishes provider outcomes", () => {
    const base = { bookingId: 42, reference: REFERENCE, status: "PENDING",
      expired: false, returnOutcome: "SUCCESS", reason: null, providerCode: null,
      providerStatus: null } as const;
    expect(vnpayResultState(base)).toBe("pending");
    expect(vnpayResultState({ ...base, status: "SUCCESS" })).toBe("success");
    expect(vnpayResultState({ ...base, returnOutcome: "CANCELLED" })).toBe("cancelled");
    expect(vnpayResultState({ ...base, returnOutcome: "EXPIRED" })).toBe("expired");
    expect(vnpayResultState({ ...base, returnOutcome: null, expired: true })).toBe("expired");
    expect(vnpayResultState({ ...base, status: "FAILED", returnOutcome: null })).toBe("failed");
  });
});

describe("VNPay reconciliation", () => {
  const checked = { bookingId: 42, reference: REFERENCE, checked: true,
    reason: null, providerCode: null, providerStatus: null };

  it("releases retry and cancellation only for a verified nonpayment", () => {
    expect(vnpayReconcileNotice({ ...checked, status: "FAILED" }))
      .toEqual({ tone: "failed", message: expect.stringContaining("try again or cancel") });
    expect(vnpayReconcileNotice({ ...checked, status: "SUCCESS" }).tone).toBe("success");
  });

  it("keeps an unresolved attempt protected and tells the traveller not to pay again", () => {
    const notice = vnpayReconcileNotice({ ...checked, status: "PENDING" });
    expect(notice.tone).toBe("pending");
    expect(notice.message).toContain("Do not pay again");
    expect(notice.message).toContain("cancellation stay blocked");
  });

  it("reports the actual safe reason instead of repeating PENDING", () => {
    const rejected = vnpayReconcileNotice({ ...checked, status: "PENDING",
      reason: "PROVIDER_REJECTED", providerCode: "99" });
    expect(rejected.message).toContain("VNPay rejected the status check (code 99)");

    expect(vnpayReasonDetail("PROVIDER_TIMEOUT", null)).toContain("timed out");
    expect(vnpayReasonDetail("PROVIDER_UNREACHABLE", null)).toContain("could not be reached");
    expect(vnpayReasonDetail("INVALID_PROVIDER_SIGNATURE", null)).toContain("not signed");
    expect(vnpayReasonDetail("TRANSACTION_NOT_FOUND", "91")).toContain("no record");
    expect(vnpayReasonDetail("TRANSACTION_UNRESOLVED", "00")).toContain("reached a final status");
    expect(vnpayReasonDetail("VERIFIED_SUCCESS", "00")).toBeNull();
  });

  it("reports nothing pending without implying a payment outcome", () => {
    expect(vnpayReconcileNotice({ bookingId: 42, reference: null, status: null, checked: false,
      reason: null, providerCode: null, providerStatus: null }))
      .toEqual({ tone: "clear", message: "No VNPay payment is waiting for confirmation." });
  });

  it("turns blocked retry or cancellation errors into actionable guidance", () => {
    expect(vnpayReconcileError(new ApiError(409, "An earlier VNPay payment is still unresolved.")))
      .toBe("An earlier VNPay payment is still unresolved.");
    expect(vnpayReconcileError(new ApiError(503, "Unavailable"))).toContain("Try again before paying again");
    expect(vnpayReconcileError(new ApiError(504, "Timeout"))).toContain("timed out");
    expect(vnpayReconcileError(new ApiError(401, "Expired"))).toContain("Sign in again");
    expect(vnpayReconcileError(new Error("Network"))).toContain("Try again before paying again");
  });
});
