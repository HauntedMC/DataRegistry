package nl.hauntedmc.dataregistry.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reconciles a raw YAML map with a schema tree by adding missing keys and removing unknown keys.
 */
final class DataRegistryConfigReconciler {

    private DataRegistryConfigReconciler() {
    }

    static Result reconcile(Map<?, ?> rawRoot, Map<String, Object> schemaRoot) {
        Objects.requireNonNull(schemaRoot, "schemaRoot must not be null");
        Counters counters = new Counters();
        Map<String, Object> reconciled = reconcileMap(rawRoot, schemaRoot, counters);
        boolean changed = counters.addedKeys > 0 || counters.removedKeys > 0 || counters.replacedValues > 0;
        return new Result(
                reconciled,
                changed,
                counters.addedKeys,
                counters.removedKeys,
                counters.replacedValues
        );
    }

    private static Map<String, Object> reconcileMap(Object rawNode, Map<String, Object> schemaNode, Counters counters) {
        Map<?, ?> rawMap;
        if (rawNode instanceof Map<?, ?> map) {
            rawMap = map;
        } else {
            if (rawNode != null) {
                counters.replacedValues++;
            }
            rawMap = Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> schemaEntry : schemaNode.entrySet()) {
            String key = schemaEntry.getKey();
            Object expectedValue = schemaEntry.getValue();
            Object rawValue = rawMap.get(key);

            if (expectedValue instanceof Map<?, ?> expectedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> expectedMapTyped = (Map<String, Object>) expectedMap;
                result.put(key, reconcileMap(rawValue, expectedMapTyped, counters));
                continue;
            }

            if (rawValue == null) {
                counters.addedKeys++;
                result.put(key, expectedValue);
                continue;
            }

            if (isTypeCompatible(rawValue, expectedValue)) {
                result.put(key, rawValue);
                continue;
            }

            counters.replacedValues++;
            result.put(key, expectedValue);
        }

        for (Object rawKey : rawMap.keySet()) {
            if (!(rawKey instanceof String rawKeyString) || !schemaNode.containsKey(rawKeyString)) {
                counters.removedKeys++;
            }
        }

        return result;
    }

    private static boolean isTypeCompatible(Object rawValue, Object expectedValue) {
        if (expectedValue instanceof String) {
            return rawValue instanceof String;
        }
        if (expectedValue instanceof Boolean) {
            return rawValue instanceof Boolean;
        }
        if (expectedValue instanceof Number) {
            return rawValue instanceof Number;
        }
        return rawValue != null && expectedValue.getClass().isInstance(rawValue);
    }

    record Result(
            Map<String, Object> configRoot,
            boolean changed,
            int addedKeys,
            int removedKeys,
            int replacedValues
    ) {
    }

    private static final class Counters {
        private int addedKeys;
        private int removedKeys;
        private int replacedValues;
    }
}
