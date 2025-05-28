package io.snyk.skemium.helpers;

import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.avro.TableAvroDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

/// Helper to interact with Schema Registry.
public class SchemaRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaRegistry.class);

    /// Check compatibility between a "Curr(ent)" and a "Next" [TableAvroDescriptor], applying the given [CompatibilityLevel].
    ///
    /// @param curr               Current schemas, provided as a [TableAvroDescriptor]
    /// @param next               Next schemas, provided as a [TableAvroDescriptor]
    /// @param compatibilityLevel Compatibility Level to apply
    /// @return [CheckCompatibilityResult]
    public static CheckCompatibilityResult checkCompatibility(final TableAvroDescriptor curr,
                                                              final TableAvroDescriptor next,
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

        return new CheckCompatibilityResult(
                compatibilityLevel,
                keyCompatibilityErrors,
                checker.isCompatible(
                        next.valueSchemaToSchemaRegistryAvroSchema(),
                        List.of(curr.valueSchemaToSchemaRegistryAvroSchema())
                ),
                checker.isCompatible(
                        next.envelopeSchemaToSchemaRegistryAvroSchema(),
                        List.of(curr.envelopeSchemaToSchemaRegistryAvroSchema())
                )
        );
    }

    /// Wraps the result of a compatibility check, executed via #checkCompatibility method.
    ///
    /// @param checkedLevel       Compatibility Level that was checked
    /// @param keyResults         [List] of errors reported for the Key Schema
    /// @param valueResults       [List] of errors reported for the Value Schema
    /// @param envelopeResults    [List] of errors reported for the Envelope Schema
    public record CheckCompatibilityResult(@Nonnull CompatibilityLevel checkedLevel,
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
