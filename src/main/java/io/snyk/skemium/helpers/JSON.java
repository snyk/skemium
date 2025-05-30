package io.snyk.skemium.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;

/// Helper to interact with JSON.
public class JSON {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static String pretty(final String jsonStr) throws JsonProcessingException {
        return pretty(toJsonNode(jsonStr));
    }

    public static JsonNode toJsonNode(final String jsonStr) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(jsonStr, JsonNode.class);
    }

    public static <T> T from(final File source, final Class<T> clazz) throws IOException {
        return OBJECT_MAPPER.readValue(source, clazz);
    }

    public static String pretty(final Object jsonObj) throws JsonProcessingException {
        return OBJECT_MAPPER
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(jsonObj);
    }

    public static String compact(final Object jsonObj) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(jsonObj);
    }

    public static String compact(final String jsonStr) throws JsonProcessingException {
        return compact(toJsonNode(jsonStr));
    }

}
