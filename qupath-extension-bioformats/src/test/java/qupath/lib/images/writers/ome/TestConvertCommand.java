package qupath.lib.images.writers.ome;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.writers.ome.zarr.OMEZarrWriter;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.commons.io.FileUtils;

public class TestConvertCommand {

    private static final String inputImagePath = Paths.get(System.getProperty("java.io.tmpdir"), "image.ome.zarr").toString();

    abstract static class GenericImage {

        abstract String getImageExtension();

        @BeforeAll
        static void createInputImage() throws Exception {
            deleteFileOrDirectory(inputImagePath);

            try (
                    ImageServer<BufferedImage> sampleServer = new SampleImageServer();
                    OMEZarrWriter writer = new OMEZarrWriter.Builder(sampleServer).build(inputImagePath)
            ) {
                writer.writeImage();
            }
        }

        @AfterAll
        static void deleteInputImage() throws IOException {
            deleteFileOrDirectory(inputImagePath);
        }

        @Test
        void Check_Image_Not_Cropped() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            int expectedWidth = SampleImageServer.IMAGE_WIDTH;

            cmd.execute(inputImagePath, outputImagePath, "-r", "");

            int imageWidth;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                imageWidth = server.getWidth();
            }
            Assertions.assertEquals(expectedWidth, imageWidth);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Image_Cropped() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            int expectedWidth = 2;

            cmd.execute(inputImagePath, outputImagePath, "-r", String.format("0,0,%d,1", expectedWidth));

            int imageWidth;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                imageWidth = server.getWidth();
            }
            Assertions.assertEquals(expectedWidth, imageWidth);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Image_Not_Z_Sliced() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            int expectedZSlices = SampleImageServer.NUMBER_OF_Z_SLICES;

            cmd.execute(inputImagePath, outputImagePath, "-z", "all");

            int zSlices;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                zSlices = server.nZSlices();
            }
            Assertions.assertEquals(expectedZSlices, zSlices);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Image_Z_Sliced() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            int expectedZSlices = 1;

            cmd.execute(inputImagePath, outputImagePath, "-z", "1");

            int zSlices;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                zSlices = server.nZSlices();
            }
            Assertions.assertEquals(expectedZSlices, zSlices);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Image_Z_Sliced_By_Range() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            int expectedZSlices = 2;

            cmd.execute(inputImagePath, outputImagePath, "-z", "1-2");

            int zSlices;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                zSlices = server.nZSlices();
            }
            Assertions.assertEquals(expectedZSlices, zSlices);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Image_Not_T_Sliced() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            int expectedTimepoints = SampleImageServer.NUMBER_OF_TIMEPOINTS;

            cmd.execute(inputImagePath, outputImagePath, "-t", "all");

            int timepoints;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                timepoints = server.nTimepoints();
            }
            Assertions.assertEquals(expectedTimepoints, timepoints);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Image_T_Sliced() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            int expectedTimepoints = 1;

            cmd.execute(inputImagePath, outputImagePath, "-t", "1");

            int timepoints;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                timepoints = server.nTimepoints();
            }
            Assertions.assertEquals(expectedTimepoints, timepoints);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Image_T_Sliced_By_Range() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            int expectedTimepoints = 2;

            cmd.execute(inputImagePath, outputImagePath, "-t", "1-2");

            int timepoints;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                timepoints = server.nTimepoints();
            }
            Assertions.assertEquals(expectedTimepoints, timepoints);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Image_Not_Downsampled() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            int expectedWidth = SampleImageServer.IMAGE_WIDTH;

            cmd.execute(inputImagePath, outputImagePath, "-d", "1.0");

            int width;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                width = server.getWidth();
            }
            Assertions.assertEquals(expectedWidth, width);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Image_Downsampled() throws Exception {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            float downsample = 2;
            int expectedWidth = (int) (SampleImageServer.IMAGE_WIDTH / downsample);

            cmd.execute(inputImagePath, outputImagePath, "-d", String.valueOf(downsample));

            int width;
            try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(outputImagePath, BufferedImage.class)) {
                width = server.getWidth();
            }
            Assertions.assertEquals(expectedWidth, width);

            deleteFileOrDirectory(outputImagePath);
        }

        @Test
        void Check_Overwritten() throws IOException {
            ConvertCommand convertCommand = new ConvertCommand();
            CommandLine cmd = new CommandLine(convertCommand);
            String outputImagePath = Paths.get(Files.createTempDirectory(UUID.randomUUID().toString()).toString(), "image" + getImageExtension()).toString();
            cmd.execute(inputImagePath, outputImagePath);

            int exitCode = cmd.execute(inputImagePath, outputImagePath, "--overwrite");

            Assertions.assertEquals(0, exitCode);

            deleteFileOrDirectory(outputImagePath);
        }

        private static void deleteFileOrDirectory(String imagePath) throws IOException {
            File image = new File(imagePath);

            if (image.exists()) {
                if (image.isDirectory()) {
                    FileUtils.deleteDirectory(image);
                } else {
                    Files.delete(image.toPath());
                }
            }
        }
    }

    @Nested
    class ZarrImage extends GenericImage {

        @Override
        String getImageExtension() {
            return ".ome.zarr";
        }
    }

    @Nested
    class TiffImage extends GenericImage {

        @Override
        String getImageExtension() {
            return ".ome.tif";
        }
    }

    private static class SampleImageServer extends AbstractImageServer<BufferedImage> {

        private static final int IMAGE_WIDTH = 6;
        private static final int IMAGE_HEIGHT = 5;
        private static final int NUMBER_OF_Z_SLICES = 3;
        private static final int NUMBER_OF_TIMEPOINTS = 2;

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
                    .sizeZ(NUMBER_OF_Z_SLICES)
                    .sizeT(NUMBER_OF_TIMEPOINTS)
                    .pixelType(PixelType.FLOAT64)
                    .preferredTileSize(1, 1)
                    .channels(List.of(
                            ImageChannel.getInstance("c1", 1),
                            ImageChannel.getInstance("c2", 2),
                            ImageChannel.getInstance("c3", 3),
                            ImageChannel.getInstance("c4", 4),
                            ImageChannel.getInstance("c5", 5)
                    ))
                    .name("name")
                    .levelsFromDownsamples(1, 2)
                    .build();
        }

        @Override
        public BufferedImage readRegion(RegionRequest request) {
            DataBuffer dataBuffer = createDataBuffer(request);

            return new BufferedImage(
                    ColorModelFactory.createColorModel(getMetadata().getPixelType(), getMetadata().getChannels()),
                    WritableRaster.createWritableRaster(
                            new BandedSampleModel(dataBuffer.getDataType(), request.getWidth(), request.getHeight(), nChannels()),
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

            return new DataBufferDouble(array, request.getWidth() * request.getHeight() / 8);
        }

        private double[] getPixels(RegionRequest request, int channel) {
            double[] pixels = new double[request.getWidth() * request.getHeight()];

            for (int y=0; y<request.getHeight(); y++) {
                for (int x=0; x<request.getWidth(); x++) {
                    pixels[y*request.getWidth() + x] = getPixel(x + request.getX(), y + request.getY(), channel, request.getZ(), request.getT());
                }
            }

            return pixels;
        }

        private static double getPixel(int x, int y, int channel, int z, int t) {
            return z + t + channel + ((double) x / IMAGE_WIDTH + (double) y / IMAGE_HEIGHT) / 2;
        }
    }
}
