package com.j2ee.tour.audit;

import java.util.Locale;

public final class AuditPath {
    private AuditPath() {}

    public record Parsed(String entityType, Long entityId, String action) {}

    public static Parsed parse(String method, String path) {
        String[] parts = path.split("/");
        int start = parts.length > 2 && "admin".equals(parts[2]) ? 3 : 2;
        String type = parts.length > start && !parts[start].isBlank() ? parts[start] : null;
        Long id = null;
        if (parts.length > start + 1) {
            try {
                id = Long.valueOf(parts[start + 1]);
            } catch (NumberFormatException ignored) {
                // Collection routes need not carry a numeric ID.
            }
        }
        String verb = switch (method.toUpperCase(Locale.ROOT)) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> method.toUpperCase(Locale.ROOT);
        };
        if (id != null && parts.length > start + 2 && parts[start + 2].matches("[A-Za-z-]+")) {
            verb = parts[start + 2].toUpperCase(Locale.ROOT);
        }
        String label = type == null ? "resource" : type.endsWith("s") ? type.substring(0, type.length() - 1) : type;
        return new Parsed(type, id, verb + " " + label);
    }
}
