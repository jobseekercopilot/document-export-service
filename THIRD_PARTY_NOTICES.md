# Third-party notices

Review date: 2026-07-26

This file records the intentionally selected third-party components and asset
used to build or run Document Export Service. It does not change the
repository's proprietary licence. Versions come from `pom.xml`, its Spring
Boot dependency management and the verified Maven runtime dependency list.
Upstream artefacts retain their own `META-INF` notices.

## Direct application and test dependencies

| Coordinate | Version | Licence |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-starter-web` | 3.2.0 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-validation` | 3.2.0 | Apache-2.0 |
| `org.springframework.boot:spring-boot-starter-actuator` | 3.2.0 | Apache-2.0 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.3.0 | Apache-2.0 |
| `org.openapitools:jackson-databind-nullable` | 0.2.10 | Apache-2.0 |
| `org.apache.poi:poi-ooxml` | 5.2.5 | Apache-2.0 |
| `com.github.librepdf:openpdf` | 1.3.39 | MPL-2.0 or LGPL-2.1; this distribution selects MPL-2.0 |
| `org.projectlombok:lombok` | 1.18.32 | MIT |
| `org.springframework.boot:spring-boot-starter-test` | 3.2.0 | Apache-2.0 |

OpenPDF is used unmodified. Its corresponding source is published by the
OpenPDF project at `https://github.com/LibrePDF/OpenPDF/tree/1.3.39`.

## Resolved runtime families

The verified runtime graph also contains these transitive families:

| Coordinates | Versions | Licence(s) recorded by upstream |
| --- | --- | --- |
| `org.springframework.boot:*`, `org.springframework:*`, `io.micrometer:*` | 3.2.0, 6.1.1, 1.12.0 | Apache-2.0 |
| `com.fasterxml.jackson.*:*` | 2.15.3 | Apache-2.0 |
| `org.apache.tomcat.embed:*` | 10.1.16 | Apache-2.0 |
| `org.hibernate.validator:hibernate-validator`, `jakarta.validation:jakarta.validation-api`, `org.jboss.logging:jboss-logging`, `com.fasterxml:classmate` | 8.0.1.Final, 3.0.2, 3.5.3.Final, 1.6.0 | Apache-2.0 |
| `org.springdoc:*`, `io.swagger.core.v3:*`, `org.webjars:swagger-ui` | 2.3.0, 2.2.19, 5.10.3 | Apache-2.0 |
| `org.apache.poi:*`, `org.apache.xmlbeans:xmlbeans`, `org.apache.commons:*`, `commons-codec:commons-codec`, `commons-io:commons-io`, `com.zaxxer:SparseBitSet` | 5.2.5, 5.2.0, managed versions, 1.16.0, 2.15.0, 1.3 | Apache-2.0 |
| `org.apache.logging.log4j:*`, `org.yaml:snakeyaml` | 2.21.1, 2.2 | Apache-2.0 |
| `org.slf4j:*` | 2.0.9 | MIT |
| `ch.qos.logback:*` | 1.4.11 | EPL-1.0 or LGPL-2.1 |
| `jakarta.annotation:jakarta.annotation-api` | 2.1.1 | EPL-2.0 or GPL-2.0-with-Classpath-exception |
| `jakarta.xml.bind:jakarta.xml.bind-api`, `jakarta.activation:jakarta.activation-api` | 4.0.1, 2.1.2 | Eclipse Distribution License 1.0 |
| `org.hdrhistogram:HdrHistogram` | 2.1.12 | CC0-1.0 or BSD-2-Clause |
| `org.latencyutils:LatencyUtils` | 2.0.3 | CC0-1.0 |
| `com.github.virtuald:curvesapi` | 1.08 | BSD-3-Clause |

The exact graph is reproducible with:

```bash
mvn -o dependency:list -DincludeScope=runtime -DexcludeTransitive=false
```

## Build tools

| Coordinate | Version source | Licence |
| --- | --- | --- |
| `org.openapitools:openapi-generator-maven-plugin` | 7.5.0 | Apache-2.0 |
| `org.apache.maven.plugins:maven-compiler-plugin` | Spring Boot parent | Apache-2.0 |
| `org.springframework.boot:spring-boot-maven-plugin` | 3.2.0 | Apache-2.0 |

## DejaVu Sans runtime font

The container obtains `/usr/share/fonts/dejavu/DejaVuSans.ttf` from Alpine's
`font-dejavu` package. The repository does not contain the font binary. PDF
exports embed only the approved runtime font. DOCX exports name the family but
do not embed it.

### Bitstream Vera Fonts Copyright

Fonts are copyright Bitstream. DejaVu changes are in the public domain. Glyphs
imported from Arev fonts are copyright Tavmjong Bah.

Copyright © 2003 Bitstream, Inc. All Rights Reserved. Bitstream Vera is a
trademark of Bitstream, Inc.

Permission is granted, free of charge, to any person obtaining the fonts and
associated documentation to reproduce and distribute the Font Software,
including use, copying, merging, publishing, distribution and sale, and to
permit others to do so, provided that the copyright, trademark and permission
notices accompany copies of the typefaces.

Modified fonts must be renamed so their names do not contain “Bitstream” or
“Vera”. The Font Software may be sold as part of a larger software package but
not by itself. It is provided “as is”, without warranty. The names of Gnome,
the Gnome Foundation and Bitstream may not be used to promote the Font
Software without prior written authorisation.

Arev-derived glyphs are copyright © 2006 Tavmjong Bah. They carry equivalent
permission and warranty terms; modified fonts must not retain “Tavmjong Bah”
or “Arev” in their names, and that name may not be used for promotion without
prior written authorisation.

The complete unabridged notice for DejaVu Sans is retained at
`licenses/DEJAVU-FONTS-LICENSE.txt`, packaged into the application JAR and
copied to `/usr/share/licenses/font-dejavu/LICENSE` in the runtime image.

## Templates and other assets

The layout in `DocumentTemplate`, `DocxExportService` and `PdfExportService`
is original Job Seeker Copilot code. No third-party template, image, icon,
sample CV, user document or proprietary asset is included.
