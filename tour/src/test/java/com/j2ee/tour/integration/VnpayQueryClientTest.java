package com.j2ee.tour.integration;

import com.j2ee.tour.integration.VnpayGateway.Outcome;
import com.j2ee.tour.integration.VnpayQueryClient.QueryCode;
import com.j2ee.tour.integration.VnpayQueryClient.QueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.j2ee.tour.integration.VnpayTestPayload.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VnpayQueryClientTest {
    private static final String QUERY_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final String REF = "VNP0123456789ABCDEF0123456789ABCDEF";
    private static final BigDecimal AMOUNT = new BigDecimal("10000.50");
    private static final long AMOUNT_TIMES_100 = 1_000_050L;
    private static final Instant TRANSACTION_CREATED = Instant.parse("2026-11-21T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T03:04:05Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ObjectMapper json = new ObjectMapper();
    private VnpayQueryClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        VnpayGateway gateway = new VnpayGateway(MERCHANT, KEY, PAY, RETURN, TOKEN);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new VnpayQueryClient(gateway, json, CLOCK, builder.build());
    }

    @Test
    void signsTheRequestForThisExactAttempt() throws Exception {
        server.expect(requestTo(QUERY_URL)).andExpect(request -> {
            JsonNode body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
            assertEquals("2.1.0", body.get("vnp_Version").stringValue());
            assertEquals("querydr", body.get("vnp_Command").stringValue());
            assertEquals(MERCHANT, body.get("vnp_TmnCode").stringValue());
            assertEquals(REF, body.get("vnp_TxnRef").stringValue());
            assertEquals(32, body.get("vnp_RequestId").stringValue().length());
            assertTrue(body.get("vnp_RequestId").stringValue().matches("[0-9a-f]{32}"));
            assertEquals("20261121090000", body.get("vnp_TransactionDate").stringValue());
            assertEquals("20261121100405", body.get("vnp_CreateDate").stringValue());
            assertEquals("203.0.113.9", body.get("vnp_IpAddr").stringValue());
            String data = String.join("|",
                    body.get("vnp_RequestId").stringValue(), "2.1.0", "querydr", MERCHANT, REF,
                    "20261121090000", "20261121100405", "203.0.113.9", "Query tour booking");
            assertEquals(sign(data), body.get("vnp_SecureHash").stringValue());
        })
                .andRespond(withSuccess(response("00", "00", AMOUNT_TIMES_100, "12345"),
                        MediaType.APPLICATION_JSON));

        var result = client.query(REF, AMOUNT, TRANSACTION_CREATED, "203.0.113.9");

        assertEquals(REF, result.notification().reference());
        assertEquals(AMOUNT_TIMES_100, result.notification().amountTimes100());
        assertEquals(Outcome.SUCCESS, result.notification().outcome());
        assertEquals(QueryCode.VERIFIED_SUCCESS, result.code());
        assertEquals("00", result.providerCode());
        assertEquals("00", result.providerStatus());
        server.verify();
    }

    @Test
    void signedSuccessIsAuthoritative() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(response("00", "00", AMOUNT_TIMES_100, "12345"),
                        MediaType.APPLICATION_JSON));

        var result = client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1");
        assertEquals(Outcome.SUCCESS, result.notification().outcome());
        assertEquals(QueryCode.VERIFIED_SUCCESS, result.code());
        server.verify();
    }

    @Test
    void failedTransactionIsAuthoritativeNonpayment() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(response("00", "02", AMOUNT_TIMES_100, "0"),
                        MediaType.APPLICATION_JSON));

        var result = client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1");
        assertEquals(Outcome.FAILED, result.notification().outcome());
        assertEquals(QueryCode.VERIFIED_NONPAYMENT, result.code());
        assertEquals("02", result.providerStatus());
        server.verify();
    }

    @Test
    void unfinishedTransactionStaysUnresolved() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(response("00", "01", AMOUNT_TIMES_100, "0"),
                        MediaType.APPLICATION_JSON));
        var result = client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1");
        assertEquals(Outcome.UNRESOLVED, result.notification().outcome());
        assertEquals(QueryCode.TRANSACTION_UNRESOLVED, result.code());
        assertEquals("01", result.providerStatus());
        server.verify();
    }

    @Test
    void notFoundTransactionStaysUnresolved() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(response("91", "", 0, "0"), MediaType.APPLICATION_JSON));
        var result = client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1");
        assertEquals(Outcome.UNRESOLVED, result.notification().outcome());
        assertEquals(QueryCode.TRANSACTION_NOT_FOUND, result.code());
        assertEquals("91", result.providerCode());
        assertNull(result.providerStatus());
        server.verify();
    }

    @Test
    void unsignedProviderErrorIsReportedWithOnlyItsSafeCode() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"vnp_ResponseCode\":\"99\",\"vnp_Message\":\"Unexpected error\"}",
                        MediaType.APPLICATION_JSON));
        var rejected = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.PROVIDER_REJECTED, rejected.code());
        assertEquals("99", rejected.providerCode());
        assertNull(rejected.providerStatus());
        assertFalse(rejected.getMessage().contains("Unexpected"));
        server.verify();
    }

    @Test
    void unsignedNotFoundStaysUnresolvedAndIsNeverAuthoritative() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"vnp_ResponseCode\":\"91\",\"vnp_Message\":\"Not found\"}",
                        MediaType.APPLICATION_JSON));
        var result = client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1");
        assertEquals(Outcome.UNRESOLVED, result.notification().outcome());
        assertEquals(QueryCode.TRANSACTION_NOT_FOUND, result.code());
        assertEquals("91", result.providerCode());
        server.verify();
    }

    @Test
    void unsignedSuccessIsNeverAccepted() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"vnp_ResponseCode\":\"00\",\"vnp_Message\":\"ok\"}",
                        MediaType.APPLICATION_JSON));
        var malformed = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.PROVIDER_MALFORMED_RESPONSE, malformed.code());
        server.verify();
    }

    @Test
    void unsignedTransactionDataIsNotTrusted() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"vnp_ResponseCode\":\"00\",\"vnp_Amount\":\"1000050\"}",
                        MediaType.APPLICATION_JSON));
        var unsigned = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.INVALID_PROVIDER_SIGNATURE, unsigned.code());
        server.verify();
    }

    @Test
    void rejectedResponseCodeIsReportedWithOnlyTheSafeCode() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(response("02", "", 0, "0"), MediaType.APPLICATION_JSON));
        var rejected = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.PROVIDER_REJECTED, rejected.code());
        assertEquals("02", rejected.providerCode());
        assertNull(rejected.providerStatus());
        assertFalse(rejected.getMessage().contains("Yêu cầu"));
        server.verify();
    }

    @Test
    void successWithoutTransactionNumberIsUnavailable() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(response("00", "00", AMOUNT_TIMES_100, "0"),
                        MediaType.APPLICATION_JSON));
        var mismatch = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.PROVIDER_MISMATCH, mismatch.code());
        server.verify();
    }

    @Test
    void foreignReferenceIsRejectedEvenWhenSigned() {
        assertRejected(Map.of("vnp_TxnRef", "VNP" + "B".repeat(32)));
    }

    @Test
    void foreignMerchantIsRejectedEvenWhenSigned() {
        assertRejected(Map.of("vnp_TmnCode", "OTHER001"));
    }

    @Test
    void foreignAmountIsRejectedEvenWhenSigned() {
        assertRejected(Map.of("vnp_Amount", "999999"));
    }

    private void assertRejected(Map<String, String> override) {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(response("00", "00", AMOUNT_TIMES_100, "12345", override),
                        MediaType.APPLICATION_JSON));
        var rejected = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.PROVIDER_MISMATCH, rejected.code());
        server.verify();
    }

    @Test
    void tamperedResponseIsRejected() throws Exception {
        Map<String, String> fields = responseFields("00", "00", AMOUNT_TIMES_100, "12345", Map.of());
        String unsigned = json.writeValueAsString(fields);
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(unsigned.replace("\"vnp_Amount\":\"1000050\"", "\"vnp_Amount\":\"2000050\"")
                        .replace("\"vnp_SecureHash\":\"", "\"vnp_SecureHash\":\"0"), MediaType.APPLICATION_JSON));
        var tampered = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.INVALID_PROVIDER_SIGNATURE, tampered.code());
    }

    @Test
    void transportFailureIsAnUnavailableQuery() {
        server.expect(requestTo(QUERY_URL)).andRespond(withServerError());
        var unavailable = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.PROVIDER_UNREACHABLE, unavailable.code());
    }

    @Test
    void providerTimeoutIsDistinguishedFromAnUnreachableProvider() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        var timeout = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.PROVIDER_TIMEOUT, timeout.code());
    }

    @Test
    void nonStringProviderFieldsAreReportedAsMalformedWithoutEchoingThem() throws Exception {
        Map<String, String> fields = responseFields("00", "00", AMOUNT_TIMES_100, "12345", Map.of());
        String body = json.writeValueAsString(fields)
                .replace("\"vnp_ResponseCode\":\"00\"", "\"vnp_ResponseCode\":0");
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var malformed = assertThrows(QueryException.class,
                () -> client.query(REF, AMOUNT, TRANSACTION_CREATED, "127.0.0.1"));
        assertEquals(QueryCode.PROVIDER_MALFORMED_RESPONSE, malformed.code());
    }

    private static String response(String responseCode, String status, long amount, String transactionNo) {
        return response(responseCode, status, amount, transactionNo, Map.of());
    }

    private static String response(String responseCode, String status, long amount, String transactionNo,
                                   Map<String, String> overrides) {
        try {
            return new ObjectMapper().writeValueAsString(
                    responseFields(responseCode, status, amount, transactionNo, overrides));
        } catch (Exception exception) {
            throw new AssertionError("Could not build synthetic query response", exception);
        }
    }

    private static Map<String, String> responseFields(String responseCode, String status, long amount,
                                                      String transactionNo, Map<String, String> overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_ResponseId", "response-1");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_ResponseCode", responseCode);
        fields.put("vnp_Message", "Yêu cầu thành công");
        fields.put("vnp_TmnCode", MERCHANT);
        fields.put("vnp_TxnRef", REF);
        fields.put("vnp_Amount", Long.toString(amount));
        fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_PayDate", "20261121090000");
        fields.put("vnp_TransactionNo", transactionNo);
        fields.put("vnp_TransactionType", "01");
        fields.put("vnp_TransactionStatus", status);
        fields.put("vnp_OrderInfo", "Query tour booking");
        fields.put("vnp_PromotionCode", "");
        fields.put("vnp_PromotionAmount", "");
        fields.putAll(overrides);
        String data = String.join("|", fields.values());
        fields.put("vnp_SecureHash", sign(data));
        return fields;
    }

    private static String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError("Could not sign synthetic query data", exception);
        }
    }
}
