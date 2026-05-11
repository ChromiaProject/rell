#!/bin/bash
#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

set -e

# Verify that the script is being run from the repository root
if [ ! -f "build.gradle.kts" ] || [ ! -f "README.md" ]; then
    echo "Error: This script must be run from the repository root directory"
    echo "The repository root contains build.gradle.kts and README.md files"
    exit 1
fi

CHR_REPO_URL="https://gitlab.com/chromaway/core-tools/chromia-cli.git"
CHR_REPO_DIR="./chromia-cli-local"
CHR_EXECUTABLE="$CHR_REPO_DIR/chromia-cli/target/chromia-cli-dev-dist/bin/chr"
GIT_BRANCH="update-rell-0.16.0-snapshot"

# chromia-cli-tools ships chromia-build-tools, which chromia-cli consumes from the
# local Maven repo. The branch tracked here carries the Rell 0.16 API fixes that the
# released 0.11.3 jar does not have.
CHR_TOOLS_REPO_URL="https://gitlab.com/chromaway/core-tools/chromia-cli-tools.git"
CHR_TOOLS_DIR="./chromia-cli-tools-local"
CHR_TOOLS_BRANCH="update-rell-0.16.0-snapshot"
CHR_TOOLS_VERSION="dev"  # value of <revision> in chromia-cli-tools/pom.xml

# Parse arguments for --rebuild and --skip-publish
REBUILD=false
SKIP_PUBLISH=false
ARGS=()

for arg in "$@"; do
    if [ "$arg" == "--rebuild" ]; then
        REBUILD=true
    elif [ "$arg" == "--skip-publish" ]; then
        SKIP_PUBLISH=true
    else
        ARGS+=("$arg")
    fi
done

sync_repo() {
    local repo_dir="$1"
    local repo_url="$2"
    local branch="$3"
    local label="$4"

    if [ -d "$repo_dir/.git" ]; then
        echo "Updating $label repository to latest $branch..."
        REPO_ROOT=$(pwd)
        cd "$repo_dir"
        git fetch --depth 1 origin "$branch"
        git checkout "$branch"
        git reset --hard "origin/$branch"
        cd "$REPO_ROOT"
    else
        if [ -d "$repo_dir" ]; then
            echo "Existing $repo_dir has no .git; removing for fresh clone..."
            rm -rf "$repo_dir"
        fi
        echo "Cloning $label repository ($branch)..."
        git clone --depth 1 --branch "$branch" "$repo_url" "$repo_dir"
    fi
}

setup_repository() {
    sync_repo "$CHR_REPO_DIR" "$CHR_REPO_URL" "$GIT_BRANCH" "chromia-cli"
    sync_repo "$CHR_TOOLS_DIR" "$CHR_TOOLS_REPO_URL" "$CHR_TOOLS_BRANCH" "chromia-cli-tools"
}

update_rell_version() {
    # Get Rell version from Gradle
    REPO_ROOT=$(pwd)
    RELL_VERSION=$(./gradlew -q properties | grep "^version:" | awk '{print $2}')
    echo "Local Rell version: $RELL_VERSION"

    # Update chromia-cli with the correct Rell version (core + dokka plugin) and point it
    # at the locally-installed chromia-cli-tools snapshot.
    cd "$CHR_REPO_DIR"
    ./mvnw versions:set-property -Dproperty=rell.version -DnewVersion="$RELL_VERSION"
    ./mvnw versions:set-property -Dproperty=rell.dokka.version -DnewVersion="$RELL_VERSION"
    ./mvnw versions:set-property -Dproperty=chromia.cli.tools.version -DnewVersion="$CHR_TOOLS_VERSION"
    cd "$REPO_ROOT"
}

install_chromia_cli_tools() {
    REPO_ROOT=$(pwd)
    cd "$CHR_TOOLS_DIR"

    # Sync Rell version inside chromia-cli-tools so chromia-build-tools compiles against
    # the same snapshot we just published.
    ./mvnw versions:set-property -Dproperty=rell.version -DnewVersion="$RELL_VERSION"

    echo "Installing chromia-cli-tools ($CHR_TOOLS_VERSION) to local Maven..."
    ./mvnw -DskipTests -DskipITs install
    cd "$REPO_ROOT"
}

build_chromia_cli() {
    echo "Building chromia-cli distribution..."
    REPO_ROOT=$(pwd)
    cd "$CHR_REPO_DIR"
    ./mvnw -DskipTests -DskipITs install
    cd "$REPO_ROOT"
}

sync_local_rell_jars() {
    local dist_lib="$CHR_REPO_DIR/chromia-cli/target/chromia-cli-dev-dist/lib"
    local synced=0

    if [ ! -d "$dist_lib" ]; then
        echo "Error: chromia-cli dist lib directory not found at $dist_lib" >&2
        exit 1
    fi

    local local_repos=("$HOME/.m2/repository/net/postchain/rell" "$HOME/.m2/repository/com/chromia/rell/dokka")

    for repo in "${local_repos[@]}"; do
        [ -d "$repo" ] || continue
        for jar in "$repo"/*/"$RELL_VERSION"/*-"$RELL_VERSION".jar; do
            [ -e "$jar" ] || continue
            jar_name=$(basename "$jar")
            if [ -f "$dist_lib/$jar_name" ]; then
                cp -f "$jar" "$dist_lib/$jar_name"
                synced=$((synced + 1))
            fi
        done
    done

    if [ "$synced" -eq 0 ]; then
        echo "Warning: no local Rell jars synced for version $RELL_VERSION" >&2
    else
        echo "Synced $synced local Rell jars into chromia-cli distribution"
    fi
}

if ! command -v git &> /dev/null; then
    echo "Error: git is not installed. Please install git and try again."
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed. Please install Java and try again."
    exit 1
fi

if [ "$REBUILD" = true ]; then
    echo "Rebuild option detected. Removing existing chromia-cli and chromia-cli-tools..."
    rm -rf "$CHR_REPO_DIR"
    rm -rf "$CHR_TOOLS_DIR"
fi

echo "Setting up repository..."
setup_repository

echo "Updating Rell version..."
update_rell_version

if [ "$SKIP_PUBLISH" != true ]; then
    # Clean first to guarantee a fresh build — Gradle's build cache can miss source changes
    echo "Publishing Rell artifacts to local Maven with Gradle..."
    ./gradlew publishToMavenLocal
    install_chromia_cli_tools
else
    echo "Skipping publishToMavenLocal + chromia-cli-tools install (requested)"
fi

if [ ! -x "$CHR_EXECUTABLE" ]; then
    build_chromia_cli
fi

sync_local_rell_jars

# Execute chr with arguments passed to this script (excluding --rebuild)
export JAVA_ARGS="${JAVA_ARGS:-} --enable-native-access=ALL-UNNAMED"
"$CHR_EXECUTABLE" "${ARGS[@]}"
