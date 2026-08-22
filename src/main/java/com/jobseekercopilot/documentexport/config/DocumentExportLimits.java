package com.jobseekercopilot.documentexport.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "document-export.limits")
public class DocumentExportLimits {

    @Min(1)
    private long uploadMaxCompressedBytes = 10L * 1024 * 1024;

    @Min(1)
    private long uploadMaxExpandedBytes = 50L * 1024 * 1024;

    @Min(1)
    private long uploadMaxEntryBytes = 10L * 1024 * 1024;

    @Min(1)
    private int uploadMaxEntries = 256;

    @Min(1)
    private int uploadMaxEntryNameCharacters = 240;

    @Min(1)
    private int uploadMaxCompressionRatio = 100;

    @Min(1)
    private long uploadMaxRelationshipBytes = 512L * 1024;

    @Min(1)
    private int renderMaxInputCharacters = 100_000;

    @Min(1)
    private int renderMaxBlocks = 1_000;

    @Min(1)
    private int renderMaxEstimatedPages = 25;

    @Min(1)
    private int renderLinesPerPage = 45;

    @Min(1)
    private int renderCharactersPerLine = 100;

    @Min(1)
    private long renderMaxOutputBytes = 8L * 1024 * 1024;

    @NotNull
    private Duration renderMaxDuration = Duration.ofSeconds(5);

    private String pdfFontPath = "";

    public long getUploadMaxCompressedBytes() {
        return uploadMaxCompressedBytes;
    }

    public void setUploadMaxCompressedBytes(long uploadMaxCompressedBytes) {
        this.uploadMaxCompressedBytes = uploadMaxCompressedBytes;
    }

    public long getUploadMaxExpandedBytes() {
        return uploadMaxExpandedBytes;
    }

    public void setUploadMaxExpandedBytes(long uploadMaxExpandedBytes) {
        this.uploadMaxExpandedBytes = uploadMaxExpandedBytes;
    }

    public long getUploadMaxEntryBytes() {
        return uploadMaxEntryBytes;
    }

    public void setUploadMaxEntryBytes(long uploadMaxEntryBytes) {
        this.uploadMaxEntryBytes = uploadMaxEntryBytes;
    }

    public int getUploadMaxEntries() {
        return uploadMaxEntries;
    }

    public void setUploadMaxEntries(int uploadMaxEntries) {
        this.uploadMaxEntries = uploadMaxEntries;
    }

    public int getUploadMaxEntryNameCharacters() {
        return uploadMaxEntryNameCharacters;
    }

    public void setUploadMaxEntryNameCharacters(
            int uploadMaxEntryNameCharacters) {
        this.uploadMaxEntryNameCharacters = uploadMaxEntryNameCharacters;
    }

    public int getUploadMaxCompressionRatio() {
        return uploadMaxCompressionRatio;
    }

    public void setUploadMaxCompressionRatio(int uploadMaxCompressionRatio) {
        this.uploadMaxCompressionRatio = uploadMaxCompressionRatio;
    }

    public long getUploadMaxRelationshipBytes() {
        return uploadMaxRelationshipBytes;
    }

    public void setUploadMaxRelationshipBytes(
            long uploadMaxRelationshipBytes) {
        this.uploadMaxRelationshipBytes = uploadMaxRelationshipBytes;
    }

    public int getRenderMaxInputCharacters() {
        return renderMaxInputCharacters;
    }

    public void setRenderMaxInputCharacters(int renderMaxInputCharacters) {
        this.renderMaxInputCharacters = renderMaxInputCharacters;
    }

    public int getRenderMaxBlocks() {
        return renderMaxBlocks;
    }

    public void setRenderMaxBlocks(int renderMaxBlocks) {
        this.renderMaxBlocks = renderMaxBlocks;
    }

    public int getRenderMaxEstimatedPages() {
        return renderMaxEstimatedPages;
    }

    public void setRenderMaxEstimatedPages(int renderMaxEstimatedPages) {
        this.renderMaxEstimatedPages = renderMaxEstimatedPages;
    }

    public int getRenderLinesPerPage() {
        return renderLinesPerPage;
    }

    public void setRenderLinesPerPage(int renderLinesPerPage) {
        this.renderLinesPerPage = renderLinesPerPage;
    }

    public int getRenderCharactersPerLine() {
        return renderCharactersPerLine;
    }

    public void setRenderCharactersPerLine(int renderCharactersPerLine) {
        this.renderCharactersPerLine = renderCharactersPerLine;
    }

    public long getRenderMaxOutputBytes() {
        return renderMaxOutputBytes;
    }

    public void setRenderMaxOutputBytes(long renderMaxOutputBytes) {
        this.renderMaxOutputBytes = renderMaxOutputBytes;
    }

    public Duration getRenderMaxDuration() {
        return renderMaxDuration;
    }

    public void setRenderMaxDuration(Duration renderMaxDuration) {
        this.renderMaxDuration = renderMaxDuration;
    }

    public String getPdfFontPath() {
        return pdfFontPath;
    }

    public void setPdfFontPath(String pdfFontPath) {
        this.pdfFontPath = pdfFontPath;
    }
}
