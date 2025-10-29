package io.snyk.skemium;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.snyk.skemium.helpers.SchemaRegistry;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/// Describes the result of comparing two Avro schema files.
///
/// @param currentSchemaFile  [Path] to the current/baseline schema file
/// @param nextSchemaFile     [Path] to the next/target schema file
/// @param compatibilityLevel [CompatibilityLevel] used during the comparison
/// @param incompatibilities  [List] of incompatibility error messages, empty if
///                           compatible
/// @param isCompatible       true if schemas are compatible according to the
///                           specified level
/// @param hasSchemaChanges   true if schemas are different (regardless of
///                           compatibility)
public record CompareFilesResult(
        @JsonProperty(required = true, index = 0) @Nonnull Path currentSchemaFile,
        @JsonProperty(required = true, index = 1) @Nonnull Path nextSchemaFile,
        @JsonProperty(required = true, index = 2) @Nonnull CompatibilityLevel compatibilityLevel,
        @JsonProperty(required = true, index = 3) @Nonnull List<String> incompatibilities,
        @JsonProperty(required = true, index = 4) boolean isCompatible,
        @JsonProperty(required = true, index = 5) boolean hasSchemaChanges) {
    private static final Logger LOG = LoggerFactory.getLogger(CompareFilesResult.class);

    /// The filename for the Avro schema of this result record.
    public static final Path AVRO_SCHEMA_FILENAME = Path.of("skemium.compare-files.result.avsc");

    /// Returns the total number of incompatibilities found.
    ///
    /// @return number of incompatibility issues
    public int incompatibilitiesTotal() {
        return incompatibilities.size();
    }

    /// Creates a SchemaComparisonResult by comparing two Avro schema files.
    ///
    /// @param currentSchemaFile  path to the current/baseline schema file
    /// @param nextSchemaFile     path to the next/target schema file
    /// @param compatibilityLevel compatibility level to apply during comparison
    /// @return SchemaComparisonResult with comparison results
    /// @throws IOException if files cannot be read or parsed
    public static CompareFilesResult build(
            @Nonnull Path currentSchemaFile,
            @Nonnull Path nextSchemaFile,
            @Nonnull CompatibilityLevel compatibilityLevel) throws IOException {
        LOG.debug("Comparing schemas: {} -> {}", currentSchemaFile, nextSchemaFile);

        // Parse the current schema
        final Schema currentSchema;
        try {
            LOG.trace("Loading current schema from: {}", currentSchemaFile);
            currentSchema = new Schema.Parser().parse(currentSchemaFile.toFile());
            LOG.debug("Successfully parsed current schema: {}", currentSchema.getName());
        } catch (Exception e) {
            throw new IOException("Failed to parse current schema file: " + currentSchemaFile, e);
        }

        // Parse the next schema
        final Schema nextSchema;
        try {
            LOG.trace("Loading next schema from: {}", nextSchemaFile);
            nextSchema = new Schema.Parser().parse(nextSchemaFile.toFile());
            LOG.debug("Successfully parsed next schema: {}", nextSchema.getName());
        } catch (Exception e) {
            throw new IOException("Failed to parse next schema file: " + nextSchemaFile, e);
        }

        // Convert to AvroSchema objects for compatibility checking
        final AvroSchema currentAvroSchema = new AvroSchema(currentSchema);
        final AvroSchema nextAvroSchema = new AvroSchema(nextSchema);

        // Perform compatibility check
        LOG.debug("Checking compatibility with level: {}", compatibilityLevel);
        final List<String> incompatibilities = SchemaRegistry.checkSchemaCompatibility(
                currentAvroSchema,
                nextAvroSchema,
                compatibilityLevel);

        final boolean isCompatible = incompatibilities.isEmpty();

        // Check for schema changes (regardless of compatibility)
        LOG.debug("Checking for schema changes");
        final boolean hasSchemaChanges = !SchemaRegistry.checkSchemaEquality(currentAvroSchema, nextAvroSchema);

        if (isCompatible) {
            LOG.info("Schemas are compatible ({})", compatibilityLevel);
        } else {
            LOG.warn("Found {} incompatibilities with {} compatibility",
                    incompatibilities.size(), compatibilityLevel);
            for (String incompatibility : incompatibilities) {
                LOG.error("Incompatibility: {}", incompatibility);
            }
        }

        if (hasSchemaChanges) {
            LOG.debug("Schema changes detected between files");
        } else {
            LOG.debug("No schema changes detected");
        }

        return new CompareFilesResult(
                currentSchemaFile,
                nextSchemaFile,
                compatibilityLevel,
                incompatibilities,
                isCompatible,
                hasSchemaChanges);
    }
}
