package de.schrell.pdftools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.sun.pdfview.PDFFile;

/**
 * Statische Methoden für PDF-Dateien.
 */
@SuppressWarnings("nls")
public class PdfProperties {

	/**
	 * Liefert die Anzahl Seiten eines PDF-Files
	 */
	public static int numberOfPages(final String fileName) {
		final File file1 = new File(fileName);
		try (final RandomAccessFile raf1 = new RandomAccessFile(file1, "r");
		     final FileChannel channel1 = raf1.getChannel()) {
			final ByteBuffer buf1 = channel1.map(FileChannel.MapMode.READ_ONLY, 0, channel1.size());
			final PDFFile pdffile1 = new PDFFile(buf1);
			return pdffile1.getNumPages();
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return 0;
	}
}
