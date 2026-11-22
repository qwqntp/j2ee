package com.j2ee.tour.dto.response;

/**
 * Result of asking VNPay about the booking's open attempt.
 * {@code checked=false} means there was nothing pending; {@code status=PENDING} with
 * {@code checked=true} means VNPay could not confirm payment or nonpayment.
 * {@code reason} is a safe diagnostic code such as {@code TRANSACTION_NOT_FOUND},
 * {@code PROVIDER_TIMEOUT}, or {@code VERIFIED_SUCCESS}; it never contains provider text,
 * credentials, or payloads. {@code providerCode} and {@code providerStatus} are the
 * documented two-digit VNPay codes when they were received.
 */
public record VnpayReconcileResponse(
        Long bookingId, String reference, String status, boolean checked,
        String reason, String providerCode, String providerStatus
) {
    public static VnpayReconcileResponse nothingToCheck(Long bookingId) {
        return new VnpayReconcileResponse(bookingId, null, null, false, null, null, null);
    }
}
