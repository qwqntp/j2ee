package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.PaymentCallbackRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class PaymentSignatureService {

    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    public PaymentSignatureService(@Value("${app.payment.callback-secret:}") String secret) {
        this.secret = secret;
    }

    public void verify(Long bookingId, PaymentCallbackRequest request) {
        if (secret == null || secret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment callback signing is not configured");
        }
        if (request.signature() == null) {
            throw invalidSignature();
        }
        String expected = sign(bookingId, request.providerTransactionId(), request.method(),
                request.amount(), request.successful());
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                request.signature().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw invalidSignature();
        }
    }

    public String sign(Long bookingId, String providerTransactionId, String method,
                       BigDecimal amount, Boolean successful) {
        String payload = bookingId + "|" + providerTransactionId + "|" + method + "|"
                + successful + "|" + canonicalAmount(amount);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create payment signature", exception);
        }
    }

    private String canonicalAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private ApiException invalidSignature() {
        return new ApiException(HttpStatus.FORBIDDEN, "Invalid payment signature");
    }
}
