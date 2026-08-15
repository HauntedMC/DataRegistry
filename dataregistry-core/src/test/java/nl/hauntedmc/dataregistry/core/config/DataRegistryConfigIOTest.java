package nl.hauntedmc.dataregistry.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistryConfigIOTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void writeIfChangedReplacesExistingContentAndLeavesNoTemporaryFile() throws Exception {
        Path configPath = temporaryDirectory.resolve("config.yml");
        Files.writeString(configPath, "before\n");

        assertTrue(DataRegistryConfigIO.writeIfChanged(configPath, "after\n"));
        assertEquals("after\n", Files.readString(configPath));
        assertFalse(DataRegistryConfigIO.writeIfChanged(configPath, "after\n"));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1L, files.count());
        }
    }
}
