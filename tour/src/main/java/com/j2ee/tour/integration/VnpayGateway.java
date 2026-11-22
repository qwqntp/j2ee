package com.j2ee.tour.integration;

import com.j2ee.tour.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class VnpayGateway {
    public static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);
    // VNPay rejects PAY requests below the merchant minimum; Sandbox uses 10,000 VND.
    public static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("10000");
    private static final String SIGNATURE = "vnp_SecureHash";
    private static final String HMAC = "HmacSHA512";
    private static final HexFormat HEX = HexFormat.of();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuuMMddHHmmss", Locale.ROOT).withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final Set<String> DEFINITIVE_FAILURE = Set.of(
            "09", "10", "11", "12", "13", "24", "51", "65", "75", "79");

    private final String merchantCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final String ipnToken;

    public VnpayGateway(@Value("${app.vnpay.tmn-code:}") String merchantCode,
                        @Value("${app.vnpay.hash-secret:}") String hashSecret,
                        @Value("${app.vnpay.pay-url:}") String payUrl,
                        @Value("${app.vnpay.return-url:}") String returnUrl,
                        @Value("${app.vnpay.ipn-token:}") String ipnToken) {
        this.merchantCode = merchantCode;
        this.hashSecret = hashSecret;
        this.payUrl = payUrl;
        this.returnUrl = returnUrl;
        this.ipnToken = ipnToken;
    }

    public void requireConfigured() {
        if (!merchantCode.matches("[a-zA-Z0-9]{8}") || hashSecret.isBlank()
                || !validToken(ipnToken) || ipnToken.equals(hashSecret)
                || !isAllowedPayUrl(payUrl) || !isAllowedReturnUrl(returnUrl)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPay Sandbox is not configured");
        }
    }

    public boolean hasTrustedIpnToken(String supplied) {
        return validToken(ipnToken) && validToken(supplied)
                && MessageDigest.isEqual(ipnToken.getBytes(StandardCharsets.US_ASCII),
                        supplied.getBytes(StandardCharsets.US_ASCII));
    }

    public URI createPaymentUrl(String reference, BigDecimal amount, Instant createdAt, String clientIp) {
        requireConfigured();
        String ip = "::1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp) ? "127.0.0.1" : clientIp;
        String query = canonical(Map.ofEntries(
                Map.entry("vnp_Version", "2.1.0"), Map.entry("vnp_Command", "pay"),
                Map.entry("vnp_TmnCode", merchantCode), Map.entry("vnp_Amount", Long.toString(encodeAmount(amount))),
                Map.entry("vnp_CurrCode", "VND"), Map.entry("vnp_TxnRef", reference),
                Map.entry("vnp_OrderInfo", "Tour booking"), Map.entry("vnp_OrderType", "other"),
                Map.entry("vnp_Locale", "vn"), Map.entry("vnp_ReturnUrl", returnUrl),
                Map.entry("vnp_IpAddr", ip), Map.entry("vnp_CreateDate", TIMESTAMP.format(createdAt)),
                Map.entry("vnp_ExpireDate", TIMESTAMP.format(createdAt.plus(PAYMENT_WINDOW)))));
        return URI.create(payUrl + "?" + query + "&" + SIGNATURE + "=" + sign(query));
    }

    public VerifiedNotification verify(Map<String, List<String>> params) {
        requireConfigured();
        if (params.size() > 32) {
            throw new VerificationException(Reason.MALFORMED);
        }
        Map<String, String> fields = new HashMap<>();
        params.forEach((key, values) -> {
            if (!key.matches("vnp_[A-Za-z0-9]{1,60}") || values.size() != 1
                    || !values.getFirst().matches("[\\x20-\\x7e]{0,512}")) {
                throw new VerificationException(Reason.MALFORMED);
            }
            fields.put(key, values.getFirst());
        });
        String received = fields.get(SIGNATURE);
        if (received == null || !sameHex(sign(canonical(fields)), received)
                || !merchantCode.equals(fields.get("vnp_TmnCode"))) {
            throw new VerificationException(Reason.INVALID_SIGNATURE);
        }
        String reference = fields.getOrDefault("vnp_TxnRef", "");
        String response = fields.getOrDefault("vnp_ResponseCode", "");
        String status = fields.getOrDefault("vnp_TransactionStatus", "");
        if (!reference.matches("[A-Za-z0-9]{1,100}") || !response.matches("[0-9]{2}")
                || !status.matches("[0-9]{2}")) {
            throw new VerificationException(Reason.MALFORMED);
        }
        String amount = fields.getOrDefault("vnp_Amount", "");
        if (!amount.matches("[0-9]{1,12}") || Long.parseLong(amount) == 0) {
            throw new VerificationException(Reason.INVALID_AMOUNT);
        }
        Outcome outcome = classify(response, status);
        if (outcome == Outcome.SUCCESS
                && !fields.getOrDefault("vnp_TransactionNo", "").matches("(?=.*[1-9])[0-9]{1,15}")) {
            throw new VerificationException(Reason.MALFORMED);
        }
        return new VerifiedNotification(reference, Long.parseLong(amount), outcome);
    }

    public static long encodeAmount(BigDecimal amount) {
        try {
            long scaled = amount.movePointRight(2).longValueExact();
            if (scaled > 0 && scaled <= 999_999_999_999L) {
                return scaled;
            }
        } catch (ArithmeticException ignored) {
            // Reject values that would need rounding or overflow.
        }
        throw new IllegalArgumentException("Amount cannot be represented in VNPay");
    }

    // PAY 2.1.0 signs the same sorted, form-encoded values as the redirect query.
    // https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html#tao-url-thanh-toan
    static String canonical(Map<String, String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty() && !SIGNATURE.equals(entry.getKey())
                        && !"vnp_SecureHashType".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }

    // QueryDr hashes a pipe-delimited string that can contain Vietnamese messages, so use
    // UTF-8. PAY's canonical query is US-ASCII, where both encodings produce the same bytes.
    String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), HMAC));
            return HEX.formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign VNPay request");
        }
    }

    String merchantCode() {
        return merchantCode;
    }

    static boolean sameHex(String expected, String received) {
        return received != null && received.matches("[0-9a-fA-F]{128}")
                && MessageDigest.isEqual(HEX.parseHex(expected), HEX.parseHex(received));
    }

    static String timestamp(Instant instant) {
        return TIMESTAMP.format(instant);
    }

    private static Outcome classify(String response, String status) {
        if ("00".equals(response) && "00".equals(status)) return Outcome.SUCCESS;
        if (!"02".equals(status) || !DEFINITIVE_FAILURE.contains(response)) return Outcome.UNRESOLVED;
        return switch (response) {
            case "24" -> Outcome.CANCELLED;
            case "11" -> Outcome.EXPIRED;
            default -> Outcome.FAILED;
        };
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{32,128}");
    }

    private static boolean isAllowedPayUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && "sandbox.vnpayment.vn".equals(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getRawUserInfo() == null
                    && "/paymentv2/vpcpay.html".equals(uri.getRawPath())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isAllowedReturnUrl(String value) {
        try {
            URI uri = URI.create(value);
            return value.length() <= 255 && uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null && uri.getRawQuery() == null
                    && "/payment/vnpay-return".equals(uri.getRawPath())
                    && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public enum Outcome { SUCCESS, FAILED, CANCELLED, EXPIRED, UNRESOLVED }
    public enum Reason { INVALID_SIGNATURE, INVALID_AMOUNT, MALFORMED }
    public record VerifiedNotification(String reference, long amountTimes100, Outcome outcome) { }

    public static final class VerificationException extends RuntimeException {
        private final Reason reason;

        public VerificationException(Reason reason) {
            super("Invalid VNPay notification");
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }
}
