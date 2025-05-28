[![CI (GitHub Actions)](https://github.com/snyk/skemium/actions/workflows/ci.yaml/badge.svg)](https://github.com/snyk/skemium/actions/workflows/ci.yaml)
[![Release Uber-JAR](https://github.com/snyk/skemium/actions/workflows/release-uberjar.yaml/badge.svg)](https://github.com/snyk/skemium/actions/workflows/release-uberjar.yaml)
[![Release Binaries](https://github.com/snyk/skemium/actions/workflows/release-binaries.yaml/badge.svg)](https://github.com/snyk/skemium/actions/workflows/release-binaries.yaml)

# Skemium

Generate and Compare [Debezium] Chance Data Capture ([CDC]) [Avro] Schema.

Leveraging [Debezium] and [Schema Registry] own codebases, each Table of a Database is mapped to 3 components:

* _Key_ [Avro] schema: describes the `PRIMARY KEY` of the Table - `NULL` if not set
* _Value_ [Avro] schema: describes each Row of the Table
* _Envelope_ [Avro] schema: wrapper for the _Value_, used by Debezium to realize [CDC] when Producing to a Topic

When producing, [Debezium CDC Source Connector] uses the _Key_ and the _Envelope_ schemas when producing to a Topic.
**Skemium** leverages those schemas to compare between evolutions of the originating Database Schema,
and identifies compatibility issues executing the comparison logic implemented by [Schema Registry].

If you make changes to your Database Schema, and want to know if it's going to break your Debezium CDC production,
`skemium` is the tool for you.

# Usage

## `generate` command

The `generate` commands connects to a Database, reads its _Database Schema_ and coverts it to a [CDC] _Avro Schema_,
using [Debezium Avro Serialization].

The output is saved in a user given _output directory_. The directory will contain:

* For each Table, a set of files following the naming structure `DB_NAME.DB_SCHEMA.DB_TABLE.EXTENSION`
  * _Table Key_ schema file (`EXTENSION = .key.avsc`)
  * _Table Value_ schema file (`EXTENSION = .val.avsc`)
  * _Table Envelope_ schema file (`EXTENSION = .env.avsc`)
  * The checksum of all 3 schema files above (`EXTENSION = .sha256`)
* A metadata file named `.skemium.meta.json`

For example, if the database `example` contains 2 tables `user` and `address` in the database schema `public`, the output
directory will look like:

```
.skemium.meta.json
example.public.address.env.avsc
example.public.address.key.avsc
example.public.address.sha256
example.public.address.val.avsc
example.public.user.env.avsc
example.public.user.key.avsc
example.public.user.sha256
example.public.user.val.avsc
```

### Help

<details>
<summary>Run `skemium help generate` for usage instructions</summary>

```shell
$ skemium help generate

Generates Avro Schema from Tables in a Database

skemium generate [-v] -d=<dbName> -h=<hostname> [--kind=<kind>] -p=<port> --password=<password> -u=<username> [-s=<dbSchemas>[,
                 <dbSchemas>...]]... [-t=<dbTables>[,<dbTables>...]]... [-x=<dbExcludedColumns>[,<dbExcludedColumns>...]]... [DIRECTORY_PATH]

Description:

Connects to Database, finds schemas and tables,
converts table schemas to Avro Schemas, stores them in a directory.

Parameters:
      [DIRECTORY_PATH]        Output directory
                                Default: skemium-20250528-112610

Options:
  -d, --database=<dbName>     Database name (env: DB_NAME)
  -h, --hostname=<hostname>   Database hostname (env: DB_HOSTNAME)
      --kind=<kind>           Database kind (values: POSTGRES - env: DB_KIND - optional)
                                Default: POSTGRES
  -p, --port=<port>           Database port (env: DB_PORT)
      --password=<password>   Database password (env: DB_PASSWORD)
  -s, --schema=<dbSchemas>[,<dbSchemas>...]
                              Database schema(s); all if omitted (env: DB_SCHEMA - optional)
  -t, --table=<dbTables>[,<dbTables>...]
                              Database table(s); all if omitted (env: DB_TABLE - optional)
  -u, --username=<username>   Database username (env: DB_USERNAME)
  -v, --verbose               Logging Verbosity - use multiple -v to increase (default: ERROR)
  -x, --exclude-column=<dbExcludedColumns>[,<dbExcludedColumns>...]
                              Database table column(s) to exclude (fmt: DB_SCHEMA.DB_TABLE.DB_COLUMN - env: DB_EXCLUDED_COLUMN - optional)
```
</details>


## Logging verbosity

The option `-v | --verbose` controls the logging verbosity. By default, the logging level is `ERROR`.
But it can be increased by passing one or more `-v` options, to a maximum level of `TRACE`. The mapping is:

```
<none>    -> ERROR
-v        -> WARN
-vv       -> INFO
-vvv      -> DEBUG
-vvvv     -> TRACE
-vvvvv... -> TRACE
```

# TODOs

* Implement `compare` command
* Support optional `compare` options:
  * Ignore tables present only in _new_ schema (i.e. created tables)
  * Ignore tables present only in _old_ schema (i.e. deleted tables)

# Future features

* [ ] Support for additional Databases (MySQL, MariaDB, MongoDB, Oracle, SQL Server, ...): currently only PostgreSQL is supported
* [ ] Support connecting to GCP CloudSQL databases via
  [dedicated `SocketFactory`](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory)
* [ ] Support custom key definition for a table, similar to what
  [`message.key.columns`](https://debezium.io/documentation/reference/stable/connectors/postgresql.html#postgresql-property-message-key-columns)
  allows when configuring Debezium. This is useful when an arbitrary key is desired or the table is missing a primary key.

# Development

## Build & Test

```shell
$ mvn clean package
```

## Package Uber-JAR

```shell
$ mvn clean package assembly:single -DskipTests
```

## Package Native Binary

```shell
$ mvn clean package native:compile-no-fork -DskipTests
```

# Credits

As any open source tool, this builds on the shoulders of the great work of others (see the [pom.xml](./pom.xml)).

But I want to especially thank 2 projects for the _core_ of the functionality:
  * [Debezium], providing logic to extract database table schemas
  * Confluent [Schema Registry], providing logic to convert to/from Avro Schemas

# License

[Apache 2.0](./LICENSE)

---

**Made with 💜 by Snyk**

[Debezium]: https://debezium.io/
[CDC]: https://en.wikipedia.org/wiki/Change_data_capture
[Avro]: https://avro.apache.org/
[Debezium CDC Source Connector]: https://debezium.io/documentation/reference/stable/connectors/index.html
[Schema Registry]: https://docs.confluent.io/platform/6.2/schema-registry/index.html
[Debezium Avro Serialization]: https://debezium.io/documentation/reference/stable/configuration/avro.html
