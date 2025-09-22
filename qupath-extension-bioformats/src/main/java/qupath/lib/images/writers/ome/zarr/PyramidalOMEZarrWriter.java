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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;

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
 * This class is thread-safe (as long as parallel writes are done on different paths), but already uses concurrency
 * internally to write tiles.
 */
public class PyramidalOMEZarrWriter {

    private static final Logger logger = LoggerFactory.getLogger(PyramidalOMEZarrWriter.class);
    private static final String FILE_EXTENSION = ".ome.zarr";
    private final static DoubleConsumer NOP_CONSUMER = d -> {};
    private final ImageServer<BufferedImage> server;
    private final List<Double> downsamples;
    private final int tileWidth;
    private final int tileHeight;
    private final Compressor compressor;
    private final int numberOfThreads;

    private PyramidalOMEZarrWriter(Builder builder) {
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
        this.compressor = builder.compressor;
        this.numberOfThreads = builder.numberOfThreads;
    }

    /**
     * Write the image level by level as described in {@link PyramidalOMEZarrWriter}. This function will block
     * until all levels are written.
     *
     * @param path the path where to write the image. It must end with ".ome.zarr" and shouldn't already exist
     * @throws Exception if one of the parameters is null, the provided path cannot be created, if it doesn't end with ".ome.zarr",
     * if a file/directory already exists at the path location, if a reading or writing error occurs, or if this function is interrupted
     */
    public void writeImage(String path) throws Exception {
        writeImage(path, NOP_CONSUMER);
    }

