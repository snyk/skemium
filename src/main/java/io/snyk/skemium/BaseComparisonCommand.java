package io.snyk.skemium;

import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.helpers.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

/// Base class for comparison commands that provides shared functionality.
///
/// Features provided:
///
///   - Common comparison options (compatibility level, output file)
///   - Output file validation and writing
///   - Shared logging and error handling patterns
///
public abstract class BaseComparisonCommand extends BaseCommand {
    private static final Logger LOG = LoggerFactory.getLogger(BaseComparisonCommand.class);

    @Spec
    CommandSpec spec;

    @Option(names = { "-c", "--compatibility" },
            defaultValue = "${env:COMPATIBILITY}",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS, description = """
                    Compatibility Level (env: COMPATIBILITY - optional)
                    See: https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html
                      Values: ${COMPLETION-CANDIDATES}
                      Default: BACKWARD"""
    )
    protected CompatibilityLevel compatibilityLevel = CompatibilityLevel.BACKWARD;

    @Option(names = { "-i", "--ci", "--ci-mode" },
            defaultValue = "${env:CI_MODE}",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = """
                    CI mode - Fail when schema changes are detected (env: CI_MODE - optional)"""
    )
    protected Boolean ciMode = false;

    @Option(names = { "-o", "--output" },
            defaultValue = "${env:OUTPUT_FILE}",
            description = "Output file (JSON); overridden if exists (env: OUTPUT_FILE - optional)"
    )
    protected Path output = null;

    /// Validates the output file parameter if provided.
    /// Logs warnings for existing files that will be overridden.
    ///
    /// @throws CommandLine.ParameterException if output path is invalid
    protected void validateOutput() throws CommandLine.ParameterException {
        if (output != null) {
            final File outputFile = this.output.toFile();
            if (outputFile.exists()) {
                if (outputFile.isDirectory()) {
                    throw new CommandLine.ParameterException(
                            spec.commandLine(),
                            "Output file is a directory: " + outputFile.getAbsolutePath());
                }
                LOG.warn("Output file exists and will be overridden: {}", outputFile.getAbsolutePath());
            }
        }
    }

    /// Writes the result to the output file if specified, otherwise logs that output
    /// will go to stdout.
    ///
    /// @param result the result object to write as JSON
    /// @throws IOException if writing to file fails
    protected void writeOutput(Object result) throws IOException {
        if (output != null) {
            LOG.debug("Writing result to file: {}", output.toAbsolutePath().normalize());
            try (final PrintWriter out = new PrintWriter(output.toString())) {
                out.println(JSON.pretty(result));
            }
        } else {
            LOG.debug("Result will be written to standard output");
        }
    }

    /**
     * Logs the common input parameters for debugging.
     */
    protected void logCommonInput() {
        LOG.debug("Common Options");
        LOG.debug("  Compatibility Level: {}", compatibilityLevel);
        LOG.debug("  CI Mode: {}", ciMode);
        LOG.debug("Output");
        if (output != null) {
            LOG.debug("  File: {} (exists: {})", output.toAbsolutePath().normalize(), output.toFile().exists());
        } else {
            LOG.debug("  Standard Output");
        }
    }
}
