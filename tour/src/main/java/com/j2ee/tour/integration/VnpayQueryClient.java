package com.j2ee.tour.integration;

import com.j2ee.tour.integration.VnpayGateway.Outcome;
import com.j2ee.tour.integration.VnpayGateway.VerifiedNotification;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-to-server transaction lookup. A signed QueryDr response is authoritative evidence
 * about a VNPay attempt, because it comes from VNPay directly rather than through the
 * customer's browser.
 *
 * <p>The protocol is documented at
 * https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html
 */
@Component
public class VnpayQueryClient {
    private static final URI ENDPOINT =
            URI.create("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private static final String ORDER_INFO = "Query tour booking";
    private static final String VERSION = "2.1.0";
    private static final Set<String> RESPONSE_FIELDS = Set.of(
            "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode",
            "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
            "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo",
            "vnp_PromotionCode", "vnp_PromotionAmount", "vnp_SecureHash");
    // VNPay sends API-level errors as an unsigned envelope with only these two fields.
    private static final Set<String> UNSIGNED_ERROR_FIELDS = Set.of("vnp_ResponseCode", "vnp_Message");

    private final VnpayGateway gateway;
    private final ObjectMapper json;
    private final Clock clock;
    private final RestClient http;

    public VnpayQueryClient(VnpayGateway gateway, ObjectMapper json, Clock clock, RestClient http) {
        this.gateway = gateway;
        this.json = json;
        this.clock = clock;
        this.http = http;
    }

    /**
     * @return a verified VNPay result together with a safe diagnostic code. Only
     *         {@link QueryCode#VERIFIED_SUCCESS} and {@link QueryCode#VERIFIED_NONPAYMENT}
     *         are authoritative; everything else leaves the attempt protected.
     * @throws QueryException when the request could not be completed or the response is
     *                        not a valid, signed answer about this exact attempt. The
     *                        exception carries only a safe code and, when VNPay supplied
     *                        one, a two-digit response/transaction status.
     */
    public QueryResult query(String reference, BigDecimal amount,
                             Instant transactionCreatedAt, String clientIp) {
        gateway.requireConfigured();
        long expectedAmount = VnpayGateway.encodeAmount(amount);
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String transactionDate = VnpayGateway.timestamp(transactionCreatedAt);
        String createDate = VnpayGateway.timestamp(clock.instant());
        String ip = requestIp(clientIp);
        String checksum = gateway.sign(String.join("|", requestId, VERSION, "querydr",
                gateway.merchantCode(), reference, transactionDate, createDate, ip, ORDER_INFO));

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("vnp_RequestId", requestId);
        payload.put("vnp_Version", VERSION);
        payload.put("vnp_Command", "querydr");
        payload.put("vnp_TmnCode", gateway.merchantCode());
        payload.put("vnp_TxnRef", reference);
        payload.put("vnp_OrderInfo", ORDER_INFO);
        payload.put("vnp_TransactionDate", transactionDate);
        payload.put("vnp_CreateDate", createDate);
        payload.put("vnp_IpAddr", ip);
        payload.put("vnp_SecureHash", checksum);

        String body;
        try {
            String payloadJson = json.writeValueAsString(payload);
            byte[] response = http.post().uri(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                    .body(payloadJson).retrieve().body(byte[].class);
            // JSON is UTF-8. Decode explicitly so a missing charset parameter cannot corrupt
            // Vietnamese messages covered by the response signature.
            if (response == null || response.length > MAX_RESPONSE_BYTES) {
                throw new QueryException(QueryCode.PROVIDER_MALFORMED_RESPONSE);
            }
            body = new String(response, StandardCharsets.UTF_8);
        } catch (ResourceAccessException exception) {
            // Never attach the provider response or request to the exception message.
            throw new QueryException(timedOut(exception)
                    ? QueryCode.PROVIDER_TIMEOUT : QueryCode.PROVIDER_UNREACHABLE);
        } catch (RestClientException | JacksonException exception) {
            throw new QueryException(QueryCode.PROVIDER_UNREACHABLE);
        } catch (RuntimeException exception) {
            throw new QueryException(QueryCode.PROVIDER_UNREACHABLE);
        }
        return verify(body, reference, expectedAmount);
    }

    private QueryResult verify(String body, String reference, long expectedAmount) {
        if (body == null || body.isEmpty() || body.length() > MAX_RESPONSE_BYTES) {
            throw new QueryException(QueryCode.PROVIDER_MALFORMED_RESPONSE);
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JacksonException exception) {
            throw new QueryException(QueryCode.PROVIDER_MALFORMED_RESPONSE);
        }
        if (root == null || !root.isObject() || root.size() > RESPONSE_FIELDS.size()) {
            throw new QueryException(QueryCode.PROVIDER_MALFORMED_RESPONSE);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        var names = root.propertyNames();
        for (String name : names) {
            JsonNode value = root.get(name);
            if (!RESPONSE_FIELDS.contains(name) || value == null || !value.isString()
                    || value.stringValue().length() > 512) {
                throw new QueryException(QueryCode.PROVIDER_MALFORMED_RESPONSE);
            }
            fields.put(name, value.stringValue());
        }
        String checksum = fields.get("vnp_SecureHash");
        if (checksum == null) {
            return unsignedAnswer(fields, reference, expectedAmount);
        }
        if (!VnpayGateway.sameHex(gateway.sign(responseData(fields)), checksum)) {
            throw new QueryException(QueryCode.INVALID_PROVIDER_SIGNATURE);
        }
        // A response must describe the same merchant and attempt the server asked about.
        if (!gateway.merchantCode().equals(fields.get("vnp_TmnCode"))
                || !reference.equals(fields.get("vnp_TxnRef"))) {
            throw new QueryException(QueryCode.PROVIDER_MISMATCH);
        }
        String command = fields.getOrDefault("vnp_Command", "");
        if (!command.isEmpty() && !"querydr".equals(command)) {
            throw new QueryException(QueryCode.PROVIDER_MISMATCH);
        }
        String responseCode = fields.getOrDefault("vnp_ResponseCode", "");
        if (!responseCode.matches("[0-9]{2}")) {
            throw new QueryException(QueryCode.PROVIDER_MALFORMED_RESPONSE);
        }
        if ("91".equals(responseCode)) {
            // VNPay reports "transaction not found". That is not proof the customer was
            // not charged, so it cannot release the attempt.
            return new QueryResult(new VerifiedNotification(reference, expectedAmount, Outcome.UNRESOLVED),
                    QueryCode.TRANSACTION_NOT_FOUND, responseCode, null);
        }
        if (!"00".equals(responseCode)) {
            // The response code is a safe, documented two-digit status; the message may
            // contain provider detail and is deliberately never surfaced.
            throw new QueryException(QueryCode.PROVIDER_REJECTED, responseCode, null);
        }
        String amount = fields.getOrDefault("vnp_Amount", "");
        if (!amount.matches("[0-9]{1,12}") || Long.parseLong(amount) != expectedAmount) {
            throw new QueryException(QueryCode.PROVIDER_MISMATCH, responseCode, null);
        }
        if (!"01".equals(fields.getOrDefault("vnp_TransactionType", ""))) {
            throw new QueryException(QueryCode.PROVIDER_MISMATCH, responseCode, null);
        }
        String status = fields.getOrDefault("vnp_TransactionStatus", "");
        if (!status.matches("[0-9]{2}")) {
            throw new QueryException(QueryCode.PROVIDER_MALFORMED_RESPONSE, responseCode, null);
        }
        Outcome outcome = switch (status) {
            case "00" -> Outcome.SUCCESS;
            case "02" -> Outcome.FAILED;
            // 01 unfinished, 04 reversed, 05/06 refund pending, 07 suspected fraud, 09 refund rejected.
            default -> Outcome.UNRESOLVED;
        };
        if (outcome == Outcome.SUCCESS
                && !fields.getOrDefault("vnp_TransactionNo", "").matches("(?=.*[1-9])[0-9]{1,15}")) {
            throw new QueryException(QueryCode.PROVIDER_MISMATCH, responseCode, status);
        }
        QueryCode code = outcome == Outcome.SUCCESS
                ? QueryCode.VERIFIED_SUCCESS
                : outcome == Outcome.FAILED ? QueryCode.VERIFIED_NONPAYMENT
                : QueryCode.TRANSACTION_UNRESOLVED;
        return new QueryResult(new VerifiedNotification(reference, expectedAmount, outcome),
                code, responseCode, status);
    }

    /**
     * VNPay API-level errors (for example a disabled/unsupported QueryDr call) are returned
     * without a signature and without any transaction data. They can never settle or release
     * an attempt; they only explain why the lookup did not produce an authoritative answer.
     * An unsigned success or a data-bearing unsigned response is never trusted.
     */
    private QueryResult unsignedAnswer(Map<String, String> fields, String reference,
                                       long expectedAmount) {
        if (!UNSIGNED_ERROR_FIELDS.containsAll(fields.keySet())) {
            throw new QueryException(QueryCode.INVALID_PROVIDER_SIGNATURE);
        }
        String code = fields.getOrDefault("vnp_ResponseCode", "");
        if (!code.matches("[0-9]{2}")) {
            throw new QueryException(QueryCode.PROVIDER_MALFORMED_RESPONSE);
        }
        if ("91".equals(code)) {
            return new QueryResult(new VerifiedNotification(reference, expectedAmount, Outcome.UNRESOLVED),
                    QueryCode.TRANSACTION_NOT_FOUND, code, null);
        }
        if ("00".equals(code)) {
            // A success must carry a signed transaction body.
            throw new QueryException(QueryCode.PROVIDER_MALFORMED_RESPONSE);
        }
        throw new QueryException(QueryCode.PROVIDER_REJECTED, code, null);
    }

    private static String responseData(Map<String, String> fields) {
        return String.join("|",
                fields.getOrDefault("vnp_ResponseId", ""),
                fields.getOrDefault("vnp_Command", ""),
                fields.getOrDefault("vnp_ResponseCode", ""),
                fields.getOrDefault("vnp_Message", ""),
                fields.getOrDefault("vnp_TmnCode", ""),
                fields.getOrDefault("vnp_TxnRef", ""),
                fields.getOrDefault("vnp_Amount", ""),
                fields.getOrDefault("vnp_BankCode", ""),
                fields.getOrDefault("vnp_PayDate", ""),
                fields.getOrDefault("vnp_TransactionNo", ""),
                fields.getOrDefault("vnp_TransactionType", ""),
                fields.getOrDefault("vnp_TransactionStatus", ""),
                fields.getOrDefault("vnp_OrderInfo", ""),
                fields.getOrDefault("vnp_PromotionCode", ""),
                fields.getOrDefault("vnp_PromotionAmount", ""));
    }

    private static boolean timedOut(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException
                    || current.getClass().getSimpleName().endsWith("TimeoutException")) {
                return true;
            }
            if (current.getCause() == current) break;
        }
        return false;
    }

    // VNPay wants an IP address, not a host name. Anything unparseable, including bracketed
    // or credential-bearing values, falls back to loopback instead of being sent verbatim.
    private static String requestIp(String candidate) {
        if (candidate == null) return "127.0.0.1";
        String ip = candidate.trim();
        if (ip.contains("::1") || ip.equals("0:0:0:0:0:0:0:1")) return "127.0.0.1";
        if (ip.matches("(\\d{1,3}\\.){3}\\d{1,3}")) {
            for (String part : ip.split("\\.")) {
                if (Integer.parseInt(part) > 255) return "127.0.0.1";
            }
            return ip;
        }
        if (ip.matches("[0-9A-Fa-f:]{2,45}") && ip.contains(":")) return ip;
        return "127.0.0.1";
    }

    /** Safe, non-sensitive classification of a QueryDr answer or failure. */
    public enum QueryCode {
        VERIFIED_SUCCESS,
        VERIFIED_NONPAYMENT,
        TRANSACTION_NOT_FOUND,
        TRANSACTION_UNRESOLVED,
        PROVIDER_REJECTED,
        PROVIDER_MISMATCH,
        INVALID_PROVIDER_SIGNATURE,
        PROVIDER_MALFORMED_RESPONSE,
        PROVIDER_TIMEOUT,
        PROVIDER_UNREACHABLE
    }

    /**
     * @param providerCode   VNPay's two-digit {@code vnp_ResponseCode}, when one was received.
     * @param providerStatus VNPay's two-digit {@code vnp_TransactionStatus}, when one was received.
     */
    public record QueryResult(VerifiedNotification notification, QueryCode code,
                              String providerCode, String providerStatus) { }

    public static final class QueryException extends RuntimeException {
        private final QueryCode code;
        private final String providerCode;
        private final String providerStatus;

        public QueryException(QueryCode code) {
            this(code, null, null);
        }

        public QueryException(QueryCode code, String providerCode, String providerStatus) {
            super("VNPay transaction query unavailable");
            this.code = code;
            this.providerCode = safeCode(providerCode);
            this.providerStatus = safeCode(providerStatus);
        }

        public QueryCode code() {
            return code;
        }

        public String providerCode() {
            return providerCode;
        }

        public String providerStatus() {
            return providerStatus;
        }

        private static String safeCode(String code) {
            return code != null && code.matches("[0-9]{2}") ? code : null;
        }
    }
}
