package io.snyk.skemium.helpers;

import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.avro.AvroSchemaFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AvroTest {

    @Test
    void shouldDetectBackwardIncompatibleSchemaChanges() throws IOException {
        final AvroSchemaFile curr = AvroSchemaFile.loadFrom(Path.of("src", "test", "resources", "schema_change_non_backward_compatible"), "current");
        final AvroSchemaFile next = AvroSchemaFile.loadFrom(Path.of("src", "test", "resources", "schema_change_non_backward_compatible"), "next");

        final List<String> compatibility = Avro.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD);
        assertEquals(2, compatibility.size());
        String s = compatibility.get(0);

        assertTrue(s.contains("errorType:'TYPE_MISMATCH'"));
        assertTrue(s.contains("description:'The type (path '/fields/1/type/0') of a field in the new schema does not match with the old schema'"));
        assertTrue(s.contains("additionalInfo:'reader type: STRING not compatible with writer type: NULL'"));
    }

    @Test
    void shouldDetectBackwardCompatibleSchemaChanges() throws IOException {
        final AvroSchemaFile curr = AvroSchemaFile.loadFrom(Path.of("src", "test", "resources", "schema_change_backward_compatible"), "current");
        final AvroSchemaFile next = AvroSchemaFile.loadFrom(Path.of("src", "test", "resources", "schema_change_backward_compatible"), "next");

        final List<String> compatibility = Avro.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD);
        assertEquals(0, compatibility.size());
    }
}
