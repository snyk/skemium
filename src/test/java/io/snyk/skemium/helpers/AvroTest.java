package io.snyk.skemium.helpers;

import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.avro.TableAvroDescriptor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroTest {

    @Test
    void shouldDetectBackwardIncompatibleSchemaChanges() throws IOException {
        final Path dirPath = Path.of("src", "test", "resources", "schema_change_non_backward_compatible");
        final TableAvroDescriptor curr = TableAvroDescriptor.loadFrom(dirPath.resolve("current"), "chinook.public.artist");
        final TableAvroDescriptor next = TableAvroDescriptor.loadFrom(dirPath.resolve("next"), "chinook.public.artist");

        final List<String> compatibility = Avro.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD);
        assertEquals(2, compatibility.size());
        String s = compatibility.get(0);

        assertTrue(s.contains("errorType:'TYPE_MISMATCH'"));
        assertTrue(s.contains("description:'The type (path '/fields/1/type/0') of a field in the new schema does not match with the old schema'"));
        assertTrue(s.contains("additionalInfo:'reader type: STRING not compatible with writer type: NULL'"));
    }

    @Test
    void shouldDetectBackwardCompatibleSchemaChanges() throws IOException {
        final Path dirPath = Path.of("src", "test", "resources", "schema_change_backward_compatible");
        final TableAvroDescriptor curr = TableAvroDescriptor.loadFrom(dirPath.resolve("current"), "chinook.public.artist");
        final TableAvroDescriptor next = TableAvroDescriptor.loadFrom(dirPath.resolve("next"), "chinook.public.artist");

        assertEquals(0, Avro.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD).size());
        assertEquals(0, Avro.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD_TRANSITIVE).size());

        assertEquals(2, Avro.checkCompatibility(curr, next, CompatibilityLevel.FORWARD).size());
        assertEquals(2, Avro.checkCompatibility(curr, next, CompatibilityLevel.FORWARD_TRANSITIVE).size());

        assertEquals(1, Avro.checkCompatibility(curr, next, CompatibilityLevel.FULL).size());
        assertEquals(1, Avro.checkCompatibility(curr, next, CompatibilityLevel.FULL_TRANSITIVE).size());
    }
}
