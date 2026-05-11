package qupath.lib.images.servers.bioformats;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import loci.formats.FormatException;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.ome.OMEPyramidStore;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.ROI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

/**
 * A pool of {@link SynchronizedImageReader} that simplifies access to pixels and metadata
 * for a single image (i.e. one ID).
 * <p>
 * Requests can be made to the pool from multiple threads, and the pool will handle
 * selecting the next available reader, creating new readers up to a fixed maximum number
 * if needed.
 * <p>
 * By using a reader pool, there is no need for most code to interface directly with
 * {@link SynchronizedImageReader} or {@link loci.formats.IFormatReader}, or worry about the
 * fact that the latter is not threadsafe.
 */
class ReaderPool implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ReaderPool.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * Absolute maximum number of permitted readers (queue capacity)
     */
    private static final int MAX_QUEUE_CAPACITY = 128;

    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    private static final Cleaner cleaner = Cleaner.create();
    private final List<Cleaner.Cleanable> cleanables = new ArrayList<>();

    private final String id;
    private final BioFormatsServerOptions options;
    private final BioFormatsArgs args;
    private final String format;

    private final OMEPyramidStore metadata;
    private final SynchronizedImageReader mainReader;

    private final AtomicInteger requestedReaders = new AtomicInteger(0);
    private final ArrayBlockingQueue<SynchronizedImageReader> queue;

    private final List<Series> allSeries;

    private final int timeoutSeconds;

    private volatile boolean isClosed = false;

    // This may be reused by OMERO extension? Not sure, but need to change cautiously...
    ReaderPool(BioFormatsServerOptions options, String id, BioFormatsArgs args) throws FormatException, IOException {
        this.id = id;
        this.options = options;
        this.args = args;

        queue = new ArrayBlockingQueue<>(MAX_QUEUE_CAPACITY); // Set a reasonably large capacity (don't want to block when trying to add)
        metadata = new OMEPyramidStore();

        timeoutSeconds = getTimeoutSeconds();

        // Create the main reader
        long startTime = System.currentTimeMillis();
        this.mainReader = SynchronizedImageReader.createMainReader(options, id, metadata, args);
        this.allSeries = List.copyOf(mainReader.getAllSeries());
        this.format = mainReader.getFormat();

        long endTime = System.currentTimeMillis();
        logger.debug("Reader {} created in {} ms", mainReader, endTime - startTime);

        // Make the main reader available
        registerReader(mainReader);
    }

    private synchronized void registerReader(SynchronizedImageReader reader) {
        cleanables.add(
                cleaner.register(this,
                        new ReaderCleaner(Integer.toString(cleanables.size() + 1), reader)
                )
        );
        queue.add(reader);
    }

    List<Series> getAllSeries() {
        return allSeries;
    }

    String getFormat() {
        return format;
    }

    ImageServerMetadata parseMetadata(Series series, String imageName, String serverPath) throws IOException, FormatException {
        return mainReader.parseMetadata(series, imageName, serverPath);
    }

    boolean checkCanRead() throws IOException, FormatException {
        return mainReader.checkCanRead();
    }

    OMEPyramidStore getMetadata() {
        return metadata;
    }

    /**
     * Make the timeout adjustable.
     * See https://github.com/qupath/qupath/issues/1265
     * @return
     */
    private int getTimeoutSeconds() {
        String timeoutString = System.getProperty("bioformats.readerpool.timeout", null);
        if (timeoutString != null) {
            try {
                return Integer.parseInt(timeoutString);
            } catch (NumberFormatException e) {
                logger.warn("Unable to parse timeout value: {}", timeoutString, e);
            }
        }
        return DEFAULT_TIMEOUT_SECONDS;
    }

    List<ROI> getROIs(int series) {
        if (metadata != null && metadata.getRoot() instanceof OMEXMLMetadataRoot root) {
            // TODO: Find more images to test if this behaves correctly.
            //       So far we have 2 examples, but both have only a single series:
            //       - https://ome-model.readthedocs.io/en/stable/ome-tiff/data.html
            //       - https://zenodo.org/records/14685515
            //       The first links ROIs to images, but the second doesn't -
            //       which is why we try to handle both cases.
            var allRois = root.copyROIList();
            if (allRois.isEmpty())
                return allRois;
            // If we have multiple images, remove any ROIs that are linked to a different image and not (also) this one
            if (metadata.getImageCount() > 1) {
                var linkedRois = new HashSet<>(root.getImage(series).copyLinkedROIList());
                allRois.removeIf(r -> r.sizeOfLinkedImageList() > 0 && !linkedRois.contains(r));
            }
            return allRois;
        } else {
            logger.debug("Unable to find instance of OMEXMLMetadataRoot. Returning no shapes");
            return List.of();
        }
    }


    private int getMaxReaders() {
        int max = options == null ? Runtime.getRuntime().availableProcessors() : options.getMaxReaders();
        return Math.min(MAX_QUEUE_CAPACITY, Math.max(1, max));
    }

    private void createAndRegisterSubReader() {
        try {
            registerReader(mainReader.createSubReader());
        } catch (Exception e) {
            logger.warn("Exception creating sub-reader: {}", e.getMessage(), e);
        }
    }

    private SynchronizedImageReader nextQueuedReader() {
        var nextReader = queue.poll();
        if (nextReader != null) {
            return nextReader;
        }
        if (isClosed)
            return null;
        synchronized (requestedReaders) {
            if (!isClosed && requestedReaders.get() < getMaxReaders()) {
                requestedReaders.incrementAndGet();
                pool.submit(this::createAndRegisterSubReader);
            }
        }
        try {
            var reader = queue.poll(timeoutSeconds, TimeUnit.SECONDS);
            // See https://github.com/qupath/qupath/issues/1265
            if (reader == null) {
                logger.warn("Bio-Formats reader request timed out after {} seconds - returning main reader", timeoutSeconds);
                return mainReader;
            } else {
                return reader;
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted when awaiting next queued reader: {}", e.getMessage());
            return isClosed ? null : mainReader;
        }
    }



    BufferedImage openImage(TileRequest tileRequest, int series, int nChannels, boolean isRGB, ColorModel colorModel) throws IOException, InterruptedException {
        SynchronizedImageReader reader = null;
        try {
            reader = nextQueuedReader();
            if (reader == null) {
                throw new IOException("Reader is null - was the image already closed? " + id);
            }
            int[] samplesPerPixel = getSamplesPerPixel(metadata, series);
            return reader.openImage(
                    tileRequest,
                    series,
                    nChannels,
                    samplesPerPixel,
                    isRGB,
                    colorModel);
        } finally {
            if (Thread.interrupted()) {
                logger.debug("Thread interrupted, flag will be reset: {}", Thread.currentThread());
            }
            if (reader != null)
                queue.put(reader);
        }
    }

    private static int[] getSamplesPerPixel(MetadataRetrieve metadata, int series) {
        return IntStream.range(0, metadata.getChannelCount(series))
                .map(channel -> metadata.getChannelSamplesPerPixel(series, channel).getValue())
                .toArray();
    }

    /**
     * Open an entire series as a {@link BufferedImage}.
     * <p>
     * This is intended for associated images (e.g., thumbnails, overviews),
     * which should be small enough to fit in memory and also not require any
     * special handling of color models.
     * <p>
     * For more exotic images, it may fail.
     * @param series the series to open
     * @return
     */
    BufferedImage openSeries(int series) throws InterruptedException, FormatException, IOException {
        SynchronizedImageReader reader = null;
        try {
            reader = nextQueuedReader();
            if (reader == null) {
                throw new IOException("Reader is null - was the image already closed? " + id);
            }
            return reader.openSeries(series);
        } finally {
            if (reader != null)
                queue.put(reader);
        }
    }



    @Override
    public void close() throws Exception {
        logger.debug("Closing ReaderManager");
        isClosed = true;
        pool.shutdownNow();
        for (var c : cleanables) {
            try {
                c.clean();
            } catch (Exception e) {
                logger.error("Exception during cleanup: {}", e.getMessage(), e);
            }
        }
    }

}
