package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

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
}
