package io.snyk.skemium;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.avro.TableAvroSchemas;
import io.snyk.skemium.helpers.SchemaRegistry;
import io.snyk.skemium.meta.MetadataFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/// Describes the result of running the `compare` command.
/// It's left to the calling logic to decide when to fail/succeed the actual CLI command.
///
/// @param currentSchemasDir          [Path] to the directory containing the CURRENT Table Schemas compared.
/// @param nextSchemasDir             [Path] to the directory containing the NEXT Table Schemas compared.
/// @param compatibilityLevel         [CompatibilityLevel] used during the comparison.
/// @param keyIncompatibilities:      [Map] of Table Avro Schemas identifiers, to [List] of Key Schema incompatibilities.
/// @param valueIncompatibilities:    [Map] of Table Avro Schemas identifiers, to [List] of Value Schema incompatibilities.
/// @param envelopeIncompatibilities: [Map] of Table Avro Schemas identifiers, to [List] of Envelope Schema incompatibilities.
/// @param removedTables:             [Set] of Table Avro Schemas that were removed: present in CURRENT but absent from NEXT.
/// @param addedTables                [Set] of Table Avro Schemas that were added: absent from CURRENT but present in NEXT.
/// @param keySchemaChanged           [Map] of Table Avro Schemas identifiers, to [Boolean] indicating if there was a Key Schema change.
/// @param valueSchemaChanged         [Map] of Table Avro Schemas identifiers, to [Boolean] indicating if there was a Value Schema change.
/// @param envelopeSchemaChanged      [Map] of Table Avro Schemas identifiers, to [Boolean] indicating if there was an Envelope Schema change.
///
public record CompareResult(
        @JsonProperty(required = true, index = 0)
        @Nonnull Path currentSchemasDir,
        @JsonProperty(required = true, index = 1)
        @Nonnull Path nextSchemasDir,
        @JsonProperty(required = true, index = 2)
        @Nonnull CompatibilityLevel compatibilityLevel,
        @JsonProperty(required = true, index = 3)
        @Nonnull Map<String, List<String>> keyIncompatibilities,
        @JsonProperty(required = true, index = 5)
        @Nonnull Map<String, List<String>> valueIncompatibilities,
        @JsonProperty(required = true, index = 7)
        @Nonnull Map<String, List<String>> envelopeIncompatibilities,
        @JsonProperty(required = true, index = 10)
        @Nonnull Set<String> removedTables,
        @JsonProperty(required = true, index = 11)
        @Nonnull Set<String> addedTables,
        @JsonProperty(required = true, index = 12)
        @Nonnull Map<String, Boolean> keySchemaChanged,
        @JsonProperty(required = true, index = 13)
        @Nonnull Map<String, Boolean> valueSchemaChanged,
        @JsonProperty(required = true, index = 14)
        @Nonnull Map<String, Boolean> envelopeSchemaChanged) {
    private static final Logger LOG = LoggerFactory.getLogger(CompareResult.class);
    public static final Path AVRO_SCHEMA_FILENAME = Path.of("skemium.compare.result.avsc");


    /// Sum of all Key Schema incompatibilities identified, across all Tables.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, index = 4)
    public int keyIncompatibilitiesTotal() {
        return keyIncompatibilities.values().stream().mapToInt(List::size).sum();
    }

    /// Sum of all Value Schema incompatibilities identified, across all Tables.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, index = 6)
    public int valueIncompatibilitiesTotal() {
        return valueIncompatibilities.values().stream().mapToInt(List::size).sum();
    }

    /// Sum of all Envelope Schema incompatibilities identified, across all Tables.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, index = 8)
    public int envelopeIncompatibilitiesTotal() {
        return envelopeIncompatibilities.values().stream().mapToInt(List::size).sum();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY, index = 9)
    public int incompatibilitiesTotal() {
        return keyIncompatibilitiesTotal() + valueIncompatibilitiesTotal() + envelopeIncompatibilitiesTotal();
    }

    /// Returns a set of table identifiers that have any schema changes (key, value,
    /// or envelope).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, index = 15)
    public Set<String> tablesWithChanges() {
        final Set<String> tablesWithChanges = new HashSet<>();

        keySchemaChanged
                .entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .forEach(entry -> tablesWithChanges.add(entry.getKey()));

        valueSchemaChanged
                .entrySet()
                .stream()
                .filter(Map.Entry::getValue).forEach(entry -> tablesWithChanges.add(entry.getKey()));

        envelopeSchemaChanged.
                entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .forEach(entry -> tablesWithChanges.add(entry.getKey()));

        return tablesWithChanges;
    }

    /// Returns true if any schema changes were detected (excluding table
    /// additions/removals).
    public boolean hasAnySchemaChanges() {
        return keySchemaChanged.values().stream().anyMatch(Boolean::booleanValue) ||
                valueSchemaChanged.values().stream().anyMatch(Boolean::booleanValue) ||
                envelopeSchemaChanged.values().stream().anyMatch(Boolean::booleanValue);
    }

    /// Returns true if schema changes were detected but no tables were added or
    /// removed.
    /// This is the key method for the enhanced CI mode functionality.
    public boolean hasSchemaChangesWithoutTableChanges() {
        return hasAnySchemaChanges() &&
                removedTables.isEmpty() &&
                addedTables.isEmpty();
    }

    /// Returns the total number of tables that have schema changes.
    public int totalTablesWithChanges() {
        return tablesWithChanges().size();
    }

    public static CompareResult build(
            @Nonnull Path currSchemasDir,
            @Nonnull Path nextSchemasDir,
            @Nonnull CompatibilityLevel compatibilityLevel) throws IOException {
        final MetadataFile currMeta = MetadataFile.loadFrom(currSchemasDir);
        final Set<String> currTableIds = currMeta.getTableSchemasIdentifiers();

        final MetadataFile nextMeta = MetadataFile.loadFrom(nextSchemasDir);
        final Set<String> nextTableIds = nextMeta.getTableSchemasIdentifiers();

        final Sets.SetView<String> removedTables = Sets.difference(currTableIds, nextTableIds);
        final Sets.SetView<String> addedTables = Sets.difference(nextTableIds, currTableIds);

        final Map<String, List<String>> keyIncompatibilities = new HashMap<>(currTableIds.size());
        final Map<String, List<String>> valueIncompatibilities = new HashMap<>(currTableIds.size());
        final Map<String, List<String>> envelopeIncompatibilities = new HashMap<>(currTableIds.size());

        // Initialize change tracking maps
        final Map<String, Boolean> keySchemaChanged = new HashMap<>(currTableIds.size());
        final Map<String, Boolean> valueSchemaChanged = new HashMap<>(currTableIds.size());
        final Map<String, Boolean> envelopeSchemaChanged = new HashMap<>(currTableIds.size());

        for (final String tableId : currTableIds) {
            if (!nextTableIds.contains(tableId)) {
                LOG.warn("Table '{}' not found in NEXT Database Schema: skipping compatibility check (table dropped?)", tableId);
                continue;
            }

            LOG.debug("Checking compatibility '{}' for Table '{}'", compatibilityLevel, tableId);
            final TableAvroSchemas currTableSchemas = TableAvroSchemas.loadFrom(currSchemasDir, tableId);
            final TableAvroSchemas nextTableSchemas = TableAvroSchemas.loadFrom(nextSchemasDir, tableId);

            // Check compatibility (existing logic)
            final SchemaRegistry.CompatibilityResult compatResult = SchemaRegistry.checkCompatibility(currTableSchemas, nextTableSchemas, compatibilityLevel);

            // Check for schema changes
            final SchemaRegistry.ChangeResult changeResult = SchemaRegistry.detectSchemaChanges(currTableSchemas, nextTableSchemas);

            // Track compatibility results (existing logic)
            if (compatResult.isCompatible()) {
                LOG.info("Compatibility for Table '{}' preserved", tableId);
                keyIncompatibilities.put(tableId, List.of());
                valueIncompatibilities.put(tableId, List.of());
                envelopeIncompatibilities.put(tableId, List.of());
            } else {
                LOG.trace("Checking Table '{}' Key Incompatibilities", tableId);
                keyIncompatibilities.put(tableId, compatResult.keyResults());
                for (final String err : compatResult.keyResults()) {
                    LOG.error("Table '{}' Key Incompatibility: {}", tableId, err);
                }

                LOG.trace("Checking Table '{}' Value Incompatibilities", tableId);
                valueIncompatibilities.put(tableId, compatResult.valueResults());
                for (final String err : compatResult.valueResults()) {
                    LOG.error("Table '{}' Value Incompatibility: {}", tableId, err);
                }

                LOG.trace("Checking Table '{}' Envelope Incompatibilities", tableId);
                envelopeIncompatibilities.put(tableId, compatResult.envelopeResults());
                for (final String err : compatResult.envelopeResults()) {
                    LOG.error("Table '{}' Envelope Incompatibility: {}", tableId, err);
                }
            }

            // Track schema changes
            keySchemaChanged.put(tableId, changeResult.keyChanged());
            valueSchemaChanged.put(tableId, changeResult.valueChanged());
            envelopeSchemaChanged.put(tableId, changeResult.envelopeChanged());

            // Log schema changes for debugging
            if (changeResult.hasAnyChanges()) {
                LOG.debug("Schema changes detected for Table '{}': key={}, value={}, envelope={}", tableId, changeResult.keyChanged(), changeResult.valueChanged(), changeResult.envelopeChanged());
            }
        }

        return new CompareResult(currSchemasDir, nextSchemasDir, compatibilityLevel, keyIncompatibilities, valueIncompatibilities, envelopeIncompatibilities, removedTables, addedTables, keySchemaChanged, valueSchemaChanged, envelopeSchemaChanged);
    }
}
