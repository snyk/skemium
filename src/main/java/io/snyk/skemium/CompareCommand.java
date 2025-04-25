package io.snyk.skemium;

import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.nio.file.Path;

@Command(
        name = "compare",
        headerHeading = "%n",
        header = "Compares Avro Schemas generated from Tables in a Database",
        synopsisHeading = "%n",
        descriptionHeading = "%nDescription:%n%n",
        description = """
                Given 2 directories (CURRENT / NEXT) containing Avro Schemas of Database Tables,
                compares them according to Compatibility Level.""",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n"
)
public class CompareCommand implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CompareCommand.class);

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(
            names = {"-c", "--compatibility"},
            defaultValue = "${env:COMPATIBILITY}",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = """
                    Compatibility Level (values: ${COMPLETION-CANDIDATES} - env: COMPATIBILITY - optional)
                    See: https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html"""
    )
    CompatibilityLevel compatibilityLevel = CompatibilityLevel.BACKWARD;

    @Parameters(
            arity = "1",
            index = "0",
            paramLabel = "CURR_SCHEMAS_DIR",
            description = "Directory with the CURRENT Database table schemas"
    )
    Path currSchemasDir;

    @Parameters(
            arity = "1",
            index = "1",
            paramLabel = "NEXT_SCHEMAS_DIR",
            description = "Directory with the NEXT Database table schemas"
    )
    Path nextSchemasDir;

    private void validate() throws CommandLine.ParameterException {
        final File currSchemasDirFile = currSchemasDir.toFile();
        if (!currSchemasDirFile.exists() || !currSchemasDirFile.isDirectory()) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Directory must exist: " + currSchemasDirFile.getAbsolutePath()
            );
        }

        final File nextSchemasDirFile = nextSchemasDir.toFile();
        if (!nextSchemasDirFile.exists() || !nextSchemasDirFile.isDirectory()) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Directory must exist: " + nextSchemasDirFile.getAbsolutePath()
            );
        }

        if (currSchemasDirFile.getAbsolutePath().equals(nextSchemasDirFile.getAbsolutePath())) {
            LOG.warn("Comparing a Schema Directory with itself?");
        }

        LOG.debug("Input validated");
    }

    private void logInput() {
        LOG.debug("Input");
        LOG.debug("  CURRENT Schema Directory: {} (exists: {})", currSchemasDir.toAbsolutePath().normalize(), currSchemasDir.toFile().exists());
        LOG.debug("  NEXT    Schema Directory: {} (exists: {})", nextSchemasDir.toAbsolutePath().normalize(), nextSchemasDir.toFile().exists());
        LOG.debug("  Compatibility Level: {}", compatibilityLevel);
    }

    @Override
    public void run() {
        validate();
        logInput();
    }
}
