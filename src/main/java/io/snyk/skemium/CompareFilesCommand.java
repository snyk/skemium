package io.snyk.skemium;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Command(
        name = "compare-files",
        headerHeading = "%n",
        header = "Compares two Avro Schema (.avsc) files",
        synopsisHeading = "%n",
        descriptionHeading = "%nDescription:%n%n",
        description = """
            Given 2 Avro Schema files (.avsc), compares them according to the specified Compatibility Level.
            This command is designed for comparing bespoke/custom Avro schemas that are not generated from database tables.""",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n"
)
public class CompareFilesCommand extends BaseComparisonCommand {
    private static final Logger LOG = LoggerFactory.getLogger(CompareFilesCommand.class);

    @Parameters(
            arity = "1",
            index = "0",
            paramLabel = "CURR_SCHEMA_FILE",
            description = "Path to the CURRENT Avro schema file (.avsc)"
    )
    Path currentSchemaFile;

    @Parameters(
            arity = "1",
            index = "1",
            paramLabel = "NEXT_SCHEMA_FILE",
            description = "Path to the NEXT Avro schema file (.avsc)"
    )
    Path nextSchemaFile;

    @Option(names = {"--include-schema", "-s"},
            description = """
                    Additional schema files to include for type resolution.
                    These schemas are parsed before the main schema files,
                    populating the parser's type registry to enable resolution
                    of referenced types. Can be specified multiple times."""
    )
    List<Path> includeSchemas = new ArrayList<>();

    @Override
    public Integer call() {
        setLogLevelFromVerbosity();
        validate();
        logInput();

        try {
            final CompareFilesResult result = CompareFilesResult.build(
                    currentSchemaFile,
                    nextSchemaFile,
                    compatibilityLevel,
                    includeSchemas);

            // Write output to file if specified
            writeOutput(result);

            // Determine if the check passed
            boolean checkPassed = true;

            // Check for incompatibilities
            if (!result.isCompatible()) {
                checkPassed = false;
                LOG.error("Schemas are NOT compatible ({} compatibility)", compatibilityLevel);
                LOG.error("Found {} incompatibility(ies):", result.incompatibilitiesTotal());
                for (String incompatibility : result.incompatibilities()) {
                    LOG.error("  - {}", incompatibility);
                }
            }

            // Check for schema changes in CI mode
            if (ciMode && result.hasSchemaChanges()) {
                checkPassed = false;
                LOG.error("Schema changes detected in CI mode");
                if (result.isCompatible()) {
                    LOG.error("Schemas are compatible but different - this fails in CI mode");
                }
            } else if (result.hasSchemaChanges() && !ciMode) {
                // In non-CI mode, log schema changes as warnings for visibility
                LOG.warn("Schema changes detected between files");
                if (result.isCompatible()) {
                    LOG.warn("Schemas are compatible but different");
                }
            }

            // Final result
            if (checkPassed) {
                if (result.hasSchemaChanges()) {
                    LOG.info("Schemas are compatible ({} compatibility) with changes", compatibilityLevel);
                } else {
                    LOG.info("Schemas are compatible ({} compatibility)", compatibilityLevel);
                    LOG.info("No schema changes detected");
                }
                return 0;
            } else {
                LOG.error("Schema comparison failed");
                return 1;
            }

        } catch (Exception e) {
            LOG.error("Failed to compare schema files", e);
            return 1;
        }
    }

    private void validate() throws CommandLine.ParameterException {
        validateSchemaFile(currentSchemaFile, "Current schema");
        validateSchemaFile(nextSchemaFile, "Next schema");

        // Validate that files are different
        if (currentSchemaFile.toFile().getAbsolutePath().equals(nextSchemaFile.toFile().getAbsolutePath())) {
            LOG.warn("Comparing a schema file with itself: {}", currentSchemaFile.toFile().getAbsolutePath());
        }

        // Validate include schema files
        for (final Path includeSchema : includeSchemas) {
            validateSchemaFile(includeSchema, "Included schema");
        }

        // Validate output file
        validateOutput();

        LOG.debug("Input validation completed successfully");
    }

    private void validateSchemaFile(Path schemaPath, String fileLabel) throws CommandLine.ParameterException {
        final File file = schemaPath.toFile();
        if (!file.exists()) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    fileLabel + " file does not exist: " + file.getAbsolutePath());
        }
        if (file.isDirectory()) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    fileLabel + " path is a directory, expected a file: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    fileLabel + " file is not readable: " + file.getAbsolutePath());
        }
        if (!file.getName().endsWith(".avsc")) {
            LOG.warn("{} file does not have .avsc extension: {}", fileLabel, file.getName());
        }
    }

    private void logInput() {
        LOG.debug("Input Files");
        LOG.debug("  CURRENT Schema File: {} (exists: {}, size: {} bytes)",
                currentSchemaFile.toAbsolutePath().normalize(),
                currentSchemaFile.toFile().exists(),
                currentSchemaFile.toFile().length());
        LOG.debug("  NEXT    Schema File: {} (exists: {}, size: {} bytes)",
                nextSchemaFile.toAbsolutePath().normalize(),
                nextSchemaFile.toFile().exists(),
                nextSchemaFile.toFile().length());
        if (!includeSchemas.isEmpty()) {
            LOG.debug("  Include Schemas: {} file(s)", includeSchemas.size());
            for (final Path includeSchema : includeSchemas) {
                LOG.debug("    - {} (exists: {}, size: {} bytes)",
                        includeSchema.toAbsolutePath().normalize(),
                        includeSchema.toFile().exists(),
                        includeSchema.toFile().length());
            }
        }

        logCommonInput();
    }
}
