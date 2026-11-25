package com.answerbase.core.documents;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

public final class TextExtractor {
    public record Chunk(
            int ordinal, Integer pageNumber, Integer startLine, Integer endLine, String content) {}

    public static final int MAX_CHARS = 1_000_000;

    public List<Chunk> extract(byte[] bytes, String mediaType) throws IOException {
        var result = new ArrayList<Chunk>();
        if (mediaType.equals("application/pdf")) {
            try (var pdf = Loader.loadPDF(bytes)) {
                if (pdf.isEncrypted())
                    throw new IOException(
                            "Encrypted PDFs are not supported. Upload an unlocked copy.");
                if (pdf.getNumberOfPages() > 200)
                    throw new IOException("PDFs are limited to 200 pages.");
                int total = 0;
                var stripper = new PDFTextStripper();
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String text = stripper.getText(pdf).replace("\u0000", "");
                    total += text.length();
                    if (total > MAX_CHARS)
                        throw new IOException(
                                "Extracted text exceeds the one-million-character limit.");
                    for (int start = 0; start < text.length(); start += 1800) {
                        String part =
                                text.substring(start, Math.min(start + 2000, text.length()))
                                        .strip();
                        if (!part.isBlank())
                            result.add(new Chunk(result.size(), page, null, null, part));
                    }
                }
            }
        } else {
            String text;
            try {
                text =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes))
                                .toString();
            } catch (CharacterCodingException e) {
                throw new IOException("Text files must use UTF-8 encoding.");
            }
            if (text.length() > MAX_CHARS)
                throw new IOException("Text exceeds the one-million-character limit.");
            if (text.indexOf(0) >= 0)
                throw new IOException("Binary files are not supported as text.");
            String[] lines = text.split("\\R", -1);
            StringBuilder part = new StringBuilder();
            int first = 1;
            int last = 1;
            for (int line = 0; line < lines.length; line++) {
                // Split long lines too, preserving their original line number.
                for (int offset = 0; offset < Math.max(1, lines[line].length()); offset += 1800) {
                    String fragment =
                            lines[line].substring(
                                    offset, Math.min(offset + 1800, lines[line].length()));
                    if (part.length() + fragment.length() > 2000 && !part.isEmpty()) {
                        result.add(new Chunk(result.size(), null, first, last, part.toString()));
                        part.setLength(0);
                    }
                    if (part.isEmpty()) first = line + 1;
                    part.append(fragment).append('\n');
                    last = line + 1;
                }
            }
            if (!part.toString().isBlank())
                result.add(new Chunk(result.size(), null, first, last, part.toString()));
        }
        if (result.isEmpty())
            throw new IOException(
                    "No readable text found. Scanned PDFs need OCR, which is not supported yet.");
        return result;
    }
}
