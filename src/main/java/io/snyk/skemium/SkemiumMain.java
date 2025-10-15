package io.snyk.skemium;

import io.snyk.skemium.cli.ManifestReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.IOException;

@Command(subcommands = {
                GenerateCommand.class,
                CompareCommand.class,
                CompareFilesCommand.class,
                CommandLine.HelpCommand.class
}, headerHeading = "%nUsage:%n", synopsisHeading = "%n", descriptionHeading = "%nDescription:%n%n", description = "Generate and Compare Avro Schema of Database Tables.", parameterListHeading = "%nParameters:%n", optionListHeading = "%nOptions:%n", commandListHeading = "%nCommands:%n", mixinStandardHelpOptions = true, versionProvider = ManifestReader.class)
public class SkemiumMain {
        private static final Logger LOG = LoggerFactory.getLogger(SkemiumMain.class);

        @Spec
        CommandSpec spec;

        public static void main(String[] args) throws IOException {
                int exitCode = new CommandLine(new SkemiumMain())
                                .setCommandName(ManifestReader.SINGLETON
                                                .getAttribute(ManifestReader.MANIFEST_KEY_PRJ_NAME))
                                .setUsageHelpLongOptionsMaxWidth(30)
                                .setUsageHelpAutoWidth(true)
                                .setCaseInsensitiveEnumValuesAllowed(true)
                                .execute(args);

                LOG.trace("Exiting with code: {}", exitCode);
                System.exit(exitCode);
        }
}
