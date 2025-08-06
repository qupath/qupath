package qupath.lib.images.writers.ome.zarr;

import com.bc.zarr.ArrayParams;
import com.bc.zarr.Compressor;
import com.bc.zarr.CompressorFactory;
import com.bc.zarr.DataType;
import com.bc.zarr.DimensionSeparator;
import com.bc.zarr.ZarrArray;
import com.bc.zarr.ZarrGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ThreadTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.images.servers.bioformats.BioFormatsImageServer;
import qupath.lib.regions.ImageRegion;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * An OME-Zarr file writer as described by version 0.4 of the specifications of the
 * <a href="https://ngff.openmicroscopy.org/0.4/index.html">Next-generation file formats (NGFF)</a>.
 * The transitional "bioformats2raw.layout" and "omero" metadata are also considered.
 * <p>
 * Contrary to {@link OMEZarrWriter}, this class will first write the full resolution level. Then, the second level is
 * written by looking at the first level pixels that were just written (so the input image is not considered anymore).
 * Then, the third level is written by looking at the second level pixels that were just written, and so on until all
 * levels are written. This provides less flexibility than {@link OMEZarrWriter} but is less likely to throw out of
 * memory errors.
 * <p>
 * Use a {@link Builder} to create an instance of this class.
 * <p>
 * This class is not thread-safe, but already uses concurrency internally to write tiles.
 * <p>
 * This writer has to be {@link #close() closed} once no longer used.
 */
public class PyramidalOMEZarrWriter implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PyramidalOMEZarrWriter.class);
    private final Map<Integer, ZarrArray> levels = new HashMap<>();
    private final ImageServer<BufferedImage> server;
    private final String path;
    private final List<Double> downsamples;
    private final int tileWidth;
    private final int tileHeight;
    private final ZarrGroup root;
    private final ExecutorService executorService;
    private OMEZarrAttributesCreator attributes;

    private PyramidalOMEZarrWriter(Builder builder, String path) throws IOException {
        TransformedServerBuilder transformedServerBuilder = new TransformedServerBuilder(builder.server);
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

        this.path = path;
        this.downsamples = builder.downsamples;
        this.tileWidth = WriterUtils.getChunkSize(
                builder.tileWidth > 0 ? builder.tileWidth : builder.server.getMetadata().getPreferredTileWidth(),
                builder.maxNumberOfChunks,
                builder.server.getWidth()
        );
        this.tileHeight = WriterUtils.getChunkSize(
                builder.tileHeight > 0 ? builder.tileHeight : builder.server.getMetadata().getPreferredTileHeight(),
                builder.maxNumberOfChunks,
                builder.server.getHeight()
        );

        this.root = ZarrGroup.create(path, null);

        try {
            WriterUtils.createOmeSubGroup(root, path, server.getMetadata());
        } catch (Exception e) {
            logger.warn("Error while creating OME XML file of {}. Some image metadata won't be written", path, e);
        }

        this.attributes = new OMEZarrAttributesCreator(server.getMetadata());
        for (int i=0; i<downsamples.size(); i++) {
            levels.put(
                    i,
                    root.createArray(
                            String.format("s%d", i),
                            new ArrayParams()
                                    .shape(WriterUtils.getDimensionsOfImage(server.getMetadata(), downsamples.get(i)))
                                    .chunks(WriterUtils.getChunksOfImage(server.getMetadata(), tileWidth, tileHeight))
                                    .compressor(builder.compressor)
                                    .dataType(switch (server.getPixelType()) {
                                        case UINT8 -> DataType.u1;
                                        case INT8 -> DataType.i1;
                                        case UINT16 -> DataType.u2;
                                        case INT16 -> DataType.i2;
                                        case UINT32 -> DataType.u4;
                                        case INT32 -> DataType.i4;
                                        case FLOAT32 -> DataType.f4;
                                        case FLOAT64 -> DataType.f8;
                                    })
                                    .dimensionSeparator(DimensionSeparator.SLASH),
                            attributes.getLevelAttributes()
                    )
            );
        }

        this.executorService = Executors.newFixedThreadPool(
                builder.numberOfThreads,
                ThreadTools.createThreadFactory("pyramidal_zarr_writer_", false)
        );
    }

    /**
     * Close this writer. This will wait until all pending tiles are written.
     * <p>
     * If this function is interrupted, all pending and active writing tasks are cancelled.
     *
     * @throws InterruptedException if the waiting is interrupted
     */
    @Override
    public void close() throws InterruptedException {
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
     * Write the image level by level as described in {@link PyramidalOMEZarrWriter}. This function will block
     * until all levels are written.
     *
     * @param onProgress a function that will be called at different steps when the writing occurs. Its parameter will be a float
     *                   between 0 and 1 indicating the progress of the operation (0: beginning, 1: finished). This function may
     *                   be called from any thread. Can be null
     * @throws Exception if a reading or writing error occurs, or if this function is interrupted
     */
    public void writeImage(Consumer<Float> onProgress) throws Exception {
        this.attributes = new OMEZarrAttributesCreator(new ImageServerMetadata.Builder(server.getMetadata())
                .levelsFromDownsamples(1)
                .build()
        );
        root.writeAttributes(attributes.getGroupAttributes());
        writeLevel(
                0,
                server,
                progress -> {
                    if (onProgress != null) {
                        onProgress.accept(progress / downsamples.size());
                    }
                }
        );

        for (int i=1; i<downsamples.size(); i++) {
            try (ImageServer<BufferedImage> server = new BioFormatsImageServer(
                    Paths.get(path).toUri(),
                    "--series",                         // since all level zarr subgroups are already created (see the constructor), BioFormats treat each subgroup
                    String.valueOf(downsamples.size() - i)  // as a different series, so the series we are interested in must be specified
            )) {
                float progressOffset = (float) i / downsamples.size();
                writeLevel(
                        i,
                        server,
                        progress -> {
                            if (onProgress != null) {
                                onProgress.accept(progressOffset + progress / downsamples.size());
                            }
                        }
                );
            }

            this.attributes = new OMEZarrAttributesCreator(new ImageServerMetadata.Builder(server.getMetadata())
                    .levelsFromDownsamples(downsamples.stream().limit(i+1).mapToDouble(d -> d).toArray())
                    .build()
            );
            root.writeAttributes(attributes.getGroupAttributes());
        }
    }

    /**
     * Builder to create an instance of a {@link PyramidalOMEZarrWriter}.
     */
    public static class Builder {

        private static final String FILE_EXTENSION = ".ome.zarr";
        private final ImageServer<BufferedImage> server;
        private final List<Double> downsamples;
        private Compressor compressor = CompressorFactory.createDefaultCompressor();
        private int numberOfThreads = ThreadTools.getParallelism();
        private int maxNumberOfChunks = -1;
        private int tileWidth = 1024;
        private int tileHeight = 1024;
        private ImageRegion boundingBox = null;
        private int zStart = 0;
        private int zEnd;
        private int tStart = 0;
        private int tEnd;

        /**
         * Create the builder.
         *
         * @param server the image to write. Only the level with downsample 1 will be considered (so it must exist)
         * @param downsamples the downsamples the output image should have. There must be at least one downsample,
         *                    and the first downsample should be equal to 1
         */
        public Builder(ImageServer<BufferedImage> server, List<Double> downsamples) {
            if (downsamples.isEmpty()) {
                throw new IllegalArgumentException("At least one downsample should be provided");
            }
            if (downsamples.getFirst() != 1) {
                throw new IllegalArgumentException(String.format("The first downsample should be equal to 1 (got %f)", downsamples.getFirst()));
            }
            if (Arrays.stream(server.getPreferredDownsamples()).noneMatch(d -> d == 1)) {
                throw new IllegalArgumentException(String.format("The input image %s must contain a level with downsample 1", server));
            }

            this.server = server;
            this.downsamples = downsamples;
            this.zEnd = this.server.nZSlices();
            this.tEnd = this.server.nTimepoints();
        }

        /**
         * Set the compressor to use when writing the image. By default, the blocs compression is used.
         *
         * @param compressor the compressor to use when writing the image
         * @return this builder
         */
        public Builder compression(Compressor compressor) {
            this.compressor = compressor;
            return this;
        }

        /**
         * Tiles will be written from a pool of thread. This function specifies the number of threads to use.
         * By default, {@link ThreadTools#getParallelism()} threads are used.
         *
         * @param numberOfThreads the number of threads to use when writing tiles
         * @return this builder
         */
        public Builder parallelize(int numberOfThreads) {
            this.numberOfThreads = numberOfThreads;
            return this;
        }

        /**
         * In Zarr images, data is stored in chunks. This parameter defines the maximum number of chunks on the
         * x,y, and z dimensions. By default, this value is set to -1.
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
         * In Zarr images, data is stored in chunks. This parameter defines the size of chunks on the x and y
         * dimensions. By default, these values are set to 1024.
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
         * In Zarr images, data is stored in chunks. This parameter defines the size of chunks on the x and y
         * dimensions. By default, these values are set to 1024.
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
         * @param boundingBox the region to consider. Only the x, y, width, and height of this region are
         *                    taken into account. Can be null to use the entire image. Null by default
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
         * @return this builder
         */
        public Builder zSlices(int zStart, int zEnd) {
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
         * @return this builder
         */
        public Builder timePoints(int tStart, int tEnd) {
            this.tStart = tStart;
            this.tEnd = tEnd;
            return this;
        }

        /**
         * Create a new instance of {@link PyramidalOMEZarrWriter}. This will also create an empty image on the provided path.
         *
         * @param path the path where to write the image. It must end with ".ome.zarr" and shouldn't already exist
         * @return the new {@link OMEZarrWriter}
         * @throws IOException when the empty image cannot be created. This can happen if the provided path is incorrect
         * or if the user doesn't have enough permissions
         * @throws IllegalArgumentException when the provided path doesn't end with ".ome.zarr" or a file/directory already
         * exists at this location
         */
        public PyramidalOMEZarrWriter build(String path) throws IOException {
            if (!path.endsWith(FILE_EXTENSION)) {
                throw new IllegalArgumentException(String.format("The provided path (%s) does not have the OME-Zarr extension (%s)", path, FILE_EXTENSION));
            }
            if (Files.exists(Paths.get(path))) {
                throw new IllegalArgumentException(String.format("The provided path (%s) already exists", path));
            }

            return new PyramidalOMEZarrWriter(this, path);
        }
    }

    private void writeLevel(int level, ImageServer<BufferedImage> server, Consumer<Float> onProgress) throws InterruptedException {
        double downsample = downsamples.get(level);
        int[] imageDimensions = WriterUtils.getDimensionsOfImage(server.getMetadata(), downsample);
        Collection<TileRequest> tileRequests = getTileRequestsForLevel(
                path,
                level,
                downsample,
                tileWidth,
                tileHeight,
                server.nTimepoints(),
                server.nZSlices(),
                imageDimensions[imageDimensions.length - 1],
                imageDimensions[imageDimensions.length - 2]
        );
        int numberOfTiles = tileRequests.size();

        ZarrArray zarrArray = levels.get(level);
        CountDownLatch latch = new CountDownLatch(tileRequests.size());
        AtomicInteger numberOfTilesProcessed = new AtomicInteger(0);
        for (TileRequest tileRequest: tileRequests) {
            executorService.execute(() -> {
                try {
                    zarrArray.write(
                            WriterUtils.convertBufferedImageToArray(server.readRegion(tileRequest.getRegionRequest()), server.getMetadata()),
                            WriterUtils.getDimensionsOfTile(tileRequest, server.getMetadata()),
                            WriterUtils.getOffsetsOfTile(tileRequest, server.getMetadata())
                    );
                } catch (Throwable e) {
                    logger.error("Error when writing tile", e);
                }

                latch.countDown();
                if (onProgress != null) {
                    onProgress.accept((float) numberOfTilesProcessed.incrementAndGet() / numberOfTiles);
                }
            });
        }
        latch.await();
    }

    private static Collection<TileRequest> getTileRequestsForLevel(
            String path,
            int level,
            double downsample,
            int tileWidth,
            int tileHeight,
            int timepoints,
            int zSlices,
            int levelWidth,
            int levelHeight
    ) {
        Set<TileRequest> tiles = new LinkedHashSet<>();

        for (int t = 0; t < timepoints; t++) {
            for (int z = 0; z < zSlices; z++) {
                for (int y = 0; y < levelHeight; y += tileHeight) {
                    int th = y + tileHeight > levelHeight ? levelHeight - y : tileHeight;

                    for (int x = 0; x < levelWidth; x += tileWidth) {
                        int tw = tileWidth;
                        if (x + tw > levelWidth) {
                            tw = levelWidth - x;
                        }

                        tiles.add(TileRequest.createInstance(
                                path,
                                level,
                                downsample,
                                ImageRegion.createInstance(x, y, tw, th, z, t)
                        ));
                    }
                }
            }
        }

        return tiles;
    }
}
