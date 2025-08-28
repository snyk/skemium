#!/bin/bash
set -e

/tmp/skemium generate "$SCHEMA_DIR" \
    --database="$PGDATABASE" \
    --hostname="$PGHOST" \
    --port="$PGPORT" \
    --username="$PGUSER" \
    --password="$PGPASSWORD" \
    --table "$TABLE_NAME" \
    --kind=POSTGRES

if ! git diff --exit-code "$SCHEMA_DIR/*.avsc"; then
    echo "------------------------------------------------------------------"
    echo "❌  Schema Check Failed: Outdated Schemas!"
    echo "The Skemium schemas are out of date, please re-generate them."
    echo ""
    # TODO: write & link documentation.
    echo "📖 For a detailed explanation, please see ..."
    echo "------------------------------------------------------------------"
    exit 1
fi

echo "✅  Schemas are up-to-date!"
