package kz.hackalem.wamigos.export.renderer;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

public class PdfTextWriter implements Closeable {

    private static final float MARGIN = 50f;
    private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;

    private final PDDocument document;
    private final PDFont font;
    private PDPage page;
    private PDPageContentStream contentStream;
    private float y;

    public PdfTextWriter(PDDocument document, PDFont font) {
        this.document = document;
        this.font = font;
    }

    public void writeTitle(String text) throws IOException {
        writeText(text, 18f, 25f);
    }

    public void writeHeading(String text) throws IOException {
        ensureSpace(32f);
        y -= 8f;
        writeText(text, 14f, 20f);
    }

    public void writeParagraph(String text) throws IOException {
        writeText(text, 10f, 14f);
        y -= 5f;
    }

    public float currentY() {
        return y;
    }

    @Override
    public void close() throws IOException {
        closeStream();
    }

    private void writeText(String text, float fontSize, float lineHeight) throws IOException {
        for (String line : wrap(text, fontSize)) {
            ensureSpace(lineHeight);
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(MARGIN, y);
            contentStream.showText(line);
            contentStream.endText();
            y -= lineHeight;
        }
    }

    private List<String> wrap(String text, float fontSize) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            appendWrappedParagraph(paragraph, fontSize, lines);
        }
        return lines;
    }

    private void appendWrappedParagraph(String paragraph, float fontSize, List<String> lines) throws IOException {
        if (paragraph.isEmpty()) {
            lines.add("");
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String word : paragraph.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(candidate, fontSize) <= WIDTH) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            appendLongWord(word, fontSize, lines, current);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
    }

    private void appendLongWord(String word, float fontSize, List<String> lines, StringBuilder current) throws IOException {
        for (int index = 0; index < word.length(); index++) {
            String candidate = current.toString() + word.charAt(index);
            if (!current.isEmpty() && width(candidate, fontSize) > WIDTH) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(word.charAt(index));
        }
    }

    private float width(String value, float fontSize) throws IOException {
        return font.getStringWidth(value) / 1_000f * fontSize;
    }

    private void ensureSpace(float required) throws IOException {
        if (page == null || y - required < MARGIN) {
            newPage();
        }
    }

    private void newPage() throws IOException {
        closeStream();
        page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        contentStream = new PDPageContentStream(document, page);
        y = PDRectangle.A4.getHeight() - MARGIN;
    }

    private void closeStream() throws IOException {
        if (contentStream != null) {
            contentStream.close();
            contentStream = null;
        }
    }
}
