package io.snyk.skemium.helpers;

import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.avro.TableAvroSchemas;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchemaRegistryTest {

    @Test
    void shouldDetectBackwardIncompatibleSchemaChanges() throws IOException {
        final Path dirPath = Path.of("src", "test", "resources", "schema_change-non_backward_compatible");
        final TableAvroSchemas curr = TableAvroSchemas.loadFrom(dirPath.resolve("current"), "chinook.public.artist");
        final TableAvroSchemas next = TableAvroSchemas.loadFrom(dirPath.resolve("next"), "chinook.public.artist");

        final SchemaRegistry.CompatibilityResult res = SchemaRegistry.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD);
        assertFalse(res.isCompatible());
        assertTrue(res.isKeyCompatible());
        assertFalse(res.isValueCompatible());
        assertFalse(res.isEnvelopeCompatible());

        assertEquals(2, res.valueResults().size());
        assertEquals(3, res.envelopeResults().size());
        final String resVal = res.valueResults().getFirst();

        assertTrue(resVal.contains("errorType:'TYPE_MISMATCH'"));
        assertTrue(resVal.contains("description:'The type (path '/fields/1/type/0') of a field in the new schema does not match with the old schema'"));
        assertTrue(resVal.contains("additionalInfo:'reader type: STRING not compatible with writer type: NULL'"));

        final String envVal = res.envelopeResults().getFirst();
        assertTrue(envVal.contains("errorType:'MISSING_UNION_BRANCH'"));
        assertTrue(envVal.contains("additionalInfo:'reader union lacking writer type: RECORD"));
    }

    @Test
    void shouldDetectBackwardCompatibleSchemaChanges() throws IOException {
        final Path dirPath = Path.of("src", "test", "resources", "schema_change-backward_compatible");
        final TableAvroSchemas curr = TableAvroSchemas.loadFrom(dirPath.resolve("current"), "chinook.public.artist");
        final TableAvroSchemas next = TableAvroSchemas.loadFrom(dirPath.resolve("next"), "chinook.public.artist");

        SchemaRegistry.CompatibilityResult res = SchemaRegistry.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD);
        assertTrue(res.isCompatible());
        res = SchemaRegistry.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD_TRANSITIVE);
        assertTrue(res.isCompatible());

        res = SchemaRegistry.checkCompatibility(curr, next, CompatibilityLevel.FORWARD);
        assertFalse(res.isCompatible());
        assertTrue(res.isKeyCompatible());
        assertFalse(res.isValueCompatible());
        assertFalse(res.isEnvelopeCompatible());
        res = SchemaRegistry.checkCompatibility(curr, next, CompatibilityLevel.FORWARD_TRANSITIVE);
        assertFalse(res.isCompatible());
        assertTrue(res.isKeyCompatible());
        assertFalse(res.isValueCompatible());
        assertFalse(res.isEnvelopeCompatible());

        res = SchemaRegistry.checkCompatibility(curr, next, CompatibilityLevel.FULL);
        assertFalse(res.isCompatible());
        assertTrue(res.isKeyCompatible());
        assertFalse(res.isValueCompatible());
        assertFalse(res.isEnvelopeCompatible());
        res = SchemaRegistry.checkCompatibility(curr, next, CompatibilityLevel.FULL_TRANSITIVE);
        assertFalse(res.isCompatible());
        assertTrue(res.isKeyCompatible());
        assertFalse(res.isValueCompatible());
        assertFalse(res.isEnvelopeCompatible());
    }

    @Test
    void shouldDetectKeyAdded() throws IOException {
        final Path dirPath = Path.of("src", "test", "resources", "schema_change-key_added");
        final TableAvroSchemas curr = TableAvroSchemas.loadFrom(dirPath.resolve("current"), "chinook.public.playlist_track");
        final TableAvroSchemas next = TableAvroSchemas.loadFrom(dirPath.resolve("next"), "chinook.public.playlist_track");

        final SchemaRegistry.CompatibilityResult res = SchemaRegistry.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD);
        assertFalse(res.isCompatible());
        assertFalse(res.isKeyCompatible());
        assertTrue(res.keyResults().getFirst().matches("Key Schema for 'chinook\\.public\\.playlist_track' changed from NULL to NOT NULL \\((.*)\\)"));
        assertTrue(res.isValueCompatible());
        assertTrue(res.isEnvelopeCompatible());
    }

    @Test
    void shouldDetectKeyRemoved() throws IOException {
        final Path dirPath = Path.of("src", "test", "resources", "schema_change-key_removed");
        final TableAvroSchemas curr = TableAvroSchemas.loadFrom(dirPath.resolve("current"), "chinook.public.playlist_track");
        final TableAvroSchemas next = TableAvroSchemas.loadFrom(dirPath.resolve("next"), "chinook.public.playlist_track");

        final SchemaRegistry.CompatibilityResult res = SchemaRegistry.checkCompatibility(curr, next, CompatibilityLevel.BACKWARD);
        assertFalse(res.isCompatible());
        assertFalse(res.isKeyCompatible());
        assertTrue(res.keyResults().getFirst().matches("Key Schema for 'chinook.public.playlist_track' changed from NOT NULL \\((.*)\\) to NULL"));
        assertTrue(res.isValueCompatible());
        assertTrue(res.isEnvelopeCompatible());
    }
}
