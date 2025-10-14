package qupath.lib.images.writers.ome.zarr;

import com.bc.zarr.Compressor;
import com.bc.zarr.CompressorFactory;
import com.bc.zarr.ZarrArray;
import com.bc.zarr.ZarrGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ThreadTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.TileRequestManager;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.regions.ImageRegion;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An OME-Zarr file writer as described by version 0.4 of the specifications of the
 * <a href="https://ngff.openmicroscopy.org/0.4/index.html">Next-generation file formats (NGFF)</a>.
 * The transitional "bioformats2raw.layout" and "omero" metadata are also considered.
 * <p>
 * Use a {@link Builder} to create an instance of this class.
 * <p>
 * This class is not thread-safe but uses concurrency internally to write tiles.
 * <p>
 * See {@link PyramidalOMEZarrWriter} for a more memory friendly Zarr writer.
 */
public class OMEZarrWriter {

    private static final Logger logger = LoggerFactory.getLogger(OMEZarrWriter.class);
    private final ImageServer<BufferedImage> server;
    private final int numberOfThreads;
    private final Map<Integer, ZarrArray> levels;
    private final Consumer<TileRequest> onTileWritten;

    private OMEZarrWriter(Builder builder, Path path) throws IOException {
        int tileWidth = ZarrWriterUtils.getChunkSize(
                builder.tileWidth > 0 ? builder.tileWidth : builder.server.getMetadata().getPreferredTileWidth(),
                builder.maxNumberOfChunks,
                builder.server.getWidth()
        );
        int tileHeight = ZarrWriterUtils.getChunkSize(
                builder.tileHeight > 0 ? builder.tileHeight : builder.server.getMetadata().getPreferredTileHeight(),
                builder.maxNumberOfChunks,
                builder.server.getHeight()
        );
        double[] downsamples = builder.downsamples.length > 0 ? builder.downsamples : builder.server.getPreferredDownsamples();
        boolean tileSizeAndDownsamplesUnchanged = tileWidth == builder.server.getMetadata().getPreferredTileWidth() &&
                tileHeight == builder.server.getMetadata().getPreferredTileHeight() &&
                Arrays.equals(downsamples, builder.server.getPreferredDownsamples());
        TransformedServerBuilder transformedServerBuilder = new TransformedServerBuilder(tileSizeAndDownsamplesUnchanged ?
                builder.server :
                ImageServers.pyramidalizeTiled(builder.server, tileWidth, tileHeight, downsamples)
        );
        if (builder.zStart != 0 || builder.zEnd != builder.server.nZSlices() || builder.tStart != 0 || builder.tEnd != builder.server.nTimepoints()) {
            transformedServerBuilder.slice(
                    builder.zStart,
                    builder.zEnd,
                    builder.tStart,
                    builder.tEnd
            );
        }
        if (builder.boundingBox != null) {
            transformedServerBuilder.crop(builder.boundingBox);
        }
        this.server = transformedServerBuilder.build();

        this.numberOfThreads = builder.numberOfThreads;

        OMEZarrAttributesCreator attributes = new OMEZarrAttributesCreator(server.getMetadata());

        ZarrGroup root = ZarrGroup.create(path, attributes.getGroupAttributes());

        try {
            ZarrWriterUtils.createOmeSubGroup(root, path, server.getMetadata());
        } catch (Exception e) {
            logger.warn("Error while creating OME XML file of {}. Some image metadata won't be written", path, e);
        }

        this.levels = ZarrWriterUtils.createLevels(
                server.getMetadata(),
                root,
                server.getMetadata().getPreferredTileWidth(),
                server.getMetadata().getPreferredTileHeight(),
                attributes.getLevelAttributes(),
                builder.compressor
        );

        this.onTileWritten = builder.onTileWritten;
    }

    /**
     * Write the entire image.
     * <p>
     * The image will be written from an internal pool of thread and this function will wait for
     * every writing to complete, so it might take some time depending on the size of the image.
     * The operation can be interrupted.
     *
     * @throws InterruptedException if the calling thread is interrupted
     */
    public void writeImage() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(
                numberOfThreads,
                ThreadTools.createThreadFactory("zarr_writer_", false)
        );

