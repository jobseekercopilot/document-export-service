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

[`contracts/openapi.json`](contracts/openapi.json) is the migration-time
OpenAPI snapshot.

The Document Store client is generated during Maven `generate-sources` from the
reviewed, checksum-protected producer contract under `src/main/openapi`.
Generated sources and binaries are build outputs and are not committed. See
[`docs/CONTRACT_GOVERNANCE.md`](docs/CONTRACT_GOVERNANCE.md).

## Build

```bash
./scripts/test-contract-policy.sh
./scripts/verify-contracts.sh
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
