package io.snyk.skemium.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.snyk.skemium.helpers.Avro;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

class MetadataFileTest {

    @Test
    void refreshMetadataFileSchema() throws JsonProcessingException, FileNotFoundException {
        Avro.saveAvroSchemaForType(MetadataFile.class, MetadataFile.AVRO_SCHEMA_FILENAME);
    }
}
