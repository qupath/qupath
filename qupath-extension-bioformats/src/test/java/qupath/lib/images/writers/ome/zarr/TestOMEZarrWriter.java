package qupath.lib.images.writers.ome.zarr;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.ImageRegion;
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

public class TestOMEZarrWriter {

    @Test
    void Check_Error_When_Extension_Incorrect() throws Exception {
        String extension = ".wrong.extension";
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image" + extension).toString();
        SampleImageServer sampleImageServer = new SampleImageServer();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new OMEZarrWriter.Builder(sampleImageServer).build(outputImagePath)
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

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new OMEZarrWriter.Builder(sampleImageServer).build(outputImagePath)
        );

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_OME_XML_File_Exists() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer).build(outputImagePath)) {
            writer.writeImage();
        }

        Assertions.assertTrue(Files.exists(Paths.get(outputImagePath, "OME", "METADATA.ome.xml")));

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Pixel_Type() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        PixelType expectedPixelType = sampleImageServer.getMetadata().getPixelType();

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer).build(outputImagePath)) {
            writer.writeImage();
        }

        PixelType pixelType;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            pixelType = server.getMetadata().getPixelType();
        }
        Assertions.assertEquals(expectedPixelType, pixelType);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Pixel_Calibration() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        PixelCalibration expectedPixelCalibration = sampleImageServer.getMetadata().getPixelCalibration();

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer).build(outputImagePath)) {
            writer.writeImage();
        }

        PixelCalibration pixelCalibration;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            pixelCalibration = server.getMetadata().getPixelCalibration();
        }
        Assertions.assertEquals(expectedPixelCalibration, pixelCalibration);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Channels() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        List<ImageChannel> expectedChannels = sampleImageServer.getMetadata().getChannels();

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer).build(outputImagePath)) {
            writer.writeImage();
        }

        List<ImageChannel> channels;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            channels = server.getMetadata().getChannels();
        }
        Assertions.assertEquals(expectedChannels, channels);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Magnification() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        double expectedMagnification = sampleImageServer.getMetadata().getMagnification();

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer).build(outputImagePath)) {
            writer.writeImage();
        }

        double magnification;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            magnification = server.getMetadata().getMagnification();
        }
        Assertions.assertEquals(expectedMagnification, magnification);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Full_Image_Pixels() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int level = 0;
        int z = 2;
        int t = 1;
        BufferedImage expectedImage = sampleImageServer.readRegion(
                sampleImageServer.getDownsampleForResolution(level),
                0,
                0,
                sampleImageServer.getWidth(),
                sampleImageServer.getHeight(),
                z,
                t
        );

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer).build(outputImagePath)) {
            writer.writeImage();
        }

        BufferedImage image;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            image = server.readRegion(
                    server.getDownsampleForResolution(level),
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

    @Test
    void Check_Downsampled_Image_Pixels() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int level = 1;
        int z = 2;
        int t = 1;
        BufferedImage expectedImage = sampleImageServer.readRegion(
                sampleImageServer.getDownsampleForResolution(level),
                0,
                0,
                sampleImageServer.getWidth(),
                sampleImageServer.getHeight(),
                z,
                t
        );

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer).build(outputImagePath)) {
            writer.writeImage();
        }

        BufferedImage image;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            image = server.readRegion(
                    server.getDownsampleForResolution(level),
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

    @Test
    void Check_Downsamples_When_Not_Specified() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        double[] expectedDownsamples = sampleImageServer.getPreferredDownsamples();

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .downsamples()
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        double[] downsamples;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            downsamples = server.getPreferredDownsamples();
        }
        Assertions.assertArrayEquals(expectedDownsamples, downsamples);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Downsamples_When_Specified() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        double[] expectedDownsamples = new double[] {1, 2, 4};

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .downsamples(expectedDownsamples)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        double[] downsamples;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            downsamples = server.getPreferredDownsamples();
        }
        Assertions.assertArrayEquals(expectedDownsamples, downsamples);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Default_Tile_Width() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int expectedTileWidth = sampleImageServer.getMetadata().getPreferredTileWidth();

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .tileSize(-1)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        int tileWidth;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            tileWidth = server.getMetadata().getPreferredTileWidth();
        }
        Assertions.assertEquals(expectedTileWidth, tileWidth);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Custom_Tile_Width() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int expectedTileWidth = 64;

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .tileSize(expectedTileWidth)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        int tileWidth;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            tileWidth = server.getMetadata().getPreferredTileWidth();
        }
        Assertions.assertEquals(expectedTileWidth, tileWidth);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Default_Tile_Height() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int expectedTileHeight = sampleImageServer.getMetadata().getPreferredTileHeight();

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .tileSize(-1)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        int tileHeight;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            tileHeight = server.getMetadata().getPreferredTileHeight();
        }
        Assertions.assertEquals(expectedTileHeight, tileHeight);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Custom_Tile_Height() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int expectedTileHeight = 64;

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .tileSize(expectedTileHeight)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        int tileHeight;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            tileHeight = server.getMetadata().getPreferredTileHeight();
        }
        Assertions.assertEquals(expectedTileHeight, tileHeight);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Bounding_Box() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int z = 2;
        int t = 1;
        ImageRegion boundingBox = ImageRegion.createInstance(5, 5, 20, 25, z, t);
        BufferedImage expectedImage = sampleImageServer.readRegion(RegionRequest.createInstance(sampleImageServer.getPath(), 1, boundingBox));

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .region(boundingBox)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        BufferedImage image;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            image = server.readRegion(1, 0, 0, server.getWidth(), server.getHeight(), z, t);
        }
        assertDoubleBufferedImagesEqual(expectedImage, image);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Z_Sliced_Image_Number_Of_Z_Stacks() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int zStart = 1;
        int zEnd = 3;
        int expectedNumberOfZStacks = zEnd - zStart;

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .zSlices(zStart, zEnd)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        int numberOfZStacks;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            numberOfZStacks = server.nZSlices();
        }
        Assertions.assertEquals(expectedNumberOfZStacks, numberOfZStacks);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Z_Sliced_Image_Pixels() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int zStart = 1;
        int zEnd = 3;
        int z = 1;
        int t = 1;
        BufferedImage expectedImage = sampleImageServer.readRegion(RegionRequest.createInstance(
                sampleImageServer.getPath(),
                1,
                0,
                0,
                sampleImageServer.getWidth(),
                sampleImageServer.getHeight(),
                z,
                t
        ));

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .zSlices(zStart, zEnd)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        BufferedImage image;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            image = server.readRegion(1, 0, 0, server.getWidth(), server.getHeight(), z - zStart, t);
        }
        assertDoubleBufferedImagesEqual(expectedImage, image);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_T_Sliced_Image_Number_Of_Timepoints() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int tStart = 1;
        int tEnd = 2;
        int expectedNumberOfTimepoints = tEnd - tStart;

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .timePoints(tStart, tEnd)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        int numberOfTimepoints;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            numberOfTimepoints = server.nTimepoints();
        }
        Assertions.assertEquals(expectedNumberOfTimepoints, numberOfTimepoints);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_T_Sliced_Image_Pixels() throws Exception {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        String outputImagePath = Paths.get(path.toString(), "image.ome.zarr").toString();
        SampleImageServer sampleImageServer = new SampleImageServer();
        int tStart = 1;
        int tEnd = 2;
        int z = 1;
        int t = 1;
        BufferedImage expectedImage = sampleImageServer.readRegion(RegionRequest.createInstance(
                sampleImageServer.getPath(),
                1,
                0,
                0,
                sampleImageServer.getWidth(),
                sampleImageServer.getHeight(),
                z,
                t
        ));

        try (OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleImageServer)
                .timePoints(tStart, tEnd)
                .build(outputImagePath)
        ) {
            writer.writeImage();
        }

        BufferedImage image;
        try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
            image = server.readRegion(1, 0, 0, server.getWidth(), server.getHeight(), z, t - tStart);
        }
        assertDoubleBufferedImagesEqual(expectedImage, image);

        sampleImageServer.close();
        FileUtils.deleteDirectory(path.toFile());
    }

    private static class SampleImageServer extends AbstractImageServer<BufferedImage> {

        private static final int IMAGE_WIDTH = 64;
        private static final int IMAGE_HEIGHT = 64;

        public SampleImageServer() {
            super(BufferedImage.class);
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
            return new ImageServerMetadata.Builder()
                    .width(IMAGE_WIDTH)
                    .height(IMAGE_HEIGHT)
                    .sizeZ(3)
                    .sizeT(2)
                    .pixelType(PixelType.FLOAT64)
                    .preferredTileSize(32, 32)
                    .channels(List.of(
                            ImageChannel.getInstance("c1", ColorTools.CYAN),
                            ImageChannel.getInstance("c2", ColorTools.BLUE),
                            ImageChannel.getInstance("c3", ColorTools.RED),
                            ImageChannel.getInstance("c4", ColorTools.GREEN),
                            ImageChannel.getInstance("c5", ColorTools.MAGENTA)
                    ))
                    .name("name")
                    .levelsFromDownsamples(1, 2)
                    .magnification(2.4)
                    .build();
        }

        @Override
        public BufferedImage readRegion(RegionRequest request) {
            DataBuffer dataBuffer = createDataBuffer(request);

            return new BufferedImage(
                    ColorModelFactory.createColorModel(getMetadata().getPixelType(), getMetadata().getChannels()),
                    WritableRaster.createWritableRaster(
                            new BandedSampleModel(
                                    dataBuffer.getDataType(),
                                    (int) (request.getWidth() / request.getDownsample()),
                                    (int) (request.getHeight() / request.getDownsample()),
                                    nChannels()
                            ),
                            dataBuffer,
                            null
                    ),
                    false,
                    null
            );
        }

        private DataBuffer createDataBuffer(RegionRequest request) {
            double[][] array = new double[nChannels()][];

            for (int c = 0; c < array.length; c++) {
                array[c] = getPixels(request, c);
            }

            return new DataBufferDouble(array, (int) (request.getWidth() * request.getHeight() / 8 / (request.getDownsample() * request.getDownsample())));
        }

        private double[] getPixels(RegionRequest request, int channel) {
            int originX = (int) (request.getX() / request.getDownsample());
            int originY = (int) (request.getY() / request.getDownsample());
            int width = (int) (request.getWidth() / request.getDownsample());
            int height = (int) (request.getHeight() / request.getDownsample());
            double[] pixels = new double[width * height];

            for (int y=0; y<height; y++) {
                for (int x=0; x<width; x++) {
                    pixels[y*width + x] = getPixel(x + originX, y + originY, channel, request.getZ(), request.getT());
                }
            }

            return pixels;
        }

        private static double getPixel(int x, int y, int channel, int z, int t) {
            return z + t + channel + ((double) x / IMAGE_WIDTH + (double) y / IMAGE_HEIGHT) / 2;
        }
    }

    private void assertDoubleBufferedImagesEqual(BufferedImage expectedImage, BufferedImage actualImage) {
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
