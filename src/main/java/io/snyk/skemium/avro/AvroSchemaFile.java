package io.snyk.skemium.avro;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.snyk.skemium.helpers.JSON;
import org.apache.avro.Schema;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Takes an Avro {@link Schema} and logically wraps it into a "logical" file.
 * Note that this is NOT (and doesn't try to be) a {@link java.io.File}.
 *
 * @param avroSchema The "wrapped" Avro {@link Schema}
 * @param identifier The identifier of the schema
 */
public record AvroSchemaFile(Schema avroSchema, String identifier) {
    private static final Logger LOG = LoggerFactory.getLogger(AvroSchemaFile.class);

    private static final String SCHEMA_FILENAME_EXTENSION = ".avsc";
    private static final String CHECKSUM_FILENAME_EXTENSION = ".avsc.sha256";

    /**
     * @return Filename the Avro {@link Schema} will be saved with.
     */
    public String filename() {
        return identifier + SCHEMA_FILENAME_EXTENSION;
    }

    /**
     * @return SHA256 checksum of the Avro {@link Schema}.
     */
    public String checksum() {
        return DigestUtils.sha256Hex(avroSchema.toString());
    }

    /**
     * @return Filename the SHA256 checksum of the Avro {@link Schema} will be saved with.
     */
    public String checksumFilename() {
        return identifier + CHECKSUM_FILENAME_EXTENSION;
    }

    /**
     * Saves the wrapped Avro {@link Schema} to a file in the given directory.
     * <p>
     * The file will be named {@link #filename()}.
     * An additional file with the content of {@link #checksum()} will also be created and named {@link #checksumFilename()}.
     * <p>
     * WARNING: Any existing files with the same names will be overwritten.
     *
     * @param outputDir {@link Path} to the directory where to save the file. Directory MUST already exist and be writable.
     * @throws FileNotFoundException
     * @throws JsonProcessingException
     */
    public void saveTo(final Path outputDir) throws FileNotFoundException, JsonProcessingException {
        final Path fileOutputPath = outputDir.toAbsolutePath().resolve(filename());
        final Path checksumOutputPath = outputDir.toAbsolutePath().resolve(checksumFilename());

        LOG.trace("Saving Avro Schema '{}': {}", identifier, fileOutputPath);
        try (final PrintWriter out = new PrintWriter(fileOutputPath.toString())) {
            out.println(JSON.pretty(avroSchema.toString()));
        }

        LOG.trace("Saving Avro Schema '{}' checksum: {}", identifier, checksumOutputPath);
        try (final PrintWriter out = new PrintWriter(checksumOutputPath.toString())) {
            out.print(checksum());
        }
    }

    /**
     * Constructs a Schema Registry's {@link AvroSchema} object, from the wrapped Avro {@link Schema}.
     *
     * @return A Schema Registry's {@link AvroSchema} object
     */
    public AvroSchema toSchemaRegistryAvroSchema() {
        return new AvroSchema(avroSchema);
    }

    /**
     * Loads an {@link AvroSchemaFile} from filesystem.
     * It validates the checksum on the filesystem (sibling file) with the one of the wrapped {@link Schema}:
     * it throws in case of mismatch.
     * <p>
     * If checksum file is absent, logs a warning but continues.
     *
     * @param inputDir   {@link Path} to the directory
     * @param identifier The identifier of the schema
     * @return An {@link AvroSchemaFile}
     * @throws IOException
     */
    public static AvroSchemaFile loadFrom(final Path inputDir, final String identifier) throws IOException {
        final Path inputFilePath = inputDir.toAbsolutePath().resolve(identifier + SCHEMA_FILENAME_EXTENSION);

        LOG.trace("Loading Avro Schema '{}': {}", identifier, inputFilePath);
        final Schema avroSchema = new Schema.Parser().parse(inputFilePath.toFile());
        final AvroSchemaFile res = new AvroSchemaFile(avroSchema, identifier);

        final Path inputFileChecksumPath = inputFilePath.resolveSibling(identifier + CHECKSUM_FILENAME_EXTENSION);
        if (!inputFileChecksumPath.toFile().exists()) {
            LOG.warn("Checksum file for Avro Schema '{}' not found: skipping checksum validation", identifier);
            return res;
        }

        LOG.trace("Loading Avro Schema '{}' checksum: {}", identifier, inputFileChecksumPath);
        final String inputFileChecksum = Files.readString(inputFileChecksumPath, StandardCharsets.UTF_8);
        if (!res.checksum().equals(inputFileChecksum.trim())) {
            throw new IOException(String.format("Checksum at '%s' does not match content for '%s'", inputFileChecksumPath, inputFilePath));
        }

        return res;
    }
}
