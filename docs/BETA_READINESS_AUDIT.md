# Beta-readiness audit

Audit date: 2026-07-23

Status: **Not ready for private beta**

Update 2026-07-24: DOCGEN-15 now implements the Document Export producer-side
service identity and owner-bound Store calls. Gateway adoption, runtime secret
injection and integrated two-user evidence are still required.

Update 2026-07-26: EXPORT-02 now streams and inspects untrusted OOXML, rejects
active/external/unsafe content, enforces configurable upload and render
budgets, and proves UK/Unicode text, DOCX heading semantics, tagged PDF
language, active HTTPS links, content-stream reading order and an approved
embedded DejaVu Sans font with synthetic tests. Third-party dependency, font
and original-template notices are reviewed in `THIRD_PARTY_NOTICES.md`.
Independent security/licence review remains an external release gate.

Update 2026-07-26: EXPORT-03 now applies one immutable ordered render model to
both formats, emits real DOCX/PDF list objects, accepts only absolute
credential-free HTTPS links, and shares privacy-bounded title/author/version
metadata. Synthetic tests compare extracted Unicode heading/list text across
DOCX and PDF and prove unsafe or unknown content remains inert plain text.

## Verified responsibility

The service fetches generated text from Document Store, converts it to DOCX
with Apache POI or PDF with OpenPDF, and stores the bytes back in Document
Store. Rendering is in memory; no service-owned temporary files were found.

The upload endpoint accepts a replacement DOCX and then advertises an updated
PDF. In the current implementation that PDF is rendered from the unchanged
stored text, not from the uploaded DOCX, so user edits are not represented.
APP-08 now gives this path a stable Tracker operation key. Export derives
independent replay-safe DOCX and PDF Store keys and reuses a previously written
PDF after a lost response. This closes duplicate-write recovery, not the
separate DOCX-to-PDF fidelity gap.

## Migration evidence

- Source was copied from the untracked service directory in the intact root
  workspace; no standalone source history was available.
- `target/`, the local document-store client JAR, generated binaries, logs,
  databases, exported documents, uploads, recordings, fonts, and environment
  files are excluded.
- The migration-time contract is `contracts/openapi.json`.
- Gitleaks and targeted personal-data checks passed on the source snapshot.
- DOC-02 replaces the `systemPath` document-store client JAR with deterministic
  source generation from an exact revision/checksum-pinned producer contract.
  Contract policy tests, Maven verification and the source-only container build
  now run in CI without a sibling repository, local `libs/` directory or
  preinstalled Job Seeker Copilot artifact.
- OWASP Dependency-Check 12.1.8 completed against the cached 2026-07-18
  advisory database: 53 dependencies, 11 vulnerable dependencies, 140
  vulnerability matches, including 17 Critical and 37 High matches. Results
  require reachability/false-positive triage; the report was not committed.

## Confirmed blockers

1. Gateway and Infrastructure must adopt the implemented Document Export 3.0.0
   service-identity and owner-context contract before ownership is enforced in
   the deployed end-to-end path.
2. The replacement flow stores uploaded DOCX bytes but regenerates PDF from
   stale generated text; it can falsely claim the PDF was updated.
3. `documentKind` is not checked against the stored document type.
4. General exporting of multiple formats is not atomic. Tracker-orchestrated
   replacement writes are now replay-safe, but other partial saves can still
   leave an inconsistent active file set.
5. A real-office DOCX edit/save round trip and integrated two-user denial
   remain outstanding system evidence; local structural/quality and
   service-boundary tests do not replace them.
6. Downstream calls do not share a bounded timeout/retry policy. APP-08 adds
   idempotency to replacement writes only.
7. The Dockerfile lacks a non-root runtime, digest-pinned bases, explicit
    resource constraints, and supply-chain scan evidence.
8. Wider document-generation consumers still need the same reproducible
    contract approach under DOCGEN-02/DOCGEN-03.
9. Current Spring, Tomcat, Jackson, compression, POI, logging, and Swagger UI
    dependency findings include untriaged Critical/High advisories.

## Required validation

Before beta, the service needs trusted identity/ownership, one authoritative
editable content model, consistent DOCX/PDF versions, bounded and inspected
inputs, deterministic accessible rendering, partial-failure recovery,
reproducible clients, dependency/licence evidence, and export security and
quality tests using synthetic fixtures.
