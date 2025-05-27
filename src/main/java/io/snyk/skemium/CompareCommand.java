package io.snyk.skemium;

import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;

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
public class CompareCommand extends BaseCommand {
    private static final Logger LOG = LoggerFactory.getLogger(CompareCommand.class);

    @Spec
    CommandSpec spec;

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

    @Override
    public Integer call() {
        setLogLevelFromVerbosity();
        validate();
        logInput();

        /* TODO pseudo-logic
        *    1. READ LIST of AVSCs in CURRENT and NEXT
        *    2. FOR each AVSC in CURRENT:
        *      2a. Apply Avro.checkCompatibility between CURR.AVSC and NEXT.AVSC
        *      2b. Accumulate any string (error) reported, grouped by AVSC, in RESULTS
        *      Q1: WHAT do we do if NEXT.AVSC doesn't exist? (i.e. a table was removed)
        *        A1: Log error and suggest they need to update CURR schema
        *    3. IF RESULTS.isEmpty()
        *      3a. EXIT with 0
        *      3b. ELSE log errors and EXIT with 1
        *
        * TODO Questions
        *  Q2: WHAT do we do for AVSC only present in NEXT? (i.e. a new table)
        *    A2: Add a "CI" mode, where it's INFO normally, but ERROR during CI
        *  Q3: SHOULD it support "schema" and "table" filtering, like the `generate` command?
        *    A3: NOPE. The filtering is left at `generate`, and this command should compare "as-is"
        *
        * TODO additional checks
        * */


        return 0;
    }

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
}
