import { ApiError } from "./api";
import type { VnpayReconcile, VnpayStart, VnpayStatus } from "./types";

const RETURN_PATH = "/payment/vnpay-return";
const REFERENCE_FORMAT = /^VNP[0-9A-F]{32}$/;
const RETURN_LIFETIME = 15 * 60 * 1000;
const STORAGE_KEY = "tour_vnpay_return";
const MAX_QUERY_LENGTH = 8192;

type TabStorage = Pick<Storage, "getItem" | "setItem" | "removeItem"> | undefined;

export function vnpayCheckoutEnabled(runtime = process.env.NODE_ENV): boolean {
  // Next's development request logger and trace include the signed return query.
  return runtime !== "development";
}

export function checkoutWithBooking(tourId: number, departureId: number, bookingId: number): string {
  return `/payment?tourId=${tourId}&departureId=${departureId}&reservedBooking=${bookingId}`;
}

export function checkoutRecoveryHref(bookingId: string | string[] | undefined): string | null {
  if (typeof bookingId !== "string" || !/^[1-9]\d*$/.test(bookingId) ||
      !Number.isSafeInteger(Number(bookingId))) return null;
  return `/account/bookings/${bookingId}`;
}

export function sandboxPaymentUrl(value: string): string | null {
  try {
    const url = new URL(value);
    if (
      url.protocol !== "https:" || url.hostname !== "sandbox.vnpayment.vn" ||
      url.pathname !== "/paymentv2/vpcpay.html" || url.port ||
      url.username || url.password || url.hash
    ) return null;
    return url.href;
  } catch {
    return null;
  }
}

function startErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 409) return error.message;
    if (error.status === 401) return "Sign in again to continue from your booking.";
    if (error.status === 503) return "VNPay Sandbox is currently unavailable.";
    if (error.status === 504) return "Opening VNPay timed out. Check your booking before continuing.";
  }
  return "Could not open VNPay. Check your booking before continuing.";
}

export async function openVnpayCheckout(
  bookingId: number,
  start: () => Promise<VnpayStart>,
  navigate: (url: string) => void,
): Promise<{ bookingHref: string; redirected: boolean; error: string | null }> {
  const bookingHref = `/account/bookings/${bookingId}`;
  if (!vnpayCheckoutEnabled()) {
    return { bookingHref, redirected: false, error: "Use a production frontend build for VNPay Sandbox." };
  }
  try {
    const attempt = await start();
    const url = sandboxPaymentUrl(attempt.paymentUrl);
    if (!url) {
      return { bookingHref, redirected: false, error: "The VNPay payment link was invalid." };
    }
    navigate(url);
    return { bookingHref, redirected: true, error: null };
  } catch (error) {
    return { bookingHref, redirected: false, error: startErrorMessage(error) };
  }
}

export function readVnpayReturn(params: URLSearchParams): { reference: string; query: string | null } | null {
  const signed = new URLSearchParams();
  params.forEach((value, key) => {
    if (key.startsWith("vnp_")) signed.append(key, value);
  });
  if (signed.size > 0 && params.has("ref")) return null;
  const refs = signed.size > 0 ? signed.getAll("vnp_TxnRef") : params.getAll("ref");
  if (refs.length !== 1 || !REFERENCE_FORMAT.test(refs[0])) return null;
  const query = signed.size > 0 ? signed.toString() : null;
  if (signed.size > 32 || (query && query.length > MAX_QUERY_LENGTH)) return null;
  return { reference: refs[0], query };
}

export function vnpayReturnHref(reference: string): string {
  return `${RETURN_PATH}?ref=${encodeURIComponent(reference)}`;
}

export function vnpayReturnLoginHref(reference: string): string {
  return `/login?next=${encodeURIComponent(vnpayReturnHref(reference))}`;
}

export function stashVnpayReturn(storage: TabStorage, reference: string, query: string, now: number): void {
  if (query.length > MAX_QUERY_LENGTH || readVnpayReturn(new URLSearchParams(query))?.reference !== reference) return;
  if (restoreVnpayReturn(storage, reference, now) !== null) return;
  try {
    storage?.setItem(STORAGE_KEY, JSON.stringify({ reference, query, expiresAt: now + RETURN_LIFETIME }));
  } catch {
    // Owner-only status lookup still works without tab storage.
  }
}

