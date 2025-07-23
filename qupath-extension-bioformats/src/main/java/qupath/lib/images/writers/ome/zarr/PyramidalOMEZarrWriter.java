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
import qupath.lib.common.ThreadTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.ImageRegion;

import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PyramidalOMEZarrWriter {

    private static final Logger logger = LoggerFactory.getLogger(PyramidalOMEZarrWriter.class);
    private final ImageServer<BufferedImage> server;
    private final String path;
    private final ExecutorService executorService;
    private final OMEZarrAttributesCreator attributes;
    private final ZarrGroup root;
    private final double[] downsamples = new double[] {1, 4, 16};
    private final Compressor compressor = CompressorFactory.nullCompressor;
    private final int tileWidth = 512;
    private final int tileHeight = 512;

    private PyramidalOMEZarrWriter(ImageServer<BufferedImage> server, String path) throws IOException {
        int numberOfThreads = ThreadTools.getParallelism();

        this.server = server;
        this.path = path;

        this.attributes = new OMEZarrAttributesCreator(new ImageServerMetadata.Builder(server.getMetadata())
                .levelsFromDownsamples(downsamples)
                .build()
        );
        this.root = ZarrGroup.create(
                path,
                attributes.getGroupAttributes()
        );
        OMEXMLCreator.create(server.getMetadata()).ifPresent(omeXML -> createOmeSubGroup(root, path, omeXML));

        this.executorService = Executors.newFixedThreadPool(
                numberOfThreads,
                ThreadTools.createThreadFactory("pyramidal_zarr_writer_", false)
        );
    }

    public void writeImage() throws InterruptedException, IOException {
        ZarrArray level0 = root.createArray(
                "s0",
                new ArrayParams()
                        .shape(getDimensionsOfImage(server, 1))
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
                attributes.getLevelAttributes()
        );
        Collection<TileRequest> tileRequests0 = getTileRequestsForLevel(
                path,
                0,
                1,
                tileWidth,
                tileHeight,
                server.nTimepoints(),
                server.nZSlices(),
                (int) (server.getWidth() / 1.),     // same as in getDimensionsOfImage()
                (int) (server.getHeight() / 1.)
        );
        ImageServer<BufferedImage> server0 = server;
        CountDownLatch latch0 = new CountDownLatch(tileRequests0.size());
        for (TileRequest tileRequest: tileRequests0) {
            executorService.execute(() -> {
                try {
                    level0.write(
                            getData(server0.readRegion(tileRequest.getRegionRequest())),
                            getDimensionsOfTile(tileRequest),
                            getOffsetsOfTile(tileRequest)
                    );
                } catch (Exception e) {
                    logger.error("Error when writing tile", e);
                }
                latch0.countDown();
            });
        }
        latch0.await();

        ZarrArray level1 = root.createArray(
                "s1",
                new ArrayParams()
                        .shape(getDimensionsOfImage(server, 4))
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
                attributes.getLevelAttributes()
        );
        Collection<TileRequest> tileRequests1 = getTileRequestsForLevel(
                path,
                1,
                4,
                tileWidth,
                tileHeight,
                server.nTimepoints(),
                server.nZSlices(),
                (int) (server.getWidth() / 4.),     // same as in getDimensionsOfImage()
                (int) (server.getHeight() / 4.)
        );
        ImageServer<BufferedImage> server1 = server;
        CountDownLatch latch1 = new CountDownLatch(tileRequests1.size());
        for (TileRequest tileRequest: tileRequests1) {
            executorService.execute(() -> {
                try {
                    level1.write(
                            getData(server1.readRegion(tileRequest.getRegionRequest())),
                            getDimensionsOfTile(tileRequest),
                            getOffsetsOfTile(tileRequest)
                    );
                } catch (Exception e) {
                    logger.error("Error when writing tile", e);
                }
                latch1.countDown();
            });
        }
        latch1.await();
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
        var set = new LinkedHashSet<TileRequest>();

        for (int t = 0; t < timepoints; t++) {
            for (int z = 0; z < zSlices; z++) {
                for (int y = 0; y < levelHeight; y += tileHeight) {
                    int th = tileHeight;
                    if (y + th > levelHeight)
                        th = levelHeight - y;

                    for (int x = 0; x < levelWidth; x += tileWidth) {
                        int tw = tileWidth;
                        if (x + tw > levelWidth)
                            tw = levelWidth - x;

                        set.add(TileRequest.createInstance(
                                path,
                                level,
                                downsample,
                                ImageRegion.createInstance(x, y, tw, th, z, t)
                        ));
                    }
                }
            }
        }

        return set;
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

    private static int[] getDimensionsOfImage(ImageServer<BufferedImage> server, double downsample) {
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
        dimensions.add((int) (server.getHeight() / downsample));
        dimensions.add((int) (server.getWidth() / downsample));

        return dimensions.stream().mapToInt(i -> i).toArray();
    }

    private int[] getChunksOfImage(ImageServer<BufferedImage> server) {
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
        chunks.add(tileHeight);
        chunks.add(tileWidth);

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
