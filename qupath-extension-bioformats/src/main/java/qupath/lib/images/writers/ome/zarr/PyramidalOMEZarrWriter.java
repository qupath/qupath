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
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.bioformats.BioFormatsImageServer;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PyramidalOMEZarrWriter {

    private static final Logger logger = LoggerFactory.getLogger(PyramidalOMEZarrWriter.class);
    private final ImageServer<BufferedImage> server;
    private final String path;
    private final ExecutorService executorService;
    private final ZarrGroup root;
    private final double[] downsamples = new double[] {1, 4, 16, 64};
    private final Compressor compressor = CompressorFactory.createDefaultCompressor();
    private final int tileWidth = 512;
    private final int tileHeight = 512;
    private OMEZarrAttributesCreator attributes;

    public PyramidalOMEZarrWriter(ImageServer<BufferedImage> server, String path) throws IOException {
        int numberOfThreads = ThreadTools.getParallelism();

        this.server = server;
        this.path = path;

        this.root = ZarrGroup.create(path, null);
        OMEXMLCreator.create(server.getMetadata()).ifPresent(omeXML -> createOmeSubGroup(root, path, omeXML));

        this.executorService = Executors.newFixedThreadPool(
                numberOfThreads,
                ThreadTools.createThreadFactory("pyramidal_zarr_writer_", false)
        );
    }

    public void writeImage() throws Exception {
        System.err.println("Level 0");
        writeLevel(0, 1, server);
        this.attributes = new OMEZarrAttributesCreator(new ImageServerMetadata.Builder(server.getMetadata())
                .levelsFromDownsamples(1)
                .build()
        );
        root.writeAttributes(attributes.getGroupAttributes());

        for (int i=1; i<downsamples.length; i++) {
            System.err.println("Level " + i);
            try (ImageServer<BufferedImage> server = new BioFormatsImageServer(Paths.get(path).toUri())) {
                writeLevel(i, downsamples[i], server);
            }

            this.attributes = new OMEZarrAttributesCreator(new ImageServerMetadata.Builder(server.getMetadata())
                    .levelsFromDownsamples(Arrays.stream(downsamples).limit(i+1).toArray())
                    .build()
            );
            root.writeAttributes(attributes.getGroupAttributes());
        }
    }

    private void writeLevel(int level, double downsample, ImageServer<BufferedImage> server) throws Exception {
        ZarrArray zarrArray = root.createArray(
                String.format("s%d", level),
                new ArrayParams()
                        .shape(getDimensionsOfImage(server, downsample))
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
        Collection<TileRequest> tileRequests = getTileRequestsForLevel(
                path,
                level,
                downsample,
                tileWidth,
                tileHeight,
                server.nTimepoints(),
                server.nZSlices(),
                (int) (server.getWidth() / downsample),     // same as in getDimensionsOfImage()
                (int) (server.getHeight() / downsample)
        );
        CountDownLatch latch = new CountDownLatch(tileRequests.size());
        for (TileRequest tileRequest: tileRequests) {
            executorService.execute(() -> {
                try {
                    if (server instanceof BioFormatsImageServer) {
                        zarrArray.write(
                                getData(server.readRegion(RegionRequest.createInstance(
                                        tileRequest.getRegionRequest().getPath(),
                                        tileRequest.getDownsample(),
                                        tileRequest.getTileX(),
                                        tileRequest.getTileY(),
                                        tileRequest.getTileWidth(),
                                        tileRequest.getTileHeight(),
                                        tileRequest.getZ(),
                                        tileRequest.getT()
                                ))),
                                getDimensionsOfTile(tileRequest),
                                getOffsetsOfTile(tileRequest)
                        );
                    } else {
                        zarrArray.write(
                                getData(server.readRegion(tileRequest.getRegionRequest())),
                                getDimensionsOfTile(tileRequest),
                                getOffsetsOfTile(tileRequest)
                        );
                    }
                } catch (Exception e) {
                    logger.error("Error when writing tile", e);
                }
                latch.countDown();
            });
        }
        latch.await();
    }

    public static Collection<TileRequest> getTileRequestsForLevel(
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
