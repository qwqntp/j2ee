package com.j2ee.tour.integration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class VnpayTestPayload {
    public static final String MERCHANT = "TEST0001";
    public static final String KEY = "synthetic-notification-test-secret";
    public static final String TOKEN = "synthetic-route-token-for-integration-tests";
    public static final String PAY = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    public static final String RETURN = "http://localhost:3000/payment/vnpay-return";

    private VnpayTestPayload() { }

    public static Map<String, List<String>> fields(String ref, String amount, String response, String status) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put("vnp_TxnRef", List.of(ref));
        fields.put("vnp_Amount", List.of(amount));
        fields.put("vnp_TmnCode", List.of(MERCHANT));
        fields.put("vnp_ResponseCode", List.of(response));
        fields.put("vnp_TransactionStatus", List.of(status));
        fields.put("vnp_TransactionNo", List.of("12345"));
        fields.put("vnp_OrderInfo", List.of("Tour booking"));
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            fields.put("vnp_SecureHash", List.of(HexFormat.of().formatHex(
                    mac.doFinal(query(fields).getBytes(StandardCharsets.US_ASCII)))));
            return fields;
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("Could not sign synthetic notification", exception);
        }
    }

    public static String query(Map<String, List<String>> fields) {
        return new TreeMap<>(fields).entrySet().stream().flatMap(entry ->
                entry.getValue().stream().map(value -> entry.getKey() + "="
                        + URLEncoder.encode(value, StandardCharsets.US_ASCII)))
                .collect(Collectors.joining("&"));
    }
}
