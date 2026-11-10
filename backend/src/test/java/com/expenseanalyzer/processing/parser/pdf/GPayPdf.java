package com.expenseanalyzer.processing.parser.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Builds a synthetic Google Pay statement PDF, one text line per entry; the bundled font has no ₹ glyph, so amounts are written without it. */
public final class GPayPdf {

    private GPayPdf() {
    }

    public static byte[] of(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream ttf = PDDocument.class.getResourceAsStream(
                     "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(doc, ttf);
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 10);
                cs.newLineAtOffset(40, 750);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -14);
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** The three lines of one statement row. */
    public static String[] row(String date, String payee, String amount, String time, String ref, String paidBy) {
        return new String[] {date + " " + payee + " " + amount, time + " UPI Transaction ID: " + ref, paidBy};
    }
}
