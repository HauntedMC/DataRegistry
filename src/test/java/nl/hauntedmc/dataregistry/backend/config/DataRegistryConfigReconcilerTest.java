package nl.hauntedmc.dataregistry.backend.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistryConfigReconcilerTest {

    @Test
    void reconcileAddsMissingAndRemovesUnknownKeys() {
        Map<String, Object> schema = linkedMap(
                "a", 1,
                "b", linkedMap(
                        "c", true,
                        "d", "default"
                ),
                "e", "required"
        );
        Map<String, Object> raw = linkedMap(
                "a", 2,
                "b", linkedMap(
                        "c", false,
                        "unknown", 99
                ),
                "legacy", "remove-me"
        );

        DataRegistryConfigReconciler.Result result = DataRegistryConfigReconciler.reconcile(raw, schema);

        assertTrue(result.changed());
        assertEquals(2, result.addedKeys());
        assertEquals(2, result.removedKeys());
        assertEquals(0, result.replacedValues());
        assertEquals(2, result.configRoot().get("a"));
        assertEquals("required", result.configRoot().get("e"));
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) result.configRoot().get("b");
        assertEquals(false, b.get("c"));
        assertEquals("default", b.get("d"));
        assertFalse(b.containsKey("unknown"));
    }

    @Test
    void reconcileReplacesTypeMismatchesWithSchemaDefaults() {
        Map<String, Object> schema = linkedMap(
                "a", 1,
                "b", linkedMap(
                        "c", true
                )
        );
        Map<String, Object> raw = linkedMap(
                "a", "not-a-number",
                "b", "not-a-map"
        );

        DataRegistryConfigReconciler.Result result = DataRegistryConfigReconciler.reconcile(raw, schema);

        assertTrue(result.changed());
        assertEquals(1, result.addedKeys());
        assertEquals(0, result.removedKeys());
        assertEquals(2, result.replacedValues());
        assertEquals(1, result.configRoot().get("a"));
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) result.configRoot().get("b");
        assertEquals(true, b.get("c"));
    }

    @Test
    void reconcileReportsUnchangedWhenRawAlreadyMatchesSchemaShape() {
        Map<String, Object> schema = linkedMap(
                "a", 1,
                "b", linkedMap(
                        "c", true
                )
        );
        Map<String, Object> raw = linkedMap(
                "a", 42,
                "b", linkedMap(
                        "c", false
                )
        );

        DataRegistryConfigReconciler.Result result = DataRegistryConfigReconciler.reconcile(raw, schema);

        assertFalse(result.changed());
        assertEquals(0, result.addedKeys());
        assertEquals(0, result.removedKeys());
        assertEquals(0, result.replacedValues());
        assertEquals(42, result.configRoot().get("a"));
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) result.configRoot().get("b");
        assertEquals(false, b.get("c"));
    }

    @SafeVarargs
    private static <K, V> Map<K, V> linkedMap(Object... entries) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) entries[i];
            @SuppressWarnings("unchecked")
            V value = (V) entries[i + 1];
            map.put(key, value);
        }
        return map;
    }
}
