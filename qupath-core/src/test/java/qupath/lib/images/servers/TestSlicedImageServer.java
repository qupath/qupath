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

public class TestSlicedImageServer {

    @Test
    void Check_Number_Of_Z_Slices() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStart = 1;
        int zEnd = 3;
        int expectedNumberOfZSlices = zEnd - zStart + 1;
        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, zStart, zEnd, 0, 0);

        int numberOfZSlices = slicedServer.nZSlices();

        Assertions.assertEquals(expectedNumberOfZSlices, numberOfZSlices);

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Z_Slices_When_Out_Of_Bound() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStart = -1;
        int zEnd = sampleServer.nZSlices() + 10;
        int expectedNumberOfZSlices = sampleServer.nZSlices();
        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, zStart, zEnd, 0, 0);

        int numberOfZSlices = slicedServer.nZSlices();

        Assertions.assertEquals(expectedNumberOfZSlices, numberOfZSlices);

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Z_Slices_When_Min_Greater_Than_Max() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStart = 5;
        int zEnd = zStart - 2;

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, zStart, zEnd, 0, 0);
            slicedServer.close();
        });

        sampleServer.close();
    }

    @Test
    void Check_Correct_Slice_Read() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int zStart = 1;
        int zEnd = 3;
        int zToRead = 1;
        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, zStart, zEnd, 0, 0);
        BufferedImage expectedImage = sampleServer.readRegion(RegionRequest.createInstance(
                slicedServer.getPath(),
                1,
                0,
                0,
                slicedServer.getWidth(),
                slicedServer.getHeight(),
                zToRead + zStart,
                0
        ));

        BufferedImage image = slicedServer.readRegion(RegionRequest.createInstance(
                slicedServer.getPath(),
                1,
                0,
                0,
                slicedServer.getWidth(),
                slicedServer.getHeight(),
                zToRead,
                0
        ));

        Assertions.assertTrue(bufferedImagesEqual(expectedImage, image));

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Timepoints() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int tStart = 1;
        int tEnd = 3;
        int expectedNumberOfTimepoints = tEnd - tStart + 1;
        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 0, tStart, tEnd);

        int numberOfTimepoints = slicedServer.nTimepoints();

        Assertions.assertEquals(expectedNumberOfTimepoints, numberOfTimepoints);

        slicedServer.close();
        sampleServer.close();
    }

    @Test
    void Check_Number_Of_Timepoints_When_Out_Of_Bound() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int tStart = -1;
        int tEnd = sampleServer.nTimepoints() + 10;
        int expectedNumberOfTimepoints = sampleServer.nTimepoints();
        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 0, tStart, tEnd);

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
            ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 0, tStart, tEnd);
            slicedServer.close();
        });

        sampleServer.close();
    }

    @Test
    void Check_Correct_Timepoint_Read() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int tStart = 1;
        int tEnd = 3;
        int tToRead = 1;
        ImageServer<BufferedImage> slicedServer = new SlicedImageServer(sampleServer, 0, 0, tStart, tEnd);
        BufferedImage expectedImage = sampleServer.readRegion(RegionRequest.createInstance(
                slicedServer.getPath(),
                1,
                0,
                0,
                slicedServer.getWidth(),
                slicedServer.getHeight(),
                0,
                tToRead + tStart
        ));

        BufferedImage image = slicedServer.readRegion(RegionRequest.createInstance(
                slicedServer.getPath(),
                1,
                0,
                0,
                slicedServer.getWidth(),
                slicedServer.getHeight(),
                0,
                tToRead
        ));

        Assertions.assertTrue(bufferedImagesEqual(expectedImage, image));

        slicedServer.close();
        sampleServer.close();
    }

    private static class SampleImageServer extends AbstractImageServer<BufferedImage> {

        private static final int IMAGE_WIDTH = 50;
        private static final int IMAGE_HEIGHT = 25;
        private static final int NUMBER_OF_Z_SLICES = 10;
        private static final int NUMBER_OF_TIMEPOINTS = 5;

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
            return new ImageServerMetadata.Builder()
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
                    .name("name")
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
            return z + t + channel + ((double) x / SampleImageServer.IMAGE_WIDTH + (double) y / SampleImageServer.IMAGE_HEIGHT) / 2;
        }
    }

    private boolean bufferedImagesEqual(BufferedImage expectedImage, BufferedImage actualImage) {
        if (expectedImage.getWidth() == actualImage.getWidth() && expectedImage.getHeight() == actualImage.getHeight()) {
            for (int x = 0; x < expectedImage.getWidth(); x++) {
                for (int y = 0; y < expectedImage.getHeight(); y++) {
                    if (expectedImage.getRGB(x, y) != actualImage.getRGB(x, y)) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }
}
