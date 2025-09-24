package qupath.lib.images.writers.ome.zarr;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.WritableRaster;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class TestPyramidalOMEZarrWriter {

    @Test
    void Check_Error_When_No_Downsample_Given() throws Exception {
        SampleImageServer sampleImageServer = new SampleImageServer();
        double[] downsamples = new double[] {};

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PyramidalOMEZarrWriter.Builder(sampleImageServer).downsamples(downsamples)
        );

        sampleImageServer.close();
    }

    @Test
    void Check_Error_When_Downsample_Less_Than_Zero_Given() throws Exception {
        SampleImageServer sampleImageServer = new SampleImageServer();
        double[] downsamples = new double[] {-1, 1, 4};

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PyramidalOMEZarrWriter.Builder(sampleImageServer).downsamples(downsamples)
        );

        sampleImageServer.close();
    }

    @Test
    void Check_Error_When_Null_Server() {
        SampleImageServer sampleImageServer = null;
        double[] downsamples = new double[] {1d, 2d, 6d};

        Assertions.assertThrows(
                NullPointerException.class,
                () -> new PyramidalOMEZarrWriter.Builder(sampleImageServer).downsamples(downsamples)
        );
    }

    @Test
    void Check_Error_When_Extension_Incorrect() throws Exception {
        String extension = ".wrong.extension";
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image" + extension).toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        double[] downsamples = new double[] {1d, 2d, 6d};

        Assertions.assertThrows(
                Exception.class,
                () -> new PyramidalOMEZarrWriter.Builder(sampleImageServer).downsamples(downsamples).build(outputImagePath).writeImage()
        );

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Error_When_Path_Already_Exists() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        Files.createFile(Paths.get(outputImagePath));
        SampleImageServer sampleImageServer = new SampleImageServer();
        double[] downsamples = new double[] {1d, 2d, 6d};

        Assertions.assertThrows(
                Exception.class,
                () -> new PyramidalOMEZarrWriter.Builder(sampleImageServer).downsamples(downsamples).build(outputImagePath).writeImage()
        );

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void Check_Image_Pixels(int level) throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int z = 1;
        int t = 1;
        double[] downsamples = new double[] {1d, 2d, 6d};
        double downsample = downsamples[level];
        BufferedImage expectedImage = sampleImageServer.readRegion(
                downsample,
                0,
                0,
                sampleImageServer.getWidth(),
                sampleImageServer.getHeight(),
                z,
                t
        );
        PyramidalOMEZarrWriter writer = new PyramidalOMEZarrWriter.Builder(sampleImageServer).downsamples(downsamples).build(outputImagePath);

        writer.writeImage();

        BufferedImage image;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            image = server.readRegion(
                    downsample,
                    0,
                    0,
                    server.getWidth(),
                    server.getHeight(),
                    z,
                    t
            );
        }
        assertDoubleBufferedImagesEqual(expectedImage, image);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void Check_Image_Pixels_With_Base_Server_With_Downsample_2(int level) throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer(2);
        int z = 1;
        int t = 1;
        double[] downsamples = new double[] {1d, 2d, 6d};
        double downsample = downsamples[level];
        BufferedImage expectedImage = sampleImageServer.readRegion(
                downsample,
                0,
                0,
                sampleImageServer.getWidth(),
                sampleImageServer.getHeight(),
                z,
                t
        );
        PyramidalOMEZarrWriter writer = new PyramidalOMEZarrWriter.Builder(sampleImageServer).downsamples(downsamples).build(outputImagePath);

        writer.writeImage();

        BufferedImage image;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            image = server.readRegion(
                    downsample,
                    0,
                    0,
                    server.getWidth(),
                    server.getHeight(),
                    z,
                    t
            );
        }
        assertDoubleBufferedImagesEqual(expectedImage, image);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void Check_Image_Pixels_With_Base_Downsample_2(int level) throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int z = 1;
        int t = 1;
        double[] downsamples = new double[] {2d, 6d};
        double[] downsamplesInExportedImage = new double[] {1d, 3d};
        double downsample = downsamples[level];
        double exportedImageDownsample = downsamplesInExportedImage[level];
        BufferedImage expectedImage = sampleImageServer.readRegion(
                downsample,
                0,
                0,
                sampleImageServer.getWidth(),
                sampleImageServer.getHeight(),
                z,
                t
        );
        PyramidalOMEZarrWriter writer = new PyramidalOMEZarrWriter.Builder(sampleImageServer).downsamples(downsamples).build(outputImagePath);

        writer.writeImage();

        BufferedImage image;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            image = server.readRegion(
                    exportedImageDownsample,
                    0,
                    0,
                    server.getWidth(),
                    server.getHeight(),
                    z,
                    t
            );
        }
        assertDoubleBufferedImagesEqual(expectedImage, image);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    private static class SampleImageServer extends AbstractImageServer<BufferedImage> {

        private static final double[][][][] PIXELS;
        private final ImageServerMetadata metadata;

        static {
            int sizeT = 2;
            int sizeZ = 2;
            int sizeY = 6;
            int sizeX = 6;
            PIXELS = new double[sizeT][sizeZ][sizeY][sizeX];

            for (int t=0; t<sizeT; t++) {
                for (int z=0; z<sizeZ; z++) {
                    for (int y=0; y<sizeY; y++) {
                        for (int x=0; x<sizeX; x++) {
                            PIXELS[t][z][y][x] = t + z + y + x;
                        }
                    }
                }
            }
        }

        public SampleImageServer() {
            this(1);
        }

        public SampleImageServer(double baseDownsample) {
            super(BufferedImage.class);

            this.metadata = new ImageServerMetadata.Builder()
                    .width(PIXELS[0][0][0].length)
                    .height(PIXELS[0][0].length)
                    .sizeZ(PIXELS[0].length)
                    .sizeT(PIXELS.length)
                    .pixelType(PixelType.FLOAT64)
                    .preferredTileSize(64, 64)
                    .channels(List.of(
                            ImageChannel.getInstance("c1", ColorTools.CYAN)
                    ))
                    .name("name")
                    .levelsFromDownsamples(baseDownsample)
                    .magnification(2.4)
                    .build();
        }

        @Override
        protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
            return null;
        }

        @Override
        protected String createID() {
            return getClass().getName();
        }

        @Override
        public Collection<URI> getURIs() {
            return List.of();
        }

        @Override
        public String getServerType() {
            return "Sample server";
        }

        @Override
        public ImageServerMetadata getOriginalMetadata() {
            return metadata;
        }

        @Override
        public BufferedImage readRegion(RegionRequest request) {
            DataBuffer dataBuffer = createDataBuffer(request);

            BufferedImage fullSizedImage = new BufferedImage(
                    ColorModelFactory.createColorModel(getMetadata().getPixelType(), getMetadata().getChannels()),
                    WritableRaster.createWritableRaster(
                            new BandedSampleModel(
                                    dataBuffer.getDataType(),
                                    request.getWidth(),
                                    request.getHeight(),
                                    nChannels()
                            ),
                            dataBuffer,
                            null
                    ),
                    false,
                    null
            );

            if (request.getDownsample() == 1) {
                return fullSizedImage;
            } else {
                return BufferedImageTools.resize(
                        fullSizedImage,
                        (int) (request.getWidth() / request.getDownsample()),
                        (int) (request.getHeight() / request.getDownsample()),
                        true
                );
            }
        }

        private DataBuffer createDataBuffer(RegionRequest request) {
            double[][] array = new double[nChannels()][];

            for (int c = 0; c < array.length; c++) {
                array[c] = getPixels(request);
            }

            return new DataBufferDouble(array, (int) (request.getWidth() * request.getHeight() / 8d));
        }

        private double[] getPixels(RegionRequest request) {
            double[] pixels = new double[request.getWidth() * request.getHeight()];

            int i = 0;
            for (int y=request.getY(); y<request.getHeight(); y++) {
                for (int x=request.getX(); x<request.getWidth(); x++) {
                    pixels[i++] = PIXELS[request.getT()][request.getZ()][y][x];
                }
            }

            return pixels;
        }
    }

    private static void assertDoubleBufferedImagesEqual(BufferedImage expectedImage, BufferedImage actualImage) {
        Assertions.assertEquals(expectedImage.getWidth(), actualImage.getWidth());
        Assertions.assertEquals(expectedImage.getHeight(), actualImage.getHeight());

        double[] expectedPixels = new double[expectedImage.getSampleModel().getNumBands()];
        double[] actualPixels = new double[actualImage.getSampleModel().getNumBands()];
        for (int x = 0; x < expectedImage.getWidth(); x++) {
            for (int y = 0; y < expectedImage.getHeight(); y++) {
                Assertions.assertArrayEquals(
                        (double[]) expectedImage.getData().getDataElements(x, y, expectedPixels),
                        (double[]) actualImage.getData().getDataElements(x, y, actualPixels)
                );
            }
        }
    }
}
