package com.jobseekercopilot.documentexport;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThirdPartyNoticePolicyTest {

    private static final List<String> REVIEWED_COMPONENTS = List.of(
            "org.springframework.boot:spring-boot-starter-web",
            "org.springframework.boot:spring-boot-starter-validation",
            "org.springframework.boot:spring-boot-starter-actuator",
            "org.springdoc:springdoc-openapi-starter-webmvc-ui",
            "org.openapitools:jackson-databind-nullable",
            "org.apache.poi:poi-ooxml",
            "com.github.librepdf:openpdf",
            "org.projectlombok:lombok",
            "org.springframework.boot:spring-boot-starter-test",
            "org.openapitools:openapi-generator-maven-plugin",
            "org.apache.maven.plugins:maven-compiler-plugin",
            "org.springframework.boot:spring-boot-maven-plugin");

    @Test
    void noticeCoversEveryDirectDependencyAndBuildPlugin()
            throws Exception {
        String notice;
        try (InputStream input = ThirdPartyNoticePolicyTest.class
                .getResourceAsStream(
                        "/META-INF/THIRD_PARTY_NOTICES.md")) {
            assertTrue(
                    input != null,
                    "Notice must be packaged with the runtime artefact");
            notice = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8);
        }

        for (String component : REVIEWED_COMPONENTS) {
            assertTrue(
                    notice.contains(component),
                    "Missing reviewed notice for " + component);
        }
        assertTrue(notice.contains("DejaVuSans.ttf"));
        assertTrue(notice.contains("Bitstream Vera Fonts Copyright"));
        try (InputStream licence = ThirdPartyNoticePolicyTest.class
                .getResourceAsStream(
                        "/META-INF/licenses/DEJAVU-FONTS-LICENSE.txt")) {
            assertTrue(
                    licence != null,
                    "Full DejaVu Sans licence must be packaged");
            String licenceText = new String(
                    licence.readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(licenceText.contains(
                    "Copyright (c) 2003 by Bitstream, Inc."));
            assertTrue(licenceText.contains(
                    "Copyright (c) 2006 by Tavmjong Bah."));
        }
    }

    @Test
    void repositoryDoesNotCommitFontBinaries() throws Exception {
        try (var paths = Files.walk(Path.of("."))) {
            List<Path> fonts = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith("./target"))
                    .filter(path -> {
                        String name = path.getFileName()
                                .toString()
                                .toLowerCase();
                        return name.endsWith(".ttf")
                                || name.endsWith(".otf")
                                || name.endsWith(".woff")
                                || name.endsWith(".woff2");
                    })
                    .toList();
            assertTrue(
                    fonts.isEmpty(),
                    "Font binaries must be supplied by the reviewed "
                            + "runtime package, not committed: "
                            + fonts);
        }
    }
}
