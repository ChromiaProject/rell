#!/bin/bash
set -e

# Verify that the script is being run from the repository root
if [ ! -f "pom.xml" ] || [ ! -f "README.md" ]; then
    echo "Error: This script must be run from the repository root directory"
    echo "The repository root contains pom.xml and README.md files"
    exit 1
fi

CHR_REPO_URL="https://gitlab.com/chromaway/core-tools/chromia-cli.git"
CHR_REPO_DIR="./chromia-cli-local"
CHR_EXECUTABLE="$CHR_REPO_DIR/chromia-cli/target/chromia-cli-dev-dist/bin/chr"
GIT_BRANCH="dev"

# Parse arguments for --rebuild
REBUILD=false
ARGS=()

for arg in "$@"; do
    if [ "$arg" == "--rebuild" ]; then
        REBUILD=true
    else
        ARGS+=("$arg")
    fi
done

setup_repository() {
    if [ -d "$CHR_REPO_DIR" ]; then
        echo "chromia-cli repository directory already exists, proceeding..."
    else
        echo "Cloning chromia-cli repository..."
        git clone --depth 1 "$CHR_REPO_URL" "$CHR_REPO_DIR"
        cd "$CHR_REPO_DIR"
        git checkout "$GIT_BRANCH"
        rm -rf .git
        cd ..
    fi
}

update_rell_version() {
    # Get Rell version from the root pom.xml
    REPO_ROOT=$(pwd)
    RELL_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
    echo "Local Rell version: $RELL_VERSION"

    # Update chromia-cli with the correct Rell version
    cd "$CHR_REPO_DIR"
    mvn versions:set-property -Dproperty=rell.version -DnewVersion="$RELL_VERSION"
    cd "$REPO_ROOT"
}

if ! command -v git &> /dev/null; then
    echo "Error: git is not installed. Please install git and try again."
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed. Please install Maven and try again."
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed. Please install Java and try again."
    exit 1
fi

if [ "$REBUILD" = true ]; then
    echo "Rebuild option detected. Removing existing chromia-cli..."
    rm -rf "$CHR_REPO_DIR"
fi

# Check if chr executable exists, if not setup repository
if [ ! -d "$CHR_REPO_DIR" ] || [ "$REBUILD" = true ]; then
    echo "Setting up repository..."
    setup_repository
fi

echo "Updating Rell version..."
update_rell_version

# Run always to ensure the build is fresh
echo "Building with mvn package -DskipTests..."
mvn package -DskipTests -Plocal-chromia-cli

# Execute the chr command with all arguments passed to this script (excluding --rebuild)
"$CHR_EXECUTABLE" "${ARGS[@]}"
