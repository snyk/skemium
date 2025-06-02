package io.snyk.skemium;

import io.debezium.config.Configuration;
import io.debezium.relational.RelationalDatabaseConnectorConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/// Base class for tests that require a PostgreSQL database.
///
/// This class starts a [PostgreSQLContainer] (using [BeforeAll] and [AfterAll])
/// with a test fixture DB and stops it after the tests are done.
/// It also provides a [PostgreSQLContainer] instance and a [Configuration] useful to connect to the DB.
///
/// @see [PostgreSQLContainer]
public abstract class WithPostgresContainer {
    protected static PostgreSQLContainer<?> POSTGRES_CONTAINER = initPostgresContainer();

    @BeforeAll
    static void startDB() {
        POSTGRES_CONTAINER.start();
    }

    @AfterAll
    static void stopDB() {
        POSTGRES_CONTAINER.stop();
    }

    protected static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(),
                POSTGRES_CONTAINER.getUsername(),
                POSTGRES_CONTAINER.getPassword()
        );
    }

    protected static final String POSTGRES_IMG = "postgres";
    protected static final String POSTGRES_VER = "17.2";
    protected static final String POSTGRES_IMGVER = POSTGRES_IMG + ":" + POSTGRES_VER;

    protected static final int POSTGRES_DEFAULT_PORT = 5432;

    protected static final String INITDB_SCRIPT = "db_schema/chinook.initdb.sql";
    protected static final String DB_NAME = "chinook";
    protected static final String DB_USER = "chinook-db-user";
    protected static final String DB_PASS = "chinook-db-pass";
    protected static final long DB_SHARED_MEMORY = (128 * 1024 * 1024);

    /// @return A [PostgreSQLContainer] already initialized with our test fixture DB.
    static PostgreSQLContainer<?> initPostgresContainer() {
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

    /// @param container [PostgreSQLContainer] to connect to.}
    /// @return A [Configuration] useful to connect to the given [PostgreSQLContainer].
    protected static Configuration createPostgresContainerConfiguration(final PostgreSQLContainer<?> container) {
        return Configuration.create()
                .with(RelationalDatabaseConnectorConfig.HOSTNAME, container.getHost())
                .with(RelationalDatabaseConnectorConfig.PORT, container.getMappedPort(POSTGRES_DEFAULT_PORT).toString())
                .with(RelationalDatabaseConnectorConfig.USER, DB_USER)
                .with(RelationalDatabaseConnectorConfig.PASSWORD, DB_PASS)
                .with(RelationalDatabaseConnectorConfig.DATABASE_NAME, DB_NAME)
                .with(RelationalDatabaseConnectorConfig.TOPIC_PREFIX, "test-topic-prefix")
                .build();
    }
}
