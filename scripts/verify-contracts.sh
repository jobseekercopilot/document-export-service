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
grep -Fx 'revision=fedcdbdec63795269c4e4c4f43fc32f38c6327b1' "$source_metadata" >/dev/null
grep -Fx 'path=contracts/openapi.json' "$source_metadata" >/dev/null
grep -Fx 'sha256=410ab1a7a2e8a5a5ad374443ec834f6aef7f778f6936b3ef90c33f8e580cdbd9' "$source_metadata" >/dev/null

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.info.version == "1.0.0") and
    (.paths["/api/v1/documents/{id}"].get.operationId == "getDocumentById") and
    (.paths["/api/v1/document-files"].post.operationId == "createDocumentFile") and
    (.paths["/api/v1/documents/{generatedDocumentId}/files/upload"].post.operationId == "uploadReplacementFile") and
    (.paths["/api/v1/documents/{generatedDocumentId}/files/latest"].get.operationId == "getLatestFilesForDocument") and
    (.components.schemas.GeneratedDocumentResponse.properties
        | has("id") and has("title") and has("content") and has("documentType")) and
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
