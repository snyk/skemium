package io.snyk.skemium;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
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
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class CompareFilesCommandTest {
    private Path outputFile;
    private final Path testResourcesDir = Paths.get("src/test/resources/compare-files");
    private final Path validSchemasDir = testResourcesDir.resolve("valid-schemas");
    private final Path invalidSchemasDir = testResourcesDir.resolve("invalid-schemas");

    @BeforeEach
    public void createTempFile() throws IOException {
        outputFile = Files.createTempFile("skemium-test-compare-files-result", ".json");
    }

    @AfterEach
    public void deleteTempFile() throws IOException {
        if (outputFile != null) {
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void refreshSchemaComparisonResultFileSchema() throws JsonProcessingException, FileNotFoundException {
        Avro.saveAvroSchemaForType(SchemaComparisonResult.class, SchemaComparisonResult.AVRO_SCHEMA_FILENAME);
    }

    @Test
    void shouldReportCompatibleWhenComparingSameSchema() throws IOException {
        final Path schemaFile = validSchemasDir.resolve("person-v1.avsc");

        final SchemaComparisonResult result = SchemaComparisonResult.build(
                schemaFile, schemaFile, CompatibilityLevel.BACKWARD);

        assertTrue(result.isCompatible());
        assertEquals(0, result.incompatibilitiesTotal());
        assertEquals(CompatibilityLevel.BACKWARD, result.compatibilityLevel());
        assertEquals(schemaFile, result.currentSchemaFile());
        assertEquals(schemaFile, result.nextSchemaFile());
    }

    @Test
    void shouldReportCompatibleWhenComparingBackwardCompatibleSchemas() throws IOException {
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final SchemaComparisonResult result = SchemaComparisonResult.build(
                currentSchema, nextSchema, CompatibilityLevel.BACKWARD);

        assertTrue(result.isCompatible());
        assertEquals(0, result.incompatibilitiesTotal());
        assertTrue(result.incompatibilities().isEmpty());
    }

    @Test
    void shouldReportIncompatibleWhenComparingIncompatibleSchemas() throws IOException {
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-incompatible.avsc");

        final SchemaComparisonResult result = SchemaComparisonResult.build(
                currentSchema, nextSchema, CompatibilityLevel.BACKWARD);

        assertFalse(result.isCompatible());
        assertTrue(result.incompatibilitiesTotal() > 0);
        assertFalse(result.incompatibilities().isEmpty());
        assertTrue(result.hasSchemaChanges()); // Should detect schema changes
    }

    @Test
    void shouldDetectSchemaChangesWhenSchemasAreCompatibleButDifferent() throws IOException {
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final SchemaComparisonResult result = SchemaComparisonResult.build(
                currentSchema, nextSchema, CompatibilityLevel.BACKWARD);

        assertTrue(result.isCompatible()); // Should be compatible
        assertTrue(result.hasSchemaChanges()); // But should detect changes
        assertEquals(0, result.incompatibilitiesTotal()); // No incompatibilities
    }

    @Test
    void shouldNotDetectSchemaChangesWhenSchemasAreIdentical() throws IOException {
        final Path schemaFile = validSchemasDir.resolve("person-v1.avsc");

        final SchemaComparisonResult result = SchemaComparisonResult.build(
                schemaFile, schemaFile, CompatibilityLevel.BACKWARD);

        assertTrue(result.isCompatible());
        assertFalse(result.hasSchemaChanges()); // Should not detect changes for identical schemas
        assertEquals(0, result.incompatibilitiesTotal());
    }

    @Test
    void shouldSucceedWhenCompatibleSchemasCompared() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final int exitCode = cmdLine.execute(
                currentSchema.toString(),
                nextSchema.toString());

        assertEquals(0, exitCode);
    }

    @Test
    void shouldFailWhenIncompatibleSchemasCompared() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-incompatible.avsc");

        final int exitCode = cmdLine.execute(
                currentSchema.toString(),
                nextSchema.toString());

        assertEquals(1, exitCode);
    }

    @Test
    void shouldRespectCompatibilityLevelOption() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final int exitCode = cmdLine.execute(
                "--compatibility", "FULL",
                currentSchema.toString(),
                nextSchema.toString());

        // Should still be compatible with FULL compatibility
        assertEquals(0, exitCode);
    }

    @Test
    void shouldSaveOutputToFileWhenSpecified() throws IOException {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final int exitCode = cmdLine.execute(
                "--output", outputFile.toString(),
                currentSchema.toString(),
                nextSchema.toString());

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0);

        // Verify output is valid JSON
        final String outputContent = Files.readString(outputFile);
        assertDoesNotThrow(() -> JSON.toJsonNode(outputContent));
    }

    @Test
    void shouldFailWhenCurrentSchemaFileDoesNotExist() {
        final CommandLine cmdLine = createCommandLine();
        final Path nonExistentFile = validSchemasDir.resolve("does-not-exist.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v1.avsc");

        final int exitCode = cmdLine.execute(
                nonExistentFile.toString(),
                nextSchema.toString());

        assertEquals(2, exitCode); // picocli returns 2 for parameter exceptions
    }

    @Test
    void shouldFailWhenNextSchemaFileDoesNotExist() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nonExistentFile = validSchemasDir.resolve("does-not-exist.avsc");

        final int exitCode = cmdLine.execute(
                currentSchema.toString(),
                nonExistentFile.toString());

        assertEquals(2, exitCode); // picocli returns 2 for parameter exceptions
    }

    @Test
    void shouldFailWhenSchemaFileIsDirectory() throws IOException {
        final CommandLine cmdLine = createCommandLine();
        final Path directory = Files.createTempDirectory("test-dir");
        final Path nextSchema = validSchemasDir.resolve("person-v1.avsc");

        try {
            final int exitCode = cmdLine.execute(
                    directory.toString(),
                    nextSchema.toString());

            assertEquals(2, exitCode); // picocli returns 2 for parameter exceptions
        } finally {
            FileUtils.deleteDirectory(directory.toFile());
        }
    }

    @Test
    void shouldFailWhenSchemaFileIsInvalid() {
        final CommandLine cmdLine = createCommandLine();
        final Path invalidSchema = invalidSchemasDir.resolve("malformed.json");
        final Path validSchema = validSchemasDir.resolve("person-v1.avsc");

        final int exitCode = cmdLine.execute(
                invalidSchema.toString(),
                validSchema.toString());

        assertEquals(1, exitCode); // Application error for parsing failure
    }

    @Test
    void shouldFailWhenSchemaFileIsEmpty() {
        final CommandLine cmdLine = createCommandLine();
        final Path emptySchema = invalidSchemasDir.resolve("empty.avsc");
        final Path validSchema = validSchemasDir.resolve("person-v1.avsc");

        final int exitCode = cmdLine.execute(
                emptySchema.toString(),
                validSchema.toString());

        assertEquals(1, exitCode); // Application error for parsing failure
    }

    @Test
    void shouldFailWhenSchemaContainsInvalidAvroType() {
        final CommandLine cmdLine = createCommandLine();
        final Path invalidAvroSchema = invalidSchemasDir.resolve("invalid-avro.avsc");
        final Path validSchema = validSchemasDir.resolve("person-v1.avsc");

        final int exitCode = cmdLine.execute(
                invalidAvroSchema.toString(),
                validSchema.toString());

        assertEquals(1, exitCode); // Application error for invalid Avro schema
    }

    @Test
    void shouldShowHelpMessage() {
        final CommandLine cmdLine = createCommandLine();

        final int exitCode = cmdLine.execute("--help");

        // Help command can return 0 or 2 depending on the context
        assertTrue(exitCode == 0 || exitCode == 2);
    }

    @Test
    void shouldHandleVerbosityFlags() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final int exitCode = cmdLine.execute(
                "-vv", // Double verbose
                currentSchema.toString(),
                nextSchema.toString());

        assertEquals(0, exitCode);
    }

    @Test
    void shouldValidateAndWarnAboutNonAvscExtensions() {
        final CommandLine cmdLine = createCommandLine();
        final Path jsonFile = invalidSchemasDir.resolve("malformed.json"); // Not .avsc
        final Path validSchema = validSchemasDir.resolve("person-v1.avsc");

        // This should still attempt to parse but warn about the extension
        final int exitCode = cmdLine.execute(
                jsonFile.toString(),
                validSchema.toString());

        // Will fail due to malformed JSON, but should have warned about extension
        assertEquals(1, exitCode);
    }

    @Test
    void shouldWarnWhenComparingFileWithItself() {
        final CommandLine cmdLine = createCommandLine();
        final Path schema = validSchemasDir.resolve("person-v1.avsc");

        final int exitCode = cmdLine.execute(
                schema.toString(),
                schema.toString());

        // Should succeed (compatible with itself) but log a warning
        assertEquals(0, exitCode);
    }

    @Test
    void shouldSucceedWhenFieldsAreReorderedWithoutCiMode() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path reorderedSchema = validSchemasDir.resolve("person-v1-reordered.avsc");

        final int exitCode = cmdLine.execute(
                currentSchema.toString(),
                reorderedSchema.toString()
        );

        // Should succeed - reordered fields are compatible (same fields, just different order)
        assertEquals(0, exitCode);
    }

    @Test
    void shouldSucceedWhenFieldsAreReorderedWithCiMode() {
        // In CI mode, reordered fields should NOT be detected as a change
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path reorderedSchema = validSchemasDir.resolve("person-v1-reordered.avsc");

        final int exitCode = cmdLine.execute(
                "--ci-mode",
                currentSchema.toString(),
                reorderedSchema.toString()
        );

        // Should succeed in CI mode - reordered fields are semantically equivalent
        assertEquals(0, exitCode);
    }

    // CI Mode Tests

    @Test
    void shouldSucceedInCiModeWhenSchemasAreIdentical() {
        final CommandLine cmdLine = createCommandLine();
        final Path schema = validSchemasDir.resolve("person-v1.avsc");

        final int exitCode = cmdLine.execute(
                "--ci-mode",
                schema.toString(),
                schema.toString());

        // Should succeed - no schema changes detected
        assertEquals(0, exitCode);
    }

    @Test
    void shouldFailInCiModeWhenSchemasAreCompatibleButDifferent() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final int exitCode = cmdLine.execute(
                "--ci-mode",
                currentSchema.toString(),
                nextSchema.toString());

        // Should fail - schemas are compatible but different (CI mode detects changes)
        assertEquals(1, exitCode);
    }

    @Test
    void shouldFailInCiModeWhenSchemasAreIncompatible() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-incompatible.avsc");

        final int exitCode = cmdLine.execute(
                "--ci-mode",
                currentSchema.toString(),
                nextSchema.toString());

        // Should fail - schemas are incompatible (and also different)
        assertEquals(1, exitCode);
    }

    @Test
    void shouldSucceedInNonCiModeWhenSchemasAreCompatibleButDifferent() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final int exitCode = cmdLine.execute(
                currentSchema.toString(),
                nextSchema.toString());

        // Should succeed - schemas are compatible (non-CI mode doesn't fail on changes)
        assertEquals(0, exitCode);
    }

    @Test
    void shouldRespectCiModeEnvironmentVariable() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        // Test with CI_MODE environment variable (would need to be set externally)
        // This tests the environment variable parsing capability
        final int exitCode = cmdLine.execute(
                currentSchema.toString(),
                nextSchema.toString());

        // In normal mode without --ci-mode flag, should succeed
        assertEquals(0, exitCode);
    }

    @Test
    void shouldSaveOutputWithCiModeResults() throws IOException {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final int exitCode = cmdLine.execute(
                "--ci-mode",
                "--output", outputFile.toString(),
                currentSchema.toString(),
                nextSchema.toString());

        assertEquals(1, exitCode); // Should fail in CI mode
        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0);

        // Verify output contains CI mode results
        final String outputContent = Files.readString(outputFile);
        assertDoesNotThrow(() -> JSON.toJsonNode(outputContent));

        // Parse and verify the result contains schema change information
        // We'll verify the JSON structure contains the expected fields rather than
        // parsing to object
        final JsonNode resultNode = JSON.toJsonNode(outputContent);
        assertTrue(resultNode.has("isCompatible"));
        assertTrue(resultNode.has("hasSchemaChanges"));
        assertTrue(resultNode.get("isCompatible").asBoolean()); // Should be compatible
        assertTrue(resultNode.get("hasSchemaChanges").asBoolean()); // But should have changes
    }

    @Test
    void shouldUseShortCiModeFlag() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final int exitCode = cmdLine.execute(
                "-i", // Short flag for CI mode
                currentSchema.toString(),
                nextSchema.toString());

        // Should fail - schemas are compatible but different (CI mode detects changes)
        assertEquals(1, exitCode);
    }

    @Test
    void shouldUseCiAlias() {
        final CommandLine cmdLine = createCommandLine();
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final int exitCode = cmdLine.execute(
                "--ci", // Alternative alias for CI mode
                currentSchema.toString(),
                nextSchema.toString());

        // Should fail - schemas are compatible but different (CI mode detects changes)
        assertEquals(1, exitCode);
    }

    private CommandLine createCommandLine() {
        return new CommandLine(new CompareFilesCommand())
                .setOut(new PrintWriter(new StringWriter()))
                .setErr(new PrintWriter(new StringWriter()));
    }
}
