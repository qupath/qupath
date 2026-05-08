package qupath.lib.images.servers.bioformats;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import loci.formats.ClassList;
import loci.formats.DimensionSwapper;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.MetadataTools;
import loci.formats.ReaderWrapper;
import loci.formats.gui.AWTImageTools;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.ZarrReader;
import loci.formats.meta.DummyMetadata;
import loci.formats.meta.MetadataStore;
import loci.formats.ome.OMEPyramidStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.TileRequest;

/**
 * Helper class that manages a pool of readers.
 * The purpose is to allow multiple threads to take the next available reader.
 */
class ReaderPool implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ReaderPool.class);

    private static final Pattern ZARR_FILE_PATTERN = Pattern.compile("\\.zarr/?(\\d+/?)?$");

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * Absolute maximum number of permitted readers (queue capacity)
     */
    private static final int MAX_QUEUE_CAPACITY = 128;

    private static final Cleaner cleaner = Cleaner.create();
    private final List<Cleaner.Cleanable> cleanables = new ArrayList<>();

    private final String id;
    private final BioFormatsServerOptions options;
    private final BioFormatsArgs args;
    private final ClassList<IFormatReader> classList;
    private final String format;

    private volatile boolean isClosed = false;

    private final AtomicInteger totalReaders = new AtomicInteger(0);
    private final List<IFormatReader> additionalReaders = Collections.synchronizedList(new ArrayList<>());
    private final ArrayBlockingQueue<IFormatReader> queue;

    private final OMEPyramidStore metadata;
    private final IFormatReader mainReader;

    private ForkJoinTask<?> task;

    private final int timeoutSeconds;

    // This may be reused by OMERO extension? Not sure, but need to change cautiously...
    ReaderPool(BioFormatsServerOptions options, String id, BioFormatsArgs args) throws FormatException, IOException {
        this.id = id;
        this.options = options;
        this.args = args;

        queue = new ArrayBlockingQueue<>(MAX_QUEUE_CAPACITY); // Set a reasonably large capacity (don't want to block when trying to add)
        metadata = (OMEPyramidStore) MetadataTools.createOMEXMLMetadata();

        timeoutSeconds = getTimeoutSeconds();

        // Create the main reader
        long startTime = System.currentTimeMillis();
        mainReader = createReader(options, null, id, metadata, args);
        this.format = mainReader.getFormat();

        long endTime = System.currentTimeMillis();
        logger.debug("Reader {} created in {} ms", mainReader, endTime - startTime);

        // Make the main reader available
        queue.add(mainReader);

        // Store the class so we don't need to go hunting later
        classList = unwrapBaseClassList(mainReader);
    }

    String getFormat() {
        return format;
    }

    boolean checkCanRead() throws IOException, FormatException {
        var reader = getMainReader();
        return reader.getSizeX() > 0
                && reader.getSizeY() > 0
                && reader.openBytes(0, 0, 0, 1, 1) != null;
    }

    OMEPyramidStore getMetadata() {
        return metadata;
    }

    /**
     * Make the timeout adjustable.
     * See https://github.com/qupath/qupath/issues/1265
     *
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

    IFormatReader getMainReader() {
        return mainReader;
    }

    private void createAdditionalReader(BioFormatsServerOptions options,
                                        final ClassList<IFormatReader> classList,
                                        final String id,
                                        final BioFormatsArgs args) {
        try {
            if (isClosed)
                return;
            logger.debug("Requesting new reader for thread {}", Thread.currentThread());
            var newReader = createReader(options, classList, id, null, args);
            if (newReader != null) {
                additionalReaders.add(newReader);
                queue.add(newReader);
                logger.debug("Created new reader (total={})", additionalReaders.size());
            } else
                logger.warn("New Bio-Formats reader could not be created (returned null)");
        } catch (Exception e) {
            logger.error("Error creating additional readers: {}", e.getMessage(), e);
        }
    }


    private int getMaxReaders() {
        int max = options == null ? Runtime.getRuntime().availableProcessors() : options.getMaxReaders();
        return Math.min(MAX_QUEUE_CAPACITY, Math.max(1, max));
    }


    /**
     * Create a new {@code IFormatReader}, with memoization if necessary.
     *
     * @param options   options used to control the reader generation
     * @param classList optionally specify a list of potential reader classes, if known (to avoid a more lengthy search)
     * @param id        file path for the image.
     * @param store     optional MetadataStore; this will be set in the reader if needed. If it is unspecified, a dummy store will be created a minimal metadata requested.
     * @param args      optional args to customize reading
     * @return the {@code IFormatReader}
     * @throws FormatException
     * @throws IOException
     */
    @SuppressWarnings("resource")
    private IFormatReader createReader(final BioFormatsServerOptions options,
                                       final ClassList<IFormatReader> classList,
                                       final String id,
                                       final MetadataStore store,
                                       final BioFormatsArgs args) throws FormatException, IOException {

        int maxReaders = getMaxReaders();
        int nReaders = totalReaders.getAndIncrement();
        if (mainReader != null && nReaders > maxReaders) {
            logger.warn("No new reader will be created (already created {}, max readers {})", nReaders, maxReaders);
            totalReaders.decrementAndGet();
            return null;
        }

        IFormatReader imageReader = createBaseReader(id, classList, args.readerOptions);
        // TODO: Warning! Memoization might not play nicely with options (it might require QuPath to be restarted)
        imageReader = maybeMemoize(imageReader, id, options);
        initializeMetadata(imageReader, store);
        imageReader = setImageAndDimensions(imageReader, id, args.series, args.swapDimensions);

        if (isClosed) {
            imageReader.close(false);
            return null;
        } else {
            cleanables.add(cleaner.register(this,
                    new ReaderCleaner(Integer.toString(cleanables.size() + 1), imageReader)));
        }
        return imageReader;
    }


    private static IFormatReader createBaseReader(String id, ClassList<IFormatReader> classList, Map<String, String> readerMetadataOptions) {
        IFormatReader imageReader;
        Matcher zarrMatcher = ZARR_FILE_PATTERN.matcher(id.toLowerCase());
        if (new File(id).isDirectory() || zarrMatcher.find()) {
            // Using new ImageReader() on a directory won't work
            imageReader = new ZarrReader();
            if (id.startsWith("https")) {
                setReaderMetadataOptions(imageReader, Map.of("omezarr.alt_store", id));
            }
        } else {
            if (classList != null) {
                imageReader = new ImageReader(classList);
            } else {
                imageReader = new ImageReader();
            }
        }
        setReaderMetadataOptions(imageReader, readerMetadataOptions);
        imageReader.setFlattenedResolutions(false);
        return imageReader;
    }


    private static void initializeMetadata(IFormatReader reader, MetadataStore store) {
        if (store == null) {
            reader.setMetadataStore(new DummyMetadata());
            reader.setOriginalMetadataPopulated(false);
        } else {
            reader.setMetadataStore(store);
        }
    }


    private static void setReaderMetadataOptions(IFormatReader reader, Map<String, String> options) {
        if (reader.getMetadataOptions() instanceof DynamicMetadataOptions dynamicOptions) {
            for (var entry : options.entrySet()) {
                dynamicOptions.set(entry.getKey(), entry.getValue());
            }
        } else if (!options.isEmpty()) {
            logger.warn("Unable to set reader metadata options: {}", options);
        }
    }


    private static IFormatReader maybeMemoize(final IFormatReader reader, String id, final BioFormatsServerOptions options) {
        int memoizationTimeMillis = options.getMemoizationTimeMillis();
        // Check if we want to (and can) use memoization
        if (!BioFormatsServerOptions.allowMemoization() || memoizationTimeMillis < 0) {
            return reader;
        }
        // Try to use a specified directory
        File dir = getSpecifiedMemoizationDirectory(options);
        boolean useTempDirectory = dir == null;
        // Use a temp directory if none specified
        if (useTempDirectory) {
            try {
                dir = MemoUtils.createTempMemoDir();
            } catch (IOException e) {
                logger.debug("Unable to create memoization directory: {}", e.getMessage(), e);
                return reader;
            }
        }
        try {
            var memoizer = new Memoizer(reader, memoizationTimeMillis, dir);
            // The call to .toPath() should throw an InvalidPathException if there are illegal characters
            // If so, we want to know that now before committing to the memoizer
            var fileMemo = memoizer.getMemoFile();
            if (fileMemo != null && fileMemo.toPath() != null) {
                MemoUtils.registerTempFileForDeletion(fileMemo);
                return memoizer;
            }
        } catch (Exception e) {
            logger.warn("Unable to use memoization: {}", e.getMessage());
            logger.debug(e.getMessage(), e);
        }
        return reader;
    }

    private static File getSpecifiedMemoizationDirectory(final BioFormatsServerOptions options) {
        String pathMemoization = options.getPathMemoization();
        if (pathMemoization != null && !pathMemoization.trim().isEmpty()) {
            var dir = new File(pathMemoization);
            if (dir.isDirectory())
                return dir;
            logger.warn("Memoization path does not refer to a valid directory, will be ignored: {}", dir.getAbsolutePath());
        }
        return null;
    }

    /**
     * Update the reader's ID, series and dimensions.
     * These are grouped together because we need to do them in a valid order.
     */
    private static IFormatReader setImageAndDimensions(IFormatReader reader, String id, int series, String swapDimensions) throws IOException, FormatException {
        if (swapDimensions != null && !swapDimensions.isBlank())
            reader = DimensionSwapper.makeDimensionSwapper(reader);
        reader.setId(id);
        if (series >= 0)
            reader.setSeries(series);
        if (reader instanceof DimensionSwapper swapper && swapDimensions != null)
            swapper.swapDimensions(swapDimensions);
        return reader;
    }


    private IFormatReader nextQueuedReader() {
        var nextReader = queue.poll();
        if (nextReader != null) {
            return nextReader;
        }
        synchronized (this) {
            if (!isClosed && (task == null || task.isDone()) && totalReaders.get() < getMaxReaders()) {
                logger.debug("Requesting reader for {}", id);
                task = ForkJoinPool.commonPool().submit(() -> createAdditionalReader(options, classList, id, args));
            }
        }
        if (isClosed)
            return null;
        try {
            var reader = queue.poll(timeoutSeconds, TimeUnit.SECONDS);
            // See https://github.com/qupath/qupath/issues/1265
            if (reader == null) {
                logger.warn("Bio-Formats reader request timed out after {} seconds - returning main reader", timeoutSeconds);
                return mainReader;
            } else
                return reader;
        } catch (InterruptedException e) {
            logger.warn("Interrupted exception when awaiting next queued reader: {}", e.getLocalizedMessage());
            return isClosed ? null : mainReader;
        }
    }

    private void ensureOpen(IFormatReader reader) throws IOException, FormatException {
        if (!id.equals(reader.getCurrentFile())) {
            reader.close();
            reader.setFlattenedResolutions(false);
            reader.setId(id);
        }
    }


    BufferedImage openImage(TileRequest tileRequest, int series, int nChannels, boolean isRGB, ColorModel colorModel) throws IOException, InterruptedException {
        int level = tileRequest.getLevel();
        int tileX = tileRequest.getTileX();
        int tileY = tileRequest.getTileY();
        int tileWidth = tileRequest.getTileWidth();
        int tileHeight = tileRequest.getTileHeight();
        int z = tileRequest.getZ();
        int t = tileRequest.getT();

        byte[][] bytes;
        int effectiveC;
        ByteOrder order;
        boolean interleaved;
        int pixelType;
        boolean normalizeFloats;
        int[] samplesPerPixel;


        IFormatReader ipReader = null;
        try {
            ipReader = nextQueuedReader();
            if (ipReader == null) {
                throw new IOException("Reader is null - was the image already closed? " + id);
            }

            // Check if this is non-zero
            if (tileWidth <= 0 || tileHeight <= 0) {
                throw new IOException("Unable to request pixels for region with downsampled size " + tileWidth + " x " + tileHeight);
            }

            synchronized (ipReader) {
                ensureOpen(ipReader);
                ipReader.setSeries(series);

                // Some files provide z scaling (the number of z stacks decreases when the resolution becomes
                // lower, like the width and height), so z needs to be updated for levels > 0
                if (level > 0 && z > 0) {
                    ipReader.setResolution(0);
                    int zStacksFullResolution = ipReader.getSizeZ();
                    ipReader.setResolution(level);
                    int zStacksCurrentResolution = ipReader.getSizeZ();

                    if (zStacksFullResolution != zStacksCurrentResolution) {
                        z = (int) (z * zStacksCurrentResolution / (float) zStacksFullResolution);
                    }


                } else {
                    ipReader.setResolution(level);
                }

                order = ipReader.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
                interleaved = ipReader.isInterleaved();
                pixelType = ipReader.getPixelType();
                normalizeFloats = ipReader.isNormalized();
                samplesPerPixel = IntStream.range(0, metadata.getChannelCount(series))
                        .map(channel -> metadata.getChannelSamplesPerPixel(series, channel).getValue())
                        .toArray();

                // Single-channel & RGB images are straightforward... nothing more to do
                if ((ipReader.isRGB() && isRGB) || nChannels == 1) {
                    // Read the image - or at least the first channel
                    int ind = ipReader.getIndex(z, 0, t);
                    try {
                        byte[] bytesSimple = ipReader.openBytes(ind, tileX, tileY, tileWidth, tileHeight);
                        return AWTImageTools.openImage(bytesSimple, ipReader, tileWidth, tileHeight);
                    } catch (Exception | UnsatisfiedLinkError e) {
                        logger.warn("Unable to open image {} for {}", ind, tileRequest.getRegionRequest());
                        throw ReaderUtils.convertToIOException(e);
                    }
                }
                // Read bytes for all the required channels
                effectiveC = ipReader.getEffectiveSizeC();
                bytes = new byte[effectiveC][];
                try {
                    for (int c = 0; c < effectiveC; c++) {
                        int ind = ipReader.getIndex(z, c, t);
                        bytes[c] = ipReader.openBytes(ind, tileX, tileY, tileWidth, tileHeight);
                    }
                } catch (ClosedChannelException e) {
                    // This occurs when a request is interrupted
                    logger.warn("Closed channel exception, closing reader");
                    ipReader.close(false);
                    throw e;
                } catch (Exception | UnsatisfiedLinkError e) {
                    throw ReaderUtils.convertToIOException(e);
                }
            }
        } catch (FormatException e) {
            logger.debug("Unable to open reader: {}", e.getMessage(), e);
            throw new IOException(e);
        } finally {
            if (Thread.interrupted()) {
                logger.debug("Thread interrupted, flag will be reset: {}", Thread.currentThread());
            }
            if (ipReader != null)
                queue.put(ipReader);
        }

        OMEPixelParser omePixelParser = new OMEPixelParser.Builder()
                .isInterleaved(interleaved)
                .pixelType(ReaderUtils.formatToPixelType(pixelType))
                .byteOrder(order)
                .normalizeFloats(normalizeFloats)
                .effectiveNChannels(effectiveC)
                .samplesPerPixel(samplesPerPixel)
                .build();

        return omePixelParser.parse(bytes, tileWidth, tileHeight, nChannels, colorModel);
    }


    public BufferedImage openSeries(int series) throws InterruptedException, FormatException, IOException {
        IFormatReader reader = null;
        try {
            reader = nextQueuedReader();
            synchronized (reader) {
                int previousSeries = reader.getSeries();
                try {
                    reader.setSeries(series);
                    int nResolutions = reader.getResolutionCount();
                    if (nResolutions > 0) {
                        reader.setResolution(0);
                    }
                    // TODO: Handle color transforms here, or in the display of labels/macro images - in case this isn't RGB
                    byte[] bytesSimple = reader.openBytes(reader.getIndex(0, 0, 0));
                    return AWTImageTools.openImage(bytesSimple, reader, reader.getSizeX(), reader.getSizeY());
                } finally {
                    reader.setSeries(previousSeries);
                }
            }
        } finally {
            queue.put(reader);
        }
    }


    private static ClassList<IFormatReader> unwrapBaseClassList(IFormatReader reader) {
        while (true) {
            IFormatReader nextReader = null;
            if (reader instanceof ReaderWrapper wrapper)
                nextReader = wrapper.getReader();
            else if (reader instanceof ImageReader imageReader)
                nextReader = imageReader.getReader();
            if (nextReader == null)
                break;
            else
                reader = nextReader;
        }
        var classlist = new ClassList<>(IFormatReader.class);
        classlist.addClass(reader.getClass());
        return classlist;
    }


    @Override
    public void close() throws Exception {
        logger.debug("Closing ReaderManager");
        isClosed = true;
        if (task != null && !task.isDone())
            task.cancel(true);
        for (var c : cleanables) {
            try {
                c.clean();
            } catch (Exception e) {
                logger.error("Exception during cleanup: {}", e.getMessage(), e);
            }
        }
    }


}
