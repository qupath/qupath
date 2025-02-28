package qupath.lib.images.servers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.WritableRaster;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestZProjectionImageServer {

    private interface PixelGetter {

        double get(int x, int y, int c);
    }

    @Test
    void Check_Number_Of_Z_Slices() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int expectedNumberOfZSlices = 1;

        ImageServer<BufferedImage> zProjectedServer = new ZProjectionImageServer(sampleServer, zValues -> 0d);

        Assertions.assertEquals(expectedNumberOfZSlices, zProjectedServer.nZSlices());

        zProjectedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Zero_Projection() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        RegionRequest request = RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                0,
                2
        );
        BufferedImage expectedImage = getExpectedImage(
                sampleServer,
                (x, y, c) -> 0
        );

        ImageServer<BufferedImage> zProjectedServer = new ZProjectionImageServer(sampleServer, zValues -> 0d);

        assertDoubleBufferedImagesEqual(
                expectedImage,
                zProjectedServer.readRegion(request)
        );

        zProjectedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Constant_Projection() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        double constant = 3.56;
        RegionRequest request = RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                0,
                2
        );
        BufferedImage expectedImage = getExpectedImage(
                sampleServer,
                (x, y, c) -> constant
        );

        ImageServer<BufferedImage> zProjectedServer = new ZProjectionImageServer(sampleServer, zValues -> constant);

        assertDoubleBufferedImagesEqual(
                expectedImage,
                zProjectedServer.readRegion(request)
        );

        zProjectedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Average_Projection() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        RegionRequest request = RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                0,
                2
        );
        BufferedImage expectedImage = getExpectedImage(
                sampleServer,
                (x, y, c) -> {
                    double average = 0;
                    for (int z=0; z<sampleServer.nZSlices(); z++) {
                        average += SampleImageServer.getPixel(x, y, c, z, request.getT());
                    }
                    average /= sampleServer.nZSlices();

                    return average;
                }
        );

        ImageServer<BufferedImage> zProjectedServer = new ZProjectionImageServer(
                sampleServer,
                zValues -> Arrays.stream(zValues).average().orElse(0)
        );

        assertDoubleBufferedImagesEqual(
                expectedImage,
                zProjectedServer.readRegion(request)
        );

        zProjectedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Max_Projection() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        RegionRequest request = RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                0,
                2
        );
        BufferedImage expectedImage = getExpectedImage(
                sampleServer,
                (x, y, c) -> {
                    double max = Double.MIN_VALUE;

                    for (int z=0; z<sampleServer.nZSlices(); z++) {
                        double pixelValue = SampleImageServer.getPixel(x, y, c, z, request.getT());
                        if (pixelValue > max) {
                            max = pixelValue;
                        }
                    }

                    return max;
                }
        );

        ImageServer<BufferedImage> zProjectedServer = new ZProjectionImageServer(
                sampleServer,
                zValues -> Arrays.stream(zValues).max().orElse(0)
        );

        assertDoubleBufferedImagesEqual(
                expectedImage,
                zProjectedServer.readRegion(request)
        );

        zProjectedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Min_Projection() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        RegionRequest request = RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                0,
                2
        );
        BufferedImage expectedImage = getExpectedImage(
                sampleServer,
                (x, y, c) -> {
                    double min = Double.MAX_VALUE;

                    for (int z=0; z<sampleServer.nZSlices(); z++) {
                        double pixelValue = SampleImageServer.getPixel(x, y, c, z, request.getT());
                        if (pixelValue < min) {
                            min = pixelValue;
                        }
                    }

                    return min;
                }
        );

        ImageServer<BufferedImage> zProjectedServer = new ZProjectionImageServer(
                sampleServer,
                zValues -> Arrays.stream(zValues).min().orElse(0)
        );

        assertDoubleBufferedImagesEqual(
                expectedImage,
                zProjectedServer.readRegion(request)
        );

        zProjectedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Sum_Projection() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        RegionRequest request = RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                0,
                2
        );
        BufferedImage expectedImage = getExpectedImage(
                sampleServer,
                (x, y, c) -> {
                    double sum = 0;

                    for (int z=0; z<sampleServer.nZSlices(); z++) {
                        sum += SampleImageServer.getPixel(x, y, c, z, request.getT());
                    }

                    return sum;
                }
        );

        ImageServer<BufferedImage> zProjectedServer = new ZProjectionImageServer(
                sampleServer,
                zValues -> Arrays.stream(zValues).sum()
        );

        assertDoubleBufferedImagesEqual(
                expectedImage,
                zProjectedServer.readRegion(request)
        );

        zProjectedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Standard_Deviation_Projection() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        RegionRequest request = RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                0,
                2
        );
        BufferedImage expectedImage = getExpectedImage(
                sampleServer,
                (x, y, c) -> {
                    double average = 0;
                    for (int z=0; z<sampleServer.nZSlices(); z++) {
                        average += SampleImageServer.getPixel(x, y, c, z, request.getT());
                    }
                    average /= sampleServer.nZSlices();

                    double variance = 0;
                    for (int z=0; z<sampleServer.nZSlices(); z++) {
                        variance += Math.pow(SampleImageServer.getPixel(x, y, c, z, request.getT()) - average, 2);
                    }
                    variance *= 1d / sampleServer.nZSlices();

                    return Math.sqrt(variance);
                }
        );

        ImageServer<BufferedImage> zProjectedServer = new ZProjectionImageServer(
                sampleServer,
                zValues -> {
                    if (zValues.length == 0) {
                        return 0d;
                    }

                    double mean = Arrays.stream(zValues).average().orElse(0);
                    return Math.sqrt((1d / zValues.length) * Arrays.stream(zValues)
                            .map(z -> Math.pow(z - mean, 2))
                            .sum());
                }
        );

        assertDoubleBufferedImagesEqual(
                expectedImage,
                zProjectedServer.readRegion(request)
        );

        zProjectedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Median_Projection() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        RegionRequest request = RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                0,
                2
        );
        BufferedImage expectedImage = getExpectedImage(
                sampleServer,
                (x, y, c) -> {
                    double[] zValues = new double[sampleServer.nZSlices()];
                    for (int z=0; z<sampleServer.nZSlices(); z++) {
                        zValues[z] = SampleImageServer.getPixel(x, y, c, z, request.getT());
                    }

                    Arrays.sort(zValues);
                    if (zValues.length % 2 == 0) {
                        return (zValues[zValues.length / 2] + zValues[zValues.length / 2 - 1]) / 2;
                    } else {
                        return zValues[zValues.length / 2];
                    }
                }
        );

        ImageServer<BufferedImage> zProjectedServer = new ZProjectionImageServer(
                sampleServer,
                zValues -> {
                    if (zValues.length == 0) {
                        return 0d;
                    }

                    Arrays.sort(zValues);

                    if (zValues.length % 2 == 0) {
                        return (zValues[zValues.length / 2] + zValues[zValues.length / 2 - 1]) / 2;
                    } else {
                        return zValues[zValues.length / 2];
                    }
                }
        );

        assertDoubleBufferedImagesEqual(
                expectedImage,
                zProjectedServer.readRegion(request)
        );

        zProjectedServer.close();
        sampleServer.close();
    }

    private static class SampleImageServer extends AbstractImageServer<BufferedImage> {

        private static final int IMAGE_WIDTH = 50;
        private static final int IMAGE_HEIGHT = 25;
        private static final int NUMBER_OF_Z_SLICES = 10;
        private static final int NUMBER_OF_TIMEPOINTS = 8;
        private final ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(IMAGE_WIDTH)
                .height(IMAGE_HEIGHT)
                .sizeZ(NUMBER_OF_Z_SLICES)
                .sizeT(NUMBER_OF_TIMEPOINTS)
                .pixelType(PixelType.FLOAT64)
                .channels(List.of(
                        ImageChannel.getInstance("c1", 1),
                        ImageChannel.getInstance("c2", 2),
                        ImageChannel.getInstance("c3", 3),
                        ImageChannel.getInstance("c4", 4),
                        ImageChannel.getInstance("c5", 5)
                ))
                .zSpacingMicrons(0.45)
                .timepoints(
                        TimeUnit.MICROSECONDS,
                        IntStream.range(0, NUMBER_OF_TIMEPOINTS)
                                .mapToDouble( t -> Math.random())
                                .toArray()
                )
                .build();

        public SampleImageServer() {
            super(BufferedImage.class);
        }

        @Override
        protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
            return null;
        }

        @Override
        protected String createID() {
            return null;
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

        private static double[] getPixels(RegionRequest request, int channel) {
            double[] pixels = new double[request.getWidth() * request.getHeight()];

            for (int y=0; y<request.getHeight(); y++) {
                for (int x=0; x<request.getWidth(); x++) {
                    pixels[y*request.getWidth() + x] = getPixel(x + request.getX(), y + request.getY(), channel, request.getZ(), request.getT());
                }
            }

            return pixels;
        }

        private static double getPixel(int x, int y, int channel, int z, int t) {
            return z + t + channel + ((double) x / SampleImageServer.IMAGE_WIDTH + (double) y / SampleImageServer.IMAGE_HEIGHT) / 2;
        }
    }

    private static BufferedImage getExpectedImage(ImageServer<?> server, PixelGetter pixelGetter) {
        double[][] array = new double[server.nChannels()][];
        for (int c = 0; c < array.length; c++) {
            array[c] = new double[server.getWidth() * server.getHeight()];

            for (int y=0; y<server.getHeight(); y++) {
                for (int x=0; x<server.getWidth(); x++) {
                    array[c][y*server.getWidth() + x] = pixelGetter.get(x, y, c);
                }
            }
        }
        DataBuffer dataBuffer = new DataBufferDouble(array, server.getWidth() * server.getHeight() / 8);

        return new BufferedImage(
                ColorModelFactory.createColorModel(server.getMetadata().getPixelType(), server.getMetadata().getChannels()),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(dataBuffer.getDataType(), server.getWidth(), server.getHeight(), server.nChannels()),
                        dataBuffer,
                        null
                ),
                false,
                null
        );
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
                        (double[]) actualImage.getData().getDataElements(x, y, actualPixels),
                        0.0000001
                );
            }
        }
    }
}
