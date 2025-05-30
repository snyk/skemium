package io.snyk.skemium;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.snyk.skemium.avro.TableAvroSchemas;
import io.snyk.skemium.helpers.JSON;
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

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Option(
            names = {"-o", "--output"},
            defaultValue = "${env:OUTPUT_FILE}",
            description = "Output file (JSON); overridden if exists (env: OUTPUT_FILE - optional)"
    )
    Path output = null;

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
            final Result res = Result.build(currSchemasDir, nextSchemasDir, compatibilityLevel);

            if (output != null) {
                LOG.debug("Writing result to file: {}", output.toAbsolutePath().normalize());
                try (final PrintWriter out = new PrintWriter(output.toString())) {
                    out.println(JSON.pretty(res));
                }
            }

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

                LOG.error("Tables removed between CURRENT and NEXT: {}", res.removedTables().size());
                res.removedTables().forEach(removedTableId -> LOG.error("  {}", removedTableId));
            }
            if (!res.addedTables().isEmpty()) {
                checkPassed = !ciMode && checkPassed;

                LOG.error("Tables added between CURRENT and NEXT: {}", res.addedTables().size());
                res.addedTables().forEach(addedTableId -> LOG.error("  {}", addedTableId));
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

        if (output != null) {
            final File outputFile = this.output.toFile();
            if (outputFile.exists()) {
                if (outputFile.isDirectory()) {
                    throw new CommandLine.ParameterException(
                            spec.commandLine(),
                            "Output file is a directory: " + outputFile.getAbsolutePath()
                    );
                }
                LOG.warn("Output file exists and will be overridden: {}", outputFile.getAbsolutePath());
            }
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
        LOG.debug("  CI Mode: {}", ciMode);
        LOG.debug("Output");
        if (output != null) {
            LOG.debug("  File: {} (exists: {})", output.toAbsolutePath().normalize(), output.toFile().exists());
        } else {
            LOG.debug("  Standard Output");
        }
    }

    /// Describes the result of running the `compare` command.
    /// It's left to the calling logic to decide when to fail/succeed the actual CLI command.
    ///
    /// @param currentSchemasDir          [Path] to the directory containing the CURRENT Table Schemas compared.
    /// @param nextSchemasDir             [Path] to the directory containing the NEXT Table Schemas compared.
    /// @param compatibilityLevel         [CompatibilityLevel] used during the comparison.
    /// @param keyIncompatibilities:      [Map] of Table Avro Schemas identifiers, to [List] of Key Schema incompatibilities.
    /// @param valueIncompatibilities:    [Map] of Table Avro Schemas identifiers, to [List] of Value Schema incompatibilities.
    /// @param envelopeIncompatibilities: [Map] of Table Avro Schemas identifiers, to [List] of Envelope Schema incompatibilities.
    /// @param removedTables:             [Set] of Table Avro Schemas that were removed: present in CURRENT but absent from NEXT.
    /// @param addedTables:               [Set] of Table Avro Schemas that were added: absent from CURRENT but present in NEXT.
    record Result(
            @Nonnull Path currentSchemasDir,
            @Nonnull Path nextSchemasDir,
            @Nonnull CompatibilityLevel compatibilityLevel,
            @Nonnull Map<String, List<String>> keyIncompatibilities,
            @Nonnull Map<String, List<String>> valueIncompatibilities,
            @Nonnull Map<String, List<String>> envelopeIncompatibilities,
            @Nonnull Set<String> removedTables,
            @Nonnull Set<String> addedTables
    ) {

        /// Sum of all Key Schema incompatibilities identified, across all Tables.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public int keyIncompatibilitiesTotal() {
            return keyIncompatibilities.values().stream().mapToInt(List::size).sum();
        }

        /// Sum of all Value Schema incompatibilities identified, across all Tables.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public int valueIncompatibilitiesTotal() {
            return valueIncompatibilities.values().stream().mapToInt(List::size).sum();
        }

        /// Sum of all Envelope Schema incompatibilities identified, across all Tables.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public int envelopeIncompatibilitiesTotal() {
            return envelopeIncompatibilities.values().stream().mapToInt(List::size).sum();
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public int incompatibilitiesTotal() {
            return keyIncompatibilitiesTotal() + valueIncompatibilitiesTotal() + envelopeIncompatibilitiesTotal();
        }

        public static Result build(@Nonnull Path currSchemasDir,
                                   @Nonnull Path nextSchemasDir,
                                   @Nonnull CompatibilityLevel compatibilityLevel) throws IOException {
            final MetadataFile currMeta = MetadataFile.loadFrom(currSchemasDir);
            final Set<String> currTableIds = currMeta.getTableSchemasIdentifiers();

            final MetadataFile nextMeta = MetadataFile.loadFrom(nextSchemasDir);
            final Set<String> nextTableIds = nextMeta.getTableSchemasIdentifiers();

            final Sets.SetView<String> removedTables = Sets.difference(currTableIds, nextTableIds);
            final Sets.SetView<String> addedTables = Sets.difference(nextTableIds, currTableIds);

            final Map<String, List<String>> keyIncompatibilities = new HashMap<>(currTableIds.size());
            final Map<String, List<String>> valueIncompatibilities = new HashMap<>(currTableIds.size());
            final Map<String, List<String>> envelopeIncompatibilities = new HashMap<>(currTableIds.size());

            for (final String tableId : currTableIds) {
                if (!nextTableIds.contains(tableId)) {
                    LOG.warn("Table '{}' not found in NEXT Database Schema: skipping compatibility check (table dropped?)", tableId);
                    continue;
                }

                LOG.debug("Checking compatibility '{}' for Table '{}'", compatibilityLevel, tableId);
                final TableAvroSchemas currTableSchemas = TableAvroSchemas.loadFrom(currSchemasDir, tableId);
                final TableAvroSchemas nextTableSchemas = TableAvroSchemas.loadFrom(nextSchemasDir, tableId);
                final SchemaRegistry.CompatibilityResult res = SchemaRegistry.checkCompatibility(
                        currTableSchemas,
                        nextTableSchemas,
                        compatibilityLevel);
                if (res.isCompatible()) {
                    LOG.info("Compatibility for Table '{}' preserved", tableId);
                    keyIncompatibilities.put(tableId, List.of());
                    valueIncompatibilities.put(tableId, List.of());
                    envelopeIncompatibilities.put(tableId, List.of());
                } else {
                    LOG.trace("Checking Table '{}' Key Incompatibilities", tableId);
                    keyIncompatibilities.put(tableId, res.keyResults());
                    for (final String err : res.keyResults()) {
                        LOG.error("Table '{}' Key Incompatibility: {}", tableId, err);
                    }

                    LOG.trace("Checking Table '{}' Value Incompatibilities", tableId);
                    valueIncompatibilities.put(tableId, res.valueResults());
                    for (final String err : res.valueResults()) {
                        LOG.error("Table '{}' Value Incompatibility: {}", tableId, err);
                    }

                    LOG.trace("Checking Table '{}' Envelope Incompatibilities", tableId);
                    envelopeIncompatibilities.put(tableId, res.envelopeResults());
                    for (final String err : res.envelopeResults()) {
                        LOG.error("Table '{}' Envelope Incompatibility: {}", tableId, err);
                    }
                }
            }

            return new Result(currSchemasDir, nextSchemasDir, compatibilityLevel,
                    keyIncompatibilities, valueIncompatibilities, envelopeIncompatibilities,
                    removedTables, addedTables);
        }
    }
}
