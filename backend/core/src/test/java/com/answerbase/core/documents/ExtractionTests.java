package com.answerbase.core.documents;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractionTests {
    @TempDir Path directory;

    @Test
    void scannedImageWithoutTextFailsWithActionableReason() throws Exception {
        try (var pdf = new PDDocument();
                var out = new java.io.ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            var raster =
                    new java.awt.image.BufferedImage(
                            600, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = raster.createGraphics();
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, 600, 400);
            graphics.setColor(java.awt.Color.BLACK);
            graphics.drawString("Scanned document with no text layer", 20, 40);
            graphics.dispose();
            try (var stream = new PDPageContentStream(pdf, page)) {
                stream.drawImage(
                        org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(
                                pdf, raster),
                        0,
                        0);
            }
            pdf.save(out);
            var error =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            java.io.IOException.class,
                            () ->
                                    new TextExtractor()
                                            .extract(out.toByteArray(), "application/pdf"));
            org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("OCR"));
        }
    }

    @Test
    void originalRoundTripAndTraversalProtection() throws Exception {
        var storage = new LocalDocumentStorage(directory);
        byte[] bytes = "Original content".getBytes(StandardCharsets.UTF_8);
        storage.put("org/workspace/version/original", bytes, "text/plain");
        assertThat(storage.get("org/workspace/version/original")).isEqualTo(bytes);
        assertThatThrownBy(() -> storage.get("../secret")).isInstanceOf(java.io.IOException.class);
        storage.delete("org/workspace/version/original");
        assertThatThrownBy(() -> storage.get("org/workspace/version/original"))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void pdfPagesAndTextLineReferencesArePreserved() throws Exception {
        var extractor = new TextExtractor();
        try (var pdf = new PDDocument();
                var out = new java.io.ByteArrayOutputStream()) {
            for (String text : java.util.List.of("Page one policy", "Page two policy")) {
                var page = new PDPage();
                pdf.addPage(page);
                try (var stream = new PDPageContentStream(pdf, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(20, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            pdf.save(out);
            var chunks = extractor.extract(out.toByteArray(), "application/pdf");
            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
            assertThat(chunks.get(1).pageNumber()).isEqualTo(2);
            assertThat(chunks.get(1).content()).contains("Page two policy");
        }
        var chunks =
                extractor.extract(
                        "Header\nPolicy text".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertThat(chunks.getFirst().startLine()).isEqualTo(1);
        assertThat(chunks.getFirst().endLine()).isEqualTo(2);
        var split =
                extractor.extract(
                        ("a".repeat(1800) + "\n" + "b".repeat(400))
                                .getBytes(StandardCharsets.UTF_8),
                        "text/plain");
        assertThat(split).hasSize(2);
        assertThat(split.get(0).endLine()).isEqualTo(1);
        // Long paragraphs overlap; the second passage includes the tail of line one.
        assertThat(split.get(1).startLine()).isEqualTo(1);
        assertThat(split.get(1).endLine()).isEqualTo(2);
        assertThatThrownBy(() -> extractor.extract(new byte[] {(byte) 0xff}, "text/plain"))
                .hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> extractor.extract(new byte[0], "text/plain"))
                .hasMessageContaining("No readable text");
    }
}
