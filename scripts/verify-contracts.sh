#!/usr/bin/env bash
set -euo pipefail

contract_dir="${1:-src/main/openapi}"
contract="$contract_dir/document-store-service.json"
source_metadata="$contract_dir/document-store-service.SOURCE"
manifest="$contract_dir/SHA256SUMS"

for required_file in "$contract" "$source_metadata" "$manifest"; do
    if [[ ! -f "$required_file" || -L "$required_file" ]]; then
        echo "contract policy: required regular file is missing or is a symlink: $required_file" >&2
        exit 1
    fi
done

(
    cd "$contract_dir"
    sha256sum --check --strict SHA256SUMS
)

test "$(wc -l < "$source_metadata" | tr -d ' ')" = 4
grep -Fx 'repository=jobseekercopilot/document-store-service' "$source_metadata" >/dev/null
grep -Fx 'revision=b4d836f04e09c62b88b1d23580fe1f939aa519a9' "$source_metadata" >/dev/null
grep -Fx 'path=contracts/openapi.json' "$source_metadata" >/dev/null
grep -Fx 'sha256=393eeb9d6701f01c4f83c394f96369113fe36f00ca5273ea1e6a261e060b0a2c' "$source_metadata" >/dev/null

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.info.version == "1.4.0") and
    (.components.securitySchemes.serviceToken.type == "apiKey") and
    (.components.securitySchemes.serviceToken.in == "header") and
    (.components.securitySchemes.serviceToken.name == "X-Service-Token") and
    (.paths["/api/v1/documents/{id}"].get.operationId == "getDocumentById") and
    (.paths["/api/v1/documents/{id}"].get.parameters
        | any(.name == "X-Document-Owner" and .in == "header")) and
    (.paths["/api/v1/documents/{id}"].get.security
        | any(has("serviceToken"))) and
    (.paths["/api/v1/documents"].post.parameters
        | any(.name == "Idempotency-Key" and .in == "header")) and
    (.paths["/api/v1/documents/applications/{applicationId}/{documentType}/active/{documentId}"]
        .patch.operationId == "activateDocumentVersion") and
    (.paths["/api/v1/document-files"].post.operationId == "createDocumentFile") and
    (.paths["/api/v1/document-files"].post.parameters
        | any(.name == "Idempotency-Key" and .in == "header")) and
    (.paths["/api/v1/document-files"].post.parameters
        | any(.name == "X-Document-Owner" and .in == "header")) and
    (.paths["/api/v1/document-files"].post.security
        | any(has("serviceToken"))) and
    (.paths["/api/v1/documents/{generatedDocumentId}/files/upload"].post.operationId == "uploadReplacementFile") and
    (.paths["/api/v1/documents/{generatedDocumentId}/files/upload"].post.parameters
        | any(.name == "X-Document-Owner" and .in == "header")) and
    (.paths["/api/v1/documents/{generatedDocumentId}/files/upload"].post.parameters
        | any(.name == "Idempotency-Key" and .in == "header")) and
    (.paths["/api/v1/documents/{generatedDocumentId}/files/latest"].get.operationId == "getLatestFilesForDocument") and
    (.paths["/api/v1/documents/{generatedDocumentId}/files/latest"].get.parameters
        | any(.name == "X-Document-Owner" and .in == "header")) and
    (.components.schemas.GeneratedDocumentResponse.properties
        | has("id") and has("applicationId") and has("title") and has("content") and
          has("documentType") and has("version") and has("active")) and
    (.components.schemas.CreateDocumentRequest.properties
        | has("applicationId") and has("sourceType") and has("active") and
          has("originalFilename") and has("createdBy")) and
    (.components.schemas.CreateDocumentFileRequest.required
        | index("generatedDocumentId") != null and
          index("fileType") != null and
          index("fileName") != null and
          index("mimeType") != null and
          index("fileContentBase64") != null) and
    (.components.schemas.DocumentFileResponse.properties
        | has("id") and has("generatedDocumentId") and has("fileType") and
          has("fileName") and has("mimeType") and has("source") and has("active"))
' "$contract" >/dev/null

echo "contract policy: pinned Document Store source is present, intact and compatible"
