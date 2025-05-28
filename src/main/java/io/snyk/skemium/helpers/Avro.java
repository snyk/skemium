package io.snyk.skemium.helpers;

import io.confluent.connect.avro.AvroData;
import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.avro.TableAvroDescriptor;
import org.apache.avro.Schema;

import java.util.List;

/// Helper to interact with Avro.
public class Avro {

    /// Convert a Kafka Connect [org.apache.kafka.connect.data.Schema] to an Avro [Schema].
    ///
    /// If input is a [#UNION], it assumes it contains a [#RECORD]
    /// and convert that. Alternatively, if input is `null`, returns `null`.
    ///
    /// TODO: Make this handle other union-ed types, in addition to RECORD.
    ///
    /// @param kafkaConnectSchema Kafka Connect (Table) [org.apache.kafka.connect.data.Schema]
    /// @return The corresponding Avro [Schema].
    public static Schema kafkaConnectSchemaToAvroSchema(final org.apache.kafka.connect.data.Schema kafkaConnectSchema) {
        if (kafkaConnectSchema != null) {
            final AvroData avroData = new AvroData(1);
            final Schema avroSchema = avroData.fromConnectSchema(kafkaConnectSchema);

            // NOTE: A record is by default mapped to a Union of `NULL` and `RECORD`.
            // We only care about the `RECORD` part.
            if (avroSchema.isUnion()) {
                for (Schema avroSubSchema : avroSchema.getTypes()) {
                    if (avroSubSchema.getType() == Schema.Type.RECORD) {
                        return avroSubSchema;
                    }
                }
            }

            return avroSchema;
        }
        return null;
    }

    /// Check compatibility between a "Curr(ent)" and a "Next" Avro Schema, applying the given [CompatibilityLevel].
    ///
    /// @param curr               Current Schema, provided as a [TableAvroDescriptor]
    /// @param next               Next Schema, provided as a [TableAvroDescriptor]
    /// @param compatibilityLevel Compatibility Level to apply
    /// @return [List] of compatibility errors, if any; if empty, means the schemas are compatible.
    public static List<String> checkCompatibility(final TableAvroDescriptor curr, final TableAvroDescriptor next, final CompatibilityLevel compatibilityLevel) {
        return CompatibilityChecker.checker(compatibilityLevel)
                .isCompatible(
                        next.valueSchemaToSchemaRegistryAvroSchema(),
                        List.of(curr.valueSchemaToSchemaRegistryAvroSchema())
                );
    }
}
