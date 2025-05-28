package io.snyk.skemium.helpers;

import io.confluent.connect.avro.AvroData;
import org.apache.avro.Schema;

/// Helper to interact with Avro.
public class Avro {

    /// Convert a Kafka Connect [org.apache.kafka.connect.data.Schema] to an Avro [Schema].
    ///
    /// If input is a [Schema.Type#UNION], it returns the first subtype that is not [Schema.Type#NULL].
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
                    if (avroSubSchema.getType() != Schema.Type.NULL) {
                        return avroSubSchema;
                    }
                }
            }

            return avroSchema;
        }
        return null;
    }

}
