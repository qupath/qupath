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

public class TestColorTransforms {

    @Test
    void Check_Channel_Index_Extractor_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int channelIndex = sampleServer.nChannels() - 1;
        ColorTransforms.ColorTransform channelExtractor = ColorTransforms.createChannelExtractor(channelIndex);

        boolean supported = channelExtractor.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_Channel_Index_Extractor_Unsupported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int channelIndex = sampleServer.nChannels() + 1;
        ColorTransforms.ColorTransform channelExtractor = ColorTransforms.createChannelExtractor(channelIndex);

        boolean supported = channelExtractor.supportsImage(sampleServer);

        Assertions.assertFalse(supported);

        sampleServer.close();
    }

    @Test
    void Check_Channel_Index_Extractor() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int channelIndex = sampleServer.nChannels() - 1;
        ColorTransforms.ColorTransform channelExtractor = ColorTransforms.createChannelExtractor(channelIndex);
        BufferedImage image = sampleServer.readRegion(1, 0, 0, 10, 10);

        float[] pixels = channelExtractor.extractChannel(sampleServer, image, null);

        sampleServer.close();
    }

    private static class SampleImageServer extends AbstractImageServer<BufferedImage> {

        public SampleImageServer() {
            super(BufferedImage.class);
        }

        @Override
        protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
            return null;
        }

        @Override
        protected String createID() {
            return getClass().getName() + ": " + getURIs();
        }

        @Override
        public Collection<URI> getURIs() {
            return List.of(URI.create(""));
        }

        @Override
        public String getServerType() {
            return "Sample server";
        }

        @Override
        public ImageServerMetadata getOriginalMetadata() {
            return new ImageServerMetadata.Builder()
                    .width(50)
                    .height(25)
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
                    pixels[y*request.getWidth() + x] = channel + ((double) x + request.getX()) / getWidth();
                }
            }

            return pixels;
        }
    }
}
