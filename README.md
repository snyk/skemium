# Skemium

Generate and Compare Avro Schema _directly_ from your Database.

## Features

* [ ] Configure log verbosity via (repeated) `-v` option
* [ ] Support different Database kinds (MySQL, MariaDB, MongoDB, Oracle, SQL Server, ...)
* [ ] Support optional `compare` options:
  * [ ] Ignore tables present only in _new_ schema (i.e. created tables)
  * [ ] Ignore tables present only in _old_ schema (i.e. deleted tables)
* [ ] Support optional `generate` options
  * [ ] Exclude table columns from generated schema
  * [ ] Store metadata in local `.skemium.meta.json` file
* [ ] Support connecting to GCP CloudSQL databases via
      [dedicated `SocketFactory`](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory)

## Development

### Build & Test

```shell
$ mvn clean package
```

### Package into a single (fat) JAR:

```shell
$ mvn clean package assembly:single -DskipTests
```

## Credits

As any open source tool, this builds on the shoulders of the great work of others (see the [pom.xml](./pom.xml)).

But I want to especially thank 2 projects for the _core_ of the functionality:
  * [Debezium](https://github.com/debezium/debezium), providing logic to extract database table schemas
  * [Confluent Schema Registry](https://github.com/confluentinc/schema-registry),
    providing logic to convert to/from Avro Schemas

## License

[Apache 2.0](./LICENSE)
