#!/bin/bash
set -e

# change to the root of this repo.
cd "$(realpath --relative-to="$(pwd)" "$(git rev-parse --show-toplevel)")"

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-postgres}"
export PGDATABASE="${PGDATABASE:-testdb}"
export TABLE_NAME="${TABLE_NAME:-resource}"

export SCHEMA_DIR=${SCHEMA_DIR:-orb/test/schemas}
export TARGET_BRANCH="${TARGET_BRANCH:-main}"

run() {
    CMD=("${@:2}")
    EXPECTED_CODE="${1}"
    set +e
    OUT=$("${CMD[@]}" 2>&1)
    EXIT_CODE=$?
    set -e

    if [ $EXIT_CODE -ne "$EXPECTED_CODE" ]; then
        echo "Command" "${CMD[@]}" "failed"
        echo "$OUT"
        echo ""
        echo "Unexpected exit code: expected=$EXPECTED_CODE got=$EXIT_CODE"
        echo "Tests failed!"
        exit 1
    fi
}

clean_up() {
    set +e # allow commands to fail, try cleaning up the rest.
    psql -U "$PGUSER" -h "$PGHOST" -p "$PGPORT" -d "postgres" -c "DROP DATABASE $PGDATABASE" >/dev/null
    git restore --staged "$SCHEMA_DIR/"
    git restore "$SCHEMA_DIR/"
}

psql-exec() {
    psql -U "$PGUSER" -h "$PGHOST" -p "$PGPORT" -d "$PGDATABASE" "$@"
}

if [ ! -f /tmp/skemium ]; then
    orb/scripts/install_skemium.sh
fi

# TODO: it'd be nice if this would not fail if the DB already existed?
echo "Setting up test database..."
run 0 psql-exec -d "postgres" -c "CREATE DATABASE $PGDATABASE"
trap clean_up EXIT

echo "Running migrations..."
run 0 psql-exec -f orb/test/migrations/0001_setup.sql

echo "Generating and comparing schemas with no expected diff..."
run 0 orb/scripts/generate_schemas.sh
run 0 orb/scripts/compare_schemas.sh

echo "Generating and comparing schemas with backwards-compatible diff..."
run 0 psql-exec -f orb/test/migrations/0002_change.sql
run 1 orb/scripts/generate_schemas.sh

run 0 git add "$SCHEMA_DIR/"
run 0 orb/scripts/compare_schemas.sh

# reset the generated schemas.
run 0 git restore --staged "$SCHEMA_DIR/"
run 0 git restore "$SCHEMA_DIR/"

echo "Generating and comparing schemas with incompatible diff..."
run 0 psql-exec -f orb/test/migrations/0003_breaking_change.sql
run 1 orb/scripts/generate_schemas.sh

run 0 git add "$SCHEMA_DIR/" &>/dev/null
run 2 orb/scripts/compare_schemas.sh

echo "Tests passed!"