        for (TileRequest tileRequest: server.getTileRequestManager().getAllTileRequests()) {
            executorService.execute(() -> {
                try {
                    writeTile(tileRequest);
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        logger.debug("Writing tile {} interrupted", tileRequest, e);
                        Thread.currentThread().interrupt();
                    } else {
                        logger.error("Error when writing tile {}", tileRequest, e);
                    }
                }
            });
        }
        executorService.shutdown();

        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            logger.debug("Waiting interrupted. Stopping tasks", e);
            executorService.shutdownNow();
            throw e;
        }
    }

    /**
     * Write the provided tile. This might take some time depending on the size of the tile and the speed
     * of reading the input image and writing the output image.
     * <p>
     * Note that the image server used internally by this writer may not be the one given in
     * {@link Builder#Builder(ImageServer)}. Therefore, the {@link ImageServer#getTileRequestManager() TileRequestManager}
     * of the internal image server may be different from the one of the provided image server,
     * so functions like {@link TileRequestManager#getAllTileRequests()} may not return the expected tiles.
     * Use the {@link ImageServer#getTileRequestManager() TileRequestManager} of {@link #getReaderServer()}
     * to get accurate tiles.
     *
     * @param tileRequest the tile to write
     * @throws Exception if an exception occurs while writing the tile
     */
    public void writeTile(TileRequest tileRequest) throws Exception {
        try {
            levels.get(tileRequest.getLevel()).write(
                    ZarrWriterUtils.convertBufferedImageToArray(server.readRegion(tileRequest.getRegionRequest())),
                    ZarrWriterUtils.getDimensionsOfTile(server.getMetadata(), tileRequest),
                    ZarrWriterUtils.getOffsetsOfTile(server.getMetadata(), tileRequest)
            );
        } finally {
            onTileWritten.accept(tileRequest);
        }
    }

    /**
     * Get the image server used internally by this writer to read the tiles. It can be
     * different from the one given in {@link Builder#Builder(ImageServer)}.
     * <p>
     * This function can be useful to get information like the tiles used by this server
     * (for example when using the {@link #writeTile(TileRequest)} function).
     *
     * @return the image server used internally by this writer to read the tiles
     */
    public ImageServer<BufferedImage> getReaderServer() {
        return server;
    }

    /**
     * Builder to create an instance of a {@link OMEZarrWriter}.
     */
    public static class Builder {

        private static final String FILE_EXTENSION = ".ome.zarr";
        private final static Consumer<TileRequest> NOP_CONSUMER = t -> {};
        private final ImageServer<BufferedImage> server;
        private Compressor compressor = CompressorFactory.createDefaultCompressor();
        private int numberOfThreads = ThreadTools.getParallelism();
        private double[] downsamples = new double[0];
        private int maxNumberOfChunks = -1;
        private int tileWidth = -1;
        private int tileHeight = -1;
        private ImageRegion boundingBox = null;
        private int zStart = 0;
        private int zEnd;
        private int tStart = 0;
        private int tEnd;
        private Consumer<TileRequest> onTileWritten = NOP_CONSUMER;

        /**
         * Create the builder.
         *
         * @param server the image to write
         * @throws NullPointerException if the provided server is null
         */
        public Builder(ImageServer<BufferedImage> server) {
            this.server = server;
            this.zEnd = this.server.nZSlices();
            this.tEnd = this.server.nTimepoints();
        }

        /**
         * Set the compressor to use when writing tiles. By default, the blocs compression is used.
         *
         * @param compressor the compressor to use when writing tiles
         * @return this builder
         * @throws NullPointerException if the provided compressor is null
         */
        public Builder compression(Compressor compressor) {
            this.compressor = Objects.requireNonNull(compressor);
            return this;
        }

        /**
         * Tiles will be written from a pool of thread. This function specifies the number of threads to use.
         * By default, {@link ThreadTools#getParallelism()} threads are used.
         *
         * @param numberOfThreads the number of threads to use when writing tiles
         * @return this builder
         * @throws IllegalArgumentException if the provided number of threads is less than 1
         */
        public Builder parallelize(int numberOfThreads) {
            if (numberOfThreads < 1) {
                throw new IllegalArgumentException(String.format("The provided number of threads %d is less than 1", numberOfThreads));
            }

            this.numberOfThreads = numberOfThreads;
            return this;
        }

        /**
         * Enable the creation of a pyramidal image with the provided downsamples. The levels corresponding
         * to the provided downsamples will be automatically generated.
         * <p>
         * If this function is not called (or if it is called with no parameters), the downsamples of
         * the provided image server will be used instead.
         *
         * @param downsamples the downsamples of the pyramid to generate
         * @return this builder
         */
        public Builder downsamples(double... downsamples) {
            this.downsamples = downsamples.clone();
            return this;
        }

        /**
         * In Zarr images, data is stored in chunks. This parameter defines the maximum number
         * of chunks on the x,y, and z dimensions. By default, this value is set to -1.
         * <p>
         * Use a negative value to not define any maximum number of chunks.
         *
         * @param maxNumberOfChunks the maximum number of chunks on the x,y, and z dimensions
         * @return this builder
         */
        public Builder setMaxNumberOfChunksOnEachSpatialDimension(int maxNumberOfChunks) {
            this.maxNumberOfChunks = maxNumberOfChunks;
            return this;
        }

        /**
         * In Zarr images, data is stored in chunks. This parameter defines the size
         * of chunks on the x and y dimensions. By default, these values are set to -1.
         * <p>
         * Use a negative value to use the tile width/height of the provided image server.
         * <p>
         * The provided tile width/height may not be used if this implies creating more chunks
         * than the value given in {@link #setMaxNumberOfChunksOnEachSpatialDimension(int)}.
         *
         * @param tileSize the width/height each chunk should have
         * @return this builder
         */
        public Builder tileSize(int tileSize) {
            return tileSize(tileSize, tileSize);
        }

        /**
         * In Zarr images, data is stored in chunks. This parameter defines the size
         * of chunks on the x and y dimensions. By default, these values are set to -1.
         * <p>
         * Use a negative value to use the tile width/height of the provided image server.
         * <p>
         * The provided tile width/height may not be used if this implies creating more chunks
         * than the value given in {@link #setMaxNumberOfChunksOnEachSpatialDimension(int)}.
         *
         * @param tileWidth the width each chunk should have
         * @param tileHeight the height each chunk should have
         * @return this builder
         */
        public Builder tileSize(int tileWidth, int tileHeight) {
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            return this;
        }

        /**
         * Define a region (on the x-axis and y-axis) of the input image to consider.
         *
         * @param boundingBox the region to consider. Only the x, y, width, and height
         *                    of this region are taken into account. Can be null to use
         *                    the entire image. Null by default
         * @return this builder
         */
        public Builder region(ImageRegion boundingBox) {
            this.boundingBox = boundingBox;
            return this;
        }

        /**
         * Define the z-slices of the input image to consider.
         *
         * @param zStart the 0-based inclusive index of the first z-slice to consider. 0 by default
         * @param zEnd the 0-based exclusive index of the last z-slice to consider. Equal to the number
         *             of z-slices of the image by default
         * @throws IllegalArgumentException if the min z-slice index is greater than the max z-slice index
         * @return this builder
         */
        public Builder zSlices(int zStart, int zEnd) {
            if (zStart > zEnd) {
                throw new IllegalArgumentException(String.format("The min z-slice index %d is greater than the max z-slice index %d", zStart, zEnd));
            }

            this.zStart = zStart;
            this.zEnd = zEnd;
            return this;
        }

        /**
         * Define the timepoints of the input image to consider.
         *
         * @param tStart the 0-based inclusive index of the first timepoint to consider. 0 by default
         * @param tEnd the 0-based exclusive index of the last timepoint to consider. Equal to the number
         *             of timepoints of the image by default
         * @throws IllegalArgumentException if the min timepoint index is greater than the max timepoint index
         * @return this builder
         */
        public Builder timePoints(int tStart, int tEnd) {
            if (tStart > tEnd) {
                throw new IllegalArgumentException(String.format("The min timepoint index %d is greater than the max timepoint index %d", tStart, tEnd));
            }

            this.tStart = tStart;
            this.tEnd = tEnd;
            return this;
        }

        /**
         * Set a function that will be called each time a {@link TileRequest} is successfully
         * or unsuccessfully written.
         * <p>
         * This function may be called from any thread.
         *
         * @param onTileWritten a function that will be called each time a {@link TileRequest} is successfully
         *                      or unsuccessfully written
         * @return this builder
         * @throws NullPointerException if the provided parameter is null
         */
        public Builder onTileWritten(Consumer<TileRequest> onTileWritten) {
            this.onTileWritten = Objects.requireNonNull(onTileWritten);
            return this;
        }

        /**
         * Create a new instance of {@link OMEZarrWriter}. This will also create an empty image on the provided path.
         *
         * @param path the path where to write the image. It must end with ".ome.zarr" and shouldn't already exist
         * @return the new {@link OMEZarrWriter}
         * @throws java.nio.file.InvalidPathException if a path object cannot be created from the provided path
         * @throws IOException if the empty image cannot be created. This can happen if the provided path is incorrect
         * or if the user doesn't have enough permissions
         * @throws IllegalArgumentException if the provided path doesn't end with ".ome.zarr" or a file/directory already
         * exists at this location
         */
        public OMEZarrWriter build(String path) throws IOException {
            Path outputPath = Paths.get(path);

            if (!path.endsWith(FILE_EXTENSION)) {
                throw new IllegalArgumentException(String.format("The provided path (%s) does not have the OME-Zarr extension (%s)", path, FILE_EXTENSION));
            }
            if (Files.exists(outputPath)) {
                throw new IllegalArgumentException(String.format("The provided path (%s) already exists", path));
            }

            return new OMEZarrWriter(this, outputPath);
        }
    }
}
