package io.snyk.skemium.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.snyk.skemium.avro.TableAvroSchemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// Helper to interact with Schema Registry.
public class SchemaRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaRegistry.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /// Check compatibility between a "Curr(ent)" and a "Next" [TableAvroSchemas], applying the given [CompatibilityLevel].
    ///
    /// @param curr               Current schemas, provided as a [TableAvroSchemas]
    /// @param next               Next schemas, provided as a [TableAvroSchemas]
    /// @param compatibilityLevel Compatibility Level to apply
    /// @return [CheckCompatibilityResult]
    public static CompatibilityResult checkCompatibility(final TableAvroSchemas curr,
                                                         final TableAvroSchemas next,
                                                         final CompatibilityLevel compatibilityLevel) {
        final CompatibilityChecker checker = CompatibilityChecker.checker(compatibilityLevel);

        if (!Objects.equals(curr.identifier(), next.identifier())) {
            LOG.warn("Checking compatibility of Table Avro Schemas with mismatching identifiers: '{}' != '{}'", curr.identifier(), next.identifier());
        }

        // Key _might_ be NULL, so we need to account for it
        List<String> keyCompatibilityErrors;
        if (curr.keySchema() != null && next.keySchema() != null) {         // curr.key=NOT NULL, next.key=NOT NULL
            keyCompatibilityErrors = checker.isCompatible(
                    next.keySchemaToSchemaRegistryAvroSchema(),
                    List.of(curr.keySchemaToSchemaRegistryAvroSchema())
            );
        } else if (curr.keySchema() == null && next.keySchema() == null) {  // curr.key=NULL, next.key=NULL
            keyCompatibilityErrors = List.of();
        } else if (curr.keySchema() == null) {                              // curr.key=NULL, next.key=NOT NULL
            keyCompatibilityErrors = List.of(
                    "Key Schema for '%s' changed from NULL to NOT NULL (%s)".formatted(
                            curr.identifier(),
                            next.keySchema().toString())
            );
        } else {                                                            // curr.key=NOT NULL, next.key=NULL
            keyCompatibilityErrors = List.of(
                    "Key Schema for '%s' changed from NOT NULL (%s) to NULL".formatted(
                            curr.identifier(),
                            curr.keySchema().toString())
            );
        }

        return new CompatibilityResult(
                compatibilityLevel,
                keyCompatibilityErrors,
                checker.isCompatible(
                        next.valueSchemaToSchemaRegistryAvroSchema(),
                        List.of(curr.valueSchemaToSchemaRegistryAvroSchema())),
                checker.isCompatible(
                        next.envelopeSchemaToSchemaRegistryAvroSchema(),
                        List.of(curr.envelopeSchemaToSchemaRegistryAvroSchema())));
    }

    /// Check compatibility between two individual AvroSchema objects, applying the
    /// given CompatibilityLevel.
    /// This is a simpler version of checkCompatibility that works with single
    /// schemas rather than table schemas.
    ///
    /// @param currentSchema Current/baseline schema as [AvroSchema]
    /// @param nextSchema Next/target schema as [AvroSchema]
    /// @param compatibilityLevel Compatibility Level to apply
    /// @return List of incompatibility error messages, empty if compatible
    public static List<String> checkSchemaCompatibility(@Nonnull final AvroSchema currentSchema,
            @Nonnull final AvroSchema nextSchema,
            @Nonnull final CompatibilityLevel compatibilityLevel) {
        final CompatibilityChecker checker = CompatibilityChecker.checker(compatibilityLevel);

        LOG.debug("Checking single schema compatibility with level: {}", compatibilityLevel);
        LOG.trace("Current schema: {}", currentSchema.rawSchema());
        LOG.trace("Next schema: {}", nextSchema.rawSchema());

        return checker.isCompatible(nextSchema, List.of(currentSchema));
    }

    /// Check if two individual AvroSchema objects are semantically equal.
    /// Since Avro's Schema.equals() is order-sensitive, we normalize the schemas
    /// to JSON, sort fields by name, and compare. This approach leverages existing
    /// Jackson utilities rather than implementing custom schema traversal logic.
    ///
    /// @param currentSchema Current/baseline schema as [AvroSchema]
    /// @param nextSchema Next/target schema as [AvroSchema]
    /// @return true if schemas are semantically identical, false if different
    public static boolean checkSchemaEquality(@Nonnull final AvroSchema currentSchema,
            @Nonnull final AvroSchema nextSchema) {
        LOG.trace("Checking schema equality");
        LOG.trace("Current schema: {}", currentSchema.rawSchema());
        LOG.trace("Next schema: {}", nextSchema.rawSchema());

        try {
            final JsonNode normalized1 = normalizeSchemaJson(
                JSON_MAPPER.readTree(currentSchema.rawSchema().toString()));
            final JsonNode normalized2 = normalizeSchemaJson(
                JSON_MAPPER.readTree(nextSchema.rawSchema().toString()));
                
            return normalized1.equals(normalized2);
        } catch (Exception e) {
            LOG.error("Failed to normalize schemas for equality check", e);
            throw new RuntimeException("Schema normalization failed unexpectedly", e);
        }
    }

    /// Normalize a schema's JSON representation by sorting all object keys and
    /// sorting record fields by name. This creates a canonical form that can be
    /// compared for semantic equality.
    private static JsonNode normalizeSchemaJson(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = JSON_MAPPER.createObjectNode();
            
            // Sort all keys alphabetically
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            Collections.sort(keys);
            
            for (String key : keys) {
                JsonNode value = node.get(key);
                
                // Special handling for "fields" array - sort by field name
                if (key.equals("fields") && value.isArray()) {
                    ArrayNode sortedFields = JSON_MAPPER.createArrayNode();
                    List<JsonNode> fieldList = new ArrayList<>();
                    value.forEach(fieldList::add);
                    
                    // Sort fields by name
                    fieldList.sort((a, b) -> {
                        String nameA = a.has("name") ? a.get("name").asText() : "";
                        String nameB = b.has("name") ? b.get("name").asText() : "";
                        return nameA.compareTo(nameB);
                    });
                    
                    // Recursively normalize each field
                    for (JsonNode field : fieldList) {
                        sortedFields.add(normalizeSchemaJson(field));
                    }
                    obj.set(key, sortedFields);
                } else {
                    obj.set(key, normalizeSchemaJson(value));
                }
            }
            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = JSON_MAPPER.createArrayNode();
            node.forEach(item -> arr.add(normalizeSchemaJson(item)));
            return arr;
        }
        return node;
    }

    /// Detect changes between current and next TableAvroSchemas.
    /// This identifies if schemas have actually changed, regardless of
    /// compatibility.
    ///
    /// @param curr Current schemas, provided as a [TableAvroSchemas]
    /// @param next Next schemas, provided as a [TableAvroSchemas]
    /// @return [ChangeResult] indicating what changed
    public static ChangeResult detectSchemaChanges(@Nonnull final TableAvroSchemas curr,
            @Nonnull final TableAvroSchemas next) {
        if (!Objects.equals(curr.identifier(), next.identifier())) {
            LOG.warn("Detecting changes in Table Avro Schemas with mismatching identifiers: '{}' != '{}'",
                    curr.identifier(), next.identifier());
        }

        // Check key schema changes
        boolean keyChanged;
        if (curr.keySchema() != null && next.keySchema() != null) {
            // Both have key schemas - compare them
            keyChanged = !checkSchemaEquality(
                    curr.keySchemaToSchemaRegistryAvroSchema(),
                    next.keySchemaToSchemaRegistryAvroSchema());
        } else if (curr.keySchema() == null && next.keySchema() == null) {
            // Both null - no change
            keyChanged = false;
        } else {
            // One is null, the other isn't - definitely changed
            keyChanged = true;
        }

        // Check value schema changes (value schema is always present)
        final boolean valueChanged = !checkSchemaEquality(
                curr.valueSchemaToSchemaRegistryAvroSchema(),
                next.valueSchemaToSchemaRegistryAvroSchema());

        // Check envelope schema changes (envelope schema is always present)
        final boolean envelopeChanged = !checkSchemaEquality(
                curr.envelopeSchemaToSchemaRegistryAvroSchema(),
                next.envelopeSchemaToSchemaRegistryAvroSchema());

        LOG.debug("Schema changes detected for '{}': key={}, value={}, envelope={}",
                curr.identifier(), keyChanged, valueChanged, envelopeChanged);

        return new ChangeResult(keyChanged, valueChanged, envelopeChanged);
    }

    /// Wraps the result of a schema change detection, executed via
    /// #detectSchemaChanges method.
    ///
    /// @param keyChanged true if key schema changed
    /// @param valueChanged true if value schema changed
    /// @param envelopeChanged true if envelope schema changed
    public record ChangeResult(
            boolean keyChanged,
            boolean valueChanged,
            boolean envelopeChanged) {

        /// @return true if any schema component changed
        public boolean hasAnyChanges() {
            return keyChanged || valueChanged || envelopeChanged;
        }

        /// @return count of schema components that changed
        public int changedComponentsCount() {
            int count = 0;
            if (keyChanged)
                count++;
            if (valueChanged)
                count++;
            if (envelopeChanged)
                count++;
            return count;
        }
    }

    /// Wraps the result of a compatibility check, executed via #checkCompatibility
    /// method.
    ///
    /// @param checkedLevel       Compatibility Level that was checked
    /// @param keyResults         [List] of errors reported for the Key Schema
    /// @param valueResults       [List] of errors reported for the Value Schema
    /// @param envelopeResults    [List] of errors reported for the Envelope Schema
    public record CompatibilityResult(@Nonnull CompatibilityLevel checkedLevel,
                                      @Nonnull List<String> keyResults,
                                      @Nonnull List<String> valueResults,
                                      @Nonnull List<String> envelopeResults) {
        public boolean isKeyCompatible() {
            return keyResults.isEmpty();
        }

        public boolean isValueCompatible() {
            return valueResults.isEmpty();
        }

        public boolean isEnvelopeCompatible() {
            return envelopeResults.isEmpty();
        }

        public boolean isCompatible() {
            return isKeyCompatible() && isValueCompatible() && isEnvelopeCompatible();
        }
    }
}
