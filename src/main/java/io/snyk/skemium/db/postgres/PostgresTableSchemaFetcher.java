package io.snyk.skemium.db.postgres;

import io.debezium.config.Configuration;
import io.debezium.connector.postgresql.PostgresConnectorConfig;
import io.debezium.connector.postgresql.PostgresValueConverter;
import io.debezium.connector.postgresql.TypeRegistry;
import io.debezium.connector.postgresql.connection.PostgresConnection;
import io.debezium.connector.postgresql.connection.PostgresDefaultValueConverter;
import io.debezium.relational.ColumnFilterMode;
import io.debezium.relational.TableId;
import io.debezium.relational.TableSchema;
import io.debezium.relational.Tables;
import io.debezium.schema.DefaultTopicNamingStrategy;
import io.debezium.spi.topic.TopicNamingStrategy;
import io.snyk.skemium.db.TableSchemaFetcher;
import org.postgresql.jdbc.TimestampUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The {@link TableSchemaFetcher} for PostgreSQL.
 * <p>
 * {@see https://www.postgresql.org/}
 */
public class PostgresTableSchemaFetcher implements TableSchemaFetcher {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresTableSchemaFetcher.class);

    private static final String CONNECTION_USAGE = "skemium-" + PostgresTableSchemaFetcher.class.getName();

    private final PostgresConnectorConfig connectorConfig;
    private final PostgresConnection connection;
    private final PostgresValueConverter valueConverter;
    private final PostgresDefaultValueConverter defaultValueConverter;

    private final Set<String> postgresBuiltInSchemas = Set.of(
            "information_schema", // See: https://www.postgresql.org/docs/current/information-schema.html
            "pg_catalog"          // See: https://www.postgresql.org/docs/current/ddl-schemas.html#DDL-SCHEMAS-CATALOG
    );

    public PostgresTableSchemaFetcher(final Configuration config) throws RuntimeException {
        LOG.trace("Creating PostgresConnector-like configuration");
        connectorConfig = new PostgresConnectorConfig(config);

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
                                   @Nullable final Set<String> includedTables) throws Exception {
        final List<TableSchema> result = new ArrayList<>();

        LOG.trace("Fetching Schemas");
        final Set<String> foundSchemas = connection.readAllSchemaNames((s) -> {
            // Always exclude built-in schemas
            if (postgresBuiltInSchemas.contains(s)) {
                return false;
            }

            if (includedSchemas != null && !includedSchemas.isEmpty()) {
                return includedSchemas.contains(s);
            }
            return true;
        });
        LOG.debug("Found {} Schemas: ", foundSchemas.size());
        foundSchemas.forEach(s -> LOG.trace("  {}", s));

        final List<TableId> allFoundTables = new ArrayList<>();
        for (final String foundSchema : foundSchemas) {
            LOG.trace("Fetching Tables from Schema: {}", foundSchema);

            final Tables foundTables = new Tables();
            connection.readSchema(
                    foundTables,
                    database,
                    foundSchema,
                    Tables.TableFilter.fromPredicate((t) -> {
                        if (includedTables != null && !includedTables.isEmpty()) {
                            return includedTables.contains(t.table());
                        }
                        return true;
                    }),
                    // TODO Add here Column Filter
                    Tables.ColumnNameFilterFactory.createExcludeListFilter("", ColumnFilterMode.SCHEMA),
                    true
            );
            LOG.debug("Found {} Tables in Schema {}", foundTables.size(), foundSchema);
            foundTables.tableIds().forEach(t -> LOG.trace("  {}", t.identifier()));
            allFoundTables.addAll(foundTables.tableIds());
        }
        LOG.debug("Found {} Tables in total: ", allFoundTables.size());

        try (final PostgresSchemaRefreshable postgresSchema = new PostgresSchemaRefreshable(
                connectorConfig,
                defaultValueConverter,
                (TopicNamingStrategy) DefaultTopicNamingStrategy.create(connectorConfig),
                valueConverter)) {
            postgresSchema.refresh(connection, true);

            for (final TableId tId : allFoundTables) {
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
