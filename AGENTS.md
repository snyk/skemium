# AGENTS.md

## Project Overview

**Skemium** is a Java CLI tool that generates and compares [Debezium](https://debezium.io/) CDC (Change Data Capture) [Avro](https://avro.apache.org/) schemas from database tables. It detects whether database schema changes will break Debezium CDC production by comparing current vs. next versions of Avro schemas using Confluent Schema Registry compatibility checks.

- **Group/Artifact**: `com.github.kafkesc:skemium`
- **Current version**: Defined in `pom.xml` (`<version>` tag)
- **License**: Apache 2.0
- **Main class**: `io.snyk.skemium.SkemiumMain`

### Three CLI commands

| Command | Purpose |
|---------|---------|
| `generate` | Connects to a PostgreSQL database, extracts table schemas, converts to Avro, saves to directory |
| `compare` | Compares two directories of generated Avro schemas for compatibility |
| `compare-files` | Compares two individual `.avsc` files for compatibility (supports external type resolution via `--include-schema`) |

## Requirements

- **JDK 21+** (AdoptOpenJDK recommended for development; GraalVM for native builds)
- **Maven 3.9+**
- **Docker** (required for tests — Testcontainers spins up PostgreSQL)
- Optional: [asdf](https://asdf-vm.com/) — run `asdf install` to get exact versions from `.tool-versions`
- Optional: [Taskfile](https://taskfile.dev/) — installed via asdf, provides shortcut tasks

### Exact versions (from `.tool-versions`)

| Tool | Version |
|------|---------|
| Maven | 3.9.12 |
| Java | adoptopenjdk-21.0.9+10.0.LTS |
| Snyk CLI | 1.1302.0 |
| Taskfile | 3.46.4 |

GraalVM is commented out in `.tool-versions` — uncomment `java oracle-graalvm-21.0.8` (and comment out the adoptopenjdk line) only for native binary builds.

## Essential Commands

> **Agent rule**: Always prefer `Taskfile.yml` tasks over raw `mvn` commands. Use `task <name>` when a matching task exists. Only fall back to crafting a direct `mvn` (or other) command when no Taskfile task covers the need.

### Taskfile Tasks (preferred)

```shell
task package                    # clean + build + test (mvn clean package)
task package.uber-jar           # clean + uber-jar, skips tests
task package.native-executable  # clean + native binary, skips tests (requires GraalVM)
task clean                      # mvn clean
task tag-version -- X.Y.Z       # Set version in pom.xml + git commit + git tag
task snyk.test                  # Run Snyk security scans
```

### Direct Maven (fallback only — use when no Taskfile task covers it)

```shell
mvn test                    # Run tests only (no Taskfile task for test-only)
mvn -B package              # CI-style build (batch mode)
```

## Code Organization

```
src/main/java/io/snyk/skemium/
├── SkemiumMain.java                 # Entry point, Picocli root command
├── BaseCommand.java                 # Abstract base for all commands (logging verbosity)
├── BaseComparisonCommand.java       # Abstract base for compare commands (compatibility, output, CI mode)
├── GenerateCommand.java             # `generate` subcommand
├── CompareCommand.java              # `compare` subcommand
├── CompareFilesCommand.java         # `compare-files` subcommand
├── CompareResult.java               # Result record for `compare`
├── CompareFilesResult.java          # Result record for `compare-files`
├── avro/
│   └── TableAvroSchemas.java        # Core data type: key/value/envelope Avro schemas for a table
├── cli/
│   └── ManifestReader.java          # Reads JAR MANIFEST.MF for version info (singleton pattern)
├── db/
│   ├── DatabaseKind.java            # Enum of supported DBs (currently only POSTGRES)
│   ├── TableSchemaFetcher.java      # Interface for fetching table schemas (extends AutoCloseable)
│   ├── CatalogSchemaAndTableTopicNamingStrategy.java  # Topic naming: <catalog>.<schema>.<table>
│   └── postgres/
│       ├── PostgresTableSchemaFetcher.java  # PostgreSQL implementation
│       └── PostgresSchemaRefreshable.java   # Package-local wrapper exposing hidden Debezium refresh()
├── helpers/
│   ├── Avro.java                    # Kafka Connect → Avro schema conversion + schema file generation
│   ├── Git.java                     # JGit helper for local repo info (commit, branch, tag)
│   ├── JSON.java                    # Jackson JSON serialization helpers (pretty, compact, from)
│   └── SchemaRegistry.java          # Compatibility checking + schema equality (JSON normalization)
└── meta/
    └── MetadataFile.java            # `.skemium.meta.json` metadata record

src/test/java/io/snyk/skemium/
├── WithPostgresContainer.java       # Base class for tests needing PostgreSQL (Testcontainers)
├── TestHelper.java                  # Test utilities (defines RESOURCES path constant)
├── GenerateCommandTest.java         # Integration tests for `generate`
├── CompareCommandTest.java          # Integration tests for `compare` + CI mode
├── CompareFilesCommandTest.java     # Tests for `compare-files` + CI mode + --include-schema
├── CompareFilesResultTest.java      # Unit tests for compare-files result logic + multi-schema
├── avro/TableAvroSchemasTest.java   # Tests for Avro schema handling
├── helpers/SchemaRegistryTest.java  # Tests for compatibility checking
├── meta/MetadataFileTest.java       # Tests for metadata serialization
└── db/postgres/                     # PostgreSQL-specific tests

src/test/resources/
├── db_schema/chinook.initdb.sql     # Test fixture: Chinook sample database
├── schema_employee/                 # Test fixture: employee table schemas
├── schema_employee_invalid_checksum/
├── schema_change-no_changes/        # Test fixture: identical current/next
├── schema_change-backward_compatible/
├── schema_change-non_backward_compatible/
├── schema_change-compatible_with_table_addition/
├── schema_change-key_added/
├── schema_change-key_removed/
└── compare-files/                   # Test fixture: individual .avsc files
    ├── valid-schemas/               # person-v1, v2-compatible, v2-incompatible, v1-reordered
    ├── invalid-schemas/             # malformed.json, empty.avsc, invalid-avro.avsc
    └── multi-schema/                # External type resolution fixtures (issue-type + event-with-issue-ref)

schemas/                             # Avro schemas for Skemium's own output formats
├── skemium.generate.meta.avsc       # Schema for `.skemium.meta.json`
├── skemium.compare.result.avsc      # Schema for `compare` JSON output
└── skemium.compare-files.result.avsc # Schema for `compare-files` JSON output

orb/                                 # CircleCI orb test fixtures
└── test/
    ├── migrations/                  # SQL migration scripts (0001_setup, 0002_change, 0003_breaking_change)
    └── schemas/                     # Pre-generated Avro schemas for orb testing
```

## Naming Conventions and Style

### Java Style

- **Java 21** features used: `record` types, text blocks (`"""`), `switch` expressions, Java doc comments with `///` syntax
- **Package structure**: `io.snyk.skemium` with sub-packages `avro`, `cli`, `db`, `db.postgres`, `helpers`, `meta`
- **Logging**: SLF4J with Logback; every class gets `private static final Logger LOG = LoggerFactory.getLogger(ClassName.class);`
- **Null annotations**: `@Nonnull` and `@Nullable` from `javax.annotation`
- **CLI framework**: [Picocli](https://picocli.info/) — commands are `Callable<Integer>`, options use `@Option`, parameters use `@Parameters`
- **JSON serialization**: Jackson with `@JsonProperty` annotations; `JSON.java` helper provides `pretty()` and `compact()` methods
- **Data classes**: Java `record` types are preferred (e.g., `CompareResult`, `CompareFilesResult`, `TableAvroSchemas`, `MetadataFile`)
- **Static factory methods**: Records use `static build(...)` methods rather than exposing constructors directly
- **Enum naming**: `SCREAMING_SNAKE_CASE` (e.g., `DatabaseKind.POSTGRES`)
- **Constants**: `private static final` with descriptive names
- **Singletons**: `ManifestReader` uses a `public static final SINGLETON` field pattern

### Command Pattern

All commands follow this structure:

1. Extend `BaseCommand` (or `BaseComparisonCommand` for comparison commands)
2. Implement `Callable<Integer>` (return 0 for success, 1 for failure)
3. In `call()`: first call `setLogLevelFromVerbosity()`, then `validate()`, then `logInput()`
4. Annotate with `@Command(name = "...", ...)` with consistent heading format strings
5. CLI options support environment variable fallback via `defaultValue = "${env:VAR_NAME}"`

### Exit Code Conventions

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Application error (incompatibility found, parsing failure, connection error) |
| 2 | Picocli parameter validation error (missing/invalid parameters) |

### Logging Verbosity

Controlled by the `-v` flag (repeatable). Default level is `ERROR`:

```
<none>    → ERROR
-v        → WARN
-vv       → INFO
-vvv      → DEBUG
-vvvv+    → TRACE
```

### File Naming in Generated Output

Generated schema files follow the pattern: `DB_NAME.DB_SCHEMA.DB_TABLE.EXTENSION`
- Key schema: `.key.avsc`
- Value schema: `.val.avsc`
- Envelope schema: `.env.avsc`
- Checksum: `.sha256`
- Metadata: `.skemium.meta.json`

## Testing

### Framework

- **JUnit 5** (Jupiter) with `junit-jupiter-api` and `junit-jupiter-params`
- **Testcontainers** for PostgreSQL integration tests — requires **Docker running**
- Tests that need a database extend `WithPostgresContainer`, which manages a PostgreSQL 17.2 container with the Chinook sample database

### Test Fixture Database

- Database: `chinook` (Chinook sample DB — music store schema)
- User: `chinook-db-user` / Password: `chinook-db-pass`
- Init script: `src/test/resources/db_schema/chinook.initdb.sql`
- PostgreSQL configured with `wal_level=logical` for Debezium CDC

### Test Data Directories

Schema change test scenarios are in `src/test/resources/schema_change-*/` with `current/` and `next/` subdirectories. Each contains pre-generated Avro schemas and `.skemium.meta.json` metadata files.

### Schema Generation During Tests

Two test methods regenerate Avro schema files from Java record annotations:

- `CompareCommandTest.refreshCompareResultFileSchema()` → writes `schemas/skemium.compare.result.avsc`
- `CompareFilesCommandTest.refreshSchemaComparisonResultFileSchema()` → writes `schemas/skemium.compare-files.result.avsc`

These call `Avro.saveAvroSchemaForType()` which generates schemas from `@JsonProperty`-annotated record classes. **CI checks for uncommitted changes after tests** via `git diff --exit-code`, so if schema files change, they must be committed.

### Test Utilities

- `TestHelper.RESOURCES` — `Path.of("src", "test", "resources")` constant for locating test fixtures
- `WithPostgresContainer.createPostgresContainerConfiguration()` — creates a Debezium `Configuration` for the test container

### Running Tests

```shell
mvn test                    # All tests
mvn test -pl .              # Single module (this is a single-module project)
```

There is no separate unit-vs-integration test split — all tests run together. Some tests are slow because they spin up Docker containers.

## Dependency Versioning

All dependency versions are centralized in `pom.xml` `<properties>` using the `ver.` prefix convention:

```xml
<ver.avro>1.12.1</ver.avro>
<ver.jackson>2.21.1</ver.jackson>
<ver.debezium>3.0.8.Final</ver.debezium>
```

Dependabot is configured (`.github/dependabot.yml`) for automated dependency updates. Configuration:
- Weekly schedule for both Maven and GitHub Actions
- Ignores major version bumps
- Groups all dependencies into a single PR per ecosystem
- Commit prefix: `chore` (conventional commits)

## Key Dependencies

| Library | Purpose |
|---------|---------|
| `debezium-core` + `debezium-connector-postgres` | Database schema extraction |
| `kafka-connect-avro-converter` (Confluent) | Avro schema conversion and compatibility checking |
| `picocli` + `picocli-codegen` | CLI framework + annotation processing |
| `jackson-*` (core, databind, annotations, jsr310, avro) | JSON/Avro serialization |
| `jgit` | Git repository info for metadata |
| `commons-codec` | SHA256 checksums |
| `commons-compress` | Transitive/compression support |
| `commons-lang3` | String/object utilities |
| `guava` (transitive via Confluent) | `Sets.difference()` for table comparison |
| `testcontainers` | PostgreSQL test containers |

### Confluent Repository

The project uses the Confluent Maven repository (`https://packages.confluent.io/maven/`) in addition to Maven Central. This is required for the `kafka-connect-avro-converter` dependency.

## CI/CD

### CI (GitHub Actions: `.github/workflows/ci.yaml`)

Runs on PRs to `main` and pushes to `main`:
1. **Gitleaks**: Secret scanning (skips dependabot PRs)
2. **Snyk**: Dependency vulnerability scanning with SARIF upload to GitHub Code Scanning (skips dependabot PRs)
3. **Build & Test**: `mvn -B package`, then `git diff --exit-code` to ensure no uncommitted changes, then `dorny/test-reporter` for JUnit test results

### CI (CircleCI: `.circleci/config.yml`)

Runs Snyk/ProdsecOrb security scans and secrets scanning. Uses `cimg/openjdk:21.0.9` Docker image.

### Releases (GitHub Actions)

Triggered by pushing a `vX.Y.Z` tag to `main`. Two separate workflows:

**`release-uberjar.yaml`**: Builds and uploads `skemium-VERSION-jar-with-dependencies.jar` to GitHub Releases.

**`release-binaries.yaml`**: Builds native binaries on 4 platform combinations using GraalVM:

| Runner | OS | Arch |
|--------|----|------|
| `ubuntu-24.04` | linux | x86_64 |
| `ubuntu-24.04-arm` | linux | aarch64 |
| `macos-15-intel` | macos | x86_64 |
| `macos-latest` | macos | aarch64 |

Native binary naming: `skemium-VERSION-OS-ARCH` (e.g., `skemium-1.2.2-linux-x86_64`). Uses `-O3 -march=compatibility` for broad CPU compatibility.

### Cutting a Release

```shell
task tag-version -- X.Y.Z                     # Updates pom.xml, commits, creates git tag
git push origin main --follow-tags --tags      # Push tag triggers release workflows
```

## Git Conventions

- **Default branch**: `main`
- **Branch naming**: Use conventional commit type prefixes: `feat/`, `fix/`, `chore/`, `docs/`, `refactor/`, `build/`, `ci/`, `test/`, `perf/`, `style/`, `revert/`
- **Commit messages**: Follow [Conventional Commits](https://www.conventionalcommits.org/)
- **Pre-commit hooks**: Gitleaks for secret detection (`.pre-commit-config.yaml`)
- **Code owners**: `@snyk/data-backend` and `@snyk/productinfra_data-backend`
- **Rebasing**: Changes to `main` should be tracked by rebasing (per `CONTRIBUTING.md`)

## Important Gotchas

1. **Docker required for tests**: Most tests use Testcontainers and will fail without Docker running.

2. **Schema files are auto-generated during tests**: Some test methods regenerate the `schemas/*.avsc` files. CI checks that these are committed — if you change a `record` type that has a corresponding schema file, you must run tests and commit the updated `.avsc` files.

3. **Jackson annotations version quirk**: `jackson-annotations` uses a separate version property (`ver.jackson-annotations`) because its version string differs from other Jackson modules (e.g., `2.21` vs `2.21.1`). See the comment in `pom.xml`.

4. **`TOPIC_PREFIX` is required but unused**: When creating Debezium configurations, `TOPIC_PREFIX` must be set even though Skemium doesn't actually produce to Kafka. See `GenerateCommand.java:217`.

5. **Only PostgreSQL is supported**: The `DatabaseKind` enum and `TableSchemaFetcher` interface are designed for multiple database types, but only `POSTGRES` is implemented.

6. **GraalVM needed for native builds only**: Regular development uses AdoptOpenJDK 21. Switch to GraalVM (uncomment in `.tool-versions`) only when building native binaries.

7. **Avro field ordering matters for equality**: `SchemaRegistry.checkSchemaEquality()` normalizes JSON (sorts object keys and record fields by name) before comparing, because Avro's `Schema.equals()` is order-sensitive.

8. **Key schema can be null**: Tables without a `PRIMARY KEY` have a `null` key schema. All code paths must handle this (see `TableAvroSchemas`, `SchemaRegistry.checkCompatibility()`).

9. **Checksum validation on load**: `TableAvroSchemas.loadFrom()` validates SHA256 checksums when loading schemas from disk. Invalid checksums throw `IOException`. Missing checksum files log a warning but continue.

10. **Environment variable fallback**: All CLI options support environment variable configuration via Picocli's `${env:VAR_NAME}` defaultValue syntax (e.g., `DB_HOSTNAME`, `DB_PORT`, `COMPATIBILITY`, `CI_MODE`).

11. **`PostgresSchemaRefreshable` is intentionally package-local**: It extends `PostgresSchema` to expose the `refresh()` method which is hidden inside the Debezium Postgres Connector library. This is necessary to load table schemas. Don't make it public.

12. **Topic naming strategy**: `CatalogSchemaAndTableTopicNamingStrategy` formats topics as `<catalog>.<schema>.<table>` (e.g., `chinook.public.artist`). This naming is also used as the table identifier (`TableAvroSchemas.identifier()`).

13. **Tests mutate the database**: `CompareCommandTest` runs `ALTER TABLE` statements against the Testcontainers PostgreSQL instance to test schema change detection. These changes persist within the container's lifecycle, so test ordering may matter if tests share the same container.

14. **Case-insensitive enum parsing**: `SkemiumMain` configures Picocli with `.setCaseInsensitiveEnumValuesAllowed(true)`, so CLI enum options like `--compatibility` accept any case.

15. **`Avro.kafkaConnectSchemaToAvroSchema()` unwraps unions**: Kafka Connect maps records to `UNION[NULL, RECORD]` by default. The helper extracts the non-null subtype. If the input is `null`, it returns `null` (for tables without primary keys).
