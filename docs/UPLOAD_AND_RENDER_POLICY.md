# Upload and render policy

This policy defines the service-side safety boundary for untrusted replacement
DOCX files and generated DOCX/PDF exports. Runtime operators may lower these
limits, but raising them requires a new resource and security review.

## DOCX upload boundary

The HTTP multipart boundary accepts one `.docx` file with the registered DOCX
media type. The service then streams and inspects the OOXML ZIP before any
downstream write:

| Limit | Default |
| --- | ---: |
| Compressed upload | 10 MiB |
| Total expanded content | 50 MiB |
| One expanded ZIP entry | 10 MiB |
| ZIP entries | 256 |
| Entry-name length | 240 characters |
| Entry or package compression ratio | 100:1 |
| Captured content-type or relationship metadata | 512 KiB |

Entry names must be relative, forward-slash paths without drive prefixes,
backslashes, NULs, `.`/`..` traversal segments or case-insensitive duplicates.
The package must contain `[Content_Types].xml` and `word/document.xml`, and
Apache POI must be able to open its OOXML structure.

The service rejects:

- macro-enabled content, `vbaProject.bin` and VBA data;
- ActiveX, embedded objects, custom UI, executables, JavaScript and JAR entries;
- every OOXML relationship with `TargetMode="External"`;
- malformed relationship XML, including DTD/entity input;
- malformed, incomplete, over-budget or suspiciously compressed packages.

This inspection is a deterministic application-layer control, not a malware
scanner. A future production malware-scanning requirement must be handled as a
separate infrastructure dependency if the deployment risk assessment requires
one.

## Render boundary

| Limit | Default |
| --- | ---: |
| Title plus content | 100,000 Unicode code points |
| Template blocks | 1,000 |
| Estimated or actual PDF pages | 25 |
| Page estimate | 45 lines, 100 code points per line |
| Rendered output | 8 MiB |
| Elapsed render time | 5 seconds |

Both renderers use a bounded in-memory output stream. Input characters, parsed
blocks and estimated pages are checked before rendering; the deadline is
checked as blocks are added and after serialisation; PDF page count is checked
after parsing the final bytes. These controls cap service-owned memory growth
from input and output. The deployment must additionally set process/container
CPU and memory limits because Java libraries can allocate internal objects that
an application byte counter cannot observe.

All limits are configurable through the `DOCUMENT_EXPORT_UPLOAD_*` and
`DOCUMENT_EXPORT_RENDER_*` environment variables listed in
`src/main/resources/application.yml`.

## Accessible output and fonts

- `DocumentTemplate` is the format-neutral render model. It classifies the
  candidate title/contact header, known CV section headings, role headings,
  paragraphs and contiguous bullet items once; DOCX and PDF consume the same
  immutable ordered block list.
- DOCX paragraphs use `Title`, `Heading1`, `Heading2`, `Normal` and
  `ListParagraph` semantics, use real OOXML bullet numbering, and mark runs as
  `en-GB`.
- PDF output is tagged, carries catalog language `en-GB`, embeds a Unicode
  character map, maps titles/headings/lists/paragraphs to `H1`/`H2`/`H3`,
  `L`/`LI` and `P` structure elements, and preserves body content-stream
  order. The repeated brand footer and accent rule are marked as artefacts so
  they do not interrupt assistive navigation or semantic text extraction.
- Only syntactically valid, absolute `https://` URLs with a host and without
  embedded credentials become active links. HTTP, active schemes, malformed
  URLs and credential-bearing HTTPS candidates remain inert plain text.
- Unrecognised content is never interpreted as markup or active content. It
  remains escaped plain paragraph text in the shared block order.
- Synthetic regression fixtures cover UK addresses, curly punctuation, the
  pound sign, Latin extended characters, headings, lists, safe/unsafe links and
  equivalent DOCX/PDF text extraction.
- DOCX requests `DejaVu Sans` for Latin, East Asian, complex-script and
  high-ANSI ranges. Office applications may substitute a locally available
  accessible font when DejaVu Sans is not installed.
- PDF fails closed unless the reviewed `DejaVuSans.ttf` runtime asset is
  available. It embeds the used glyph subset with Identity-H encoding and a
  ToUnicode map.

The container installs DejaVu Sans from Alpine's `font-dejavu` package at
`/usr/share/fonts/dejavu/DejaVuSans.ttf`. No font binary is stored in this
repository. The runtime path can be set with
`DOCUMENT_EXPORT_PDF_FONT_PATH`, but the file must be named
`DejaVuSans.ttf` and identify internally as DejaVu Sans. See
`THIRD_PARTY_NOTICES.md` for the font notice.

## Metadata and privacy

DOCX and PDF use the same bounded metadata model:

| Field | Exported value |
| --- | --- |
| Title | The visible stored document title, collapsed to one line and capped at 200 Unicode code points; otherwise `Generated document` |
| Author | `Job Seeker Copilot` |
| Creator | The generic Job Seeker Copilot export service |
| Subject/description | Generic accessible, owner-controlled export wording |
| Version | The positive stored document version, when supplied |

The metadata model never copies owner/user IDs, job or application IDs,
creator identity, source filename, contact details or document body content.
The title is deliberately retained because it is already visible document
content; callers must therefore continue to use a user-approved, bounded
document title. Invalid or absent versions are omitted rather than inferred.
