# Document Export Service

## Role in Job Seeker Copilot

| Role | Called by | Calls | Data | Local port |
|---|---|---|---|---:|
| Stateless DOCX/PDF renderer and bounded DOCX upload processor | Document Generation Gateway | Document Store Service | None | 8094 |

See the central [document journey](https://docs.jobseekercopilot.com/journeys/documents/), [domain services](https://docs.jobseekercopilot.com/services/domain-services/), and [API map](https://docs.jobseekercopilot.com/apis/overview/).

Renders stored generated-document text to DOCX and PDF and writes exported
bytes back to `document-store-service`.

This service is implemented, composed and exercised for controlled private-beta
DOCX/PDF export. Its build is reproducible from committed source and its
service-to-service boundary requires an authenticated Gateway
identity with owner-bound Store calls, and untrusted DOCX uploads and in-memory
renders now have explicit safety budgets. PDF/DOCX output has synthetic
Unicode, accessible structure, link, reading-order and reviewed-font evidence.
The replacement-flow limitation and remaining production deployment controls
are not concealed by that beta evidence.
See [`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

## Technology

- Java 17
- Spring Boot 3.2.0
- Maven
- Apache POI for DOCX
- OpenPDF for PDF

## API contract

[`contracts/openapi.json`](contracts/openapi.json) is the producer-owned
OpenAPI source. The immutable Java client version and reviewed source revision
are recorded in [`api/client-release.json`](api/client-release.json). Generated
client source and packages are disposable build output and are never committed.
See [`api/README.md`](api/README.md) for the release and compatibility policy.

Version `2.1.0` adds an optional Tracker workflow idempotency key to replacement
uploads. Document Export derives stable DOCX and PDF Store keys and reuses an
already-written PDF on replay, so a lost response cannot create duplicate file
versions. Calls without the header retain the existing behavior.

Version `3.0.0` makes `Idempotency-Key` mandatory for ordinary exports.
Document Export derives a stable key for each requested format and uses
Document Store `2.2.0` replay semantics, so a timeout after one file is stored
can resume without creating duplicate file versions.

The Document Store client is generated during Maven `generate-sources` from the
reviewed, checksum-protected producer contract under `src/main/openapi`.
Generated sources and binaries are build outputs and are not committed. See
[`docs/CONTRACT_GOVERNANCE.md`](docs/CONTRACT_GOVERNANCE.md).

## Authorization boundary

Every `/api/v1/document-exports/**` request must carry the approved Gateway
credential in `X-Service-Token` and exactly one owner in `X-Document-Owner`.
Document Export sends that owner to Document Store with the reader credential
for document/latest-file reads and the producer credential for exported or
replacement-file writes.

All three credentials are required at startup, must contain at least 32 bytes
and must be pairwise distinct:

- `DOCUMENT_EXPORT_GATEWAY_TOKEN`
- `DOCUMENT_STORE_PRODUCER_TOKEN`
- `DOCUMENT_STORE_READER_TOKEN`

They must be injected and rotated by the runtime. Never place values in Git,
Compose defaults, container layers, workflow output or shell history. See
[`docs/AUTHORIZATION_BOUNDARY.md`](docs/AUTHORIZATION_BOUNDARY.md).

## Build

```bash
./scripts/test-contract-policy.sh
./scripts/verify-contracts.sh
./scripts/test-api-contract-policy.sh
./scripts/verify-api-contract.sh
python3 scripts/verify_client_release.py
python3 scripts/test_openapi_breaking.py
./scripts/test-client-generation.sh
mvn -B --no-transfer-progress clean verify
docker build --tag local/document-export-service .
```

These commands are the clean-clone verification contract. They require no
sibling repository, local `libs/` directory, generated JAR or preinstalled
Job Seeker Copilot artifact.

## Safe local use

Use synthetic content only. Do not commit exported files, uploads, fonts,
temporary data, or real documents.

The enforced upload/render boundary and configurable limits are documented in
[`docs/UPLOAD_AND_RENDER_POLICY.md`](docs/UPLOAD_AND_RENDER_POLICY.md).
Reviewed dependency, font and template evidence is in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).
