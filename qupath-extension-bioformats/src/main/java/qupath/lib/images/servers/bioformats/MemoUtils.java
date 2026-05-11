package qupath.lib.images.servers.bioformats;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for working with Bio-Formats memoization.
 */
class MemoUtils {

    private static final Logger logger = LoggerFactory.getLogger(MemoUtils.class);

    /**
     * Temporary directory for storing memoization files
     */
    private static File dirMemoTemp = null;

    /**
     * Set of created temp memo files
     */
    private static final Set<File> tempMemoFiles = new HashSet<>();


    /**
     * Get a temporary directory to use for memoization, creating it if it does not already exist.
     * @return a temporary directory
     * @throws IOException if the directory could not be created
     */
    static File createTempMemoDir() throws IOException {
        return getTempMemoDir(true);
    }

    private static File getTempMemoDir(boolean createIfNeeded) throws IOException {
        if (createIfNeeded && dirMemoTemp == null) {
            synchronized (ReaderPool.class) {
                if (dirMemoTemp == null) {
                    Path path = Files.createTempDirectory("qupath-memo-");
                    dirMemoTemp = path.toFile();
                    Runtime.getRuntime().addShutdownHook(new Thread() {
                        @Override
                        public void run() {
                            deleteTempMemoFiles();
                        }
                    });
                    logger.warn("Temp memoization directory created at {}", dirMemoTemp);
                    logger.warn("If you want to avoid this warning, either specify a memoization directory in the preferences or turn off memoization by setting the time to < 0");
                }
            }
        }
        return dirMemoTemp;
    }

    /**
     * Register a temp memoization file so that it should be deleted when QuPath is shut down.
     * @param file the file to delete
     */
    static void registerTempFileForDeletion(File file) {
        Objects.requireNonNull(file, "Memo file must not be null");
        tempMemoFiles.add(file);
    }

    /**
     * Delete any memoization files registered as being temporary, and also the
     * temporary memoization directory (if it exists).
     * Note that this acts both recursively and rather conservatively, stopping if a file is
     * encountered that is not expected.
     */
    private static void deleteTempMemoFiles() {
        for (File f : tempMemoFiles) {
            // Be extra-careful not to delete too much...
            if (!f.exists())
                continue;
            if (!f.isFile() || !f.getName().endsWith(".bfmemo")) {
                logger.warn("Unexpected memoization file, will not delete {}", f.getAbsolutePath());
                return;
            }
            if (f.delete())
                logger.debug("Deleted temp memoization file {}", f.getAbsolutePath());
            else
                logger.warn("Could not delete temp memoization file {}", f.getAbsolutePath());
        }
        if (dirMemoTemp == null)
            return;
        deleteEmptyDirectories(dirMemoTemp);
    }

    /**
     * Delete a directory and all sub-directories, assuming each contains only empty directories.
     * This is applied recursively, stopping at the first failure (i.e. any directory containing files).
     *
     * @param dir
     * @return true if the directory could be deleted, false otherwise
     */
    private static boolean deleteEmptyDirectories(File dir) {
        if (!dir.isDirectory())
            return false;
        int nFiles = 0;
        var files = dir.listFiles();
        if (files == null) {
            logger.debug("Unable to list files for {}", dir);
            return false;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                if (!deleteEmptyDirectories(f))
                    return false;
            } else if (f.isFile())
                nFiles++;
        }
        if (nFiles == 0) {
            if (dir.delete()) {
                logger.debug("Deleting empty memoization directory {}", dir.getAbsolutePath());
                return true;
            } else {
                logger.warn("Could not delete temp memoization directory {}", dir.getAbsolutePath());
                return false;
            }
        } else {
            logger.warn("Temp memoization directory contains files, will not delete {}", dir.getAbsolutePath());
            return false;
        }
    }

}
