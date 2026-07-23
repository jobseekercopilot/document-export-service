# Beta-readiness audit

Audit date: 2026-07-23

Status: **Not ready for private beta**

## Verified responsibility

The service fetches generated text from Document Store, converts it to DOCX
with Apache POI or PDF with OpenPDF, and stores the bytes back in Document
Store. Rendering is in memory; no service-owned temporary files were found.

The upload endpoint accepts a replacement DOCX and then advertises an updated
PDF. In the current implementation that PDF is rendered from the unchanged
stored text, not from the uploaded DOCX, so user edits are not represented.

## Migration evidence

- Source was copied from the untracked service directory in the intact root
  workspace; no standalone source history was available.
- `target/`, the local document-store client JAR, generated binaries, logs,
  databases, exported documents, uploads, recordings, fonts, and environment
  files are excluded.
- The migration-time contract is `contracts/openapi.json`.
- Gitleaks and targeted personal-data checks passed on the source snapshot.
- A clean `mvn -B clean verify` fails before compilation because the
  `systemPath` document-store client JAR is absent. Ten test methods exist in
  source, but they were not executed in the clean candidate.
- The candidate container build fails at `COPY libs ./libs`; no image was
  produced.
- OWASP Dependency-Check 12.1.8 completed against the cached 2026-07-18
  advisory database: 53 dependencies, 11 vulnerable dependencies, 140
  vulnerability matches, including 17 Critical and 37 High matches. Results
  require reachability/false-positive triage; the report was not committed.

## Confirmed blockers

1. Export and replacement endpoints do not authenticate a user or authorise
   ownership of the requested generated document.
2. The replacement flow stores uploaded DOCX bytes but regenerates PDF from
   stale generated text; it can falsely claim the PDF was updated.
3. `documentKind` is not checked against the stored document type.
4. Exporting multiple formats is not atomic; partial saves can leave an
   inconsistent active file set.
5. Upload inspection is delegated to weak downstream ZIP checks and has no
   malware, macro, external relationship, decompression, or content-policy
   evidence.
6. Render input and output have no service-level length, page, memory, or
   response-size budgets.
7. PDF uses built-in Helvetica and DOCX requests Aptos; Unicode coverage,
   font substitution, licensing, accessibility, and environment consistency
   have not been demonstrated.
8. There are no tests for PDF text extraction, DOCX editability after a real
   office round trip, links, page breaks, large content, malicious input,
   accessibility, or cross-user denial.
9. Downstream calls do not share a bounded timeout/retry/idempotency policy.
10. Third-party dependency and licence review evidence is incomplete.
11. The Dockerfile lacks a non-root runtime, digest-pinned bases, explicit
    resource constraints, and supply-chain scan evidence.
12. The build depends on an untracked generated client JAR.
13. Current Spring, Tomcat, Jackson, compression, POI, logging, and Swagger UI
    dependency findings include untriaged Critical/High advisories.

## Required validation

Before beta, the service needs trusted identity/ownership, one authoritative
editable content model, consistent DOCX/PDF versions, bounded and inspected
inputs, deterministic accessible rendering, partial-failure recovery,
reproducible clients, dependency/licence evidence, and export security and
quality tests using synthetic fixtures.
