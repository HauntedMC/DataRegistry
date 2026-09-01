package nl.hauntedmc.dataregistry.api.session;

/** Versioned reference to session metadata owned by another domain. */
public record SessionMetadataReference(String namespace, String key, long revision) {
    public SessionMetadataReference {
        namespace = require(namespace, "namespace");
        key = require(key, "key");
        if (revision < 0) throw new IllegalArgumentException("revision cannot be negative");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
}
