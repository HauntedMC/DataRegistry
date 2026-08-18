package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        ClassLoader resourceLoader = resourceLoader(template);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);

        DataRegistryConfigIO.addMissingDefaults(configPath, resourceLoader, logger);

        String updated = Files.readString(configPath);
        assertTrue(updated.contains("# keep my comment"));
        Map<?, ?> config = DataRegistryConfigIO.readConfig(configPath, logger);
        Map<?, ?> features = (Map<?, ?>) config.get("features");
        Map<?, ?> playtime = (Map<?, ?>) config.get("playtime");
        Map<?, ?> query = (Map<?, ?>) config.get("query");
        assertEquals(false, features.get("online-status"));
        assertEquals(false, features.get("population"));
        assertEquals(45, playtime.get("flush-interval-seconds"));
        assertEquals(true, playtime.get("resolve-unknown-servers-as-gamemode"));
        assertEquals(3000, query.get("timeout-millis"));
        verify(logger).info("Updated DataRegistry config with 3 missing default settings: "
                + "features.population, playtime.resolve-unknown-servers-as-gamemode, query.timeout-millis");
    }

    @Test
    void addMissingDefaultsReportsUnknownSettingsWithoutRemovingThem() throws Exception {
        Path configPath = temporaryDirectory.resolve(DataRegistryConfigIO.FILE_NAME);
        Files.writeString(configPath, """
                features:
                  online-status: true
                  online-stauts: false
                custom-section:
                  custom-value: true
                """);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);

        DataRegistryConfigIO.addMissingDefaults(
                configPath,
                resourceLoader("features:\n  online-status: true\n"),
                logger
        );

        Map<?, ?> config = DataRegistryConfigIO.readConfig(configPath, logger);
        assertTrue(config.containsKey("custom-section"));
        verify(logger).warn("Unknown DataRegistry config settings are ignored: custom-section, features.online-stauts");
    }

    @Test
    void addMissingDefaultsReportsMalformedYamlWithConfigPath() throws Exception {
        Path configPath = temporaryDirectory.resolve(DataRegistryConfigIO.FILE_NAME);
        Files.writeString(configPath, "features: [\n");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DataRegistryConfigIO.addMissingDefaults(
                        configPath,
                        resourceLoader("features:\n  online-status: true\n"),
                        mock(ILoggerAdapter.class)
                )
        );

        assertTrue(failure.getMessage().contains(configPath.toString()));
    }

    private static ClassLoader resourceLoader(String template) {
        return new ClassLoader() {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                return DataRegistryConfigIO.FILE_NAME.equals(name)
                        ? new ByteArrayInputStream(template.getBytes(StandardCharsets.UTF_8))
                        : null;
            }
        };
    }
}
