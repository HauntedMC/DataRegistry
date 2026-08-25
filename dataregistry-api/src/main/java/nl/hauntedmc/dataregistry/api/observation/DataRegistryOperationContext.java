package nl.hauntedmc.dataregistry.api.observation;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable, payload-free metadata for one DataRegistry operation.
 *
 * <p>Operation names are deliberately limited to a bounded internal vocabulary. Player identifiers,
 * usernames, addresses, service-instance identifiers, connection names, query text, and arbitrary
 * caller input do not belong in this context.</p>
 *
 * @param operation stable operation name such as {@code player.identity.lookup}
 */
public record DataRegistryOperationContext(String operation) {

    private static final int MAX_OPERATION_LENGTH = 96;
    private static final Pattern OPERATION_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    public DataRegistryOperationContext {
        Objects.requireNonNull(operation, "Operation cannot be null.");
        if (operation.isBlank()) {
            throw new IllegalArgumentException("Operation cannot be blank.");
        }
        if (operation.length() > MAX_OPERATION_LENGTH || !OPERATION_PATTERN.matcher(operation).matches()) {
            throw new IllegalArgumentException("Operation must use the stable lower-case operation vocabulary.");
        }
    }
}
