package io.snyk.skemium.helpers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.jsr310.AvroJavaTimeModule;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.confluent.connect.avro.AvroData;
import io.snyk.skemium.meta.MetadataFile;
import org.apache.avro.Schema;

import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;

/// Helper to interact with Avro.
public class Avro {

    private static final AvroMapper AVRO_MAPPER = AvroMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new AvroJavaTimeModule())
            .build();

    /// Stores Avro Schemas of the files Skemium commands can produce.
    private static final Path AVRO_SCHEMAS_DIRECTORY = Path.of("schemas");

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

    /// Save Avro [Schema] of an arbitrary [Class] to an Avro Schema file, under [#AVRO_SCHEMAS_DIRECTORY].
    ///
    /// The given [Class] is expected to be annotated with Jackson [JsonProperty] annotations
    /// to dictate the final output.
    ///
    /// This is expected to be called as part of the test suite,
    /// so that the very latest version of the schemas can be generated.
    /// The CI will fail if a difference is detected.
    ///
    /// @param clazz [Class] to save
    /// @param outputFilename [Path] filename used to save the Avro schema
    /// @throws JsonProcessingException
    /// @throws FileNotFoundException
    public static void saveAvroSchemaForType(@Nonnull final Class<?> clazz,
                                             @Nonnull final Path outputFilename) throws JsonProcessingException, FileNotFoundException {
        final AvroSchemaGenerator avroSchemaGenerator = new AvroSchemaGenerator().enableLogicalTypes();

        AVRO_MAPPER.acceptJsonFormatVisitor(clazz, avroSchemaGenerator);
        final Schema avroSchema = avroSchemaGenerator.getGeneratedSchema().getAvroSchema();

        try (PrintWriter out = new PrintWriter(AVRO_SCHEMAS_DIRECTORY.resolve(outputFilename).toString()) ) {
            out.println(JSON.pretty(avroSchema.toString()));
        }
    }
}
