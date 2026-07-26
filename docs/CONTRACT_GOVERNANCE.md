# Document Store contract governance

Document Store is the producer for the generated client used by Document
Export. Document Export builds that client from reviewed source during Maven
`generate-sources`; no compiled client JAR is committed or loaded from
`libs/`.

## Current pin

| Property | Value |
| --- | --- |
| Producer | `jobseekercopilot/document-store-service` |
| Producer revision | `b4d836f04e09c62b88b1d23580fe1f939aa519a9` |
| Producer path | `contracts/openapi.json` |
| Contract version | `1.4.0` |
| SHA-256 | `393eeb9d6701f01c4f83c394f96369113fe36f00ca5273ea1e6a261e060b0a2c` |
| Generator | OpenAPI Generator `7.5.0`, Java `resttemplate` library |

`src/main/openapi/document-store-service.SOURCE` records the source revision
and checksum. `SHA256SUMS` protects the reviewed contract bytes. Maven validates
the OpenAPI document and generates into `target/generated-sources`; generated
sources and binaries are disposable build outputs.

## Compatibility boundary

Document Export currently requires:

- `GET /api/v1/documents/{id}` / `getDocumentById`;
- `POST /api/v1/documents` / `createDocument`, including idempotency;
- the application document-version activation operation;
- `POST /api/v1/document-files` / `createDocumentFile`;
- replacement upload idempotency and the latest-file operation;
- document ID, application ID, title, content, type, version and active state;
- generated-document ID, file type, filename, MIME type, source and active
  metadata;
- required base64 file content on exported-file writes.
- service-token authentication and `X-Document-Owner` on every read and write.

`scripts/verify-contracts.sh` checks provenance, checksum and this semantic
boundary. `scripts/test-contract-policy.sh` proves that missing, drifted or
incompatible inputs fail closed.

The pinned producer contract implements STORE-01's owner-scoped security
boundary. Document Export uses the Store reader role for reads and producer
role for file writes. Gateway adoption, Infrastructure credential injection
and integrated two-user evidence remain dependencies under DOCGEN-15, GW-01
and INFRA-08.

## Updating the pin

1. Merge and verify the producer change in Document Store.
2. Export its tracked contract through the producer test and record the exact
   merged `develop` revision.
3. Review the API diff for compatibility, ownership and sensitive-data impact.
4. Copy the exact producer artifact into `src/main/openapi`.
5. Update `document-store-service.SOURCE` and `SHA256SUMS`.
6. Update policy assertions only when the consumer change is intentional.
7. Run:

   ```bash
   ./scripts/test-contract-policy.sh
   ./scripts/verify-contracts.sh
   mvn -B --no-transfer-progress clean verify
   docker build --tag local/document-export-service .
   ```

8. Merge only after pull-request and post-merge `develop` CI pass.

If a producer change is incompatible, retain the previous reviewed pin until
the consumer is ready. Rollback is a normal revert to the previous contract,
source metadata and checksum as one change; never substitute an untracked
generated JAR.

The metadata-only licence correction tracked by
[BACKLOG-DOCS-02](https://github.com/jobseekercopilot/document-store-service/issues/19)
will intentionally change the producer checksum. It must follow this update
procedure and does not change the actual repository licence terms.
