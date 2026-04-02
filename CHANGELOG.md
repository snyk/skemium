# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.0] - 2026-04-02

### Added

- Taskfile task `compile` to compile the project without running tests.
- Taskfile tasks `deps.show-patch-updates` and `deps.show-minor-updates` to inspect available dependency updates.

### Changed

- Bumped Debezium from `3.0.8.Final` to `3.2.7.Final`, along with other dependency updates.

### Fixed

- Compatibility with Debezium 3.2.x: the PostgreSQL connector no longer populates the catalog in `TableId`, so the database name is now explicitly prepended to form `database.schema.table` identifiers.
- `CatalogSchemaAndTableTopicNamingStrategy` now handles a `null` catalog gracefully instead of producing `null.schema.table` topic names.
- Corrected `maven-native-plugin` version (`0.11.4` is the actual latest, not `0.11.5`).

## [1.2.4] - 2026-03-03

### Fixed

- Corrected `maven-native-plugin` version to `0.11.4` (the actual latest release).

## [1.2.3] - 2026-03-03

### Changed

- Bumped all dependencies to their latest compatible versions.
- Configured Dependabot to group dependency PRs into a single PR per ecosystem.
- Added `AGENTS.md` for AI-assisted development context.

### Fixed

- Upgraded `jackson-core` to `2.21.1` to address a security vulnerability.

## [1.2.2] - 2026-01-20

### Changed

- Bumped Jackson to `2.21.0`, PostgreSQL driver to `42.7.9`, Logback to `1.5.25`, and `jackson-annotations` to `2.21`.
- Dependabot configured to skip major version bumps and avoid running Gitleaks/Snyk CI on Dependabot PRs.

## [1.2.1] - 2026-01-20

### Fixed

- CI release workflow updated to use latest supported `macos` images and to override (not append) release body across parallel jobs.

### Added

- Dependabot configuration for automated Maven and GitHub Actions dependency updates.

## [1.2.0] - 2026-01-19

### Added

- `--include-schema` option for the `compare-files` command, allowing external Avro schema files to be included for type resolution during comparison. Thank you to [@MantasSnyk](https://github.com/MantasSnyk) for the contribution!

### Changed

- Updated all Java dependencies and CI workflow actions to latest versions.

### Fixed

- Addressed `lz4-java` security vulnerabilities (`SNYK-JAVA-ORGLZ4-14151788` and `SNYK-JAVA-ORGLZ4-14219384`).

## [1.1.0] - 2025-11-04

### Added

- New `compare-files` command to compare two individual `.avsc` files for compatibility, with support for external type resolution. Thank you to [@MantasSnyk](https://github.com/MantasSnyk) for the contribution!
- `--ci-mode` flag for comparison commands: fails on any schema change (including compatible ones), table additions, or removals.
- Schema equality checking with JSON normalization (sorts object keys and record fields by name before comparing).
- Testing schemas and migrations for the CircleCI orb. Thank you to [@ramonr](https://github.com/ramonr) for the contribution!

### Changed

- Refactored `ComparisonCommand` into `BaseComparisonCommand` to share logic between `compare` and `compare-files`.
- Updated release process documentation in `README.md`.

### Fixed

- Restored Snyk CI integration after GitHub SARIF format changes.
- Upgraded `logback-classic` to `1.5.19` to fix `SNYK-JAVA-CHQOSLOGBACK-13169722`.

## [1.0.3] - 2025-07-28

### Fixed

- Upgraded `commons-compress` to address vulnerability `SNYK-JAVA-ORGAPACHECOMMONS-10734078`.

## [1.0.2] - 2025-06-16

### Added

- `Taskfile.yml` for convenient project task management (`task package`, `task clean`, etc.).

### Changed

- Tweaked logging levels, adding a conclusion message at `INFO` level for each command.

### Fixed

- Upgraded `postgresql` and `kafka` dependencies to address security vulnerabilities (`SNYK-JAVA-ORGPOSTGRESQL-10343494`, `SNYK-JAVA-ORGAPACHEKAFKA-10336719`). Thank you to [@snyk-io](https://github.com/apps/snyk-io) for the fix!

## [1.0.1-rc5] - 2025-06-11

### Added

- The `--table` filter option now supports the `DB_SCHEMA.DB_TABLE` format (e.g., `public.artist`) in addition to just table names.

### Fixed

- Typo in `README.md`. Thank you to [@SorooshDeveloper](https://github.com/SorooshDeveloper) for the fix!

## [1.0.0-rc4] - 2025-06-06

### Fixed

- Provided GraalVM reachability metadata hints for dynamically-loaded classes and resources, fixing native binary crashes at runtime.

## [1.0.0-rc3] - 2025-06-06

### Fixed

- Compiled GraalVM native binaries with `-march=compatibility` to avoid CPU feature mismatches on different hardware (previously used `-march=native` which hyper-optimised for the build machine).

## [1.0.0-rc2] - 2025-06-04

### Added

- `linux-aarch64` native binary to release artifacts.

## [1.0.0-rc1] - 2025-06-04

### Added

- `compare` command: compares two directories of generated Avro schemas for backward/forward/full compatibility.
- `--output` option for the `compare` command to save results as a JSON file.
- `-v|--verbose` flag (repeatable) to control logging verbosity (default `ERROR`, up to `TRACE`).
- `-x|--exclude-column` option for the `generate` command to exclude specific table columns.
- Generation of key, value, and envelope Avro schemas (previously only value schemas were generated).
- Published Avro schema definitions for Skemium's own output formats (`schemas/*.avsc`).
- CI check to detect uncommitted schema file changes after tests.

### Changed

- Refactored `AvroSchemaFile` to `TableAvroSchemas` for clearer naming.
- Refactored `CheckCompatibilityResult` to `CompatibilityResult`.
- `checkCompatibility` now returns a detailed `Record` with per-schema-type incompatibility lists.

## [0.0.1-dev] - 2025-04-28

### Added

- Initial release of Skemium.
- `generate` command: connects to a PostgreSQL database, extracts table schemas, converts them to Avro, and saves to a directory.
- PostgreSQL `TableSchemaFetcher` implementation using Debezium's schema extraction.
- `CatalogSchemaAndTableTopicNamingStrategy` for `<catalog>.<schema>.<table>` topic/identifier naming.
- Metadata file (`.skemium.meta.json`) generation with VCS information, checksums, and CLI arguments.
- SHA256 checksum validation on schema load.
- CI via GitHub Actions (Gitleaks, Snyk, build and test).
- Release workflows for uber-JAR and GraalVM native binaries (linux-x86\_64, macos-x86\_64, macos-aarch64).
- Test infrastructure using Testcontainers with the Chinook sample database.
