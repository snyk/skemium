package io.snyk.skemium;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

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
public class CompareCommand extends BaseComparisonCommand {
    private static final Logger LOG = LoggerFactory.getLogger(CompareCommand.class);

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
            final CompareResult res = CompareResult.build(currSchemasDir, nextSchemasDir, compatibilityLevel);

            // Write output to file if specified
            writeOutput(res);

            // Determine if the compatibility check was passed
            boolean checkPassed = true;

            // Were there incompatibilities detected?
            if (res.incompatibilitiesTotal() > 0) {
                checkPassed = false;

                LOG.error("Incompatibilities: {}", res.incompatibilitiesTotal());
                LOG.error("  Key Incompatibilities: {}", res.keyIncompatibilitiesTotal());
                LOG.error("  Value Incompatibilities: {}", res.valueIncompatibilitiesTotal());
                LOG.error("  Envelope Incompatibilities: {}", res.envelopeIncompatibilitiesTotal());
            }

            // Were there table removals/additions detected?
            if (!res.removedTables().isEmpty()) {
                checkPassed = !ciMode && checkPassed;

                if (ciMode) {
                    LOG.error("Tables removed between CURRENT and NEXT: {}", res.removedTables().size());
                } else {
                    LOG.warn("Tables removed between CURRENT and NEXT: {}", res.removedTables().size());
                }
                res.removedTables().forEach(removedTableId -> LOG.error("  {}", removedTableId));
            }
            if (!res.addedTables().isEmpty()) {
                checkPassed = !ciMode && checkPassed;

                if (ciMode) {
                    LOG.error("Tables added between CURRENT and NEXT: {}", res.addedTables().size());
                } else {
                    LOG.warn("Tables added between CURRENT and NEXT: {}", res.addedTables().size());
                }
                res.addedTables().forEach(addedTableId -> LOG.error("  {}", addedTableId));
            }

            // Check for schema changes without table changes in CI mode
            if (ciMode && res.hasSchemaChangesWithoutTableChanges()) {
                checkPassed = false;
                LOG.error("Schema changes detected in CI mode: {} tables modified", res.totalTablesWithChanges());

                // Log details of what changed
                for (String tableId : res.tablesWithChanges()) {
                    if (res.keySchemaChanged().getOrDefault(tableId, false)) {
                        LOG.error("  Table '{}': Key schema changed", tableId);
                    }
                    if (res.valueSchemaChanged().getOrDefault(tableId, false)) {
                        LOG.error("  Table '{}': Value schema changed", tableId);
                    }
                    if (res.envelopeSchemaChanged().getOrDefault(tableId, false)) {
                        LOG.error("  Table '{}': Envelope schema changed", tableId);
                    }
                }
            } else if (res.hasAnySchemaChanges() && !ciMode) {
                // In non-CI mode, log schema changes as warnings for visibility
                LOG.warn("Schema changes detected: {} tables modified", res.totalTablesWithChanges());
                for (String tableId : res.tablesWithChanges()) {
                    if (res.keySchemaChanged().getOrDefault(tableId, false)) {
                        LOG.warn("  Table '{}': Key schema changed", tableId);
                    }
                    if (res.valueSchemaChanged().getOrDefault(tableId, false)) {
                        LOG.warn("  Table '{}': Value schema changed", tableId);
                    }
                    if (res.envelopeSchemaChanged().getOrDefault(tableId, false)) {
                        LOG.warn("  Table '{}': Envelope schema changed", tableId);
                    }
                }
            }

            if (checkPassed) {
                LOG.info("Compatibility check succeeded");
                return 0;
            }
            LOG.error("Compatibility check failed");
            return 1;
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

        // Validate output file
        validateOutput();

        if (currSchemasDirFile.getAbsolutePath().equals(nextSchemasDirFile.getAbsolutePath())) {
            LOG.warn("Comparing a Schema Directory with itself?");
        }

        LOG.debug("Input validated");
    }

    private void logInput() {
        LOG.debug("Input");
        LOG.debug("  CURRENT Schema Directory: {} (exists: {})", currSchemasDir.toAbsolutePath().normalize(), currSchemasDir.toFile().exists());
        LOG.debug("  NEXT    Schema Directory: {} (exists: {})", nextSchemasDir.toAbsolutePath().normalize(), nextSchemasDir.toFile().exists());

        logCommonInput();
    }
}
