package de.schrell.pdftools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Stellt {@linke BufferedImage}s zu den PDF-Seiten bereit.
 */
class PdfImager implements AutoCloseable {

    private final PDPageTree list;

    private final PDFRenderer renderer;

    private final PDDocument document;

    public PdfImager(final String source) throws IOException {
        this.document = PDDocument.load(new File(source));
        this.list = this.document.getDocumentCatalog().getPages();
        this.renderer = new PDFRenderer(this.document);
    }

    public int getNumberOfPages() {
        return this.list.getCount();
    }

    public BufferedImage convertToImage(final int page) throws IOException {
        return this.renderer.renderImageWithDPI(page, 150);
    }

    @Override
    public void close() throws Exception {
        this.document.close();
    }
}