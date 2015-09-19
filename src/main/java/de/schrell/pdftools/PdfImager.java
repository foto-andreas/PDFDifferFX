package de.schrell.pdftools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Stellt {@linke BufferedImage}s zu den PDF-Seiten bereit.
 */
class PdfImager implements AutoCloseable {

    private final PDDocument document;

    private final List<PDPage> list;

    public PdfImager(final String source) throws IOException {
        this.document = PDDocument.load(source);
        this.list = this.document.getDocumentCatalog().getAllPages();
    }

    public int getNumberOfPages() {
        return this.list.size();
    }

    public BufferedImage convertToImage(final int page) throws IOException {
        return this.list.get(page).convertToImage();
    }

    @Override
    public void close() throws Exception {
        this.document.close();
    }
}