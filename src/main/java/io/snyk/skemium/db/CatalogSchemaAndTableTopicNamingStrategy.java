package io.snyk.skemium.db;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.relational.TableId;
import io.debezium.schema.AbstractTopicNamingStrategy;
import io.debezium.spi.topic.TopicNamingStrategy;

import java.util.Properties;

/**
 * Implementation of {@link TopicNamingStrategy} that uses the format {@code <catalog>.<schema>.<table>}.
 */
public class CatalogSchemaAndTableTopicNamingStrategy extends AbstractTopicNamingStrategy<TableId> {
    public CatalogSchemaAndTableTopicNamingStrategy(final Properties props) {
        super(props);
    }

    public CatalogSchemaAndTableTopicNamingStrategy(final Properties props, final boolean multiPartitionMode) {
        this(props);
        this.multiPartitionMode = multiPartitionMode;
    }

    public static CatalogSchemaAndTableTopicNamingStrategy create(CommonConnectorConfig config) {
        return new CatalogSchemaAndTableTopicNamingStrategy(config.getConfig().asProperties(), false);
    }

    public static CatalogSchemaAndTableTopicNamingStrategy create(CommonConnectorConfig config, boolean multiPartitionMode) {
        return new CatalogSchemaAndTableTopicNamingStrategy(config.getConfig().asProperties(), multiPartitionMode);
    }

    @Override
    public String dataChangeTopic(final TableId id) {
        return String.format("%s.%s.%s", id.catalog(), id.schema(), id.table());
    }
}
