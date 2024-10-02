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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestSlicedImageServer {

    @Test
    void Check_Number_Of_Z_Slices() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStart = 3;
        int zEnd = 8;
        int zStep = 2;
        int expectedNumberOfZSlices = 3;

        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, zStart, zEnd, zStep, 0, 1, 1);

        Assertions.assertEquals(expectedNumberOfZSlices, slicedServer.nZSlices());

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Z_Slices_When_Out_Of_Bound() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStart = -1;
        int zEnd = sampleServer.nZSlices() + 10;
        int expectedNumberOfZSlices = sampleServer.nZSlices();

        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, zStart, zEnd, 1, 0, 1, 1);

        Assertions.assertEquals(expectedNumberOfZSlices, slicedServer.nZSlices());

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Z_Slices_When_Min_Greater_Than_Max() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStart = 5;
        int zEnd = zStart - 2;

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, zStart, zEnd, 1,0, 1, 1);
            slicedServer.close();
        });

        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Z_Slices_When_Step_Invalid() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStep = 0;

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 1, zStep,0, 1, 1);
            slicedServer.close();
        });

        sampleServer.close();
    }

    @Test
    void Check_Z_Spacing_With_Step() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStep = 4;
        double expectedZSpacing = sampleServer.getMetadata().getPixelCalibration().getZSpacingMicrons() * zStep;

        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 1, zStep,0, 1, 1);

        Assertions.assertEquals(expectedZSpacing, slicedServer.getMetadata().getZSpacingMicrons());

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Correct_Slice_Read() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStart = 3;
        int zEnd = 8;
        int zStep = 2;
        int zToRead = 1;
        BufferedImage expectedImage = sampleServer.readRegion(RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                5,
                0
        ));

        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, zStart, zEnd, zStep, 0, 1, 1);

        assertDoubleBufferedImagesEqual(
                expectedImage,
                slicedServer.readRegion(RegionRequest.createInstance(
                        slicedServer.getPath(),
                        1,
                        0,
                        0,
                        slicedServer.getWidth(),
                        slicedServer.getHeight(),
                        zToRead,
                        0
                ))
        );

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Timepoints() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int tStart = 1;
        int tEnd = 6;
        int tStep = 2;
        int expectedNumberOfTimepoints = 3;

        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 1, 1, tStart, tEnd, tStep);

        Assertions.assertEquals(expectedNumberOfTimepoints, slicedServer.nTimepoints());

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Timepoints_When_Out_Of_Bound() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int tStart = -1;
        int tEnd = sampleServer.nTimepoints() + 10;
        int expectedNumberOfTimepoints = sampleServer.nTimepoints();
        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 1, 1, tStart, tEnd, 1);

        int numberOfTimepoints = slicedServer.nTimepoints();

        Assertions.assertEquals(expectedNumberOfTimepoints, numberOfTimepoints);

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Timepoints_When_Min_Greater_Than_Max() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int tStart = 5;
        int tEnd = tStart - 2;

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 1, 1, tStart, tEnd, 1);
            slicedServer.close();
        });

        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Timepoints_When_Step_Invalid() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int tStep = 0;

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 1, 1,0, 0, tStep);
            slicedServer.close();
        });

        sampleServer.close();
    }

    @Test
    void Check_Timepoints_With_Step() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int tStart = 2;
        int tEnd = 7;
        int tStep = 4;
        double[] expectedTimepoints = new double[] {
                sampleServer.getMetadata().getTimepoint(tStart),
                sampleServer.getMetadata().getTimepoint(tStart + tStep)
        };

        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 1, 1, tStart, tEnd, tStep);

        Assertions.assertArrayEquals(
                expectedTimepoints,
                IntStream.range(0, slicedServer.getMetadata().getPixelCalibration().nTimepoints())
                        .mapToDouble(t -> slicedServer.getMetadata().getTimepoint(t))
                        .toArray()
        );

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Correct_Timepoint_Read() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int tStart = 1;
        int tEnd = 6;
        int tStep = 2;
        int tToRead = 1;
        BufferedImage expectedImage = sampleServer.readRegion(RegionRequest.createInstance(
                sampleServer.getPath(),
                1,
                0,
                0,
                sampleServer.getWidth(),
                sampleServer.getHeight(),
                0,
                3
        ));

        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 1, 1, tStart, tEnd, tStep);

        assertDoubleBufferedImagesEqual(
                expectedImage,
                slicedServer.readRegion(RegionRequest.createInstance(
                        slicedServer.getPath(),
                        1,
                        0,
                        0,
                        slicedServer.getWidth(),
                        slicedServer.getHeight(),
                        0,
                        tToRead
                ))
        );

        slicedServer.close();
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
            return z + t + channel + ((double) x / SampleImageServer.IMAGE_WIDTH + (double) y / SampleImageServer.IMAGE_HEIGHT) / 2;
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
