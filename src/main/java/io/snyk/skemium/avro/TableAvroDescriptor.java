package io.snyk.skemium.avro;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.debezium.relational.TableSchema;
import io.snyk.skemium.helpers.JSON;
import org.apache.avro.Schema;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static io.snyk.skemium.helpers.Avro.kafkaConnectSchemaToAvroSchema;

/// Describes a Database Table as an aggregation of Avro Schemas and an identifier.
///
/// @param identifier     The identifier of the table
/// @param keySchema      Avro [Schema] for the Key (primary key of the table) or `null` if absent
/// @param valueSchema    Avro [Schema] for the Value (row of the table)
/// @param envelopeSchema Avro [Schema] for the Debezium {@link io.debezium.data.Envelope}
public record TableAvroDescriptor(@Nonnull String identifier,
                                  @Nullable Schema keySchema,
                                  @Nonnull Schema valueSchema,
                                  @Nonnull Schema envelopeSchema) {
    private static final Logger LOG = LoggerFactory.getLogger(TableAvroDescriptor.class);

    private static final String KEY_FILENAME_FMT = "%s.key.avsc";
    private static final String VALUE_FILENAME_FMT = "%s.val.avsc";
    private static final String ENVELOPE_FILENAME_FMT = "%s.env.avsc";
    private static final String CHECKSUM_FILENAME_FMT = "%s.sha256";

    /// Builds a [TableAvroDescriptor] from a Debezium [TableSchema].
    public static TableAvroDescriptor build(final TableSchema debeziumTableSchema) {
        return new TableAvroDescriptor(
                debeziumTableSchema.id().identifier(),
                kafkaConnectSchemaToAvroSchema(debeziumTableSchema.keySchema()),
                kafkaConnectSchemaToAvroSchema(debeziumTableSchema.valueSchema()),
                kafkaConnectSchemaToAvroSchema(debeziumTableSchema.getEnvelopeSchema().schema())
        );
    }

    /// @return Filename of the Table Avro [Schema] for the Key.
    public String keyFilename() {
        return KEY_FILENAME_FMT.formatted(identifier);
    }

    /// @return Filename of the Table Avro [Schema] for the Value.
    public String valueFilename() {
        return VALUE_FILENAME_FMT.formatted(identifier);
    }

    /// @return Filename of the Table Avro [Schema] for the Envelope.
    public String envelopeFilename() {
        return ENVELOPE_FILENAME_FMT.formatted(identifier);
    }

    /// @return SHA256 checksum of this descriptor.
    public String checksum() {
        final StringBuilder sb = new StringBuilder();
        if (keySchema != null) {
            sb.append(keySchema).append("\n");
        }
        sb.append(valueSchema).append("\n");
        sb.append(envelopeSchema).append("\n");

        return DigestUtils.sha256Hex(sb.toString());
    }

    /// @return Filename of the SHA256 checksum of this descriptor.
    public String checksumFilename() {
        return CHECKSUM_FILENAME_FMT.formatted(identifier);
    }

    /// Saves the descriptor to filesystem in the given directory.
    ///
    /// The files will be named based on the [#identifier()].
    /// An additional file with the content of [#checksum()] will also be created and named [#checksumFilename()].
    ///
    /// WARNING: Any existing files with the same names will be overridden.
    ///
    /// @param outputDir [Path] to the directory where to save the files. Directory MUST already exist and be writable.
    /// @throws FileNotFoundException
    /// @throws JsonProcessingException
    public void saveTo(@Nonnull final Path outputDir) throws FileNotFoundException, JsonProcessingException {
        LOG.debug("Saving Table Avro Descriptor: {} -> {}", identifier, outputDir);
        final Path keyOutputPath = outputDir.toAbsolutePath().resolve(keyFilename());
        final Path valueOutputPath = outputDir.toAbsolutePath().resolve(valueFilename());
        final Path envelopeOutputPath = outputDir.toAbsolutePath().resolve(envelopeFilename());
        final Path checksumOutputPath = outputDir.toAbsolutePath().resolve(checksumFilename());

        if (keySchema != null) {
            LOG.trace("Saving KEY Avro Schema: {} -> {}", identifier, keyOutputPath);
            try (final PrintWriter out = new PrintWriter(keyOutputPath.toString())) {
                out.println(JSON.pretty(keySchema.toString()));
            }
        } else {
            LOG.trace("Skip saving KEY Avro Schema: {} == NULL", identifier);
        }

        LOG.trace("Saving VALUE Avro Schema: {} -> {}", identifier, valueOutputPath);
        try (final PrintWriter out = new PrintWriter(valueOutputPath.toString())) {
            out.println(JSON.pretty(valueSchema.toString()));
        }

        LOG.trace("Saving ENVELOPE Avro Schema: {} -> {}", identifier, envelopeOutputPath);
        try (final PrintWriter out = new PrintWriter(envelopeOutputPath.toString())) {
            out.println(JSON.pretty(envelopeSchema.toString()));
        }

        LOG.trace("Saving checksum: {} -> {}", identifier, checksumOutputPath);
        try (final PrintWriter out = new PrintWriter(checksumOutputPath.toString())) {
            out.print(checksum());
        }
    }

    /// @return A Schema Registry's [AvroSchema] object, from the [#keySchema()].
    public AvroSchema keySchemaToSchemaRegistryAvroSchema() {
        return new AvroSchema(keySchema);
    }

    /// @return A Schema Registry's [AvroSchema] object, from the [#valueSchema()].
    public AvroSchema valueSchemaToSchemaRegistryAvroSchema() {
        return new AvroSchema(valueSchema);
    }

    /// @return A Schema Registry's [AvroSchema] object, from the [#envelopeSchema()].
    public AvroSchema envelopeSchemaToSchemaRegistryAvroSchema() {
        return new AvroSchema(envelopeSchema);
    }

    /// Loads an [TableAvroDescriptor] from filesystem.
    /// It validates the checksum on the filesystem (sibling file) with the one computed from the input [Schema]s.
    /// It will throw in case of mismatch.
    ///
    /// If the checksum file is absent, logs a warning but continues.
    ///
    /// @param inputDir   [Path] to the directory
    /// @param identifier The identifier of the schema
    /// @return An [TableAvroDescriptor]
    /// @throws IOException
    public static TableAvroDescriptor loadFrom(@Nonnull final Path inputDir, @Nonnull final String identifier) throws IOException {
        final Path keyInputPath = inputDir.toAbsolutePath().resolve(KEY_FILENAME_FMT.formatted(identifier));
        final Path valueInputPath = inputDir.toAbsolutePath().resolve(VALUE_FILENAME_FMT.formatted(identifier));
        final Path envelopeInputPath = inputDir.toAbsolutePath().resolve(ENVELOPE_FILENAME_FMT.formatted(identifier));
        final Path checksumInputPath = inputDir.toAbsolutePath().resolve(CHECKSUM_FILENAME_FMT.formatted(identifier));

        Schema keySchema = null;
        if (keyInputPath.toFile().exists()) {
            LOG.trace("Loading KEY Avro Schema: {} <- {}", identifier, keyInputPath);
            keySchema = new Schema.Parser().parse(keyInputPath.toFile());
            if (!Objects.equals(identifier, keySchema.getNamespace())) {
                LOG.warn("KEY Avro Schema does not match Table identifier: '{}' != '{}'", keySchema.getNamespace(), identifier);
            }
        } else {
            LOG.trace("Skip loading KEY Avro Schema: {} == NULL", identifier);
        }

        LOG.trace("Loading VALUE Avro Schema: {} <- {}", identifier, valueInputPath);
        final Schema valueSchema = new Schema.Parser().parse(valueInputPath.toFile());
        if (!Objects.equals(identifier, valueSchema.getNamespace())) {
            LOG.warn("VALUE Avro Schema does not match Table identifier: '{}' != '{}'", valueSchema.getNamespace(), identifier);
        }

        LOG.trace("Loading ENVELOPE Avro Schema: {} <- {}", identifier, envelopeInputPath);
        final Schema envelopeSchema = new Schema.Parser().parse(envelopeInputPath.toFile());
        if (!Objects.equals(identifier, envelopeSchema.getNamespace())) {
            LOG.warn("ENVELOPE Avro Schema does not match Table identifier: '{}' != '{}'", envelopeSchema.getNamespace(), identifier);
        }

        final TableAvroDescriptor res = new TableAvroDescriptor(identifier, keySchema, valueSchema, envelopeSchema);

        if (!checksumInputPath.toFile().exists()) {
            LOG.warn("Checksum '{}' not found: skipping validation", identifier);
            return res;
        }

        LOG.trace("Loading checksum: {} <- {}", identifier, checksumInputPath);
        final String checksumInput = Files.readString(checksumInputPath, StandardCharsets.UTF_8).trim();
        final String checksumComputed = res.checksum();
        if (!checksumInput.equals(checksumComputed)) {
            throw new IOException(String.format("Input Checksum '%s' (%s) does not match '%s' computed checksum (%s)",
                    checksumInputPath,
                    checksumInput,
                    identifier,
                    checksumComputed
            ));
        }

        return res;
    }
}
