[![CI (GitHub Actions)](https://github.com/snyk/skemium/actions/workflows/ci.yaml/badge.svg)](https://github.com/snyk/skemium/actions/workflows/ci.yaml)
[![Release Uber-JAR](https://github.com/snyk/skemium/actions/workflows/release-uberjar.yaml/badge.svg)](https://github.com/snyk/skemium/actions/workflows/release-uberjar.yaml)
[![Release Binaries](https://github.com/snyk/skemium/actions/workflows/release-binaries.yaml/badge.svg)](https://github.com/snyk/skemium/actions/workflows/release-binaries.yaml)

# Skemium

Generate and Compare Avro Schema _directly_ from your Database.

## Usage

<details>
<summary>`skemium help generate` </summary>

```
Generates Avro Schema from Tables in a Database

skemium generate -d=<dbName> -h=<hostname> [--kind=<kind>] -p=<port> --password=<password> -u=<username> [-s=<dbSchemas>[,<dbSchemas>...]]...
                 [-t=<dbTables>[,<dbTables>...]]... [-x=<dbExcludedColumns>[,<dbExcludedColumns>...]]... [DIRECTORY_PATH]

Description:

Connects to Database, finds schemas and tables,
converts table schemas to Avro Schemas, stores them in a directory.

Parameters:
      [DIRECTORY_PATH]        Output directory
                                Default: skemium-20250513-171052

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
  -x, --exclude-column=<dbExcludedColumns>[,<dbExcludedColumns>...]
                              Database table column(s) to exclude (fmt: DB_SCHEMA.DB_TABLE.DB_COLUMN - env: DB_EXCLUDED_COLUMN - optional)
```
</details>

## TODOs

* Configure log verbosity via (repeated) `-v` option
* Implement `compare` command
* Support optional `compare` options:
  * Ignore tables present only in _new_ schema (i.e. created tables)
  * Ignore tables present only in _old_ schema (i.e. deleted tables)

## Future features

* [ ] Support for additional Databases (MySQL, MariaDB, MongoDB, Oracle, SQL Server, ...): currently only PostgreSQL is supported
* [ ] Support connecting to GCP CloudSQL databases via
  [dedicated `SocketFactory`](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory)

## Development

### Build & Test

```shell
$ mvn clean package
```

### Package Uber-JAR

```shell
$ mvn clean package assembly:single -DskipTests
```

### Package Native Binary

```shell
$ mvn clean package native:compile-no-fork -DskipTests
```

## Credits

As any open source tool, this builds on the shoulders of the great work of others (see the [pom.xml](./pom.xml)).

But I want to especially thank 2 projects for the _core_ of the functionality:
  * [Debezium](https://github.com/debezium/debezium), providing logic to extract database table schemas
  * [Confluent Schema Registry](https://github.com/confluentinc/schema-registry),
    providing logic to convert to/from Avro Schemas

## License

[Apache 2.0](./LICENSE)

---

**Made with 💜 by Snyk**
