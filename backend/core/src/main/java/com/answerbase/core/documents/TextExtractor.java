package com.answerbase.core.documents;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.w3c.dom.*;

/** No network, scripts, macros or external entities are executed by these parsers. */
public final class TextExtractor {
    public record Chunk(
            int ordinal,
            Integer pageNumber,
            Integer startLine,
            Integer endLine,
            String content,
            String sectionPath,
            int startOffset,
            int endOffset) {}

    public record Parsed(String text, List<Chunk> chunks) {}

    private record Block(int start, int end, Integer page, String section) {}

    public static final int MAX_CHARS = 1_000_000;
    private static final String WORD =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private final int chunkChars;

    public TextExtractor() {
        this(1800);
    }

    public TextExtractor(int chunkChars) {
        if (chunkChars < 600 || chunkChars > 4000)
            throw new IllegalArgumentException("Chunk size must be 600–4000 characters");
        this.chunkChars = chunkChars;
    }

    public List<Chunk> extract(byte[] bytes, String mediaType) throws IOException {
        return parse(bytes, mediaType).chunks();
    }

    public Parsed parse(byte[] bytes, String mediaType) throws IOException {
        StringBuilder source = new StringBuilder();
        var blocks = new ArrayList<Block>();
        if (mediaType.equals("application/pdf")) {
            try (var pdf = Loader.loadPDF(bytes)) {
                if (pdf.isEncrypted())
                    throw new IOException(
                            "Encrypted PDFs are not supported. Upload an unlocked copy.");
                if (pdf.getNumberOfPages() > 200)
                    throw new IOException("PDFs are limited to 200 pages.");
                var stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    appendText(source, blocks, stripper.getText(pdf).replace("\u0000", ""), page);
                }
            }
        } else if (mediaType.equals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            appendText(source, blocks, docx(bytes), null);
        } else {
            String text = utf8(bytes);
            if (mediaType.equals("text/html")) {
                var html = Jsoup.parse(text);
                html.select("script,style,iframe,object,embed,template,noscript,svg").remove();
                StringBuilder readable = new StringBuilder();
                for (var element : html.body().select("h1,h2,h3,h4,h5,h6,p,li,tr,pre")) {
                    if (element.parents().stream()
                            .anyMatch(p -> Set.of("li", "tr", "pre").contains(p.tagName())))
                        continue;
                    String value =
                            element.tagName().equals("tr")
                                    ? String.join(" | ", element.select("th,td").eachText())
                                    : element.wholeText().strip();
                    if (element.tagName().matches("h[1-6]"))
                        value =
                                "#".repeat(Integer.parseInt(element.tagName().substring(1)))
                                        + " "
                                        + value;
                    if (element.tagName().equals("li")) value = "- " + value;
                    readable.append(value).append("\n\n");
                }
                text = readable.isEmpty() ? html.body().text() : readable.toString();
            }
            appendText(source, blocks, text, null);
        }
        String text = source.toString();
        var chunks = chunk(text, blocks);
        if (chunks.isEmpty())
            throw new IOException(
                    "No readable text found. Scanned PDFs need OCR, which is not supported yet.");
        return new Parsed(text, chunks);
    }

    private String utf8(byte[] bytes) throws IOException {
        try {
            String text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
            if (text.indexOf(0) >= 0)
                throw new IOException("Binary files are not supported as text.");
            return text;
        } catch (CharacterCodingException e) {
            throw new IOException("Text files must use UTF-8 encoding.");
        }
    }

    private void appendText(StringBuilder source, List<Block> blocks, String input, Integer page)
            throws IOException {
        String text = input.replace("\r\n", "\n").replace('\r', '\n');
        if (source.length() + text.length() + 1 > MAX_CHARS)
            throw new IOException("Extracted text exceeds the one-million-character limit.");
        int base = source.length();
        source.append(text).append('\n');
        String[] headings = new String[6];
        var matcher =
                java.util.regex.Pattern.compile(
                                "(?m)^#{1,6}\\s+.+$|(?:[^\\n]+(?:\\n(?!\\n|#{1,6}\\s)[^\\n]+)*)")
                        .matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (value.isBlank()) continue;
            if (value.matches("(?s)^#{1,6}\\s+.*")) {
                int level = value.indexOf(' ');
                if (level > 0 && level <= 6) {
                    headings[level - 1] = value.substring(level).strip();
                    Arrays.fill(headings, level, 6, null);
                }
            }
            String section =
                    String.join(" > ", Arrays.stream(headings).filter(Objects::nonNull).toList());
            blocks.add(new Block(base + matcher.start(), base + matcher.end(), page, section));
        }
    }

    private List<Chunk> chunk(String text, List<Block> blocks) {
        var chunks = new ArrayList<Chunk>();
        int start = -1, end = 0;
        Integer page = null;
        String section = "";
        for (var block : blocks) {
            if (start >= 0
                    && (!Objects.equals(page, block.page())
                            || !section.equals(block.section())
                            || block.end() - start > chunkChars)) {
                add(chunks, text, start, end, page, section);
                start = -1;
            }
            int pos = block.start();
            while (block.end() - pos > chunkChars) {
                int cut = pos + chunkChars;
                int boundary = text.lastIndexOf(' ', cut);
                if (boundary > pos + chunkChars / 2) cut = boundary;
                if (Character.isHighSurrogate(text.charAt(cut - 1))) cut--;
                add(chunks, text, pos, cut, block.page(), block.section());
                pos = Math.max(pos + 1, cut - 160);
                if (Character.isLowSurrogate(text.charAt(pos))) pos++;
            }
            if (start < 0) {
                start = pos;
                page = block.page();
                section = block.section();
            }
            end = block.end();
        }
        if (start >= 0) add(chunks, text, start, end, page, section);
        return chunks;
    }

    private void add(
            List<Chunk> chunks, String text, int start, int end, Integer page, String section) {
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        if (start == end) return;
        int first = 1, last = 1;
        for (int i = 0; i < end; i++)
            if (text.charAt(i) == '\n') {
                last++;
                if (i < start) first++;
            }
        chunks.add(
                new Chunk(
                        chunks.size(),
                        page,
                        page == null ? first : null,
                        page == null ? last : null,
                        text.substring(start, end),
                        section,
                        start,
                        end));
    }

    private String docx(byte[] bytes) throws IOException {
        byte[] document = null;
        long total = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 1000) throw new IOException("DOCX has too many archive entries.");
                var out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (total > 20L * 1024 * 1024)
                        throw new IOException("DOCX expanded content exceeds 20 MB.");
                    if (entry.getName().equals("word/document.xml")) out.write(buffer, 0, count);
                }
                if (entry.getName().equals("word/document.xml")) document = out.toByteArray();
            }
        }
        if (document == null) throw new IOException("Not a valid DOCX document.");
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var xml = factory.newDocumentBuilder().parse(new ByteArrayInputStream(document));
            StringBuilder result = new StringBuilder();
            var body = xml.getElementsByTagNameNS(WORD, "body").item(0);
            if (body == null) throw new IOException("DOCX has no document body.");
            for (Node n = body.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element element && "p".equals(n.getLocalName())) {
                    var styles = element.getElementsByTagNameNS(WORD, "pStyle");
                    if (styles.getLength() > 0) {
                        String style = ((Element) styles.item(0)).getAttributeNS(WORD, "val");
                        if (style.matches("(?i)heading[1-6]"))
                            result.append(
                                            "#"
                                                    .repeat(
                                                            Integer.parseInt(
                                                                    style.substring(
                                                                            style.length() - 1))))
                                    .append(' ');
                    }
                    if (element.getElementsByTagNameNS(WORD, "numPr").getLength() > 0)
                        result.append("- ");
                    result.append(wordText(element)).append("\n\n");
                } else if (n instanceof Element table && "tbl".equals(n.getLocalName())) {
                    var rows = table.getElementsByTagNameNS(WORD, "tr");
                    for (int r = 0; r < rows.getLength(); r++) {
                        var cells = ((Element) rows.item(r)).getElementsByTagNameNS(WORD, "tc");
                        for (int c = 0; c < cells.getLength(); c++) {
                            if (c > 0) result.append(" | ");
                            result.append(wordText((Element) cells.item(c)));
                        }
                        result.append('\n');
                    }
                    result.append('\n');
                }
                if (result.length() > MAX_CHARS)
                    throw new IOException(
                            "Extracted text exceeds the one-million-character limit.");
            }
            return result.toString();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unable to parse DOCX safely.");
        }
    }

    private String wordText(Element element) {
        StringBuilder text = new StringBuilder();
        var nodes = element.getElementsByTagNameNS(WORD, "t");
        for (int i = 0; i < nodes.getLength(); i++) text.append(nodes.item(i).getTextContent());
        return text.toString();
    }
}
