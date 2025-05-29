package io.snyk.skemium.db;

import io.debezium.relational.TableSchema;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/// Interface describing a [AutoCloseable] type that can fetch all/some [TableSchema] from a Database.
public interface TableSchemaFetcher extends AutoCloseable {
    /// Fetches all the [TableSchema] in the Database.
    /// Optionally, filtering can be applied:
    ///
    ///   * Only include specific Database Schemas
    ///   * Only include specific Database Tables
    ///   * Exclude specific Table Columns/
    ///
    /// @param database        Database (Catalog) name
    /// @param includedSchemas [Set] of Schemas to include; if `null` all schemas are included
    /// @param includedTables  [Set] of Tables to include; if `null` all tables are included
    /// @param excludedColumns [Set] of Columns to exclude;
    ///                        each column has to be a fully qualified name (e.g. `SCHEMA.TABLE.COLUMN`)
    /// @return A [List] of [TableSchema] of all the tables found
    /// @throws Exception Thrown if schemas/tables where indicated but were not found.
    List<TableSchema> fetch(String database,
                            @Nullable Set<String> includedSchemas,
                            @Nullable Set<String> includedTables,
                            @Nullable Set<String> excludedColumns) throws Exception;
}
