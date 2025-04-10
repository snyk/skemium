package io.snyk.skemium.db;

import io.debezium.config.Configuration;
import io.snyk.skemium.db.postgres.PostgresTableSchemaFetcher;

/**
 * Kind of Databases supported.
 */
public enum DatabaseKind {
    /**
     * PostgreSQL.
     * {@see https://www.postgresql.org/}
     */
    POSTGRES;

    /**
     * Creates the {@link TableSchemaFetcher} for the given {@link DatabaseKind}.
     *
     * @param config Debezium Relational database {@link Configuration}.
     * @return Corresponding {@link TableSchemaFetcher}.
     */
    public TableSchemaFetcher fetcher(final Configuration config) {
        return switch (this) {
            case POSTGRES -> new PostgresTableSchemaFetcher(config);
        };
    }
}
