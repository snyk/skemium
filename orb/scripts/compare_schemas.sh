#!/bin/bash
set -e

if ! git fetch origin "$TARGET_BRANCH" 2>/dev/null; then # yes, git seriously prints out to stderr.
    echo "Error fetching target branch $TARGET_BRANCH"
    exit 1
fi

# We "copy" over all files from our TARGET_BRANCH into a temp dir to run the comparison against.
OLD_SCHEMA_DIR=$(mktemp -d)
git ls-tree -r --name-only "origin/$TARGET_BRANCH" -- "$SCHEMA_DIR" |
    while IFS= read -r src; do
        # bash expansion to get only the filename from $src.
        dest="$OLD_SCHEMA_DIR/${src##*/}"
        git show "origin/$TARGET_BRANCH:$src" >"$dest"
    done

if ! /tmp/skemium compare \
    --compatibility BACKWARD \
    --ci-mode \
    "$OLD_SCHEMA_DIR" \
    "$SCHEMA_DIR"; then
    echo "------------------------------------------------------------------"
    echo "❌  Schema Compatibility Check Failed!"
    echo "A breaking change was detected in the Avro schemas."
    echo ""
    # TODO: write & link documentation.
    echo "📖 For instructions on how to resolve this, please see the guide ..."
    echo "------------------------------------------------------------------"
    exit 2
fi

echo "✅  Schemas are compatible!"
