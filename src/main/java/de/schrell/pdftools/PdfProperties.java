package de.schrell.pdftools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.sun.pdfview.PDFFile;

/**
 * Some static methods for pdf files
 * 
 * @author Andreas Schrell
 * 
 */
public class PdfProperties {

	/**
	 * tells the number of pages in a pdf file
	 * 
	 * @param fileName
	 * @return the number of pages
	 */
	public static int numberOfPages(String fileName) {
		File file1 = new File(fileName);
		RandomAccessFile raf1;
		try {
			raf1 = new RandomAccessFile(file1, "r");
			FileChannel channel1 = raf1.getChannel();
			ByteBuffer buf1 = channel1.map(FileChannel.MapMode.READ_ONLY, 0,
					channel1.size());
			PDFFile pdffile1 = new PDFFile(buf1);
			return pdffile1.getNumPages();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}
}
