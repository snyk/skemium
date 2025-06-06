# GraalVM `native-image` Reachability Metadata

When compiling a native binary out of a Java project using `native-image`, [GraalVM] tries really hard
to omit from the final binary any unused class: it does it for performance and final binary size reasons.
Through static code analysis, it compiles a _graph_ of what code is _actually_ used by the binary.

The only issue with this static code analysis, is that it is done at build time, so it misses classes
that are dynamically loaded at runtime via `java.lang.Class.forName()`. Detailed documentation
can be found [here][Reachability Metadata].

Because of this we need to provide "hints" for the `native-image` compiler to spot classes and resources
that it wouldn't normally include.

So we provide under the path `src/main/resources/META-INF/native-image` 2 JSON files:

* `reflect-config.json`: Classes we want included and are not picked up automatically
* `resource-config.json`: Resources expected by libraries to live at `src/main/resources`

## How do we know what classes were picked up and which weren't?

This is tricky: we need to test the binary, and determine what wasn't picked up by looking at the exceptions
the executable will throw. For example

```shell
10:05:38.142 [main] ERROR io.snyk.skemium.GenerateCommand -- Failed to generate Database Tables Schemas
java.lang.IllegalArgumentException: Unable to find class io.debezium.connector.postgresql.PostgresSourceInfoStructMaker
	at io.debezium.config.Instantiator.getInstanceWithProvidedConstructorType(Instantiator.java:68)
	at io.debezium.config.Instantiator.getInstance(Instantiator.java:33)
	at io.debezium.config.Configuration.getInstance(Configuration.java:1528)
	at io.debezium.config.CommonConnectorConfig.getSourceInfoStructMaker(CommonConnectorConfig.java:1697)
	at io.debezium.connector.postgresql.PostgresConnectorConfig.getSourceInfoStructMaker(PostgresConnectorConfig.java:1252)
	at io.debezium.config.CommonConnectorConfig.<init>(CommonConnectorConfig.java:1202)
	at io.debezium.relational.RelationalDatabaseConnectorConfig.<init>(RelationalDatabaseConnectorConfig.java:588)
	at io.debezium.connector.postgresql.PostgresConnectorConfig.<init>(PostgresConnectorConfig.java:1097)
	at io.snyk.skemium.db.postgres.PostgresTableSchemaFetcher.<init>(PostgresTableSchemaFetcher.java:50)
	at io.snyk.skemium.db.DatabaseKind.fetcher(DatabaseKind.java:24)
	at io.snyk.skemium.GenerateCommand.call(GenerateCommand.java:136)
	at io.snyk.skemium.GenerateCommand.call(GenerateCommand.java:29)
	at picocli.CommandLine.executeUserObject(CommandLine.java:2031)
	at picocli.CommandLine.access$1500(CommandLine.java:148)
	at picocli.CommandLine$RunLast.executeUserObjectOfLastSubcommandWithSameParent(CommandLine.java:2469)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2461)
	at picocli.CommandLine$RunLast.handle(CommandLine.java:2423)
	at picocli.CommandLine$AbstractParseResultHandler.execute(CommandLine.java:2277)
	at picocli.CommandLine$RunLast.execute(CommandLine.java:2425)
	at picocli.CommandLine.execute(CommandLine.java:2174)
	at io.snyk.skemium.SkemiumMain.main(SkemiumMain.java:41)
	at java.base@21.0.7/java.lang.invoke.LambdaForm$DMH/sa346b79c.invokeStaticInit(LambdaForm$DMH)
Caused by: java.lang.ClassNotFoundException: io.debezium.connector.postgresql.PostgresSourceInfoStructMaker
	at java.base@21.0.7/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:52)
	at java.base@21.0.7/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188)
	at java.base@21.0.7/java.lang.ClassLoader.loadClass(ClassLoader.java:121)
	at io.debezium.config.Instantiator.getInstanceWithProvidedConstructorType(Instantiator.java:63)
	... 21 common frames omitted
```

Indicates that `io.debezium.connector.postgresql.PostgresSourceInfoStructMaker` was not included by `native-image`
during the static analysis it did. And so, we need to explicitly include it.

For details, please refer to the [Reachability Metadata] documentation provided by [GraalVM] 

[Reachability Metadata]: https://www.graalvm.org/latest/reference-manual/native-image/metadata/
[GraalVM]: https://www.graalvm.org/
