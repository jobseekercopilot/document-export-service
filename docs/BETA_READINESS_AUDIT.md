# Beta-readiness audit

Audit date: 2026-07-23

Status: **Not ready for private beta**

Update 2026-07-24: DOCGEN-15 now implements the Document Export producer-side
service identity and owner-bound Store calls. Gateway adoption, runtime secret
injection and integrated two-user evidence are still required.

Update 2026-07-26: EXPORT-01 now imports supported edited DOCX text into one new
inactive, owned document version, stores the authoritative DOCX and a PDF
rendered from the same content, validates the document kind and activates the
version only after both files succeed. Stable retry keys replay each Store
step. The final Gateway/client journey and canonical application linking remain
dependent on DOCGEN-14 and DOCGEN-16.

## Verified responsibility

The service fetches generated text from Document Store, converts it to DOCX
with Apache POI or PDF with OpenPDF, and stores the bytes back in Document
Store. Rendering is in memory; no service-owned temporary files were found.

The upload endpoint accepts an application-linked replacement DOCX. Supported
text is imported into a new inactive document version; the original DOCX and a
PDF rendered from that imported text are stored before the version becomes
active. The previous version therefore remains current on conversion or storage
failure.

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

1. Gateway and Infrastructure must adopt the implemented Document Export 2.0.0
   service-identity and owner-context contract before ownership is enforced in
   the deployed end-to-end path.
2. Gateway and client consumers must adopt the version-return and
   `Idempotency-Key` replacement contract; canonical application linking under
   DOCGEN-16 remains a prerequisite.
3. Exporting multiple formats outside the replacement workflow is not atomic;
   partial saves can leave an
   inconsistent active file set.
4. Upload inspection relies on Document Store for archive bounds and rejection
   of active, embedded and external content. Malware scanning, content policy
   and integrated denial evidence remain incomplete.
5. Render input and output have no service-level length, page, memory, or
   response-size budgets.
6. PDF uses built-in Helvetica and DOCX requests Aptos; Unicode coverage,
   font substitution, licensing, accessibility, and environment consistency
   have not been demonstrated.
7. Replacement tests now extract PDF text and cover DOCX import, wrong-kind,
   partial failure and retry. Real office round trips, links, page breaks,
   large/malicious content and accessibility are still unproven. Integrated
   two-user denial also remains outstanding.
8. Downstream calls do not yet share a bounded timeout/retry policy outside the
   idempotent replacement workflow.
9. Third-party dependency and licence review evidence is incomplete.
10. The Dockerfile lacks a non-root runtime, digest-pinned bases, explicit
   resource constraints, and supply-chain scan evidence.
11. Wider document-generation consumers still need the same reproducible
   contract approach under DOCGEN-02/DOCGEN-03.
12. Current Spring, Tomcat, Jackson, compression, POI, logging, and Swagger UI
   dependency findings include untriaged Critical/High advisories.

## Required validation

Before beta, the service needs trusted identity/ownership, one authoritative
editable content model, consistent DOCX/PDF versions, bounded and inspected
inputs, deterministic accessible rendering, partial-failure recovery,
reproducible clients, dependency/licence evidence, and export security and
quality tests using synthetic fixtures.
