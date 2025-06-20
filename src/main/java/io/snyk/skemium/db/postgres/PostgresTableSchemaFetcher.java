package io.snyk.skemium.db.postgres;

import io.debezium.config.Configuration;
import io.debezium.connector.postgresql.PostgresConnectorConfig;
import io.debezium.connector.postgresql.PostgresValueConverter;
import io.debezium.connector.postgresql.TypeRegistry;
import io.debezium.connector.postgresql.connection.PostgresConnection;
import io.debezium.connector.postgresql.connection.PostgresDefaultValueConverter;
import io.debezium.relational.TableId;
import io.debezium.relational.TableSchema;
import io.debezium.relational.Tables;
import io.snyk.skemium.db.CatalogSchemaAndTableTopicNamingStrategy;
import io.snyk.skemium.db.TableSchemaFetcher;
import org.postgresql.jdbc.TimestampUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.debezium.relational.RelationalDatabaseConnectorConfig.COLUMN_EXCLUDE_LIST;

/**
 * The {@link TableSchemaFetcher} for PostgreSQL.
 * <p>
 * {@see https://www.postgresql.org/}
 */
public class PostgresTableSchemaFetcher implements TableSchemaFetcher {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresTableSchemaFetcher.class);

    private static final String CONNECTION_USAGE = "skemium-" + PostgresTableSchemaFetcher.class.getName();

    private final Configuration configuration;
    private final PostgresConnection connection;
    private final PostgresValueConverter valueConverter;
    private final PostgresDefaultValueConverter defaultValueConverter;

    private final Set<String> postgresBuiltInSchemas = Set.of(
            "information_schema", // See: https://www.postgresql.org/docs/current/information-schema.html
            "pg_catalog"          // See: https://www.postgresql.org/docs/current/ddl-schemas.html#DDL-SCHEMAS-CATALOG
    );

    public PostgresTableSchemaFetcher(final Configuration config) throws RuntimeException {
        this.configuration = config;

        LOG.trace("Creating PostgresConnector-like configuration");
        final PostgresConnectorConfig connectorConfig = new PostgresConnectorConfig(configuration);

        LOG.trace("Determining database type registry, charset and more");
        final TypeRegistry dbTypeRegistry;
        final Charset dbCharset;
        final TimestampUtils dbTimestampUtils;
        try (final PostgresConnection tmpDbConn = new PostgresConnection(connectorConfig.getJdbcConfig(), CONNECTION_USAGE)) {
            dbTypeRegistry = new TypeRegistry(tmpDbConn);
            dbCharset = tmpDbConn.getDatabaseCharset();
            dbTimestampUtils = tmpDbConn.getTimestampUtils();
        } catch (final Exception e) {
            LOG.error("Failed to establish first database connection", e);
            throw new RuntimeException(e);
        }

        LOG.trace("Setting up value converters");
        final PostgresConnection.PostgresValueConverterBuilder psqlValueConverterBuilder = (typeRegistry) -> PostgresValueConverter.of(
                connectorConfig,
                dbCharset,
                dbTypeRegistry);
        valueConverter = psqlValueConverterBuilder.build(dbTypeRegistry);
        defaultValueConverter = new PostgresDefaultValueConverter(valueConverter, dbTimestampUtils, dbTypeRegistry);

        LOG.trace("Setting up database connection");
        connection = new PostgresConnection(connectorConfig.getJdbcConfig(), psqlValueConverterBuilder, CONNECTION_USAGE);
    }

    @Override
    public List<TableSchema> fetch(final String database,
                                   @Nullable final Set<String> includedSchemas,
                                   @Nullable final Set<String> includedTables,
                                   @Nullable final Set<String> excludedColumns) throws Exception {
        final List<TableSchema> result = new ArrayList<>();

        // IMPORTANT: At this stage we only filter by `includedSchemas` and `includedTables`.
        // Filtering out `excludedColumns` happens later,
        // by injecting configuration (i.e. `COLUMN_EXCLUDE_LIST`).

        LOG.trace("Fetching Schemas");
        final AtomicInteger totalSchemas = new AtomicInteger(0);
        final Set<String> selectedSchemas = connection.readAllSchemaNames((s) -> {
            // Always exclude built-in schemas
            if (postgresBuiltInSchemas.contains(s)) {
                return false;
            }

            totalSchemas.getAndIncrement();
            if (includedSchemas != null && !includedSchemas.isEmpty()) {
                return includedSchemas.contains(s);
            }
            return true;
        });
        LOG.debug("Selected {} Schemas (out of {}): ", selectedSchemas.size(), totalSchemas.get());
        selectedSchemas.forEach(s -> LOG.trace("  {}", s));

        LOG.trace("Fetching Tables");
        final List<TableId> allSelectedTables = new ArrayList<>();
        for (final String selectedSchema : selectedSchemas) {
            LOG.trace("Fetching Tables from Schema: {}", selectedSchema);

            final AtomicInteger totalTablesForSchema = new AtomicInteger(0);
            final Tables selectedTables = new Tables();
            connection.readSchema(
                    selectedTables,
                    database,
                    selectedSchema,
                    Tables.TableFilter.fromPredicate((t) -> {
                        totalTablesForSchema.getAndIncrement();
                        if (includedTables != null && !includedTables.isEmpty()) {
                            return includedTables.contains(t.table()) ||
                                    includedTables.contains("%s.%s".formatted(t.schema(), t.table()));
                        }
                        return true;
                    }),
                    null, //< No Column filtering during this step
                    true
            );
            LOG.debug("Selected {} Tables in Schema {} (out of {})", selectedTables.size(), selectedSchema, totalTablesForSchema.get());
            selectedTables.tableIds().forEach(t -> LOG.trace("  {}", t.identifier()));
            allSelectedTables.addAll(selectedTables.tableIds());
        }
        LOG.debug("Selected {} Tables in total: ", allSelectedTables.size());

        // Filter-out Columns, if requested
        final PostgresConnectorConfig connectorConfig = excludedColumns != null
                ? new PostgresConnectorConfig(configuration.edit().with(COLUMN_EXCLUDE_LIST, String.join(",", excludedColumns)).build())
                : new PostgresConnectorConfig(configuration);

        try (final PostgresSchemaRefreshable postgresSchema = new PostgresSchemaRefreshable(
                connectorConfig,
                defaultValueConverter,
                CatalogSchemaAndTableTopicNamingStrategy.create(connectorConfig),
                valueConverter)) {
            postgresSchema.refresh(connection, true);

            for (final TableId tId : allSelectedTables) {
                result.add(postgresSchema.schemaFor(tId));
            }
        } catch (final Exception e) {
            throw new Exception("Failed to load Postgres Schema", e);
        }

        return result;
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (final Exception e) {
            LOG.error("Error closing connection to database", e);
        }
    }
}
