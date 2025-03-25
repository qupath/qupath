package qupath.lib.images.writers.ome.zarr;

import com.bc.zarr.ArrayParams;
import com.bc.zarr.Compressor;
import com.bc.zarr.CompressorFactory;
import com.bc.zarr.DataType;
import com.bc.zarr.DimensionSeparator;
import com.bc.zarr.ZarrArray;
import com.bc.zarr.ZarrGroup;
import loci.formats.gui.AWTImageTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.TileRequestManager;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.regions.ImageRegion;

import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Create an OME-Zarr file writer as described by version 0.4 of the specifications of the
 * <a href="https://ngff.openmicroscopy.org/0.4/index.html">Next-generation file formats (NGFF)</a>.
 * The transitional "bioformats2raw.layout" and "omero" metadata are also considered.
 * <p>
 * Use a {@link Builder} to create an instance of this class.
 * <p>
 * This class is thread-safe but already uses concurrency internally to write tiles.
 * <p>
 * This writer has to be {@link #close() closed} once no longer used.
 */
public class OMEZarrWriter implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OMEZarrWriter.class);
    private final ImageServer<BufferedImage> server;
    private final Map<Integer, ZarrArray> levelArrays;
    private final ExecutorService executorService;

    private OMEZarrWriter(Builder builder, String path) throws IOException {
        TransformedServerBuilder transformedServerBuilder = new TransformedServerBuilder(ImageServers.pyramidalizeTiled(
                builder.server,
                getChunkSize(
                        builder.tileWidth > 0 ? builder.tileWidth : builder.server.getMetadata().getPreferredTileWidth(),
                        builder.maxNumberOfChunks,
                        builder.server.getWidth()
                ),
                getChunkSize(
                        builder.tileHeight > 0 ? builder.tileHeight : builder.server.getMetadata().getPreferredTileHeight(),
                        builder.maxNumberOfChunks,
                        builder.server.getHeight()
                ),
                builder.downsamples.length == 0 ? builder.server.getPreferredDownsamples() : builder.downsamples
        ));
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
        server = transformedServerBuilder.build();

        OMEZarrAttributesCreator attributes = new OMEZarrAttributesCreator(server.getMetadata());

        ZarrGroup root = ZarrGroup.create(
                path,
                attributes.getGroupAttributes()
        );

        OMEXMLCreator.create(server.getMetadata()).ifPresent(omeXML -> createOmeSubGroup(root, path, omeXML));
        levelArrays = createLevelArrays(
                server,
                root,
                attributes.getLevelAttributes(),
                builder.compressor
        );

        executorService = Executors.newFixedThreadPool(builder.numberOfThreads);
    }

    /**
     * Close this writer. This will wait until all pending tiles
     * are written.
     * <p>
     * If this function is interrupted, all pending and active tasks
     * are cancelled.
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
     * Write the entire image in a background thread.
     * <p>
     * The image will be written from an internal pool of thread, so this function may
     * return before the image is actually written.
     */
    public void writeImage() {
        for (TileRequest tileRequest: server.getTileRequestManager().getAllTileRequests()) {
            writeTile(tileRequest);
        }
    }

    /**
     * Write the provided tile in a background thread.
     * <p>
     * The tile will be written from an internal pool of thread, so this function may
     * return before the tile is actually written.
     * <p>
     * Note that the image server used internally by this writer may not be the one given in
     * {@link Builder#Builder(ImageServer)}. Therefore, the {@link ImageServer#getTileRequestManager() TileRequestManager}
     * of the internal image server may be different from the one of the provided image server,
     * so functions like {@link TileRequestManager#getAllTileRequests()} may not return the expected tiles.
     * Use the {@link ImageServer#getTileRequestManager() TileRequestManager} of {@link #getReaderServer()}
     * to get accurate tiles.
     *
     * @param tileRequest the tile to write
     */
    public void writeTile(TileRequest tileRequest) {
        executorService.execute(() -> {
            try {
                levelArrays.get(tileRequest.getLevel()).write(
                        getData(server.readRegion(tileRequest.getRegionRequest())),
                        getDimensionsOfTile(tileRequest),
                        getOffsetsOfTile(tileRequest)
                );
            } catch (Exception e) {
                logger.error("Error when writing tile", e);
            }
        });
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
        private final ImageServer<BufferedImage> server;
        private Compressor compressor = CompressorFactory.createDefaultCompressor();
        private int numberOfThreads = 12;
        private double[] downsamples = new double[0];
        private int maxNumberOfChunks = 50;
        private int tileWidth = 512;
        private int tileHeight = 512;
        private ImageRegion boundingBox = null;
        private int zStart = 0;
        private int zEnd;
        private int tStart = 0;
        private int tEnd;

        /**
         * Create the builder.
         *
         * @param server the image to write
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
         */
        public Builder compression(Compressor compressor) {
            this.compressor = compressor;
            return this;
        }

        /**
         * Tiles will be written from a pool of thread. This function
         * specifies the number of threads to use. By default, 12 threads are
         * used.
         *
         * @param numberOfThreads the number of threads to use when writing tiles
         * @return this builder
         */
        public Builder parallelize(int numberOfThreads) {
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
            this.downsamples = downsamples;
            return this;
        }

        /**
         * In Zarr files, data is stored in chunks. This parameter defines the maximum number
         * of chunks on the x,y, and z dimensions. By default, this value is set to 50.
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
         * In Zarr files, data is stored in chunks. This parameter defines the size
         * of chunks on the x and y dimensions. By default, these values are set to 512.
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
         * In Zarr files, data is stored in chunks. This parameter defines the size
         * of chunks on the x and y dimensions. By default, these values are set to 512.
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
         *                    the entire image
         * @return this builder
         */
        public Builder region(ImageRegion boundingBox) {
            this.boundingBox = boundingBox;
            return this;
        }

        /**
         * Define the z-slices of the input image to consider.
         *
         * @param zStart the 0-based inclusive index of the first z-slice to consider
         * @param zEnd the 0-based exclusive index of the last z-slice to consider
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
         * @param tStart the 0-based inclusive index of the first timepoint to consider
         * @param tEnd the 0-based exclusive index of the last timepoint to consider
         * @return this builder
         */
        public Builder timePoints(int tStart, int tEnd) {
            this.tStart = tStart;
            this.tEnd = tEnd;
            return this;
        }

        /**
         * Create a new instance of {@link OMEZarrWriter}. This will also
         * create an empty image on the provided path.
         *
         * @param path the path where to write the image. It must end with ".ome.zarr" and shouldn't already exist
         * @return the new {@link OMEZarrWriter}
         * @throws IOException when the empty image cannot be created. This can happen if the provided path is incorrect
         * or if the user doesn't have enough permissions
         * @throws IllegalArgumentException when the provided path doesn't end with ".ome.zarr" or a file/directory already
         * exists at this location
         */
        public OMEZarrWriter build(String path) throws IOException {
            if (!path.endsWith(FILE_EXTENSION)) {
                throw new IllegalArgumentException(String.format("The provided path (%s) does not have the OME-Zarr extension (%s)", path, FILE_EXTENSION));
            }
            if (Files.exists(Paths.get(path))) {
                throw new IllegalArgumentException(String.format("The provided path (%s) already exists", path));
            }

            return new OMEZarrWriter(this, path);
        }
    }

    private static int getChunkSize(int tileSize, int maxNumberOfChunks, int imageSize) {
        return Math.min(
                imageSize,
                maxNumberOfChunks > 0 ?
                        Math.max(tileSize, imageSize / maxNumberOfChunks) :
                        tileSize
        );
    }

    private static void createOmeSubGroup(ZarrGroup mainGroup, String imagePath, String omeXMLContent) {
        String fileName = "OME";

        try {
            mainGroup.createSubGroup(fileName);

            try (OutputStream outputStream = new FileOutputStream(Files.createFile(Paths.get(imagePath, fileName, "METADATA.ome.xml")).toString())) {
                outputStream.write(omeXMLContent.getBytes());
            }
        } catch (IOException e) {
            logger.error("Error while creating OME group or metadata XML file", e);
        }
    }

    private static Map<Integer, ZarrArray> createLevelArrays(
            ImageServer<BufferedImage> server,
            ZarrGroup root,
            Map<String, Object> levelAttributes,
            Compressor compressor
    ) throws IOException {
        Map<Integer, ZarrArray> levelArrays = new HashMap<>();

        for (int level=0; level<server.getMetadata().nLevels(); ++level) {
            levelArrays.put(level, root.createArray(
                    "s" + level,
                    new ArrayParams()
                            .shape(getDimensionsOfImage(server, level))
                            .chunks(getChunksOfImage(server))
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
                    levelAttributes
            ));
        }

        return levelArrays;
    }

    private static int[] getDimensionsOfImage(ImageServer<BufferedImage> server, int level) {
        List<Integer> dimensions = new ArrayList<>();
        if (server.nTimepoints() > 1) {
            dimensions.add(server.nTimepoints());
        }
        if (server.nChannels() > 1) {
            dimensions.add(server.nChannels());
        }
        if (server.nZSlices() > 1) {
            dimensions.add(server.nZSlices());
        }
        dimensions.add((int) (server.getHeight() / server.getDownsampleForResolution(level)));
        dimensions.add((int) (server.getWidth() / server.getDownsampleForResolution(level)));

        return dimensions.stream().mapToInt(i -> i).toArray();
    }

    private static int[] getChunksOfImage(ImageServer<BufferedImage> server) {
        List<Integer> chunks = new ArrayList<>();
        if (server.nTimepoints() > 1) {
            chunks.add(1);
        }
        if (server.nChannels() > 1) {
            chunks.add(1);
        }
        if (server.nZSlices() > 1) {
            chunks.add(1);
        }
        chunks.add(server.getMetadata().getPreferredTileHeight());
        chunks.add(server.getMetadata().getPreferredTileWidth());

        return chunks.stream().mapToInt(i -> i).toArray();
    }

    private Object getData(BufferedImage image) {
        Object pixels = AWTImageTools.getPixels(image);

        if (server.isRGB()) {
            int[][] data = (int[][]) pixels;

            int[] output = new int[server.nChannels() * image.getWidth() * image.getHeight()];
            int i = 0;
            for (int c=0; c<server.nChannels(); ++c) {
                for (int y=0; y<image.getHeight(); ++y) {
                    for (int x=0; x<image.getWidth(); ++x) {
                        output[i] = data[c][x + image.getWidth()*y];
                        i++;
                    }
                }
            }
            return output;
        } else {
            return switch (server.getPixelType()) {
                case UINT8, INT8 -> {
                    byte[][] data = (byte[][]) pixels;

                    byte[] output = new byte[server.nChannels() * image.getWidth() * image.getHeight()];
                    int i = 0;
                    for (int c=0; c<server.nChannels(); ++c) {
                        for (int y=0; y<image.getHeight(); ++y) {
                            for (int x=0; x<image.getWidth(); ++x) {
                                output[i] = data[c][x + image.getWidth()*y];
                                i++;
                            }
                        }
                    }
                    yield output;
                }
                case UINT16, INT16 -> {
                    short[][] data = (short[][]) pixels;

                    short[] output = new short[server.nChannels() * image.getWidth() * image.getHeight()];
                    int i = 0;
                    for (int c=0; c<server.nChannels(); ++c) {
                        for (int y=0; y<image.getHeight(); ++y) {
                            for (int x=0; x<image.getWidth(); ++x) {
                                output[i] = data[c][x + image.getWidth()*y];
                                i++;
                            }
                        }
                    }
                    yield output;
                }
                case UINT32, INT32 -> {
                    int[][] data = (int[][]) pixels;

                    int[] output = new int[server.nChannels() * image.getWidth() * image.getHeight()];
                    int i = 0;
                    for (int c=0; c<server.nChannels(); ++c) {
                        for (int y=0; y<image.getHeight(); ++y) {
                            for (int x=0; x<image.getWidth(); ++x) {
                                output[i] = data[c][x + image.getWidth()*y];
                                i++;
                            }
                        }
                    }
                    yield output;
                }
                case FLOAT32 -> {
                    float[][] data = (float[][]) pixels;

                    float[] output = new float[server.nChannels() * image.getWidth() * image.getHeight()];
                    int i = 0;
                    for (int c=0; c<server.nChannels(); ++c) {
                        for (int y=0; y<image.getHeight(); ++y) {
                            for (int x=0; x<image.getWidth(); ++x) {
                                output[i] = data[c][x + image.getWidth()*y];
                                i++;
                            }
                        }
                    }
                    yield output;
                }
                case FLOAT64 -> {
                    double[][] data = (double[][]) pixels;

                    double[] output = new double[server.nChannels() * image.getWidth() * image.getHeight()];
                    int i = 0;
                    for (int c=0; c<server.nChannels(); ++c) {
                        for (int y=0; y<image.getHeight(); ++y) {
                            for (int x=0; x<image.getWidth(); ++x) {
                                output[i] = data[c][x + image.getWidth()*y];
                                i++;
                            }
                        }
                    }
                    yield output;
                }
            };
        }
    }

    private int[] getDimensionsOfTile(TileRequest tileRequest) {
        List<Integer> dimensions = new ArrayList<>();
        if (server.nTimepoints() > 1) {
            dimensions.add(1);
        }
        if (server.nChannels() > 1) {
            dimensions.add(server.nChannels());
        }
        if (server.nZSlices() > 1) {
            dimensions.add(1);
        }
        dimensions.add(tileRequest.getTileHeight());
        dimensions.add(tileRequest.getTileWidth());

        return dimensions.stream().mapToInt(i -> i).toArray();
    }

    private int[] getOffsetsOfTile(TileRequest tileRequest) {
        List<Integer> offset = new ArrayList<>();
        if (server.nTimepoints() > 1) {
            offset.add(tileRequest.getT());
        }
        if (server.nChannels() > 1) {
            offset.add(0);
        }
        if (server.nZSlices() > 1) {
            offset.add(tileRequest.getZ());
        }
        offset.add(tileRequest.getTileY());
        offset.add(tileRequest.getTileX());

        return offset.stream().mapToInt(i -> i).toArray();
    }
}
