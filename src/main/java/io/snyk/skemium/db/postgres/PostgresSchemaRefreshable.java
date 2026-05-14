package io.snyk.skemium.db.postgres;

import io.debezium.connector.common.CdcSourceTaskContext;
import io.debezium.connector.postgresql.PostgresConnectorConfig;
import io.debezium.connector.postgresql.PostgresSchema;
import io.debezium.connector.postgresql.PostgresValueConverter;
import io.debezium.connector.postgresql.connection.PostgresConnection;
import io.debezium.connector.postgresql.connection.PostgresDefaultValueConverter;
import io.debezium.relational.CustomConverterRegistry;
import io.debezium.relational.TableId;
import io.debezium.spi.topic.TopicNamingStrategy;

import java.sql.SQLException;

/**
 * Exposes a minimum package-local version of {@link PostgresSchema} that can be refreshed.
 * The class interface is hidden inside Debezium Postgres Connector library,
 * but here we need it to load tables' schemas.
 */
class PostgresSchemaRefreshable extends PostgresSchema {
    PostgresSchemaRefreshable(CdcSourceTaskContext<PostgresConnectorConfig> taskContext,
                              PostgresDefaultValueConverter defaultValueConverter,
                              TopicNamingStrategy<TableId> topicNamingStrategy,
                              PostgresValueConverter valueConverter,
                              CustomConverterRegistry customConverterRegistry) {
        super(taskContext, defaultValueConverter, topicNamingStrategy, valueConverter, customConverterRegistry);
    }

    @Override
    public PostgresSchema refresh(final PostgresConnection connection, final boolean printReplicaIdentityInfo) throws SQLException {
        return super.refresh(connection, printReplicaIdentityInfo);
    }
}
