package nl.hauntedmc.dataregistry.backend.service;

/**
 * Internal value sanitization helpers.
 */
final class Sanitization {

    private Sanitization() {
    }

    static String trimToLengthOrNull(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    static String safeForLog(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.replace('\n', '_').replace('\r', '_');
    }
}
