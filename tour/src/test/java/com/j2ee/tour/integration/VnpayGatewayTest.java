package com.j2ee.tour.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class VnpayGatewayTest {
    private static final String KEY = "protocol-test-only";
    private static final String ROUTE_TOKEN = "synthetic-ipn-route-token-for-unit-tests";
    private static final String RETURN = "http://localhost:3000/payment/vnpay-return";
    private static final String PAY = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    private final VnpayGateway gateway = new VnpayGateway("TEST0001", KEY, PAY, RETURN, ROUTE_TOKEN);

    @Test
    void matchesIndependentSigningVector() {
        var fields = Map.of("vnp_OrderInfo", "Tour booking", "vnp_TxnRef", "VNPTEST",
                "vnp_Amount", "1000000");
        assertEquals("34259f933e61574c37c1b89082b5926dcc4fd64520edda153c4b230f5db76c5aea6a4"
                        + "c2a710cbf52d9925293aac71c29338921211325ea8341fbae74c03413bc",
                gateway.sign(VnpayGateway.canonical(fields)));
    }

    @Test
    void buildsSignedSandboxUrlWithVietnamTimesAndExactAmount() throws Exception {
        var url = gateway.createPaymentUrl("VNPTEST", new BigDecimal("10000.50"),
                Instant.parse("2026-11-21T02:00:00Z"), "127.0.0.1");
        String query = url.getRawQuery();
        String expected = "vnp_Amount=1000050&vnp_Command=pay&vnp_CreateDate=20261121090000"
                + "&vnp_CurrCode=VND&vnp_ExpireDate=20261121091500&vnp_IpAddr=127.0.0.1"
                + "&vnp_Locale=vn&vnp_OrderInfo=Tour+booking&vnp_OrderType=other"
                + "&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A3000%2Fpayment%2Fvnpay-return"
                + "&vnp_TmnCode=TEST0001&vnp_TxnRef=VNPTEST&vnp_Version=2.1.0";
        assertEquals("sandbox.vnpayment.vn", url.getHost());
        assertEquals(expected + "&vnp_SecureHash=" + hmac(expected), query);
    }

    @Test
    void rejectsNonExactOrOutOfRangeAmounts() {
        assertEquals(1000050L, VnpayGateway.encodeAmount(new BigDecimal("10000.50")));
        for (var amount : List.of("0", "-1", "1.001", "10000000000")) {
            assertThrows(IllegalArgumentException.class,
                    () -> VnpayGateway.encodeAmount(new BigDecimal(amount)));
        }
    }

    @ParameterizedTest
    @CsvSource({"00,00,SUCCESS", "00,02,UNRESOLVED", "07,00,UNRESOLVED", "24,02,CANCELLED",
            "11,02,EXPIRED", "51,02,FAILED", "99,02,UNRESOLVED", "51,00,UNRESOLVED"})
    void requiresCoherentSignedProviderStatuses(String response, String status, String expected)
            throws Exception {
        var result = gateway.verify(signed(fields(response, status)));
        assertEquals(expected, result.outcome().name());
        assertEquals(1000050L, result.amountTimes100());
        assertEquals("VNPTEST", result.reference());
    }

    @Test
    void rejectsTamperingDuplicateFieldsAndMissingSignature() throws Exception {
        var notification = signed(fields("00", "00"));
        notification.put("vnp_Amount", List.of("1"));
        assertReason("INVALID_SIGNATURE", notification);
        notification = signed(fields("00", "00"));
        notification.put("vnp_ResponseCode", List.of("00", "24"));
        assertReason("MALFORMED", notification);
        notification = signed(fields("00", "00"));
        notification.remove("vnp_SecureHash");
        assertReason("INVALID_SIGNATURE", notification);
    }

    @ParameterizedTest
    @CsvSource({"vnp_TmnCode,DIFF0001,INVALID_SIGNATURE", "vnp_Amount,100.00,INVALID_AMOUNT",
            "vnp_TxnRef,W!ONG,MALFORMED", "vnp_TransactionNo,0,MALFORMED",
            "vnp_TransactionStatus,X0,MALFORMED"})
    void rejectsInvalidFieldsEvenWhenSignatureMatches(String field, String value, String reason)
            throws Exception {
        var raw = fields("00", "00");
        raw.put(field, value);
        assertReason(reason, signed(raw));
    }

    @Test
    void permitsOnlyConfiguredSandboxAndSeparateIpnPathCredential() {
        assertTrue(gateway.hasTrustedIpnToken(ROUTE_TOKEN));
        assertFalse(gateway.hasTrustedIpnToken(null));
        assertFalse(gateway.hasTrustedIpnToken(KEY));
        assertThrows(RuntimeException.class, () ->
                new VnpayGateway("TEST0001", KEY, "https://example.test/pay", RETURN, ROUTE_TOKEN)
                        .requireConfigured());
        assertThrows(RuntimeException.class, () ->
                new VnpayGateway("TEST0001", KEY, PAY, RETURN, "").requireConfigured());
    }

    private void assertReason(String reason, Map<String, List<String>> fields) {
        assertEquals(reason, assertThrows(VnpayGateway.VerificationException.class,
                () -> gateway.verify(fields)).reason().name());
    }

    private static Map<String, String> fields(String response, String status) {
        return new LinkedHashMap<>(Map.of("vnp_TmnCode", "TEST0001", "vnp_TxnRef", "VNPTEST",
                "vnp_Amount", "1000050", "vnp_ResponseCode", response, "vnp_TransactionStatus",
                status, "vnp_TransactionNo", "12345", "vnp_OrderInfo", "Tour booking"));
    }

    private static Map<String, List<String>> signed(Map<String, String> raw) throws Exception {
        var sorted = new TreeMap<>(raw);
        String query = sorted.entrySet().stream().map(entry -> entry.getKey() + "="
                + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
        Map<String, List<String>> result = new LinkedHashMap<>();
        sorted.forEach((key, value) -> result.put(key, List.of(value)));
        result.put("vnp_SecureHash", List.of(hmac(query)));
        return result;
    }

    private static String hmac(String query) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(query.getBytes(StandardCharsets.UTF_8)));
    }
}