    /**
     * Write the image level by level as described in {@link PyramidalOMEZarrWriter}. This function will block
     * until all levels are written.
     *
     * @param path the path where to write the image. It must end with ".ome.zarr" and shouldn't already exist
     * @param onProgress a function that will be called at different steps when the writing occurs. Its parameter will be a double
     *                   between 0 and 1 indicating the progress of the operation (0: beginning, 1: finished). This function may
     *                   be called from any thread. See {@link #writeImage(String)} if you want to omit this parameter
     * @throws Exception if one of the parameters is null, the provided path cannot be created, if it doesn't end with ".ome.zarr",
     * if a file/directory already exists at the path location, if a reading or writing error occurs, or if this function is interrupted
     */
    public void writeImage(String path, DoubleConsumer onProgress) throws Exception {
        Objects.requireNonNull(onProgress);

        Path outputPath = Paths.get(path);
        if (!path.endsWith(FILE_EXTENSION)) {
            throw new IllegalArgumentException(String.format("The provided path (%s) does not have the OME-Zarr extension (%s)", path, FILE_EXTENSION));
        }
        if (Files.exists(outputPath)) {
            throw new IllegalArgumentException(String.format("The provided path (%s) already exists", path));
        }

        ZarrGroup root = ZarrGroup.create(path, null);
        try {
            WriterUtils.createOmeSubGroup(root, outputPath, server.getMetadata());
        } catch (Exception e) {
            logger.warn("Error while creating OME XML file of {}. Some image metadata won't be written", path, e);
        }

        Map<Integer, ZarrArray> levels = createLevels(root);

        ExecutorService executorService = Executors.newFixedThreadPool(
                numberOfThreads,
                ThreadTools.createThreadFactory("pyramidal_zarr_writer_", false)
        );

        try {
            root.writeAttributes(
                    new OMEZarrAttributesCreator(new ImageServerMetadata.Builder(server.getMetadata())
                            .levelsFromDownsamples(downsamples.getFirst())
                            .build()
                    ).getGroupAttributes()
            );
            writeLevel(
                    path,
                    0,
                    levels.get(0),
                    server,
                    executorService,
                    progress -> onProgress.accept(progress / downsamples.size())
            );

            for (int i=1; i<downsamples.size(); i++) {
                try (ImageServer<BufferedImage> server = new BioFormatsImageServer(
                        outputPath.toUri(),
                        "--series",                         // since all level zarr subgroups are already created (see the constructor), BioFormats treat each subgroup
                        String.valueOf(downsamples.size() - i)  // as a different series, so the series we are interested in must be specified
                )) {
                    double progressOffset = (double) i / downsamples.size();
                    writeLevel(
                            path,
                            i,
                            levels.get(i),
                            server,
                            executorService,
                            progress -> onProgress.accept(progressOffset + progress / downsamples.size())
                    );
                }

                root.writeAttributes(
                        new OMEZarrAttributesCreator(new ImageServerMetadata.Builder(server.getMetadata())
                                .levelsFromDownsamples(downsamples.stream().limit(i+1).mapToDouble(d -> d).toArray())
                                .build()
                        ).getGroupAttributes()
                );
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * Builder to create an instance of a {@link PyramidalOMEZarrWriter}.
     */
    public static class Builder {

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
         * @param server the image to write
         * @param downsamples the downsamples the output image should have. There must be at least one downsample
         *                    of value 1 and each downsample should be greater than or equal to 1
         * @throws IllegalArgumentException if no downsample with value 1 is provided or if one provided downsample
         * is less than 1
         * @throws NullPointerException if one of the provided parameter is null
         */
        public Builder(ImageServer<BufferedImage> server, double... downsamples) {
            List<Double> downsampleList = Arrays.stream(downsamples).boxed().toList();

            if (downsampleList.stream().noneMatch(downsample -> downsample == 1)) {
                throw new IllegalArgumentException("At least one downsample with value 1 should be provided");
            }
            if (downsampleList.stream().anyMatch(downsample -> downsample < 1)) {
                throw new IllegalArgumentException(String.format("One of the provided downsamples %s is less than or equal to 0", downsampleList));
            }

            this.server = server;
            this.downsamples = downsampleList.stream().distinct().sorted().toList();
            this.zEnd = this.server.nZSlices();
            this.tEnd = this.server.nTimepoints();
        }

        /**
         * Set the compressor to use when writing the image. By default, the blocs compression is used.
         *
         * @param compressor the compressor to use when writing the image
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
         * @throws IllegalArgumentException if the min z-slice index is greater than the max z-slice index
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
         * @return this builder
         * @throws IllegalArgumentException if the min timepoint index is greater than the max timepoint index
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
         * Create a new instance of {@link PyramidalOMEZarrWriter}.
         *
         * @return the new {@link OMEZarrWriter}
         */
        public PyramidalOMEZarrWriter build() {
            return new PyramidalOMEZarrWriter(this);
        }
    }

    private Map<Integer, ZarrArray> createLevels(ZarrGroup root) throws IOException {
        Map<Integer, ZarrArray> levels = new HashMap<>();
        for (int i=0; i<downsamples.size(); i++) {
            levels.put(
                    i,
                    root.createArray(
                            String.format("s%d", i),
                            new ArrayParams()
                                    .shape(WriterUtils.getDimensionsOfImage(server.getMetadata(), downsamples.get(i)))
                                    .chunks(WriterUtils.getDimensionsOfChunks(server.getMetadata(), tileWidth, tileHeight))
                                    .compressor(compressor)
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
                            new OMEZarrAttributesCreator(server.getMetadata()).getLevelAttributes()
                    )
            );
        }
        return levels;
    }

    private void writeLevel(
            String path,
            int level,
            ZarrArray zarrArray,
            ImageServer<BufferedImage> server,
            ExecutorService executorService,
            DoubleConsumer onProgress
    ) throws InterruptedException {
        int[] imageDimensions = WriterUtils.getDimensionsOfImage(server.getMetadata(), downsamples.get(level));
        Collection<TileRequest> tileRequests = getTileRequestsForLevel(
                path,
                level,
                downsamples.get(level),
                tileWidth,
                tileHeight,
                server.nTimepoints(),
                server.nZSlices(),
                imageDimensions[imageDimensions.length - 1],
                imageDimensions[imageDimensions.length - 2]
        );
        int numberOfTiles = tileRequests.size();

        CountDownLatch latch = new CountDownLatch(tileRequests.size());
        AtomicInteger numberOfTilesProcessed = new AtomicInteger(0);
        for (TileRequest tileRequest: tileRequests) {
            executorService.execute(() -> {
                try {
                    zarrArray.write(
                            WriterUtils.convertBufferedImageToArray(server.readRegion(tileRequest.getRegionRequest())),
                            WriterUtils.getDimensionsOfTile(server.getMetadata(), tileRequest),
                            WriterUtils.getOffsetsOfTile(server.getMetadata(), tileRequest)
                    );
                } catch (Throwable e) {
                    if (e.getCause() instanceof InterruptedException) {
                        logger.debug("Tile {} writing interrupted", tileRequest, e);
                    } else {
                        logger.error("Error when writing tile {}", tileRequest, e);
                    }
                }

                latch.countDown();
                onProgress.accept((double) numberOfTilesProcessed.incrementAndGet() / numberOfTiles);
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
        List<TileRequest> tiles = new ArrayList<>(timepoints * zSlices * (levelHeight / tileHeight) * (levelWidth / tileWidth));

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

        // Reverse tiles every two levels so that cache is more used
        if (level % 2 == 0) {
            return tiles;
        } else {
            return tiles.reversed();
        }
    }
}
