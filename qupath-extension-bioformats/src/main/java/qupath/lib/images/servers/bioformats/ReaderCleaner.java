package qupath.lib.images.servers.bioformats;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class that helps ensure readers are closed when they are no longer reachable.
 */
class ReaderCleaner implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ReaderCleaner.class);

    private final String name;
    private final SynchronizedImageReader reader;

    ReaderCleaner(String name, SynchronizedImageReader reader) {
        this.name = name;
        this.reader = reader;
    }

    @Override
    public void run() {
        logger.info("Cleaner {} called for {} ({})", name, reader, reader.getID());
        try {
            this.reader.close();
        } catch (IOException e) {
            logger.warn("Error when calling cleaner for {}", name, e);
        }
    }

}
