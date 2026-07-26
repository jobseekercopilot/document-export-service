package com.jobseekercopilot.documentexport.service;

import com.jobseekercopilot.documentexport.config.DocumentExportLimits;
import com.jobseekercopilot.documentexport.exception.DocumentExportException;
import com.lowagie.text.pdf.BaseFont;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExportFontProvider {

    public static final String DOCX_FONT_FAMILY = "DejaVu Sans";
    private static final String APPROVED_FONT_FILE = "DejaVuSans.ttf";
    private static final List<Path> APPROVED_RUNTIME_PATHS = List.of(
            Path.of("/usr/share/fonts/TTF/DejaVuSans.ttf"),
            Path.of("/usr/share/fonts/dejavu/DejaVuSans.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));

    private final DocumentExportLimits limits;
    private volatile BaseFont pdfFont;

    public ExportFontProvider(DocumentExportLimits limits) {
        this.limits = limits;
    }

    public BaseFont pdfFont() {
        BaseFont current = pdfFont;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (pdfFont == null) {
                pdfFont = loadApprovedFont(resolveApprovedFont());
            }
            return pdfFont;
        }
    }

    Path resolveApprovedFont() {
        String configured = limits.getPdfFontPath();
        if (configured != null && !configured.isBlank()) {
            return requireApprovedFile(Path.of(configured));
        }
        return APPROVED_RUNTIME_PATHS.stream()
                .filter(Files::isRegularFile)
                .filter(Files::isReadable)
                .findFirst()
                .map(this::requireApprovedFile)
                .orElseThrow(() -> new DocumentExportException(
                        "Approved DejaVu Sans runtime font is unavailable"));
    }

    private Path requireApprovedFile(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!APPROVED_FONT_FILE.equals(absolute.getFileName().toString())
                || !Files.isRegularFile(absolute)
                || !Files.isReadable(absolute)) {
            throw new DocumentExportException(
                    "Configured PDF font is not the approved DejaVu Sans file");
        }
        return absolute;
    }

    private BaseFont loadApprovedFont(Path path) {
        try {
            BaseFont font = BaseFont.createFont(
                    path.toString(),
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED);
            if (!"DejaVuSans".equals(font.getPostscriptFontName())) {
                throw new DocumentExportException(
                        "Configured PDF font does not identify as DejaVu Sans");
            }
            return font;
        } catch (DocumentExportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentExportException(
                    "Approved PDF font could not be loaded",
                    exception);
        }
    }
}
