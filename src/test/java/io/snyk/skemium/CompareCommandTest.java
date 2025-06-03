package io.snyk.skemium;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.CompareCommand.Result;
import io.snyk.skemium.helpers.Avro;
import io.snyk.skemium.helpers.JSON;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import picocli.CommandLine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompareCommandTest extends WithPostgresContainer {
    Path CURR_DIR;
    Path NEXT_DIR;
    Path OUTPUT_FILE;

    @BeforeEach
    public void createTempFiles() throws IOException {
        CURR_DIR = Files.createTempDirectory("skemium-test-curr-");
        NEXT_DIR = Files.createTempDirectory("skemium-test-next-");
        OUTPUT_FILE = Files.createTempFile("skemium-test-compare-result", ".json");
    }

    @AfterEach
    public void deleteTempFiles() throws IOException {
        if (CURR_DIR != null) {
            FileUtils.deleteDirectory(CURR_DIR.toFile());
        }
        if (NEXT_DIR != null) {
            FileUtils.deleteDirectory(NEXT_DIR.toFile());
        }
        if (OUTPUT_FILE != null) {
            Files.deleteIfExists(OUTPUT_FILE);
        }
    }

    @Test
    void refreshCompareResultFileSchema() throws JsonProcessingException, FileNotFoundException {
        Avro.saveAvroSchemaForType(Result.class, Result.AVRO_SCHEMA_FILENAME);
    }

    @Test
    public void shouldReportNoIncompatibilitiesWhenComparingLikeForLike() throws IOException {
        // TODO Map logger to stdout/err, if possible
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(POSTGRES_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--table", "customer,genre,track,playlist,invoice,employee,album,artist",
                CURR_DIR.toAbsolutePath().toString()
        ));
        FileUtils.copyDirectory(CURR_DIR.toFile(), NEXT_DIR.toFile());

        final Result res = Result.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.BACKWARD);

        assertEquals(CompatibilityLevel.BACKWARD, res.compatibilityLevel());
        assertEquals(Map.of(
                "chinook.public.customer", List.of(),
                "chinook.public.genre", List.of(),
                "chinook.public.track", List.of(),
                "chinook.public.invoice", List.of(),
                "chinook.public.employee", List.of(),
                "chinook.public.playlist", List.of(),
                "chinook.public.album", List.of(),
                "chinook.public.artist", List.of()
        ), res.keyIncompatibilities());
        assertEquals(Map.of(
                "chinook.public.customer", List.of(),
                "chinook.public.genre", List.of(),
                "chinook.public.track", List.of(),
                "chinook.public.invoice", List.of(),
                "chinook.public.employee", List.of(),
                "chinook.public.playlist", List.of(),
                "chinook.public.album", List.of(),
                "chinook.public.artist", List.of()
        ), res.valueIncompatibilities());
        assertEquals(Map.of(
                "chinook.public.customer", List.of(),
                "chinook.public.genre", List.of(),
                "chinook.public.track", List.of(),
                "chinook.public.invoice", List.of(),
                "chinook.public.employee", List.of(),
                "chinook.public.playlist", List.of(),
                "chinook.public.album", List.of(),
                "chinook.public.artist", List.of()
        ), res.envelopeIncompatibilities());
        assertEquals(Set.of(), res.removedTables());
        assertEquals(Set.of(), res.addedTables());
    }

    @Test
    public void shouldReportRemovedAndAddedTables() throws IOException {
        // TODO Map logger to stdout/err, if possible
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // CURR to have: customer, genre, track, playlist
        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(POSTGRES_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--table", "customer,genre,track,playlist",
                CURR_DIR.toAbsolutePath().toString()
        ));
        // NEXT to have: genre, track, invoice, employee
        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(POSTGRES_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--table", "genre,track,invoice,employee",
                NEXT_DIR.toAbsolutePath().toString()
        ));

        final Result res = Result.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.BACKWARD);

        assertEquals(CompatibilityLevel.BACKWARD, res.compatibilityLevel());
        assertEquals(Map.of(
                "chinook.public.genre", List.of(),
                "chinook.public.track", List.of()
        ), res.keyIncompatibilities());
        assertEquals(Map.of(
                "chinook.public.genre", List.of(),
                "chinook.public.track", List.of()
        ), res.valueIncompatibilities());
        assertEquals(Map.of(
                "chinook.public.genre", List.of(),
                "chinook.public.track", List.of()
        ), res.envelopeIncompatibilities());
        assertEquals(Set.of(
                "chinook.public.customer",
                "chinook.public.playlist"
        ), res.removedTables());
        assertEquals(Set.of(
                "chinook.public.invoice",
                "chinook.public.employee"
        ), res.addedTables());
    }

    @Test
    public void shouldReportIncompatibleSchemaChange_MakingColumnNotNull() throws IOException, SQLException {
        // TODO Map logger to stdout/err, if possible
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // First, generate the schema for the `artist` table only
        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(POSTGRES_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--table", "artist",
                CURR_DIR.toAbsolutePath().toString()
        ));

        // Alter schema of `artist` table: make column `name` not null:
        // this is a NON BACKWARE COMPATIBLE change
        try (final Connection connection = getConnection()) {
            connection.prepareStatement("ALTER TABLE chinook.public.artist ALTER COLUMN name SET NOT NULL").execute();
        }

        // Then, generate the new schema for the `artist` table only
        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(POSTGRES_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--table", "artist",
                NEXT_DIR.toAbsolutePath().toString()
        ));

        // Change is not BACKWARD compatible
        Result res = Result.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.BACKWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(0, res.keyIncompatibilities().get("chinook.public.artist").size());
        assertEquals(2, res.valueIncompatibilitiesTotal());
        assertEquals(2, res.valueIncompatibilities().get("chinook.public.artist").size());
        assertEquals(3, res.envelopeIncompatibilitiesTotal());
        assertEquals(3, res.envelopeIncompatibilities().get("chinook.public.artist").size());
        assertEquals(Set.of(), res.removedTables());
        assertEquals(Set.of(), res.addedTables());

        // Change would be BACKWARD compatible, if it was in reverse (from NEXT to CURR)
        res = Result.build(NEXT_DIR, CURR_DIR, CompatibilityLevel.BACKWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(0, res.valueIncompatibilitiesTotal());
        assertEquals(0, res.envelopeIncompatibilitiesTotal());
        assertEquals(Set.of(), res.removedTables());
        assertEquals(Set.of(), res.addedTables());

        // Change is FORWARD compatible
        res = Result.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.FORWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(0, res.valueIncompatibilitiesTotal());
        assertEquals(0, res.envelopeIncompatibilitiesTotal());
        assertEquals(Set.of(), res.removedTables());
        assertEquals(Set.of(), res.addedTables());
    }

    @Test
    public void shouldReportIncompatibleSchemaChange_AddMandatoryColumnWithoutDefaultValue() throws IOException, SQLException {
        // TODO Map logger to stdout/err, if possible
        final CommandLine cmdLine = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // First, generate the schema for the `artist` table only
        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(POSTGRES_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--table", "artist",
                CURR_DIR.toAbsolutePath().toString()
        ));

        // Alter schema of `artist` table: add mandatory column `genre`, without adding a default value:
        // this is a NON BACKWARD COMPATIBLE change
        try (final Connection connection = getConnection()) {
            connection.prepareStatement("""
                    ALTER TABLE chinook.public.artist ADD COLUMN genre VARCHAR(50);
                    UPDATE chinook.public.artist SET genre = 'Neomelodic' WHERE genre IS NULL;
                    ALTER TABLE chinook.public.artist ALTER COLUMN genre SET NOT NULL;
                    """).execute();
        }

        // Then, generate the new schema for the `artist` table only
        assertEquals(0, cmdLine.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(POSTGRES_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--table", "artist",
                NEXT_DIR.toAbsolutePath().toString()
        ));

        // Change is not BACKWARD compatible
        Result res = Result.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.BACKWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(0, res.keyIncompatibilities().get("chinook.public.artist").size());
        assertEquals(2, res.valueIncompatibilitiesTotal());
        assertEquals(2, res.valueIncompatibilities().get("chinook.public.artist").size());
        assertEquals(3, res.envelopeIncompatibilitiesTotal());
        assertEquals(3, res.envelopeIncompatibilities().get("chinook.public.artist").size());
        assertEquals(Set.of(), res.removedTables());
        assertEquals(Set.of(), res.addedTables());

        // Change would be BACKWARD compatible, if it was in reverse (from NEXT to CURR)
        res = Result.build(NEXT_DIR, CURR_DIR, CompatibilityLevel.BACKWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(0, res.valueIncompatibilitiesTotal());
        assertEquals(0, res.envelopeIncompatibilitiesTotal());
        assertEquals(Set.of(), res.removedTables());
        assertEquals(Set.of(), res.addedTables());

        // Change is FORWARD compatible
        res = Result.build(CURR_DIR, NEXT_DIR, CompatibilityLevel.FORWARD);
        assertEquals(0, res.keyIncompatibilitiesTotal());
        assertEquals(0, res.valueIncompatibilitiesTotal());
        assertEquals(0, res.envelopeIncompatibilitiesTotal());
        assertEquals(Set.of(), res.removedTables());
        assertEquals(Set.of(), res.addedTables());
    }

    @Test
    public void shouldReportIncompatibleSchemaChange_SaveToOutputFile() throws IOException, SQLException {
        // TODO Map logger to stdout/err, if possible
        final CommandLine generateCLI = new CommandLine(new GenerateCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // First, generate the schema for the `employee` table only
        assertEquals(0, generateCLI.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(POSTGRES_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--table", "employee",
                CURR_DIR.toAbsolutePath().toString()
        ));

        // Alter schema of `employee` table: make column `title` not null:
        // this is a NON BACKWARE COMPATIBLE change
        try (final Connection connection = getConnection()) {
            connection.prepareStatement("ALTER TABLE chinook.public.employee ALTER COLUMN title SET NOT NULL").execute();
        }

        // Then, generate the new schema for the `employee` table only
        assertEquals(0, generateCLI.execute(
                "--hostname", POSTGRES_CONTAINER.getHost(),
                "--port", POSTGRES_CONTAINER.getMappedPort(POSTGRES_DEFAULT_PORT).toString(),
                "--database", DB_NAME,
                "--username", DB_USER,
                "--password", DB_PASS,
                "--table", "employee",
                NEXT_DIR.toAbsolutePath().toString()
        ));

        // TODO Map logger to stdout/err, if possible
        final CommandLine compareCLI = new CommandLine(new CompareCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));

        // Change is not BACKWARD compatible, so the command is expected to fail (exit == 1).
        // Output saved to file.
        assertEquals(1, compareCLI.execute(
                "--compatibility", CompatibilityLevel.BACKWARD.toString(),
                "--output", OUTPUT_FILE.toAbsolutePath().toString(),
                CURR_DIR.toAbsolutePath().toString(),
                NEXT_DIR.toAbsolutePath().toString()
        ));

        final Result resFromOutputFile = JSON.from(OUTPUT_FILE.toFile(), Result.class);
        assertEquals(0, resFromOutputFile.keyIncompatibilitiesTotal());
        assertEquals(0, resFromOutputFile.keyIncompatibilities().get("chinook.public.employee").size());
        assertEquals(2, resFromOutputFile.valueIncompatibilitiesTotal());
        assertEquals(2, resFromOutputFile.valueIncompatibilities().get("chinook.public.employee").size());
        assertEquals(3, resFromOutputFile.envelopeIncompatibilitiesTotal());
        assertEquals(3, resFromOutputFile.envelopeIncompatibilities().get("chinook.public.employee").size());
        assertEquals(Set.of(), resFromOutputFile.removedTables());
        assertEquals(Set.of(), resFromOutputFile.addedTables());
    }
}
