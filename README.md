# Document Export Service

Renders stored generated-document text to DOCX and PDF and writes exported
bytes back to `document-store-service`.

This migration baseline is **not beta-ready**. The service has no trusted user
identity or ownership enforcement, clean-clone builds depend on an untracked
client JAR, and the replacement flow creates a PDF from the old stored text
instead of the uploaded DOCX. Export quality, accessibility, resource bounds,
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

## Build

```bash
mvn -B clean verify
```

The command currently fails in a clean clone because the document-store client
is referenced from an untracked local `libs/` directory. Compiled clients must
not be committed as the fix.

## Safe local use

Use synthetic content only. Do not commit exported files, uploads, fonts,
temporary data, or real documents.

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).
