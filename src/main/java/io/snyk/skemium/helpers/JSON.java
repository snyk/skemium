package io.snyk.skemium.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;

/**
 * Helper to interact with JSON.
 */
public class JSON {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static String pretty(final String jsonStr) throws JsonProcessingException {
        return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(toJsonNode(jsonStr));
    }

    public static JsonNode toJsonNode(final String jsonStr) throws JsonProcessingException {
        return objectMapper.readValue(jsonStr, JsonNode.class);
    }

    public static <T> T from(final File source, final Class<T> clazz) throws IOException {
        return objectMapper.readValue(source, clazz);
    }

    public static String pretty(final Object jsonObj) throws JsonProcessingException {
        return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(jsonObj);
    }

}
