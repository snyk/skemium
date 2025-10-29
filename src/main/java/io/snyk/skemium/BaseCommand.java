package io.snyk.skemium;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

/// Base class for all commands (i.e., shared [Option]).
///
/// Features provided:
///
///   - Configure logging level via a `-v` "verbosity" flag
///
public abstract class BaseCommand implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(BaseCommand.class);

    @Option(
            names = {"-v", "--verbose"},
            description = "Logging Verbosity - use multiple -v to increase (default: ERROR)"
    )
    List<Boolean> verbose = List.of();

    private ch.qos.logback.classic.Level verbosityToLevel() {
        if (verbose.isEmpty()) {
            return ch.qos.logback.classic.Level.ERROR;
        }

        return switch (verbose.size()) {
            case 1 -> ch.qos.logback.classic.Level.WARN;
            case 2 -> ch.qos.logback.classic.Level.INFO;
            case 3 -> ch.qos.logback.classic.Level.DEBUG;
            default -> ch.qos.logback.classic.Level.TRACE;
        };
    }

    /**
     * To be invoked by implementors on the first line of their {@link Callable#call()} implementation.
     */
    protected void setLogLevelFromVerbosity() {
        final ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
        final ch.qos.logback.classic.Level level = verbosityToLevel();
        rootLogger.setLevel(level);

        LOG.debug("Logging level: {} / Verbosity: {}", level, verbose.size());
    }
}
