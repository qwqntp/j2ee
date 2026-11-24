package com.j2ee.tour.audit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class AuditSanitizer {
    private static final int MAX_INPUT = 8192;
    private static final int MAX_OUTPUT = 2048;
    private static final Set<String> SENSITIVE = Set.of("password", "currentpassword", "newpassword",
            "token", "refreshtoken", "idtoken", "secret", "signature", "cardnumber", "cvv");
    private final ObjectMapper mapper;

    public AuditSanitizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String sanitize(String body) {
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT) return null;
        try {
            JsonNode node = mapper.readTree(body);
            if (node == null || (!node.isObject() && !node.isArray())) return null;
            redact(node);
            String result = mapper.writeValueAsString(node);
            return result.getBytes(StandardCharsets.UTF_8).length <= MAX_OUTPUT ? result : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void redact(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.properties().forEach(entry -> {
                String key = entry.getKey().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                if (SENSITIVE.contains(key)) object.put(entry.getKey(), "[REDACTED]");
                else redact(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }
}
