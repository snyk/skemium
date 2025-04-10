package io.snyk.skemium.db.postgres;

import io.debezium.config.Configuration;
import io.debezium.relational.TableSchema;
import io.snyk.skemium.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresTableSchemaFetcherTest {
    static PostgreSQLContainer<?> POSTGRES_CONTAINER = TestHelper.initPostgresContainer();

    @BeforeAll
    static void startDB() {
        POSTGRES_CONTAINER.start();
    }

    @AfterAll
    static void stopDB() {
        POSTGRES_CONTAINER.stop();
    }

    @Test
    void shouldFetchAllTableSchemas() throws Exception {
        final Configuration config = TestHelper.createPostgresContainerConfiguration(POSTGRES_CONTAINER);

        try (final PostgresTableSchemaFetcher fetcher = new PostgresTableSchemaFetcher(config)) {
            final List<TableSchema> tableSchemas = fetcher.fetch(TestHelper.DB_NAME, null, null);

            List<String> expectedTableSchemaIds = List.of(
                    "chinook.public.media_type",
                    "chinook.public.customer",
                    "chinook.public.genre",
                    "chinook.public.track",
                    "chinook.public.playlist",
                    "chinook.public.invoice_line",
                    "chinook.public.playlist_track",
                    "chinook.public.invoice",
                    "chinook.public.employee",
                    "chinook.public.artist",
                    "chinook.public.album");

            assertEquals(expectedTableSchemaIds.size(), tableSchemas.size());
            final Map<String, TableSchema> tableSchemasMap = tableSchemas.stream()
                    .collect(Collectors.toMap(t -> t.id().toString(), Function.identity()));
            assertTrue(tableSchemasMap.keySet().containsAll(expectedTableSchemaIds));
        }
    }

    @Test
    void shouldFetchSomeTableSchemas() throws Exception {
        final Configuration config = TestHelper.createPostgresContainerConfiguration(POSTGRES_CONTAINER);

        try (final PostgresTableSchemaFetcher fetcher = new PostgresTableSchemaFetcher(config)) {
            final List<TableSchema> tableSchemas = fetcher.fetch(TestHelper.DB_NAME, Set.of("public"), Set.of("customer", "invoice"));

            List<String> expectedTableSchemaIds = List.of(
                    "chinook.public.customer",
                    "chinook.public.invoice");

            assertEquals(expectedTableSchemaIds.size(), tableSchemas.size());
            final Map<String, TableSchema> tableSchemasMap = tableSchemas.stream()
                    .collect(Collectors.toMap(t -> t.id().toString(), Function.identity()));
            assertTrue(tableSchemasMap.keySet().containsAll(expectedTableSchemaIds));
        }
    }
}
