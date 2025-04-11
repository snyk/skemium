package io.snyk.skemium.helpers;

import io.confluent.connect.avro.AvroData;
import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.debezium.relational.TableSchema;
import io.snyk.skemium.avro.AvroSchemaFile;
import org.apache.avro.Schema;

import java.util.List;

/**
 * Helper to interact with Avro.
 */
public class Avro {

    /**
     * Convert a Debezium Relational Database {@link TableSchema} to an {@link Schema}.
     *
     * @param debeziumTableSchema Database {@link TableSchema}
     * @return The corresponding Avro {@link Schema}, wrapped in an {@link AvroSchemaFile}
     */
    public static AvroSchemaFile relationalTableSchemaToAvroSchemaHandler(final TableSchema debeziumTableSchema) {
        return new AvroSchemaFile(
                kafkaConnectSchemaToAvroSchema(debeziumTableSchema.valueSchema()),
                debeziumTableSchema.id().identifier()
        );
    }

    /**
     * Convert a Kafka Connect {@link org.apache.kafka.connect.data.Schema} to an Avro {@link Schema}.
     * <p>
     * NOTE: If input is a {@link Schema.Type#UNION}, it assumes it contains a {@link Schema.Type#RECORD}
     * and convert that.
     * <p>
     * TODO: Make this handle other union-ed types, in addition to RECORD.
     *
     * @param kafkaConnectSchema Kafka Connect (Table) {@link org.apache.kafka.connect.data.Schema}
     * @return The corresponding Avro {@link Schema}.
     */
    public static Schema kafkaConnectSchemaToAvroSchema(final org.apache.kafka.connect.data.Schema kafkaConnectSchema) {
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

    /**
     * Check compatibility between a "Curr(ent)" and a "Next" Avro Schema, applying the given {@link CompatibilityLevel}.
     *
     * @param curr Current Schema, provided as a {@link AvroSchemaFile}
     * @param next Next Schema, provided as a {@link AvroSchemaFile}
     * @param compatibilityLevel Compatibility Level to apply
     * @return {@link List} of compatibility errors, if any; if empty, means the schemas are compatible.
     */
    public static List<String> checkCompatibility(final AvroSchemaFile curr, final AvroSchemaFile next, final CompatibilityLevel compatibilityLevel) {
        final CompatibilityChecker checker = CompatibilityChecker.checker(compatibilityLevel);
        return checker.isCompatible(next.toSchemaRegistryAvroSchema(), List.of(curr.toSchemaRegistryAvroSchema()));
    }
}
