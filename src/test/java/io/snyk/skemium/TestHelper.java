package io.snyk.skemium;

import io.debezium.config.Configuration;
import io.debezium.relational.RelationalDatabaseConnectorConfig;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Path;

public class TestHelper {
    public static final String POSTGRES_IMG = "postgres";
    public static final String POSTGRES_VER = "17.2";
    public static final String POSTGRES_IMGVER = POSTGRES_IMG + ":" + POSTGRES_VER;

    public static final int POSTGRES_DEFAULT_PORT = 5432;

    public static final String INITDB_SCRIPT = "db_schema/chinook.initdb.sql";
    public static final String DB_NAME = "chinook";
    public static final String DB_USER = "chinook-db-user";
    public static final String DB_PASS = "chinook-db-pass";
    public static final long DB_SHARED_MEMORY = (128 * 1024 * 1024);

    public static final Path RESOURCES = Path.of("src", "test", "resources");

    /**
     * @return A {@link PostgreSQLContainer} already initialized with our test fixture DB.
     */
    public static PostgreSQLContainer<?> initPostgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMGVER)
                .withSharedMemorySize(DB_SHARED_MEMORY)
                .withInitScript(INITDB_SCRIPT)
                .withDatabaseName(DB_NAME)
                .withUsername(DB_USER)
                .withPassword(DB_PASS)
                .withCommand(
                        "-c", "wal_level=logical",
                        "-c", "max_replication_slots=10",
                        "-c", "max_wal_senders=10"
                );
    }

    /**
     * @param container
     * @return A {@link Configuration} useful to connect to the given {@link PostgreSQLContainer}.
     */
    public static Configuration createPostgresContainerConfiguration(final PostgreSQLContainer<?> container) {
        return Configuration.create()
                .with(RelationalDatabaseConnectorConfig.HOSTNAME, container.getHost())
                .with(RelationalDatabaseConnectorConfig.PORT, container.getMappedPort(TestHelper.POSTGRES_DEFAULT_PORT).toString())
                .with(RelationalDatabaseConnectorConfig.USER, DB_USER)
                .with(RelationalDatabaseConnectorConfig.PASSWORD, DB_PASS)
                .with(RelationalDatabaseConnectorConfig.DATABASE_NAME, DB_NAME)
                .with(RelationalDatabaseConnectorConfig.TOPIC_PREFIX, "test-topic-prefix")
                .build();
    }
}
