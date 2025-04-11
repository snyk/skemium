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

import static org.junit.jupiter.api.Assertions.*;

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
        final AvroSchemaFile employee = AvroSchemaFile.loadFrom(TestHelper.RESOURCES.resolve("schema_employee"), "chinook.public.employee");

        assertEquals("chinook.public.employee.avsc", employee.filename());

        assertEquals("fede0d51a439d9bcd9cf7a6e6b323400591102f1ff8b25b73329ac81ffac3ed1", employee.checksum());
        assertEquals("chinook.public.employee.avsc.sha256", employee.checksumFilename());

        employee.saveTo(TEMP_DIR.toAbsolutePath());
        final String originalSchemaContent = FileUtils.readFileToString(TestHelper.RESOURCES.resolve("schema_employee").resolve("chinook.public.employee.avsc").toFile(), StandardCharsets.UTF_8);
        final String savedSchemaContent = FileUtils.readFileToString(TEMP_DIR.resolve("chinook.public.employee.avsc").toFile(), StandardCharsets.UTF_8);
        assertEquals(originalSchemaContent, savedSchemaContent);
    }

    @Test
    void shouldConvertToSchemaRegistryAvroSchemaType() throws IOException {
        final AvroSchemaFile employee = AvroSchemaFile.loadFrom(TestHelper.RESOURCES.resolve("schema_employee"), "chinook.public.employee");

        AvroSchema regAvroSchema = employee.toSchemaRegistryAvroSchema();

        assertEquals("AVRO", regAvroSchema.schemaType());
        assertEquals(
                "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"com.sun.jna.chinook.employee\",\"fields\":[{\"name\":\"employee_id\",\"type\":\"int\"},{\"name\":\"last_name\",\"type\":\"string\"},{\"name\":\"first_name\",\"type\":\"string\"},{\"name\":\"title\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"reports_to\",\"type\":[\"null\",\"int\"],\"default\":null},{\"name\":\"birth_date\",\"type\":[\"null\",{\"type\":\"long\",\"connect.version\":1,\"connect.name\":\"io.debezium.time.MicroTimestamp\"}],\"default\":null},{\"name\":\"hire_date\",\"type\":[\"null\",{\"type\":\"long\",\"connect.version\":1,\"connect.name\":\"io.debezium.time.MicroTimestamp\"}],\"default\":null},{\"name\":\"address\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"city\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"state\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"country\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"postal_code\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"phone\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"fax\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"email\",\"type\":[\"null\",\"string\"],\"default\":null}],\"connect.name\":\"com.sun.jna.chinook.employee.Value\"}",
                regAvroSchema.canonicalString()
        );
        assertEquals("com.sun.jna.chinook.employee.Value", regAvroSchema.name());
    }

    @Test
    void shouldThrowIfChecksumDoesNotMatch() {
        final IOException expectedException = assertThrows(IOException.class, () -> {
            AvroSchemaFile.loadFrom(TestHelper.RESOURCES.resolve("schema_employee_invalid_checksum"), "chinook.public.employee");
        });


        assertTrue(expectedException
                .getMessage()
                .matches("Checksum '(.*)/schema_employee_invalid_checksum/chinook\\.public\\.employee\\.avsc\\.sha256' ((.*)) does not match content '(.*)/schema_employee_invalid_checksum/chinook\\.public\\.employee\\.avsc' ((.*))"));
    }
}
