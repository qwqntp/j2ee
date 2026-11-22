package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.dto.response.VnpayReconcileResponse;
import com.j2ee.tour.dto.response.VnpayStartResponse;
import com.j2ee.tour.dto.response.VnpayStatusResponse;
import com.j2ee.tour.integration.VnpayGateway;
import com.j2ee.tour.integration.VnpayGateway.VerificationException;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.VnpayPaymentService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
public class VnpayPaymentController {
    private static final int MAX_RETURN_BODY_BYTES = 16 * 1024;
    private static final int MAX_QUERY_LENGTH = 8192;
    private static final int MAX_FIELDS = 32;
    private static final String IPN = "/api/payments/vnpay/ipn";

    private final VnpayGateway gateway;
    private final VnpayPaymentService service;
    private final ObjectMapper json;

    public VnpayPaymentController(VnpayGateway gateway, VnpayPaymentService service, ObjectMapper json) {
        this.gateway = gateway;
        this.service = service;
        this.json = json;
    }

    @PostMapping("/api/bookings/{bookingId}/payments/vnpay")
    public ResponseEntity<ApiResponse<VnpayStartResponse>> start(
            @PathVariable Long bookingId, @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest request) {
        return userResponse(() -> service.start(bookingId, principal, request.getRemoteAddr()));
    }

    @PostMapping(path = "/api/payments/vnpay/return", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<VnpayStatusResponse>> verifyReturn(
            @AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request) {
        return userResponse(() -> service.verifyReturn(parseQuery(readReturnQuery(request)), principal,
                request.getRemoteAddr()));
    }

    @PostMapping("/api/bookings/{bookingId}/payments/vnpay/reconcile")
    public ResponseEntity<ApiResponse<VnpayReconcileResponse>> reconcile(
            @PathVariable Long bookingId, @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest request) {
        return userResponse(() -> service.reconcileBooking(bookingId, principal, request.getRemoteAddr()));
    }

    @GetMapping("/api/payments/vnpay/status/{reference}")
    public ResponseEntity<ApiResponse<VnpayStatusResponse>> status(
            @PathVariable String reference, @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest request) {
        return userResponse(() -> service.status(reference, principal, request.getRemoteAddr()));
    }

    // VNPay calls a registered HTTPS URL with a standard GET; it need not send a header.
    @GetMapping({IPN, IPN + "/{routeToken}"})
    public ResponseEntity<IpnAcknowledgement> ipn(@PathVariable(required = false) String routeToken,
                                                 HttpServletRequest request) {
        if (!gateway.hasTrustedIpnToken(routeToken)) {
            return acknowledgement("97", "Invalid signature");
        }
        try {
            var notification = gateway.verify(parseQuery(request.getQueryString()));
            // The transactional service proxy does not return until the database commits.
            return switch (service.applyIpn(notification)) {
                case APPLIED -> acknowledgement("00", "Confirm Success");
                case ALREADY_PROCESSED -> acknowledgement("02", "Order already confirmed");
                case NOT_FOUND -> acknowledgement("01", "Order not found");
                case INVALID_AMOUNT -> acknowledgement("04", "Invalid amount");
                case UNRESOLVED -> acknowledgement("99", "Unresolved payment");
            };
        } catch (VerificationException exception) {
            return switch (exception.reason()) {
                case INVALID_SIGNATURE -> acknowledgement("97", "Invalid signature");
                case INVALID_AMOUNT -> acknowledgement("04", "Invalid amount");
                case MALFORMED -> acknowledgement("99", "Invalid request");
            };
        } catch (RuntimeException exception) {
            // Do not report the request URL, signature, or provider fields to application logs.
            return acknowledgement("99", "Processing error");
        }
    }

    private String readReturnQuery(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_RETURN_BODY_BYTES + 1);
            if (body.length > MAX_RETURN_BODY_BYTES) throw malformed();
            var value = json.readTree(body);
            if (value == null || !value.isObject() || value.size() != 1 || !value.path("query").isString()) {
                throw malformed();
            }
            return value.path("query").stringValue();
        } catch (IOException | JacksonException exception) {
            throw malformed();
        }
    }

    private static Map<String, List<String>> parseQuery(String query) {
        if (query == null || query.isEmpty() || query.length() > MAX_QUERY_LENGTH) throw malformed();
        var fields = new HashMap<String, List<String>>();
        String[] entries = query.split("&", MAX_FIELDS + 1);
        if (entries.length > MAX_FIELDS) throw malformed();
        try {
            for (String entry : entries) {
                int separator = entry.indexOf('=');
                if (separator <= 0) throw malformed();
                String key = URLDecoder.decode(entry.substring(0, separator), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(entry.substring(separator + 1), StandardCharsets.UTF_8);
                fields.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
            }
        } catch (IllegalArgumentException exception) {
            throw malformed();
        }
        return fields;
    }

    private static VerificationException malformed() {
        return new VerificationException(VnpayGateway.Reason.MALFORMED);
    }

    private static <T> ResponseEntity<ApiResponse<T>> userResponse(Supplier<T> action) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(action.get()));
        } catch (ApiException exception) {
            throw exception;
        } catch (VerificationException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not verify the VNPay return");
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not process the VNPay request");
        }
    }

    private static ResponseEntity<IpnAcknowledgement> acknowledgement(String code, String message) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new IpnAcknowledgement(code, message));
    }

    public record IpnAcknowledgement(@JsonProperty("RspCode") String code,
                                    @JsonProperty("Message") String message) { }
}
