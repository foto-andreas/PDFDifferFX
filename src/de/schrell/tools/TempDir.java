package de.schrell.tools;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Original Source from here:
 * http://stackoverflow.com/questions/617414/create-a-
 * temporary-directory-in-java
 *
 * @author Keith Sheppard and Additions from Andreas Schrell
 */
public class TempDir {

    /**
     * Create a new temporary directory. Use something like
     * {@link #recursiveDelete(File)} to clean this directory up since it isn't
     * deleted automatically
     *
     * @param prefix
     *            Prefix for the directory name
     *
     * @return the new directory
     */
    public static File createTempDir(final String prefix) throws IOException {
        final File sysTempDir = new File(System.getProperty("java.io.tmpdir"));
        File newTempDir;
        final int maxAttempts = 9;
        int attemptCount = 0;
        do {
            attemptCount++;
            if (attemptCount > maxAttempts) {
                throw new IOException(
                    "The highly improbable has occurred! Failed to create a unique temporary directory after "
                        + maxAttempts + " attempts.");
            }
            final String dirName = prefix + UUID.randomUUID().toString();
            newTempDir = new File(sysTempDir, dirName);
        } while (newTempDir.exists());
        if (newTempDir.mkdirs()) {
            deleteOnExitRecursive(newTempDir);
            return newTempDir;
        } else {
            throw new IOException("Failed to create temp dir named " + newTempDir.getAbsolutePath());
        }
    }

    /**
     * This Method adds a shutdiwn hook to delete the temporary directory
     * recursively
     *
     * @param file
     *            the directory to delete
     */
    public static void deleteOnExitRecursive(final File file) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> recursiveDelete(file)));
    }

    /**
     * Recursively delete file or directory
     *
     * @param fileOrDir the file or dir to delete
     * @return true iff all files are successfully deleted
     */
    public static boolean recursiveDelete(final File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            // recursively delete contents
            for (final File innerFile : fileOrDir.listFiles()) {
                if (!recursiveDelete(innerFile)) {
                    return false;
                }
            }
        }
        return fileOrDir.delete();
    }

}
