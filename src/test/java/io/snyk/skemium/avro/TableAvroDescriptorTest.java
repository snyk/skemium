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

class TableAvroDescriptorTest {

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
        final TableAvroDescriptor employee = TableAvroDescriptor.loadFrom(TestHelper.RESOURCES.resolve("schema_employee"), "chinook.public.employee");

        assertEquals("chinook.public.employee.key.avsc", employee.keyFilename());
        assertEquals("chinook.public.employee.val.avsc", employee.valueFilename());
        assertEquals("chinook.public.employee.env.avsc", employee.envelopeFilename());

        assertEquals("4c5342ece801db10a61b457ca84ff4b9daddcc19381bc22f77f937df061427fd", employee.checksum());
        assertEquals("chinook.public.employee.sha256", employee.checksumFilename());

        employee.saveTo(TEMP_DIR.toAbsolutePath());

        assertEquals(
                FileUtils.readFileToString(TestHelper.RESOURCES.resolve("schema_employee").resolve("chinook.public.employee.key.avsc").toFile(), StandardCharsets.UTF_8),
                FileUtils.readFileToString(TEMP_DIR.resolve("chinook.public.employee.key.avsc").toFile(), StandardCharsets.UTF_8));
        assertEquals(
                FileUtils.readFileToString(TestHelper.RESOURCES.resolve("schema_employee").resolve("chinook.public.employee.val.avsc").toFile(), StandardCharsets.UTF_8),
                FileUtils.readFileToString(TEMP_DIR.resolve("chinook.public.employee.val.avsc").toFile(), StandardCharsets.UTF_8));
        assertEquals(
                FileUtils.readFileToString(TestHelper.RESOURCES.resolve("schema_employee").resolve("chinook.public.employee.env.avsc").toFile(), StandardCharsets.UTF_8),
                FileUtils.readFileToString(TEMP_DIR.resolve("chinook.public.employee.env.avsc").toFile(), StandardCharsets.UTF_8));
        assertEquals(
                FileUtils.readFileToString(TestHelper.RESOURCES.resolve("schema_employee").resolve("chinook.public.employee.sha256").toFile(), StandardCharsets.UTF_8),
                FileUtils.readFileToString(TEMP_DIR.resolve("chinook.public.employee.sha256").toFile(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldConvertValueSchemaToSchemaRegistryAvroSchemaType() throws IOException {
        final TableAvroDescriptor employee = TableAvroDescriptor.loadFrom(TestHelper.RESOURCES.resolve("schema_employee"), "chinook.public.employee");

        final AvroSchema valueRegAvroSchema = employee.valueSchemaToSchemaRegistryAvroSchema();
        assertEquals("AVRO", valueRegAvroSchema.schemaType());
        assertEquals(
                "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"chinook.public.employee\",\"fields\":[{\"name\":\"employee_id\",\"type\":\"int\"},{\"name\":\"last_name\",\"type\":\"string\"},{\"name\":\"first_name\",\"type\":\"string\"},{\"name\":\"title\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"reports_to\",\"type\":[\"null\",\"int\"],\"default\":null},{\"name\":\"birth_date\",\"type\":[\"null\",{\"type\":\"long\",\"connect.version\":1,\"connect.name\":\"io.debezium.time.MicroTimestamp\"}],\"default\":null},{\"name\":\"hire_date\",\"type\":[\"null\",{\"type\":\"long\",\"connect.version\":1,\"connect.name\":\"io.debezium.time.MicroTimestamp\"}],\"default\":null},{\"name\":\"address\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"city\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"state\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"country\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"postal_code\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"phone\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"fax\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"email\",\"type\":[\"null\",\"string\"],\"default\":null}],\"connect.name\":\"chinook.public.employee.Value\"}",
                valueRegAvroSchema.canonicalString()
        );
        assertEquals("chinook.public.employee.Value", valueRegAvroSchema.name());

        final AvroSchema keyRegAvroSchema = employee.keySchemaToSchemaRegistryAvroSchema();
        assertEquals("AVRO", keyRegAvroSchema.schemaType());
        assertEquals(
                "{\"type\":\"record\",\"name\":\"Key\",\"namespace\":\"chinook.public.employee\",\"fields\":[{\"name\":\"employee_id\",\"type\":\"int\"}],\"connect.name\":\"chinook.public.employee.Key\"}",
                keyRegAvroSchema.canonicalString()
        );
        assertEquals("chinook.public.employee.Key", keyRegAvroSchema.name());

        final AvroSchema envelopeRegAvroSchema = employee.envelopeSchemaToSchemaRegistryAvroSchema();
        assertEquals("AVRO", envelopeRegAvroSchema.schemaType());
        assertEquals(
                "{\"type\":\"record\",\"name\":\"Envelope\",\"namespace\":\"chinook.public.employee\",\"fields\":[{\"name\":\"before\",\"type\":[\"null\",{\"type\":\"record\",\"name\":\"Value\",\"fields\":[{\"name\":\"employee_id\",\"type\":\"int\"},{\"name\":\"last_name\",\"type\":\"string\"},{\"name\":\"first_name\",\"type\":\"string\"},{\"name\":\"title\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"reports_to\",\"type\":[\"null\",\"int\"],\"default\":null},{\"name\":\"birth_date\",\"type\":[\"null\",{\"type\":\"long\",\"connect.version\":1,\"connect.name\":\"io.debezium.time.MicroTimestamp\"}],\"default\":null},{\"name\":\"hire_date\",\"type\":[\"null\",{\"type\":\"long\",\"connect.version\":1,\"connect.name\":\"io.debezium.time.MicroTimestamp\"}],\"default\":null},{\"name\":\"address\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"city\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"state\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"country\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"postal_code\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"phone\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"fax\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"email\",\"type\":[\"null\",\"string\"],\"default\":null}],\"connect.name\":\"chinook.public.employee.Value\"}],\"default\":null},{\"name\":\"after\",\"type\":[\"null\",\"Value\"],\"default\":null},{\"name\":\"source\",\"type\":{\"type\":\"record\",\"name\":\"Source\",\"namespace\":\"io.debezium.connector.postgresql\",\"fields\":[{\"name\":\"version\",\"type\":\"string\"},{\"name\":\"connector\",\"type\":\"string\"},{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"ts_ms\",\"type\":\"long\"},{\"name\":\"snapshot\",\"type\":[{\"type\":\"string\",\"connect.version\":1,\"connect.parameters\":{\"allowed\":\"true,first,first_in_data_collection,last_in_data_collection,last,false,incremental\"},\"connect.default\":\"false\",\"connect.name\":\"io.debezium.data.Enum\"},\"null\"],\"default\":\"false\"},{\"name\":\"db\",\"type\":\"string\"},{\"name\":\"sequence\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"ts_us\",\"type\":[\"null\",\"long\"],\"default\":null},{\"name\":\"ts_ns\",\"type\":[\"null\",\"long\"],\"default\":null},{\"name\":\"schema\",\"type\":\"string\"},{\"name\":\"table\",\"type\":\"string\"},{\"name\":\"txId\",\"type\":[\"null\",\"long\"],\"default\":null},{\"name\":\"lsn\",\"type\":[\"null\",\"long\"],\"default\":null},{\"name\":\"xmin\",\"type\":[\"null\",\"long\"],\"default\":null}],\"connect.name\":\"io.debezium.connector.postgresql.Source\"}},{\"name\":\"transaction\",\"type\":[\"null\",{\"type\":\"record\",\"name\":\"block\",\"namespace\":\"event\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"total_order\",\"type\":\"long\"},{\"name\":\"data_collection_order\",\"type\":\"long\"}],\"connect.version\":1,\"connect.name\":\"event.block\"}],\"default\":null},{\"name\":\"op\",\"type\":\"string\"},{\"name\":\"ts_ms\",\"type\":[\"null\",\"long\"],\"default\":null},{\"name\":\"ts_us\",\"type\":[\"null\",\"long\"],\"default\":null},{\"name\":\"ts_ns\",\"type\":[\"null\",\"long\"],\"default\":null}],\"connect.version\":2,\"connect.name\":\"chinook.public.employee.Envelope\"}",
                envelopeRegAvroSchema.canonicalString()
        );
        assertEquals("chinook.public.employee.Envelope", envelopeRegAvroSchema.name());
    }

    @Test
    void shouldThrowIfChecksumDoesNotMatch() {
        final IOException expectedException = assertThrows(IOException.class, () -> {
            TableAvroDescriptor.loadFrom(TestHelper.RESOURCES.resolve("schema_employee_invalid_checksum"), "chinook.public.employee");
        });

        assertTrue(expectedException
                .getMessage()
                .matches("Input Checksum '(.*)/schema_employee_invalid_checksum/chinook\\.public\\.employee\\.sha256' ((.*)) does not match 'chinook.public.employee' computed checksum ((.*))"));
    }
}
