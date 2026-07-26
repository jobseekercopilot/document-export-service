#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

copy_contract() {
    local destination="$1"
    mkdir -p "$destination"
    cp "$repository_root/contracts/openapi.json" \
       "$repository_root/contracts/SHA256SUMS" \
       "$destination/"
}

"$repository_root/scripts/verify-api-contract.sh" "$repository_root/contracts" >/dev/null

copy_contract "$temporary_dir/missing"
rm "$temporary_dir/missing/openapi.json"
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/missing" >/dev/null 2>&1; then
    echo "API contract policy negative test accepted a missing contract" >&2
    exit 1
fi

copy_contract "$temporary_dir/drift"
jq '.info.description = "unreviewed drift"' \
    "$temporary_dir/drift/openapi.json" \
    > "$temporary_dir/drift/changed.json"
mv "$temporary_dir/drift/changed.json" "$temporary_dir/drift/openapi.json"
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/drift" >/dev/null 2>&1; then
    echo "API contract policy negative test accepted checksum drift" >&2
    exit 1
fi

copy_contract "$temporary_dir/operation"
jq 'del(.paths["/api/v1/document-exports/documents/{documentId}"].post)' \
    "$temporary_dir/operation/openapi.json" \
    > "$temporary_dir/operation/changed.json"
mv "$temporary_dir/operation/changed.json" "$temporary_dir/operation/openapi.json"
(cd "$temporary_dir/operation" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/operation" >/dev/null 2>&1; then
    echo "API contract policy negative test accepted removal of exportDocument" >&2
    exit 1
fi

copy_contract "$temporary_dir/request"
jq 'del(.components.schemas.DocumentExportRequest.required[] | select(. == "formats"))' \
    "$temporary_dir/request/openapi.json" \
    > "$temporary_dir/request/changed.json"
mv "$temporary_dir/request/changed.json" "$temporary_dir/request/openapi.json"
(cd "$temporary_dir/request" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/request" >/dev/null 2>&1; then
    echo "API contract policy negative test accepted optional export formats" >&2
    exit 1
fi

copy_contract "$temporary_dir/enum"
jq 'del(.components.schemas.DocumentExportRequest.properties.formats.items.enum[]
    | select(. == "PDF"))' \
    "$temporary_dir/enum/openapi.json" \
    > "$temporary_dir/enum/changed.json"
mv "$temporary_dir/enum/changed.json" "$temporary_dir/enum/openapi.json"
(cd "$temporary_dir/enum" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/enum" >/dev/null 2>&1; then
    echo "API contract policy negative test accepted removal of PDF export" >&2
    exit 1
fi

copy_contract "$temporary_dir/service-identity"
jq 'del(.components.securitySchemes.serviceToken)' \
    "$temporary_dir/service-identity/openapi.json" \
    > "$temporary_dir/service-identity/changed.json"
mv "$temporary_dir/service-identity/changed.json" "$temporary_dir/service-identity/openapi.json"
(cd "$temporary_dir/service-identity" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/service-identity" >/dev/null 2>&1; then
    echo "API contract policy negative test accepted removal of service authentication" >&2
    exit 1
fi

copy_contract "$temporary_dir/owner-context"
jq 'del(
    .paths["/api/v1/document-exports/documents/{documentId}"].post.parameters[]
    | select(.name == "X-Document-Owner")
)' \
    "$temporary_dir/owner-context/openapi.json" \
    > "$temporary_dir/owner-context/changed.json"
mv "$temporary_dir/owner-context/changed.json" "$temporary_dir/owner-context/openapi.json"
(cd "$temporary_dir/owner-context" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/owner-context" >/dev/null 2>&1; then
    echo "API contract policy negative test accepted removal of owner context" >&2
    exit 1
fi

copy_contract "$temporary_dir/retry-key"
jq 'del(
    .paths["/api/v1/document-exports/documents/{documentId}/upload"].post.parameters[]
    | select(.name == "Idempotency-Key")
)' \
    "$temporary_dir/retry-key/openapi.json" \
    > "$temporary_dir/retry-key/changed.json"
mv "$temporary_dir/retry-key/changed.json" "$temporary_dir/retry-key/openapi.json"
(cd "$temporary_dir/retry-key" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/retry-key" >/dev/null 2>&1; then
    echo "API contract policy negative test accepted removal of replacement idempotency" >&2
    exit 1
fi

echo "API contract policy tests passed"
