package io.snyk.skemium.db;

import io.debezium.relational.TableSchema;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Interface describing an {@link AutoCloseable} type that can fetch all/some {@link TableSchema} from a Database.
 */
public interface TableSchemaFetcher extends AutoCloseable {
    /**
     * Fetches all the {@link TableSchema} in the Database.
     * Optionally a filtering can be done, specifying specific Schemas and/or Tables.
     * <p>
     * TODO Add support to filter columns, similarly to schemas and tables.
     *
     * @param database        Database (Catalog) name
     * @param includedSchemas {@link Set} of Schemas to include; if {@code null} all schemas will be included
     * @param includedTables  {@link Set} of Tables to include; if {@code null} all tables will be included
     * @param excludedColumns {@link Set} of Columns to exclude;
     *                        each column has to be a fully qualified name (e.g. {@code SCHEMA.TABLE.COLUMN})
     * @return A {@link List} of {@link TableSchema} of all the tables found
     * @throws Exception Thrown if schemas/tables where indicated but were not found.
     */
    List<TableSchema> fetch(String database,
                            @Nullable Set<String> includedSchemas,
                            @Nullable Set<String> includedTables,
                            @Nullable Set<String> excludedColumns) throws Exception;
}
