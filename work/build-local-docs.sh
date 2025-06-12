#!/bin/bash
set -e

# Script to generate Rell documentation for preview
echo "Generating Rell documentation preview..."

# Create output directory if it doesn't exist
mkdir -p libdoc

# Run the command to generate documentation
./work/local-chr.sh generate docs-site --system -d libdoc

echo "Documentation preview generated in the libdoc directory"
