# Contributing

## Contents

- [Guidelines](#guidelines)

## Guidelines

Branches should be created from the default branch `main`. Changes to `main` should be tracked by rebasing.

All changes must be peer-reviewed by the relevant [code owner](CODEOWNERS).

Branches should begin with one of the following prefixes which corresponds to the `type` enumeration of the [conventional commit standard](https://www.conventionalcommits.org/).

- `build` - enhances the build pipeline only
- `chore` - changes not covered by other types
- `ci` - enhances continuous integration pipeline only
- `docs` - updates documentation only
- `feat` - adds a new feature
- `fix` - fixes broken functionality
- `perf` - makes performance improvements only
- `refactor` - does not modify the application behaviour
- `revert` - reverts a previous code change
- `style` - fixes formatting issues
- `test` - updates units or integration tests only

All commits must follow the [conventional commit standard](https://www.conventionalcommits.org/) which is enforced by the CI.

All changes should be appropriately tested with unit, smoke, and/or integration tests.

The relevant PR must have passing CI status checks and should not reduce test coverage.
