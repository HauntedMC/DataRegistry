package nl.hauntedmc.dataregistry.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SanitizationTest {

    @Test
    void trimToLengthOrNullHandlesNullBlankAndTruncation() {
        assertNull(Sanitization.trimToLengthOrNull(null, 8));
        assertNull(Sanitization.trimToLengthOrNull("   ", 8));
        assertEquals("abc", Sanitization.trimToLengthOrNull("  abc  ", 8));
        assertEquals("abcd", Sanitization.trimToLengthOrNull("  abcdef  ", 4));
    }

    @Test
    void emptyIfNullAndSafeForLogNormalizeValues() {
        assertEquals("", Sanitization.emptyIfNull(null));
        assertEquals("value", Sanitization.emptyIfNull("value"));
        assertEquals("<null>", Sanitization.safeForLog(null));
        assertEquals("line_1_line_2", Sanitization.safeForLog("line\n1\rline\n2"));
    }
}
