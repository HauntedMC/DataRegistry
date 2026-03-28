package nl.hauntedmc.dataregistry.backend.service;

/**
 * Internal value sanitization helpers.
 */
final class Sanitization {

    private static final int MAX_LOG_VALUE_LENGTH = 256;

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
        int outputLimit = Math.min(value.length(), MAX_LOG_VALUE_LENGTH);
        StringBuilder sanitized = new StringBuilder(outputLimit + 3);
        for (int i = 0; i < value.length() && sanitized.length() < outputLimit; i++) {
            char character = value.charAt(i);
            sanitized.append(Character.isISOControl(character) ? '_' : character);
        }
        if (value.length() > outputLimit) {
            sanitized.append("...");
        }
        return sanitized.toString();
    }
}
