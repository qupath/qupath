package qupath.lib.images.servers.bioformats;

import java.util.ArrayList;
import java.util.List;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to check if a Bio-Formats reader is available for a specified path,
 * or if the path relates to a file format that is unsupported.
 */
class ReaderChecker {

    private static final Logger logger = LoggerFactory.getLogger(ReaderChecker.class);

    private static final List<IFormatReader> allReaders = getAllAvailableReaders();

    /**
     * Check if there is an available {@link IFormatReader} that claims to support the path, or with a compatible
     * suffix.
     * Note that this performs a fast check that does not access the file system, so the results are a useful
     * filtered but do not confirm that the path is definitely supported.
     *
     * @param path the path to check
     * @return the class of the first potential reader, or null if no reader is found
     */
    public static Class<? extends IFormatReader> getPotentialReader(String path) {
        for (var reader : allReaders) {
            if (reader.isThisType(path, false) || readerSupportsSuffix(reader, path))
                return reader.getClass();
        }
        return null;
    }

    private static boolean readerSupportsSuffix(IFormatReader reader, String path) {
        String lowerPath = path.toLowerCase();
        for (String s : reader.getSuffixes()) {
            if (s != null && !s.isBlank() && lowerPath.endsWith(s.toLowerCase()))
                return true;
        }
        return false;
    }

    private static List<IFormatReader> getAllAvailableReaders() {
        List<IFormatReader> readers = new ArrayList<>();
        for (var cls : ImageReader.getDefaultReaderClasses().getClasses()) {
            try {
                var reader = cls.getConstructor().newInstance();
                readers.add(reader);
            } catch (Throwable t) {
                logger.warn("Unsupported reader {} ({})", cls.getSimpleName(), t.getMessage());
                logger.debug(t.getMessage(), t);
            }
        }
        return List.copyOf(readers);
    }

}
