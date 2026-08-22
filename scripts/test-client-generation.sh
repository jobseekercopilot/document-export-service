#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
module="$repository_root/api/client"
client_version="3.1.0-rev.cda9a2af811b"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

manifest() {
    local output="$module/target/generated-sources/openapi"
    test -d "$output"
    (
        cd "$output"
        find . -type f -print0 | sort -z | xargs -0 sha256sum
    )
}

mvn -B --no-transfer-progress -f "$module/pom.xml" clean package
manifest > "$temporary_dir/first"
test -s "$temporary_dir/first"
sha256sum "$module/target/document-export-service-client-$client_version.jar" \
    > "$temporary_dir/first-jar"

mvn -B --no-transfer-progress -f "$module/pom.xml" clean package
manifest > "$temporary_dir/second"
sha256sum "$module/target/document-export-service-client-$client_version.jar" \
    > "$temporary_dir/second-jar"

cmp "$temporary_dir/first" "$temporary_dir/second"
cmp "$temporary_dir/first-jar" "$temporary_dir/second-jar"

echo "Generated source and client package reproducibility policy passed"
