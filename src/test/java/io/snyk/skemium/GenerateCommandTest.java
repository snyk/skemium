package io.snyk.skemium;

import io.snyk.skemium.avro.AvroSchemaFile;
import io.snyk.skemium.meta.MetadataFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GenerateCommandTest {
    static PostgreSQLContainer<?> POSTGRES_CONTAINER = TestHelper.initPostgresContainer();

    Path TEMP_DIR;

    @BeforeAll
    static void startDB() {
        POSTGRES_CONTAINER.start();
    }

    @AfterAll
    static void stopDB() {
        POSTGRES_CONTAINER.stop();
    }

    @BeforeEach
    public void createTempDir() throws IOException {
        if (TEMP_DIR != null) {
            FileUtils.deleteDirectory(TEMP_DIR.toFile());
        }
        TEMP_DIR = Files.createTempDirectory("skemium-test-");
    }

    @Test
    void shouldGenerateSchemasIntoTheGivenDirectory() throws IOException {
        // TODO Map logger to stdout/err, if possible
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // `generate` runs successfully
        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(TestHelper.POSTGRES_DEFAULT_PORT).toString(),
                "--database", TestHelper.DB_NAME,
                "--username", TestHelper.DB_USER,
                "--password", TestHelper.DB_PASS,
                TEMP_DIR.toAbsolutePath().toString()
        ));

        // Metadata file contains...
        final MetadataFile meta = MetadataFile.loadFrom(TEMP_DIR);

        // ... command line arguments
        assertEquals(List.of(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(TestHelper.POSTGRES_DEFAULT_PORT).toString(),
                "--database", TestHelper.DB_NAME,
                "--username", TestHelper.DB_USER,
                "--password", TestHelper.DB_PASS,
                TEMP_DIR.toAbsolutePath().toString()), meta.arguments());

        // .. count of schemas
        assertEquals(11, meta.schemaCount());

        // ... specific schemas checksums, as well as a summary of all
        for (final Map.Entry<String, String> schemaMetaEntry : meta.schemas().entrySet()) {
            final AvroSchemaFile schemaFile = AvroSchemaFile.loadFrom(TEMP_DIR, schemaMetaEntry.getKey());
            assertEquals(schemaMetaEntry.getValue(), schemaFile.checksum());
        }
        assertEquals("328efc831fc344c7b4c30937174fd5727e270926cb54dcdde7d80a2e2b77d825", meta.checksumSHA256());

        // ... VCS information
        assertNotNull(meta.vcsCommit());
        assertNotNull(meta.vcsBranch());
    }

    @Test
    void shouldGenerateSchemaForSubsetOfTables() throws IOException {
        // TODO Map logger to stdout/err, if possible
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // `generate` runs successfully
        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(TestHelper.POSTGRES_DEFAULT_PORT).toString(),
                "--database", TestHelper.DB_NAME,
                "--username", TestHelper.DB_USER,
                "--password", TestHelper.DB_PASS,
                "--table", "employee",
                "--table", "artist,album",
                TEMP_DIR.toAbsolutePath().toString()
        ));

        // Metadata file contains...
        final MetadataFile meta = MetadataFile.loadFrom(TEMP_DIR);

        // ... command line arguments
        assertEquals(List.of(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(TestHelper.POSTGRES_DEFAULT_PORT).toString(),
                "--database", TestHelper.DB_NAME,
                "--username", TestHelper.DB_USER,
                "--password", TestHelper.DB_PASS,
                "--table", "employee",
                "--table", "artist,album",
                TEMP_DIR.toAbsolutePath().toString()), meta.arguments());

        // .. count of schemas
        assertEquals(3, meta.schemaCount());

        // ... specific schemas checksums, as well as a summary of all
        for (final Map.Entry<String, String> schemaMetaEntry : meta.schemas().entrySet()) {
            final AvroSchemaFile schemaFile = AvroSchemaFile.loadFrom(TEMP_DIR, schemaMetaEntry.getKey());
            assertEquals(schemaMetaEntry.getValue(), schemaFile.checksum());
        }
        assertEquals("5c814aff5e5e21609d33ee15bc206957da03d4cdc3991c5ac7927d1c11b71209", meta.checksumSHA256());

        // ... VCS information
        assertNotNull(meta.vcsCommit());
        assertNotNull(meta.vcsBranch());
    }

    @Test
    void shouldGenerateEmptyMetaIfDBSchemaIsNotFound() throws IOException {
        // TODO Map logger to stdout/err, if possible
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // `generate` runs successfully
        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(TestHelper.POSTGRES_DEFAULT_PORT).toString(),
                "--database", TestHelper.DB_NAME,
                "--username", TestHelper.DB_USER,
                "--password", TestHelper.DB_PASS,
                "--schema", "BaNaNa",
                TEMP_DIR.toAbsolutePath().toString()
        ));

        // Metadata file contains...
        final MetadataFile meta = MetadataFile.loadFrom(TEMP_DIR);

        // ... command line arguments
        assertEquals(List.of(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(TestHelper.POSTGRES_DEFAULT_PORT).toString(),
                "--database", TestHelper.DB_NAME,
                "--username", TestHelper.DB_USER,
                "--password", TestHelper.DB_PASS,
                "--schema", "BaNaNa",
                TEMP_DIR.toAbsolutePath().toString()), meta.arguments());

        // .. count of schemas
        assertEquals(0, meta.schemaCount());

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", meta.checksumSHA256());

        // ... VCS information
        assertNotNull(meta.vcsCommit());
        assertNotNull(meta.vcsBranch());
    }

    @Test
    void shouldFailIfCannotConnectToDB() throws IOException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand());

        // `generate` runs successfully
        assertEquals(1, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", String.valueOf(POSTGRES_CONTAINER.getMappedPort(TestHelper.POSTGRES_DEFAULT_PORT)+1),
                "--database", TestHelper.DB_NAME,
                "--username", TestHelper.DB_USER,
                "--password", TestHelper.DB_PASS,
                TEMP_DIR.toAbsolutePath().toString()
        ));
    }

    @Test
    void shouldFailIfDBNameDoesNotExist() throws IOException {
        final CommandLine cmdLine = new CommandLine(new GenerateCommand());

        // `generate` runs successfully
        assertEquals(1, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(TestHelper.POSTGRES_DEFAULT_PORT).toString(),
                "--database", "BaNaNa",
                "--username", TestHelper.DB_USER,
                "--password", TestHelper.DB_PASS,
                TEMP_DIR.toAbsolutePath().toString()
        ));
    }
}
