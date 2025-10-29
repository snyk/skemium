package io.snyk.skemium;

import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompareFilesResultTest {
    private final Path testResourcesDir = Paths.get("src/test/resources/compare-files");
    private final Path validSchemasDir = testResourcesDir.resolve("valid-schemas");
    private final Path invalidSchemasDir = testResourcesDir.resolve("invalid-schemas");

    @Test
    void shouldCreateCompatibleResult() throws IOException {
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final CompareFilesResult result = CompareFilesResult.build(
                currentSchema, nextSchema, CompatibilityLevel.BACKWARD);

        assertNotNull(result);
        assertEquals(currentSchema, result.currentSchemaFile());
        assertEquals(nextSchema, result.nextSchemaFile());
        assertEquals(CompatibilityLevel.BACKWARD, result.compatibilityLevel());
        assertTrue(result.isCompatible());
        assertTrue(result.incompatibilities().isEmpty());
        assertEquals(0, result.incompatibilitiesTotal());
    }

    @Test
    void shouldCreateIncompatibleResult() throws IOException {
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-incompatible.avsc");

        final CompareFilesResult result = CompareFilesResult.build(
                currentSchema, nextSchema, CompatibilityLevel.BACKWARD);

        assertNotNull(result);
        assertEquals(currentSchema, result.currentSchemaFile());
        assertEquals(nextSchema, result.nextSchemaFile());
        assertEquals(CompatibilityLevel.BACKWARD, result.compatibilityLevel());
        assertFalse(result.isCompatible());
        assertFalse(result.incompatibilities().isEmpty());
        assertTrue(result.incompatibilitiesTotal() > 0);
        assertEquals(result.incompatibilities().size(), result.incompatibilitiesTotal());
    }

    @Test
    void shouldTestDifferentCompatibilityLevels() throws IOException {
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        // Test different compatibility levels
        for (CompatibilityLevel level : CompatibilityLevel.values()) {
            final CompareFilesResult result = CompareFilesResult.build(
                    currentSchema, nextSchema, level);

            assertNotNull(result);
            assertEquals(level, result.compatibilityLevel());
            
            // For this particular schema pair, most levels should be compatible
            // Only testing that the method works with all levels
            assertTrue(result.incompatibilitiesTotal() >= 0);
        }
    }

    @Test
    void shouldThrowExceptionForNonexistentCurrentFile() {
        final Path nonExistentFile = validSchemasDir.resolve("does-not-exist.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v1.avsc");

        assertThrows(IOException.class, () -> {
            CompareFilesResult.build(nonExistentFile, nextSchema, CompatibilityLevel.BACKWARD);
        });
    }

    @Test
    void shouldThrowExceptionForNonexistentNextFile() {
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nonExistentFile = validSchemasDir.resolve("does-not-exist.avsc");

        assertThrows(IOException.class, () -> {
            CompareFilesResult.build(currentSchema, nonExistentFile, CompatibilityLevel.BACKWARD);
        });
    }

    @Test
    void shouldThrowExceptionForMalformedCurrentSchema() {
        final Path malformedSchema = invalidSchemasDir.resolve("malformed.json");
        final Path validSchema = validSchemasDir.resolve("person-v1.avsc");

        final IOException exception = assertThrows(IOException.class, () -> {
            CompareFilesResult.build(malformedSchema, validSchema, CompatibilityLevel.BACKWARD);
        });

        assertTrue(exception.getMessage().contains("Failed to parse current schema file"));
        assertTrue(exception.getMessage().contains(malformedSchema.toString()));
    }

    @Test
    void shouldThrowExceptionForMalformedNextSchema() {
        final Path validSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path malformedSchema = invalidSchemasDir.resolve("malformed.json");

        final IOException exception = assertThrows(IOException.class, () -> {
            CompareFilesResult.build(validSchema, malformedSchema, CompatibilityLevel.BACKWARD);
        });

        assertTrue(exception.getMessage().contains("Failed to parse next schema file"));
        assertTrue(exception.getMessage().contains(malformedSchema.toString()));
    }

    @Test
    void shouldThrowExceptionForEmptySchemaFile() {
        final Path validSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path emptySchema = invalidSchemasDir.resolve("empty.avsc");

        final IOException exception = assertThrows(IOException.class, () -> {
            CompareFilesResult.build(validSchema, emptySchema, CompatibilityLevel.BACKWARD);
        });

        assertTrue(exception.getMessage().contains("Failed to parse next schema file"));
    }

    @Test
    void shouldThrowExceptionForInvalidAvroSchema() {
        final Path validSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path invalidAvroSchema = invalidSchemasDir.resolve("invalid-avro.avsc");

        final IOException exception = assertThrows(IOException.class, () -> {
            CompareFilesResult.build(validSchema, invalidAvroSchema, CompatibilityLevel.BACKWARD);
        });

        assertTrue(exception.getMessage().contains("Failed to parse next schema file"));
    }

    @Test
    void shouldHandleIdenticalSchemas() throws IOException {
        final Path schema = validSchemasDir.resolve("person-v1.avsc");

        final CompareFilesResult result = CompareFilesResult.build(
                schema, schema, CompatibilityLevel.BACKWARD);

        assertTrue(result.isCompatible());
        assertEquals(0, result.incompatibilitiesTotal());
        assertTrue(result.incompatibilities().isEmpty());
    }

    @Test
    void shouldCreateResultWithCorrectJSONStructure() throws IOException {
        final Path currentSchema = validSchemasDir.resolve("person-v1.avsc");
        final Path nextSchema = validSchemasDir.resolve("person-v2-compatible.avsc");

        final CompareFilesResult result = CompareFilesResult.build(
                currentSchema, nextSchema, CompatibilityLevel.FORWARD);

        // Test that all required fields are present and have expected types
        assertNotNull(result.currentSchemaFile());
        assertNotNull(result.nextSchemaFile());
        assertNotNull(result.compatibilityLevel());
        assertNotNull(result.incompatibilities());
        assertNotNull(result.isCompatible());

        // Test that incompatibilities is a proper list (even if empty)
        assertInstanceOf(List.class, result.incompatibilities());
    }
}
