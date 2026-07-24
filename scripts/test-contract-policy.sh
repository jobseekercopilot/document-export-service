#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

copy_contract() {
    local destination="$1"
    mkdir -p "$destination"
    cp "$repository_root/src/main/openapi/document-store-service.json" \
       "$repository_root/src/main/openapi/document-store-service.SOURCE" \
       "$repository_root/src/main/openapi/SHA256SUMS" \
       "$destination/"
}

"$repository_root/scripts/verify-contracts.sh" "$repository_root/src/main/openapi" >/dev/null

copy_contract "$temporary_dir/missing"
rm "$temporary_dir/missing/document-store-service.json"
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/missing" >/dev/null 2>&1; then
    echo "contract policy negative test accepted a missing producer contract" >&2
    exit 1
fi

copy_contract "$temporary_dir/checksum-drift"
jq '.info.description = "unreviewed drift"' \
    "$temporary_dir/checksum-drift/document-store-service.json" \
    > "$temporary_dir/checksum-drift/changed.json"
mv "$temporary_dir/checksum-drift/changed.json" \
   "$temporary_dir/checksum-drift/document-store-service.json"
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/checksum-drift" >/dev/null 2>&1; then
    echo "contract policy negative test accepted checksum drift" >&2
    exit 1
fi

copy_contract "$temporary_dir/read-operation"
jq 'del(.paths["/api/v1/documents/{id}"].get)' \
    "$temporary_dir/read-operation/document-store-service.json" \
    > "$temporary_dir/read-operation/changed.json"
mv "$temporary_dir/read-operation/changed.json" \
   "$temporary_dir/read-operation/document-store-service.json"
(cd "$temporary_dir/read-operation" && sha256sum document-store-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/read-operation" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of document retrieval" >&2
    exit 1
fi

copy_contract "$temporary_dir/file-operation"
jq 'del(.paths["/api/v1/document-files"].post)' \
    "$temporary_dir/file-operation/document-store-service.json" \
    > "$temporary_dir/file-operation/changed.json"
mv "$temporary_dir/file-operation/changed.json" \
   "$temporary_dir/file-operation/document-store-service.json"
(cd "$temporary_dir/file-operation" && sha256sum document-store-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/file-operation" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of exported-file persistence" >&2
    exit 1
fi

copy_contract "$temporary_dir/file-content"
jq '.components.schemas.CreateDocumentFileRequest.required -= ["fileContentBase64"]' \
    "$temporary_dir/file-content/document-store-service.json" \
    > "$temporary_dir/file-content/changed.json"
mv "$temporary_dir/file-content/changed.json" \
   "$temporary_dir/file-content/document-store-service.json"
(cd "$temporary_dir/file-content" && sha256sum document-store-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/file-content" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of required file content" >&2
    exit 1
fi

copy_contract "$temporary_dir/source-revision"
sed 's/revision=fedcdbd/revision=0000000/' \
    "$temporary_dir/source-revision/document-store-service.SOURCE" \
    > "$temporary_dir/source-revision/changed.SOURCE"
mv "$temporary_dir/source-revision/changed.SOURCE" \
   "$temporary_dir/source-revision/document-store-service.SOURCE"
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/source-revision" >/dev/null 2>&1; then
    echo "contract policy negative test accepted unreviewed producer revision metadata" >&2
    exit 1
fi

echo "contract policy tests passed"
