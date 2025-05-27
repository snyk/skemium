package io.snyk.skemium;

import io.debezium.config.Configuration;
import io.debezium.relational.RelationalDatabaseConnectorConfig;
import io.debezium.relational.TableSchema;
import io.snyk.skemium.avro.AvroSchemaFile;
import io.snyk.skemium.cli.ManifestReader;
import io.snyk.skemium.db.DatabaseKind;
import io.snyk.skemium.db.TableSchemaFetcher;
import io.snyk.skemium.helpers.Avro;
import io.snyk.skemium.meta.MetadataFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static java.time.temporal.ChronoField.*;

@Command(
        name = "generate",
        headerHeading = "%n",
        header = "Generates Avro Schema from Tables in a Database",
        synopsisHeading = "%n",
        descriptionHeading = "%nDescription:%n%n",
        description = """
                Connects to Database, finds schemas and tables,
                converts table schemas to Avro Schemas, stores them in a directory.""",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n"
)
public class GenerateCommand extends BaseCommand {
    private static final Logger LOG = LoggerFactory.getLogger(GenerateCommand.class);

    @Spec
    CommandSpec spec;

    @Option(
            names = {"-h", "--hostname"},
            defaultValue = "${env:DB_HOSTNAME}",
            description = "Database hostname (env: DB_HOSTNAME)",
            required = true
    )
    String hostname;

    @Option(
            names = {"-p", "--port"},
            defaultValue = "${env:DB_PORT}",
            description = "Database port (env: DB_PORT)",
            required = true
    )
    int port;

    @Option(
            names = {"-u", "--username"},
            defaultValue = "${env:DB_USERNAME}",
            description = "Database username (env: DB_USERNAME)",
            required = true
    )
    String username;

    @Option(
            names = {"--password"},
            defaultValue = "${env:DB_PASSWORD}",
            description = "Database password (env: DB_PASSWORD)",
            required = true
    )
    String password;

    @Option(
            names = {"-d", "--database"},
            defaultValue = "${env:DB_NAME}",
            description = "Database name (env: DB_NAME)",
            required = true
    )
    String dbName;

    @Option(
            names = {"-s", "--schema"},
            defaultValue = "${env:DB_SCHEMA}",
            description = "Database schema(s); all if omitted (env: DB_SCHEMA - optional)",
            split = ","
    )
    Set<String> dbSchemas;

    @Option(
            names = {"-t", "--table"},
            defaultValue = "${env:DB_TABLE}",
            description = "Database table(s); all if omitted (env: DB_TABLE - optional)",
            split = ","
    )
    Set<String> dbTables;

    @Option(
            names = {"-x", "--exclude-column"},
            defaultValue = "${env:DB_EXCLUDED_COLUMN}",
            description = "Database table column(s) to exclude (fmt: DB_SCHEMA.DB_TABLE.DB_COLUMN - env: DB_EXCLUDED_COLUMN - optional)",
            split = ","
    )
    Set<String> dbExcludedColumns;

    @Option(
            names = {"--kind"},
            defaultValue = "${env:DB_KIND}",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "Database kind (values: ${COMPLETION-CANDIDATES} - env: DB_KIND - optional)"
    )
    DatabaseKind kind = DatabaseKind.POSTGRES;

    @Parameters(
            arity = "0..1",
            index = "0",
            paramLabel = "DIRECTORY_PATH",
            description = "Output directory",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS
    )
    Path outputDir = defaultOutputDir();

    @Override
    public Integer call() {
        setLogLevelFromVerbosity();
        validate();
        logInput();

        try (final TableSchemaFetcher schemaFetcher = kind.fetcher(createConfiguration())) {
            final List<TableSchema> tableSchemas = schemaFetcher.fetch(dbName, dbSchemas, dbTables, dbExcludedColumns);

            LOG.info("Will convert {} Table Schemas to Avro", tableSchemas.size());
            for (final TableSchema ts : tableSchemas) {
                LOG.info("  {}", ts.id());
            }

            // Ensure the output directory either is ready or can be created
            if (!outputDir.toFile().exists() && !outputDir.toFile().mkdirs()) {
                throw new RuntimeException("Could not create output directory: " + outputDir);
            }
            LOG.info("Will generate schema to: {}", outputDir.toAbsolutePath().normalize());

            // Map table schemas to avro schemas
            final List<AvroSchemaFile> avroSchemas = tableSchemas.stream().parallel()
                    .map(Avro::relationalTableSchemaToAvroSchemaHandler)
                    .sorted((a, b) -> a.identifier().compareTo(b.identifier()))
                    .toList();

            // Save avro schemas to the designated output directory
            for (final AvroSchemaFile as : avroSchemas) {
                LOG.debug("  {} -> {}", as.identifier(), as.filename());
                as.saveTo(outputDir);
            }

            // Save skemium metadata to the designated output directory
            MetadataFile.build(spec.commandLine().getParseResult().originalArgs(), avroSchemas).saveTo(outputDir);
        } catch (Exception e) {
            LOG.error("Failed to generate tables schema", e);
            return 1;
        }
        return 0;
    }

    private void validate() throws ParameterException {
        final File outputDirFile = outputDir.toFile();
        if (outputDirFile.exists() && (!outputDirFile.isDirectory() || !outputDirFile.canWrite())) {
            throw new ParameterException(
                    spec.commandLine(),
                    "Output directory must not exist or be a writable directory: " + outputDirFile.getAbsolutePath()
            );
        }

        LOG.debug("Input validated");
    }

    private void logInput() {
        LOG.debug("Database");
        LOG.debug("  Kind: {}", kind);
        LOG.debug("  Host: {}:{}", hostname, port);
        LOG.debug("  User: {}:{}", username, password.replaceAll(".", "*"));
        LOG.debug("  Database (i.e. catalog): {}", dbName);
        LOG.debug("Input");
        if (dbSchemas == null || dbSchemas.isEmpty()) {
            LOG.debug("  Schema(s): ALL");
        } else {
            LOG.debug("  Schema(s): {}", String.join(", ", dbSchemas));
        }
        if (dbTables == null || dbTables.isEmpty()) {
            LOG.debug("  Table(s): ALL");
        } else {
            LOG.debug("  Table(s): {}", String.join(", ", dbTables));
        }
        LOG.debug("Output");
        LOG.debug("  Directory: {} (exists: {})", outputDir.toAbsolutePath().normalize(), outputDir.toFile().exists());
    }

    private Configuration createConfiguration() {
        return Configuration.create()
                .with(RelationalDatabaseConnectorConfig.HOSTNAME, hostname)
                .with(RelationalDatabaseConnectorConfig.PORT, port)
                .with(RelationalDatabaseConnectorConfig.USER, username)
                .with(RelationalDatabaseConnectorConfig.PASSWORD, password)
                .with(RelationalDatabaseConnectorConfig.DATABASE_NAME, dbName)
                .with(RelationalDatabaseConnectorConfig.TOPIC_PREFIX, "unused.topic.prefix") //< NOTE: Required but unused field
                .build();
    }

    private static final DateTimeFormatter outputDirDateFormatter = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral('-')
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .toFormatter(Locale.getDefault());

    private static Path defaultOutputDir() {
        return Path.of(String.format("%s-%s",
                ManifestReader.SINGLETON.getAttribute(ManifestReader.MANIFEST_KEY_PRJ_NAME),
                outputDirDateFormatter.format(LocalDateTime.now()))
        );
    }
}
