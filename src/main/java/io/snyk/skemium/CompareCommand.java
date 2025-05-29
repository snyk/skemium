package io.snyk.skemium;

import com.google.common.collect.Sets;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.avro.TableAvroSchemas;
import io.snyk.skemium.helpers.SchemaRegistry;
import io.snyk.skemium.meta.MetadataFile;
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
import java.util.Set;

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

    @Option(
            names = {"-i", "--ci", "--ci-mode"},
            defaultValue = "${env:CI_MODE}",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = """
                    CI mode - Fail when a Table is only detected in one of the two directories (env: CI_MODE - optional)"""
    )
    Boolean ciMode = false;

    @Parameters(
            arity = "1",
            index = "0",
            paramLabel = "CURR_SCHEMAS_DIR",
            description = "Directory with the CURRENT Database Table schemas"
    )
    Path currSchemasDir;

    @Parameters(
            arity = "1",
            index = "1",
            paramLabel = "NEXT_SCHEMAS_DIR",
            description = "Directory with the NEXT Database Table schemas"
    )
    Path nextSchemasDir;

    @Override
    public Integer call() {
        setLogLevelFromVerbosity();
        validate();
        logInput();

        try {
            final MetadataFile currMeta = MetadataFile.loadFrom(currSchemasDir);
            final Set<String> currTableIds = currMeta.getTableSchemasIdentifiers();

            final MetadataFile nextMeta = MetadataFile.loadFrom(nextSchemasDir);
            final Set<String> nextTableIds = nextMeta.getTableSchemasIdentifiers();

            int totIncompatibilities = 0;
            int keyIncompatibilities = 0;
            int valIncompatibilities = 0;
            int envIncompatibilities = 0;
            for (final String tableId : currTableIds) {
                LOG.debug("Checking compatibility '{}' for Table '{}'", compatibilityLevel, tableId);

                if (!nextTableIds.contains(tableId)) {
                    LOG.warn("Table '{}' not found in NEXT Database Schema: skipping compatibility check (table dropped?)", tableId);
                    continue;
                }

                final TableAvroSchemas currTableSchemas = TableAvroSchemas.loadFrom(currSchemasDir, tableId);
                final TableAvroSchemas nextTableSchemas = TableAvroSchemas.loadFrom(nextSchemasDir, tableId);
                final SchemaRegistry.CompatibilityResult res = SchemaRegistry.checkCompatibility(
                        currTableSchemas,
                        nextTableSchemas,
                        compatibilityLevel);
                if (res.isCompatible()) {
                    LOG.info("Compatibility for Table '{}' preserved", tableId);
                } else {
                    LOG.trace("Checking Table '{}' Key Incompatibilities", tableId);
                    for (final String err : res.keyResults()) {
                        keyIncompatibilities++;
                        totIncompatibilities++;
                        LOG.error("Table '{}' Key Incompatibility: {}", tableId, err);
                    }

                    LOG.trace("Checking Table '{}' Value Incompatibilities", tableId);
                    for (final String err : res.valueResults()) {
                        valIncompatibilities++;
                        totIncompatibilities++;
                        LOG.error("Table '{}' Value Incompatibility: {}", tableId, err);
                    }

                    LOG.trace("Checking Table '{}' Envelope Incompatibilities", tableId);
                    for (final String err : res.envelopeResults()) {
                        envIncompatibilities++;
                        totIncompatibilities++;
                        LOG.error("Table '{}' Envelope Incompatibility: {}", tableId, err);
                    }
                }
            }

            // Determine if the compatibility check was passed
            boolean checkPassed = true;

            // Were there incompatibilities detected?
            if (totIncompatibilities > 0) {
                checkPassed = false;

                LOG.error("Incompatibilities: {}", totIncompatibilities);
                LOG.error("  Key Incompatibilities: {}", keyIncompatibilities);
                LOG.error("  Value Incompatibilities: {}", valIncompatibilities);
                LOG.error("  Envelope Incompatibilities: {}", envIncompatibilities);
            }

            // Were there table removals/additions detected?
            final Sets.SetView<String> removedTables = Sets.difference(currTableIds, nextTableIds);
            final Sets.SetView<String> addedTables = Sets.difference(nextTableIds, currTableIds);
            if (!removedTables.isEmpty()) {
                checkPassed = !ciMode && checkPassed;

                LOG.error("Tables removed between CURRENT and NEXT: {}", removedTables.size());
                removedTables.forEach(removedTableId -> LOG.error("  {}", removedTableId));
            }
            if (!addedTables.isEmpty()) {
                checkPassed = !ciMode && checkPassed;

                LOG.error("Tables added between CURRENT and NEXT: {}", addedTables.size());
                addedTables.forEach(addedTableId -> LOG.error("  {}", addedTableId));
            }

            return checkPassed ? 0 : 1;
        } catch (Exception e) {
            LOG.error("Failed to compare Database Tables Schemas", e);
            return 1;
        }
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
