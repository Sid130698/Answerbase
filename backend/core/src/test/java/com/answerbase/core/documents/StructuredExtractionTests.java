package com.answerbase.core.documents;

import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.jupiter.api.Test;

class StructuredExtractionTests {
    private byte[] docx(String xml) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void extractsWordHeadingsAndTablesWithoutFetchingRelationships() throws Exception {
        String xml =
                """
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Refunds</w:t></w:r></w:p>
        <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Annual</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>30 days</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
        </w:body></w:document>
        """;
        var parsed =
                new TextExtractor()
                        .parse(
                                docx(xml),
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(parsed.text()).contains("# Refunds", "Annual | 30 days");
        assertThat(parsed.chunks())
                .allSatisfy(
                        c -> {
                            assertThat(c.sectionPath()).isEqualTo("Refunds");
                            assertThat(parsed.text().substring(c.startOffset(), c.endOffset()))
                                    .isEqualTo(c.content());
                        });
    }

    @Test
    void rejectsEntitiesAndZipBombs() throws Exception {
        String type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        byte[] external =
                docx("<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><foo>&xxe;</foo>");
        assertThatThrownBy(() -> new TextExtractor().parse(external, type))
                .isInstanceOf(IOException.class);
        byte[] bomb = docx("x".repeat(21 * 1024 * 1024));
        assertThatThrownBy(() -> new TextExtractor().parse(bomb, type))
                .hasMessageContaining("20 MB");
    }

    @Test
    void htmlIsExtractedAsInertTextAndOffsetsSurviveUnicode() throws Exception {
        String html =
                "<h1>Policy</h1><p>Refunds within 30 days.</p><script>stealSecrets()</script><table><tr><td>Plan</td><td>Annual</td></tr></table>";
        var parsed = new TextExtractor().parse(html.getBytes(StandardCharsets.UTF_8), "text/html");
        assertThat(parsed.text())
                .contains("# Policy", "Plan | Annual")
                .doesNotContain("stealSecrets");
        var longText =
                new TextExtractor(600)
                        .parse(
                                ("# Policy\n\n" + "😀 refund policy ".repeat(200))
                                        .getBytes(StandardCharsets.UTF_8),
                                "text/markdown");
        assertThat(longText.chunks())
                .allSatisfy(
                        c -> {
                            assertThat(longText.text().substring(c.startOffset(), c.endOffset()))
                                    .isEqualTo(c.content());
                            assertThat(Character.isLowSurrogate(c.content().charAt(0))).isFalse();
                            assertThat(
                                            Character.isHighSurrogate(
                                                    c.content().charAt(c.content().length() - 1)))
                                    .isFalse();
                        });
    }
}
