package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DataRegistryConfigIOTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void ensureConfigFileCopiesPackagedTemplateExactly() throws Exception {
        String template = "# operator documentation\nfeatures:\n  population: true\n";
        ClassLoader resourceLoader = new ClassLoader() {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                return DataRegistryConfigIO.FILE_NAME.equals(name)
                        ? new ByteArrayInputStream(template.getBytes(StandardCharsets.UTF_8))
                        : null;
            }
        };

        Path configPath = DataRegistryConfigIO.ensureConfigFile(
                temporaryDirectory,
                resourceLoader,
                mock(ILoggerAdapter.class)
        );

        assertEquals(template, Files.readString(configPath));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void ensureConfigFileNeverOverwritesExistingOperatorConfig() throws Exception {
        Path configPath = temporaryDirectory.resolve(DataRegistryConfigIO.FILE_NAME);
        String existing = "# keep my comment\ncustom-section:\n  custom-value: true\n";
        Files.writeString(configPath, existing);

        ClassLoader resourceLoader = new ClassLoader() {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                return new ByteArrayInputStream("replacement: true\n".getBytes(StandardCharsets.UTF_8));
            }
        };

        DataRegistryConfigIO.ensureConfigFile(temporaryDirectory, resourceLoader, mock(ILoggerAdapter.class));

        assertEquals(existing, Files.readString(configPath));
    }

    @Test
    void addMissingDefaultsPreservesOperatorChoicesAndReportsAddedSettings() throws Exception {
        Path configPath = temporaryDirectory.resolve(DataRegistryConfigIO.FILE_NAME);
        Files.writeString(configPath, """
                # keep my comment
                features:
                  online-status: false
                playtime:
                  flush-interval-seconds: 45
                """);
        String template = """
                features:
                  online-status: true
                  population: true
                playtime:
                  flush-interval-seconds: 30
                  resolve-unknown-servers-as-gamemode: true
                query:
                  timeout-millis: 3000
                """;
        ClassLoader resourceLoader = new ClassLoader() {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                return DataRegistryConfigIO.FILE_NAME.equals(name)
                        ? new ByteArrayInputStream(template.getBytes(StandardCharsets.UTF_8))
                        : null;
            }
        };
        ILoggerAdapter logger = mock(ILoggerAdapter.class);

        DataRegistryConfigIO.addMissingDefaults(configPath, resourceLoader, logger);

        String updated = Files.readString(configPath);
        assertTrue(updated.contains("# keep my comment"));
        assertTrue(updated.contains("online-status: false"));
        assertTrue(updated.contains("population: false"));
        assertTrue(updated.contains("flush-interval-seconds: 45"));
        assertTrue(updated.contains("resolve-unknown-servers-as-gamemode: true"));
        assertTrue(updated.contains("timeout-millis: 3000"));
        verify(logger).info("Updated DataRegistry config with 3 missing default settings: "
                + "features.population, playtime.resolve-unknown-servers-as-gamemode, query");
    }
}
