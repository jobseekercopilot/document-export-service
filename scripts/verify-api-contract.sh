#!/usr/bin/env bash
set -euo pipefail

contract_dir="${1:-contracts}"
contract="$contract_dir/openapi.json"
manifest="$contract_dir/SHA256SUMS"

for required_file in "$contract" "$manifest"; do
    if [[ ! -f "$required_file" || -L "$required_file" ]]; then
        echo "API contract policy: required regular file is missing or is a symlink: $required_file" >&2
        exit 1
    fi
done

(
    cd "$contract_dir"
    sha256sum --check --strict SHA256SUMS
)

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.info.version == "2.1.0") and
    (.components.securitySchemes.serviceToken.type == "apiKey") and
    (.components.securitySchemes.serviceToken.in == "header") and
    (.components.securitySchemes.serviceToken.name == "X-Service-Token") and
    (.paths["/api/v1/document-exports/documents/{documentId}"].post.operationId
        == "exportDocument") and
    (.paths["/api/v1/document-exports/documents/{documentId}"].post.parameters
        | any(.name == "documentId" and .in == "path" and .required == true)) and
    (.paths["/api/v1/document-exports/documents/{documentId}"].post.parameters
        | any(.name == "X-Document-Owner" and .in == "header" and .required == true)) and
    (.paths["/api/v1/document-exports/documents/{documentId}"].post.security
        | any(has("serviceToken"))) and
    (.paths["/api/v1/document-exports/documents/{documentId}"].post.requestBody.required
        == true) and
    (.paths["/api/v1/document-exports/documents/{documentId}"].post.requestBody
        .content["application/json"].schema["$ref"]
        == "#/components/schemas/DocumentExportRequest") and
    (.paths["/api/v1/document-exports/documents/{documentId}"].post.responses["201"]
        .content["*/*"].schema["$ref"]
        == "#/components/schemas/DocumentExportResponse") and
    (.paths["/api/v1/document-exports/documents/{documentId}/upload"].post.operationId
        == "uploadReplacement") and
    (.paths["/api/v1/document-exports/documents/{documentId}/upload"].post.parameters
        | any(.name == "documentKind" and .in == "query" and .required == true)) and
    (.paths["/api/v1/document-exports/documents/{documentId}/upload"].post.parameters
        | any(.name == "uploadedFormat" and .in == "query" and .required == true)) and
    (.paths["/api/v1/document-exports/documents/{documentId}/upload"].post.parameters
        | any(.name == "X-Document-Owner" and .in == "header" and .required == true)) and
    (.paths["/api/v1/document-exports/documents/{documentId}/upload"].post.parameters
        | any(.name == "Idempotency-Key" and .in == "header" and .required == false)) and
    (.paths["/api/v1/document-exports/documents/{documentId}/upload"].post.security
        | any(has("serviceToken"))) and
    (.components.schemas.DocumentExportRequest.required | index("formats") != null) and
    (.components.schemas.DocumentExportRequest.properties.formats.items.enum
        | index("DOCX") != null and index("PDF") != null)
' "$contract" >/dev/null

echo "API contract policy: Document Export OpenAPI source is present, intact and compatible"
