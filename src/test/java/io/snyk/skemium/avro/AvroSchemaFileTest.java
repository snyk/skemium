package io.snyk.skemium.avro;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.snyk.skemium.TestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvroSchemaFileTest {

    Path TEMP_DIR;

    @BeforeEach
    public void createTempDir() throws IOException {
        TEMP_DIR = Files.createTempDirectory("skemium-test-");
    }

    @AfterEach
    public void deleteTempDir() throws IOException {
        FileUtils.deleteDirectory(TEMP_DIR.toFile());
    }

    @Test
    void shouldLoadAndSaveFromFilesystem() throws IOException {
        final AvroSchemaFile curr = AvroSchemaFile.loadFrom(TestHelper.RESOURCES.resolve("schema_change_non_backward_compatible"), "current");

        assertEquals("current.avsc", curr.filename());

        assertEquals("2a73aeb227d130d54af6b428931253bd35a0ef6ba590de059454764dc847b0b6", curr.checksum());
        assertEquals("current.avsc.sha256", curr.checksumFilename());

        curr.saveTo(TEMP_DIR.toAbsolutePath());
        final String originalSchemaContent = FileUtils.readFileToString(TestHelper.RESOURCES.resolve("schema_change_non_backward_compatible/current.avsc").toFile(), StandardCharsets.UTF_8);
        final String savedSchemaContent = FileUtils.readFileToString(TEMP_DIR.resolve("current.avsc").toFile(), StandardCharsets.UTF_8);
        assertEquals(originalSchemaContent, savedSchemaContent);
    }

    @Test
    void shouldConvertToSchemaRegistryAvroSchemaType() throws IOException {
        final AvroSchemaFile curr = AvroSchemaFile.loadFrom(TestHelper.RESOURCES.resolve("schema_change_non_backward_compatible"), "current");

        AvroSchema regAvroSchema = curr.toSchemaRegistryAvroSchema();

        assertEquals("AVRO", regAvroSchema.schemaType());
        assertEquals("{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"com.sun.jna.chinook.artist\",\"fields\":[{\"name\":\"artist_id\",\"type\":\"int\"},{\"name\":\"name\",\"type\":[\"null\",\"string\"],\"default\":null}],\"connect.name\":\"com.sun.jna.chinook.artist.Value\"}", regAvroSchema.canonicalString());
        assertEquals("com.sun.jna.chinook.artist.Value", regAvroSchema.name());
    }
}
