package io.snyk.skemium.db.postgres;

import io.debezium.config.Configuration;
import io.debezium.relational.TableSchema;
import io.snyk.skemium.WithPostgresContainer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PostgresTableSchemaFetcherTest extends WithPostgresContainer {

    @Test
    void shouldFetchAllTableSchemas() throws Exception {
        final Configuration config = createPostgresContainerConfiguration(POSTGRES_CONTAINER);

        try (final PostgresTableSchemaFetcher fetcher = new PostgresTableSchemaFetcher(config)) {
            final List<TableSchema> tableSchemas = fetcher.fetch(DB_NAME, null, null, null);

            List<String> expectedTableSchemaIds = List.of(
                    "public.album",
                    "public.artist",
                    "public.customer",
                    "public.employee",
                    "public.genre",
                    "public.invoice",
                    "public.invoice_line",
                    "public.media_type",
                    "public.playlist",
                    "public.playlist_track",
                    "public.playlist_track_no_pkey",
                    "public.track"
            );

            assertEquals(expectedTableSchemaIds.size(), tableSchemas.size());
            final Map<String, TableSchema> tableSchemasMap = tableSchemas.stream()
                    .collect(Collectors.toMap(t -> t.id().toString(), Function.identity()));
            assertTrue(tableSchemasMap.keySet().containsAll(expectedTableSchemaIds));
        }
    }

    @Test
    void shouldFetchSomeTableSchemas() throws Exception {
        final Configuration config = createPostgresContainerConfiguration(POSTGRES_CONTAINER);

        try (final PostgresTableSchemaFetcher fetcher = new PostgresTableSchemaFetcher(config)) {
            final List<TableSchema> tableSchemas = fetcher.fetch(DB_NAME, Set.of("public"), Set.of("customer", "invoice"), null);

            List<String> expectedTableSchemaIds = List.of(
                    "public.customer",
                    "public.invoice");

            assertEquals(expectedTableSchemaIds.size(), tableSchemas.size());
            final Map<String, TableSchema> tableSchemasMap = tableSchemas.stream()
                    .collect(Collectors.toMap(t -> t.id().toString(), Function.identity()));
            assertTrue(tableSchemasMap.keySet().containsAll(expectedTableSchemaIds));
        }
    }

    @Test
    void shouldExcludeSomeColumns() throws Exception {
        final Configuration config = createPostgresContainerConfiguration(POSTGRES_CONTAINER);

        try (final PostgresTableSchemaFetcher fetcher = new PostgresTableSchemaFetcher(config)) {
            final List<TableSchema> tableSchemas = fetcher.fetch(DB_NAME, null,
                    Set.of("customer", "album", "artist"),
                    Set.of(
                            "public.customer.first_name", "public.customer.last_name",
                            "public.album.artist_id"
                    ));

            assertEquals(3, tableSchemas.size());
            final Map<String, TableSchema> tableSchemasMap = tableSchemas.stream()
                    .collect(Collectors.toMap(t -> t.id().toString(), Function.identity()));

            final TableSchema customerTableSchema = tableSchemasMap.get("public.customer");
            final TableSchema albumTableSchema = tableSchemasMap.get("public.album");
            final TableSchema artistTableSchema = tableSchemasMap.get("public.artist");

            assertNull(customerTableSchema.valueSchema().field("first_name"));
            assertNull(customerTableSchema.valueSchema().field("last_name"));
            assertNull(albumTableSchema.valueSchema().field("artist_id"));
            assertNotNull(artistTableSchema.valueSchema().field("artist_id"));
        }
    }
}