export function restoreVnpayReturn(storage: TabStorage, reference: string, now: number): string | null {
  try {
    const raw = storage?.getItem(STORAGE_KEY);
    if (!raw) return null;
    const saved = JSON.parse(raw);
    if (typeof saved?.expiresAt !== "number" || saved.expiresAt <= now || typeof saved.query !== "string" ||
        saved.query.length > MAX_QUERY_LENGTH) {
      storage?.removeItem(STORAGE_KEY);
      return null;
    }
    if (saved.reference !== reference) return null;
    return readVnpayReturn(new URLSearchParams(saved.query))?.reference === reference ? saved.query : null;
  } catch {
    return null;
  }
}

export function forgetVnpayReturn(storage: TabStorage, reference: string): void {
  try {
    const raw = storage?.getItem(STORAGE_KEY);
    if (raw && JSON.parse(raw)?.reference === reference) storage?.removeItem(STORAGE_KEY);
  } catch {
    // Storage may be unavailable.
  }
}

export async function withVnpaySession<T>(
  getToken: () => Promise<string | null>,
  clearToken: () => void,
  request: (token: string) => Promise<T>,
): Promise<T | null> {
  for (let attempt = 0; attempt < 2; attempt++) {
    const token = await getToken();
    if (!token) return null;
    try {
      return await request(token);
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
      clearToken();
    }
  }
  return null;
}

export function vnpayResultState(status: VnpayStatus): "success" | "failed" | "cancelled" | "expired" | "pending" {
  if (status.status === "SUCCESS") return "success";
  if (status.returnOutcome === "CANCELLED") return "cancelled";
  if (status.returnOutcome === "EXPIRED") return "expired";
  if (status.status === "FAILED" || status.returnOutcome === "FAILED") return "failed";
  return status.expired ? "expired" : "pending";
}

export type VnpayReconcileNotice = {
  tone: "success" | "failed" | "pending" | "clear";
  message: string;
};

// Safe, user-facing explanation of the backend's diagnostic reason. It never contains
// provider text, credentials, payloads, or signed URLs.
export function vnpayReasonDetail(reason: string | null, providerCode: string | null): string | null {
  switch (reason) {
    case "PROVIDER_TIMEOUT":
      return "The VNPay status check timed out.";
    case "PROVIDER_UNREACHABLE":
      return "VNPay could not be reached.";
    case "INVALID_PROVIDER_SIGNATURE":
      return "VNPay's answer was not signed, so it was not trusted.";
    case "PROVIDER_REJECTED":
      return providerCode
        ? `VNPay rejected the status check (code ${providerCode}).`
        : "VNPay rejected the status check.";
    case "PROVIDER_MISMATCH":
      return "VNPay's answer did not match this payment attempt.";
    case "PROVIDER_MALFORMED_RESPONSE":
      return "VNPay returned an unexpected answer.";
    case "TRANSACTION_NOT_FOUND":
      return "VNPay has no record of this payment reference yet.";
    case "TRANSACTION_UNRESOLVED":
      return "VNPay has the transaction but has not reached a final status.";
    default:
      return null;
  }
}

function pendingMessage(result: VnpayReconcile): string {
  const base = "VNPay has not confirmed this payment yet. Do not pay again; retry and cancellation stay " +
    "blocked until VNPay resolves it.";
  const detail = vnpayReasonDetail(result.reason, result.providerCode);
  return detail ? `${base} ${detail}` : base;
}

// Only a signed VNPay answer may release an attempt. Until then the UI must not
// invite another payment or a cancellation.
export function vnpayReconcileNotice(result: VnpayReconcile): VnpayReconcileNotice {
  if (!result.checked) {
    return { tone: "clear", message: "No VNPay payment is waiting for confirmation." };
  }
  if (result.status === "SUCCESS") {
    return { tone: "success", message: "VNPay confirmed this payment. Your booking has been updated." };
  }
  if (result.status === "FAILED") {
    return {
      tone: "failed",
      message: "VNPay confirmed this attempt was not paid. You can try again or cancel the booking.",
    };
  }
  return { tone: "pending", message: pendingMessage(result) };
}

export function vnpayReconcileError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 409) return error.message;
    if (error.status === 401) return "Sign in again to check this payment.";
    if (error.status === 503) return "VNPay Sandbox is currently unavailable. Try again before paying again.";
    if (error.status === 504) return "Checking the payment timed out. Try again before paying again.";
  }
  return "Could not check the payment with VNPay. Try again before paying again.";
}
