package qupath.lib.images.servers.bioformats;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import loci.formats.ClassList;
import loci.formats.DimensionSwapper;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.ReaderWrapper;
import loci.formats.gui.AWTImageTools;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.ZarrReader;
import loci.formats.meta.DummyMetadata;
import loci.formats.meta.MetadataStore;
import loci.formats.ome.OMEXMLMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

/**
 * Wrapper for an {@link IFormatReader} that simplifies pixel requests, while ensuring synchronization.
 */
class SynchronizedImageReader implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(SynchronizedImageReader.class);

    private static final Pattern ZARR_FILE_PATTERN = Pattern.compile("\\.zarr/?(\\d+/?)?$");

    private final IFormatReader reader;
    private final String id;

    private final BioFormatsArgs args;
    private final BioFormatsServerOptions options;

    private SynchronizedImageReader(IFormatReader reader, String id, BioFormatsArgs args, BioFormatsServerOptions options) {
        Objects.requireNonNull(reader, "Reader must not be null");
        this.reader = reader;
        this.id = id;
        this.args = args;
        this.options = options;
    }

    String getID() {
        return id;
    }

    private static SynchronizedImageReader create(final BioFormatsServerOptions options,
                                                 final ClassList<IFormatReader> classList,
                                                 final String id,
                                                 final MetadataStore store,
                                                 final BioFormatsArgs args) throws FormatException, IOException {

        IFormatReader imageReader = createBaseReader(id, classList, args.readerOptions);
        // TODO: Warning! Memoization might not play nicely with options (it might require QuPath to be restarted)
        imageReader = maybeMemoize(imageReader, id, options);
        initializeMetadata(imageReader, store);
        imageReader = setImageAndDimensions(imageReader, id, args.series, args.swapDimensions);

        return new SynchronizedImageReader(imageReader, id, args, options);
    }

    /**
     * Create a synchronized reader to wrap an {@link IFormatReader} that stores its metadata in the
     * given metadata object.
     * @param options options to determine whether memoization is used
     * @param id the ID (location, file path) for the reader
     * @param metadata the metadata object; typically {@link loci.formats.ome.OMEPyramidStore} is expected,
     *                 but it is possible to pass {@code null} if the metadata does not need to be accessed later.
     * @param args more optional arguments to customize how the reader is created
     * @return a new synchronized reader
     */
    public static SynchronizedImageReader createMainReader(final BioFormatsServerOptions options,
                                                 final String id,
                                                 final OMEXMLMetadata metadata,
                                                 final BioFormatsArgs args) throws IOException, FormatException {
        return create(options, null, id, metadata, args);
    }

    /**
     * Create a reader for the same image, which may be used in other threads.
     * This does not provide access to full OME metadata, which may enable it to be initialized more quickly.
     * @return a new synchronized reader
     */
    public SynchronizedImageReader createSubReader() throws IOException, FormatException {
        return createReader(null);
    }

    private SynchronizedImageReader createReader(OMEXMLMetadata metadata) throws IOException, FormatException {
        ClassList<IFormatReader> classList = null;
        try {
            classList = getBaseClassList();
        } catch (Exception e) {
            logger.warn("Exception getting class list: {}", e.getMessage(), e);
        }
        return create(options, classList, id, metadata, args);
    }

    private static IFormatReader createBaseReader(String id, ClassList<IFormatReader> classList, Map<String, String> readerMetadataOptions) {
        IFormatReader imageReader;
        Matcher zarrMatcher = ZARR_FILE_PATTERN.matcher(id.toLowerCase());
        if (new File(id).isDirectory() || zarrMatcher.find()) {
            // Using new ImageReader() on a directory won't work
            imageReader = new ZarrReader();
            if (id.startsWith("https")) {
                setReaderMetadataOptions(imageReader, Map.of(ZarrReader.ALT_STORE_KEY, id));
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
            var fileMemo = memoizer.getMemoFile(id);
            if (fileMemo != null && fileMemo.toPath() != null) {
                MemoUtils.registerTempFileForDeletion(fileMemo);
                return memoizer;
            }
            return memoizer;
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



    BufferedImage openImage(TileRequest tileRequest, int series, int nChannels, int[] samplesPerPixel, boolean isRGB, ColorModel colorModel) throws IOException {
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

        try {
            // Check if this is non-zero
            if (tileWidth <= 0 || tileHeight <= 0) {
                throw new IOException("Unable to request pixels for region with downsampled size " + tileWidth + " x " + tileHeight);
            }

            synchronized (reader) {
                ensureOpen();
                reader.setSeries(series);

                // Some files provide z scaling (the number of z stacks decreases when the resolution becomes
                // lower, like the width and height), so z needs to be updated for levels > 0
                if (level > 0 && z > 0) {
                    reader.setResolution(0);
                    int zStacksFullResolution = reader.getSizeZ();
                    reader.setResolution(level);
                    int zStacksCurrentResolution = reader.getSizeZ();

                    if (zStacksFullResolution != zStacksCurrentResolution) {
                        z = (int) (z * zStacksCurrentResolution / (float) zStacksFullResolution);
                    }


                } else {
                    reader.setResolution(level);
                }

                order = reader.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
                interleaved = reader.isInterleaved();
                pixelType = reader.getPixelType();
                normalizeFloats = reader.isNormalized();

                // Single-channel & RGB images are straightforward... nothing more to do
                if ((reader.isRGB() && isRGB) || nChannels == 1) {
                    // Read the image - or at least the first channel
                    int ind = reader.getIndex(z, 0, t);
                    try {
                        byte[] bytesSimple = reader.openBytes(ind, tileX, tileY, tileWidth, tileHeight);
                        return AWTImageTools.openImage(bytesSimple, reader, tileWidth, tileHeight);
                    } catch (Exception | UnsatisfiedLinkError e) {
                        logger.warn("Unable to open image {} for {}", ind, tileRequest.getRegionRequest());
                        throw ReaderUtils.convertToIOException(e);
                    }
                }
                // Read bytes for all the required channels
                effectiveC = reader.getEffectiveSizeC();
                bytes = new byte[effectiveC][];
                try {
                    for (int c = 0; c < effectiveC; c++) {
                        int ind = reader.getIndex(z, c, t);
                        bytes[c] = reader.openBytes(ind, tileX, tileY, tileWidth, tileHeight);
                    }
                } catch (ClosedChannelException e) {
                    // This occurs when a request is interrupted
                    logger.debug("Closed channel exception, closing reader ({})", e.getMessage());
                    logger.trace(e.getMessage(), e);
                    reader.close(false);
                    throw e;
                } catch (Exception | UnsatisfiedLinkError e) {
                    throw ReaderUtils.convertToIOException(e);
                }
            }
        } catch (FormatException e) {
            logger.debug("Unable to open reader: {}", e.getMessage(), e);
            throw new IOException(e);
        }

        return new OMEPixelParser.Builder()
                .isInterleaved(interleaved)
                .pixelType(ReaderUtils.formatToPixelType(pixelType))
                .byteOrder(order)
                .normalizeFloats(normalizeFloats)
                .effectiveNChannels(effectiveC)
                .samplesPerPixel(samplesPerPixel)
                .build()
                .parse(bytes, tileWidth, tileHeight, nChannels, colorModel);
    }


    String getFormat() {
        return reader.getFormat();
    }


    private void ensureOpen() throws IOException, FormatException {
        synchronized (reader) {
            if (!id.equals(reader.getCurrentFile())) {
                reader.close();
                reader.setFlattenedResolutions(false);
                reader.setId(id);
            }
        }
    }

    /**
     * Open a single series as an image.
     * This is expected to be used for 'associated' images (e.g., bar codes, thumbnails) and may not be
     * able to return images with an arbitrary size or type.
     * @param series the number of the series
     * @return a buffered image representing the entire series, if possible
     */
    public BufferedImage openSeries(int series) throws FormatException, IOException {
        synchronized (reader) {
            ensureOpen();
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
    }

    /**
     * Create an {@link ImageServerMetadata} object for the specified series.
     * @param series the series
     * @param imageName the image name that should be set in the metadata object; this may be different from
     *                  the name stored in the OME metadata.
     * @param serverPath the server 'path' (unique ID) used for tile caching.
     * @return a new image server metadata object
     */
    public ImageServerMetadata parseMetadata(Series series, String imageName, String serverPath) throws IOException, FormatException {
        synchronized (reader) {
            ensureOpen();
            return ReaderUtils.buildOriginalMetadata(
                    reader,
                    series,
                    imageName,
                    serverPath
            );
        }
    }

    /**
     * Attempt to read a pixel from the image.
     * @return true if the read succeeded. If it fails, returns false or throws an exception.
     */
    public boolean checkCanRead() throws IOException, FormatException {
        synchronized (reader) {
            ensureOpen();
            return reader.getSizeX() > 0
                    && reader.getSizeY() > 0
                    && reader.openBytes(0, 0, 0, 1, 1) != null;
        }
    }

    @Override
    public void close() throws IOException {
        this.reader.close();
    }

    /**
     * Get all the series found within the image file.
     * @return a list of series
     */
    public List<Series> getAllSeries() throws IOException, FormatException {
        synchronized (reader) {
            ensureOpen();
            return Series.parseFromReader(reader);
        }
    }

    private ClassList<IFormatReader> getBaseClassList() throws IOException, FormatException {
        synchronized (reader) {
            ensureOpen();
            return unwrapBaseClassList(reader);
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

}
