package com.jobseekercopilot.documentexport.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessibleTextTest {

    @Test
    void createsLinksOnlyForAbsoluteCredentialFreeHttpsUrls() {
        List<AccessibleText.Segment> segments = AccessibleText.segments("""
                Safe https://example.test/profile.
                Upper HTTPS://EXAMPLE.TEST/path?q=one.
                Plain http://example.test/old.
                Plain javascript:alert(1).
                Plain https://user:secret@example.test/private.
                Plain https:///missing-host.
                """);

        assertEquals(
                List.of(
                        "https://example.test/profile",
                        "HTTPS://EXAMPLE.TEST/path?q=one"),
                segments.stream()
                        .filter(AccessibleText.Segment::isLink)
                        .map(AccessibleText.Segment::url)
                        .toList());
    }

    @Test
    void preservesUnknownAndUnsafeContentAsPlainText() {
        String value = "Unknown [label](custom:target) and "
                + "https://user:secret@example.test/private.";

        List<AccessibleText.Segment> segments =
                AccessibleText.segments(value);

        assertEquals(value, segments.stream()
                .map(AccessibleText.Segment::text)
                .reduce("", String::concat));
        assertEquals(0, segments.stream()
                .filter(AccessibleText.Segment::isLink)
                .count());
    }
}
