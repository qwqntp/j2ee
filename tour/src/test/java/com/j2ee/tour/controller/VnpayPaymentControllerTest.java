package com.j2ee.tour.controller;

import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.dto.response.VnpayReconcileResponse;
import com.j2ee.tour.dto.response.VnpayStartResponse;
import com.j2ee.tour.dto.response.VnpayStatusResponse;
import com.j2ee.tour.integration.VnpayGateway;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.security.FixedWindowRateLimiter;
import com.j2ee.tour.security.JwtService;
import com.j2ee.tour.security.SecurityConfig;
import com.j2ee.tour.service.VnpayPaymentService;
import com.j2ee.tour.service.VnpayPaymentService.IpnCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.j2ee.tour.integration.VnpayTestPayload.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(controllers = VnpayPaymentController.class, properties = {
        "app.vnpay.tmn-code=" + MERCHANT, "app.vnpay.hash-secret=" + KEY,
        "app.vnpay.ipn-token=" + TOKEN, "app.vnpay.pay-url=" + PAY, "app.vnpay.return-url=" + RETURN
})
@Import({SecurityConfig.class, VnpayGateway.class, GlobalExceptionHandler.class})
class VnpayPaymentControllerTest {
    private static final String REF = "VNP" + "A".repeat(32);
    private static final String START = "/api/bookings/42/payments/vnpay";
    private static final String RETURN_API = "/api/payments/vnpay/return";
    private static final String STATUS = "/api/payments/vnpay/status/" + REF;
    private static final String RECONCILE = "/api/bookings/42/payments/vnpay/reconcile";
    private static final String IPN = "/api/payments/vnpay/ipn";
    private static final AuthPrincipal OWNER = new AuthPrincipal(1L, "owner", "ROLE_USER");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private VnpayPaymentService vnpay;
    @MockitoBean private JwtService jwt;
    @MockitoBean private FixedWindowRateLimiter rateLimiter;

    @BeforeEach
    void ownerToken() {
        when(jwt.parse("synthetic-owner")).thenReturn(OWNER);
    }

    @Test
    void requiresBearerForCustomerEndpointsAndProtectsLegacyRoutes() throws Exception {
        mvc.perform(post(START)).andExpect(status().isUnauthorized());
        mvc.perform(post(RETURN_API).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(STATUS)).andExpect(status().isUnauthorized());
        mvc.perform(post(RECONCILE)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings/42/payments/callback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-owner"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-owner"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanStartAndReadNoStoreResponsesWithoutAdminRole() throws Exception {
        when(vnpay.start(eq(42L), eq(OWNER), any())).thenReturn(
                new VnpayStartResponse(REF, URI.create(PAY), Instant.parse("2026-11-21T02:15:00Z")));
        when(vnpay.status(REF, OWNER, "127.0.0.1"))
                .thenReturn(new VnpayStatusResponse(42L, REF, "PENDING", false, null,
                        "TRANSACTION_NOT_FOUND", "91", null));
        when(vnpay.verifyReturn(any(), eq(OWNER), any())).thenReturn(
                new VnpayStatusResponse(42L, REF, "PENDING", false, VnpayGateway.Outcome.SUCCESS,
                        "PROVIDER_TIMEOUT", null, null));
        when(vnpay.reconcileBooking(eq(42L), eq(OWNER), any()))
                .thenReturn(new VnpayReconcileResponse(42L, REF, "FAILED", true,
                        "VERIFIED_NONPAYMENT", "00", "02"));
        mvc.perform(post(START).header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-owner"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.reference").value(REF));
        mvc.perform(get(STATUS).header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-owner"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reason").value("TRANSACTION_NOT_FOUND"))
                .andExpect(jsonPath("$.data.providerCode").value("91"));
        mvc.perform(post(RETURN_API).header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("query", query(fields(REF, "1000050", "00", "00"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.returnOutcome").value("SUCCESS"));
        mvc.perform(post(RECONCILE).header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-owner"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.bookingId").value(42))
                .andExpect(jsonPath("$.data.reason").value("VERIFIED_NONPAYMENT"))
                .andExpect(jsonPath("$.data.providerStatus").value("02"))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
        verify(vnpay, never()).applyIpn(any());
    }

    @Test
    void signedBrowserReturnIsNotAuthorizedAsIpnWithoutRegisteredPath() throws Exception {
        String q = query(fields(REF, "1000050", "00", "00"));
        mvc.perform(get(URI.create(IPN + "?" + q)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("97"));
        mvc.perform(get(URI.create(IPN + "/not-the-registered-path?" + q)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("97"));
        verify(vnpay, never()).applyIpn(any());
    }

    @ParameterizedTest
    @CsvSource({"APPLIED,00", "ALREADY_PROCESSED,02", "NOT_FOUND,01", "INVALID_AMOUNT,04", "UNRESOLVED,99"})
    void registeredIpnUsesStandardSignedGetWithoutExtraHeaders(IpnCode result, String code) throws Exception {
        when(vnpay.applyIpn(any())).thenReturn(result);
        mvc.perform(get(URI.create(IPN + "/" + TOKEN + "?" + query(fields(REF, "1000050", "00", "00")))))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.RspCode").value(code)).andExpect(jsonPath("$.Message").isString())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void ipnRejectsRepeatedProviderFieldsWithoutSettling() throws Exception {
        var payload = fields(REF, "1000050", "00", "00");
        payload.put("vnp_TxnRef", List.of(REF, REF));
        mvc.perform(get(URI.create(IPN + "/" + TOKEN + "?" + query(payload))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("99"));
        verify(vnpay, never()).applyIpn(any());
    }

    @Test
    void invalidSignatureAndProcessingFailureReceiveProviderAcknowledgements() throws Exception {
        var payload = fields(REF, "1000050", "00", "00");
        payload.put("vnp_SecureHash", List.of("0".repeat(128)));
        mvc.perform(get(URI.create(IPN + "/" + TOKEN + "?" + query(payload))))
                .andExpect(jsonPath("$.RspCode").value("97"));

        when(vnpay.applyIpn(any())).thenThrow(new IllegalStateException("synthetic processing failure"));
        mvc.perform(get(URI.create(IPN + "/" + TOKEN + "?" + query(fields(REF, "1000050", "00", "00")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("99"));
    }

    @Test
    void browserReturnRejectsOversizedJsonWithoutCallingService() throws Exception {
        mvc.perform(post(RETURN_API).header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("query", "x".repeat(17000)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verify(vnpay, never()).verifyReturn(any(), any(), any());
    }
}
