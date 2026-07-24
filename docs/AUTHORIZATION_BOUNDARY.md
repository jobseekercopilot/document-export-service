# Document Export authorization boundary

Status: producer boundary implemented for DOCGEN-15; Gateway consumer,
Infrastructure injection and integrated two-user evidence remain incomplete.

## Trusted request

Document Export is an internal service. Its public export and replacement
routes accept only the approved Document Generation Gateway identity:

- exactly one `X-Service-Token` must match `DOCUMENT_EXPORT_GATEWAY_TOKEN`;
- exactly one nonblank `X-Document-Owner` must be present;
- a missing, invalid or duplicated service credential fails with `401`;
- a missing or duplicated owner context fails with `400`;
- raw document or file UUIDs are locators and never establish ownership.

The Gateway is responsible for validating the platform access token and
binding its subject as the owner. A browser-supplied owner header has no
authority because requests without the Gateway credential are rejected.

## Document Store calls

The validated owner is carried exactly once on every Store call:

| Operation | Store credential | Owner context |
| --- | --- | --- |
| Read generated document | Reader | `X-Document-Owner` |
| Read latest file metadata | Reader | `X-Document-Owner` |
| Save rendered DOCX/PDF | Producer | `X-Document-Owner` |
| Upload replacement file | Producer | `X-Document-Owner` |

The reader credential cannot mutate files. The producer credential is not
used for read-only operations. All three service credentials contain at least
32 bytes, are pairwise distinct and fail startup when missing or invalid.

## Runtime configuration

| Variable | Purpose |
| --- | --- |
| `DOCUMENT_EXPORT_GATEWAY_TOKEN` | Authenticate the approved Gateway caller |
| `DOCUMENT_STORE_PRODUCER_TOKEN` | Save generated and replacement files |
| `DOCUMENT_STORE_READER_TOKEN` | Read owner-scoped document/file metadata |

Infrastructure owns injection and rotation. Values must not appear in Git,
Compose defaults, image layers, workflow logs or shell history.

## Remaining dependencies

- Document Generation Gateway must consume the Document Export 2.0.0 contract,
  send its dedicated credential and derive exactly one owner from the
  validated platform token subject.
- Infrastructure must inject and rotate all credentials without defaults.
- Integrated tests must run the Gateway → Export → Store path for one owner
  and prove the same document and file IDs are non-enumerating for another.
- DOCGEN-15 and EXPORT-01..03 still own content authority, atomicity, hostile
  input, resource limits, rendering fidelity and accessibility.
