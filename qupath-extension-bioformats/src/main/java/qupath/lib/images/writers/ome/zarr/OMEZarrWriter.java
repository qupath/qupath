package qupath.lib.images.writers.ome.zarr;

import com.bc.zarr.ArrayParams;
import com.bc.zarr.Compressor;
import com.bc.zarr.CompressorFactory;
import com.bc.zarr.DataType;
import com.bc.zarr.DimensionSeparator;
import com.bc.zarr.ZarrArray;
import com.bc.zarr.ZarrGroup;
import loci.formats.gui.AWTImageTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import ucar.ma2.InvalidRangeException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OMEZarrWriter implements AutoCloseable {

    private final ImageServer<BufferedImage> server;
    private final Compressor compressor;
    private final Map<Integer, ZarrArray> levelArrays;
    private final ExecutorService executorService;
    private int numberOfTasksRunning = 0;

    private OMEZarrWriter(Builder builder) throws IOException {
        this.server = builder.server;
        this.compressor = builder.compressor;
        this.levelArrays = createLevelArrays(ZarrGroup.create(
                builder.path,
                OMEZarrAttributes.getGroupAttributes(server)
        ));
        executorService = Executors.newFixedThreadPool(builder.numberOfThreads);
    }

    @Override
    public void close() {
        executorService.shutdown();
    }

    public void write(TileRequest tileRequest) throws IOException {
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    addTask();
                    writeTile(tileRequest);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    removeTask();
                }
            }, executorService);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public synchronized boolean isWriting() {
        return numberOfTasksRunning > 0;
    }

    public static class Builder {

        private static final String FILE_EXTENSION = ".ome.zarr";
        private final ImageServer<BufferedImage> server;
        private final String path;
        private Compressor compressor = CompressorFactory.createDefaultCompressor();
        private int numberOfThreads = 12;

        public Builder(ImageServer<BufferedImage> server, String path) {
            if (!path.endsWith(FILE_EXTENSION)) {
                throw new IllegalArgumentException(String.format("The provided path (%s) does not have the OME-Zarr extension (%s)", path, FILE_EXTENSION));
            }

            this.server = server;
            this.path = path;
        }

        public Builder setCompressor(Compressor compressor) {
            this.compressor = compressor;
            return this;
        }

        public Builder setNumberOfThreads(int numberOfThreads) {
            this.numberOfThreads = numberOfThreads;
            return this;
        }

        public OMEZarrWriter build() throws IOException {
            return new OMEZarrWriter(this);
        }
    }

    private Map<Integer, ZarrArray> createLevelArrays(ZarrGroup root) throws IOException {
        Map<Integer, ZarrArray> levelArrays = new HashMap<>();

        for (int level=0; level<server.getMetadata().nLevels(); ++level) {
            levelArrays.put(level, root.createArray(
                    "s" + level,
                    new ArrayParams()
                            .shape(getDimensionsOfImage(level))
                            .chunks(getChunksOfImage())
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
                    OMEZarrAttributes.getLevelAttributes(server)
            ));
        }

        return levelArrays;
    }

    private synchronized void addTask() {
        numberOfTasksRunning++;
    }

    private synchronized void removeTask() {
        numberOfTasksRunning--;
    }

    private void writeTile(TileRequest tileRequest) throws IOException {
        try {
            levelArrays.get(tileRequest.getLevel()).write(
                    getData(
                            server.readRegion(tileRequest.getRegionRequest()),
                            server.getPixelType(),
                            server.nChannels(),
                            tileRequest.getTileHeight(),
                            tileRequest.getTileWidth()
                    ),
                    getDimensionsOfTile(tileRequest),
                    getOffsetsOfTile(tileRequest)
            );
        } catch (InvalidRangeException e) {
            throw new IOException(e);
        }
    }

    private int[] getDimensionsOfImage(int level) {
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

        int[] dimensionArray = new int[dimensions.size()];
        for(int i = 0; i < dimensions.size(); i++) {
            dimensionArray[i] = dimensions.get(i);
        }
        return dimensionArray;
    }

    private int[] getChunksOfImage() {
        List<Integer> chunks = new ArrayList<>();
        if (server.nTimepoints() > 1) {
            chunks.add(1);
        }
        if (server.nChannels() > 1) {
            chunks.add(1);
        }
        if (server.nZSlices() > 1) {
            chunks.add(Math.max(server.getMetadata().getPreferredTileWidth(), server.getMetadata().getPreferredTileHeight()));
        }
        chunks.add(server.getMetadata().getPreferredTileHeight());

        chunks.add(server.getMetadata().getPreferredTileWidth());

        int[] chunksArray = new int[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            chunksArray[i] = chunks.get(i);
        }
        return chunksArray;
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

        int[] dimensionArray = new int[dimensions.size()];
        for (int i = 0; i < dimensions.size(); i++) {
            dimensionArray[i] = dimensions.get(i);
        }
        return dimensionArray;
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

        int[] offsetArray = new int[offset.size()];
        for (int i = 0; i < offset.size(); i++) {
            offsetArray[i] = offset.get(i);
        }
        return offsetArray;
    }

    private static Object getData(BufferedImage image, PixelType pixelType, int numberOfChannels, int height, int width) {
        Object pixels = AWTImageTools.getPixels(image);

        return switch (pixelType) {
            case UINT8, INT8 -> {
                byte[][] data = (byte[][]) pixels;

                byte[] output = new byte[numberOfChannels * width * height];
                int i = 0;
                for (int c=0; c<numberOfChannels; ++c) {
                    for (int y=0; y<height; ++y) {
                        for (int x=0; x<width; ++x) {
                            output[i] = data[c][x + width*y];
                            i++;
                        }
                    }
                }
                yield output;
            }
            case UINT16, INT16 -> {
                short[][] data = (short[][]) pixels;

                short[] output = new short[numberOfChannels * width * height];
                int i = 0;
                for (int c=0; c<numberOfChannels; ++c) {
                    for (int y=0; y<height; ++y) {
                        for (int x=0; x<width; ++x) {
                            output[i] = data[c][x + width*y];
                            i++;
                        }
                    }
                }
                yield output;
            }
            case UINT32, INT32 -> {
                int[][] data = (int[][]) pixels;

                int[] output = new int[numberOfChannels * width * height];
                int i = 0;
                for (int c=0; c<numberOfChannels; ++c) {
                    for (int y=0; y<height; ++y) {
                        for (int x=0; x<width; ++x) {
                            output[i] = data[c][x + width*y];
                            i++;
                        }
                    }
                }
                yield output;
            }
            case FLOAT32 -> {
                float[][] data = (float[][]) pixels;

                float[] output = new float[numberOfChannels * width * height];
                int i = 0;
                for (int c=0; c<numberOfChannels; ++c) {
                    for (int y=0; y<height; ++y) {
                        for (int x=0; x<width; ++x) {
                            output[i] = data[c][x + width*y];
                            i++;
                        }
                    }
                }
                yield output;
            }
            case FLOAT64 -> {
                double[][] data = (double[][]) pixels;

                double[] output = new double[numberOfChannels * width * height];
                int i = 0;
                for (int c=0; c<numberOfChannels; ++c) {
                    for (int y=0; y<height; ++y) {
                        for (int x=0; x<width; ++x) {
                            output[i] = data[c][x + width*y];
                            i++;
                        }
                    }
                }
                yield output;
            }
        };
    }
}
