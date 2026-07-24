# Document Export Service

Renders stored generated-document text to DOCX and PDF and writes exported
bytes back to `document-store-service`.

This service is **not beta-ready**. Its build is reproducible from committed
source, but it still has no trusted user identity or ownership enforcement, and
the replacement flow creates a PDF from the old stored text instead of the
uploaded DOCX. Export quality, accessibility, resource bounds,
malicious-document handling, and third-party licence evidence are incomplete.
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

The Document Store client is generated during Maven `generate-sources` from the
reviewed, checksum-protected producer contract under `src/main/openapi`.
Generated sources and binaries are build outputs and are not committed. See
[`docs/CONTRACT_GOVERNANCE.md`](docs/CONTRACT_GOVERNANCE.md).

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

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).
