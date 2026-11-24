package com.j2ee.tour.audit;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class AuditHelpersTest {
    @Test
    void parsesEntityAndExplicitAction() {
        var result = AuditPath.parse("POST", "/api/bookings/5/confirm");
        assertEquals("bookings", result.entityType());
        assertEquals(5L, result.entityId());
        assertEquals("CONFIRM booking", result.action());
    }

    @Test
    void parsesAdminResourceWithNumericId() {
        var result = AuditPath.parse("PUT", "/api/admin/tours/12");
        assertEquals("tours", result.entityType());
        assertEquals(12L, result.entityId());
        assertEquals("UPDATE tour", result.action());
    }

    @Test
    void redactsNestedSecretsAndPreservesSafeValues() {
        var sanitizer = new AuditSanitizer(new ObjectMapper());
        String result = sanitizer.sanitize("{\"name\":\"safe\",\"nested\":[{\"NewPassword\":\"secret\",\"token\":\"abc\"}]}");
        assertTrue(result.contains("safe"));
        assertFalse(result.contains("secret"));
        assertFalse(result.contains("abc"));
    }

    @Test
    void omitsMalformedAndOversizedBodies() {
        var sanitizer = new AuditSanitizer(new ObjectMapper());
        assertNull(sanitizer.sanitize("{\"password\":\"unfinished"));
        assertNull(sanitizer.sanitize("{\"x\":\"" + "a".repeat(9000) + "\"}"));
    }
}
