package qupath.lib.images.servers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.color.ColorDeconvolutionStains;
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
import java.util.Map;
import java.util.stream.Stream;

public class TestColorTransforms {

    @Test
    void Check_Channel_Index_Extractor_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int channelIndex = sampleServer.nChannels() - 1;
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createChannelExtractor(channelIndex);

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_Channel_Index_Extractor_Not_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int channelIndex = sampleServer.nChannels() + 1;
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createChannelExtractor(channelIndex);

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertFalse(supported);

        sampleServer.close();
    }

    @Test
    void Check_Channel_Index_Extractor_Pixels() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int channelIndex = sampleServer.nChannels() - 1;
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createChannelExtractor(channelIndex);
        BufferedImage image = sampleServer.readRegion(1, 0, 0, 10, 10);
        float[] expectedPixels = new float[image.getWidth() * image.getHeight()];
        for (int y=0; y<image.getHeight(); y++) {
            for (int x=0; x<image.getWidth(); x++) {
                expectedPixels[y*image.getWidth() + x] = (float) SampleImageServer.getPixel(x, y, channelIndex);
            }
        }

        float[] pixels = colorTransform.extractChannel(sampleServer, image, null);

        Assertions.assertArrayEquals(expectedPixels, pixels);

        sampleServer.close();
    }

    @Test
    void Check_Channel_Index_Extractor_To_And_From_JSON() {
        ColorTransforms.ColorTransform expectedColorTransform = ColorTransforms.createChannelExtractor(2);
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter())
                .create();

        ColorTransforms.ColorTransform colorTransform = gson.fromJson(
                gson.toJson(expectedColorTransform),
                ColorTransforms.ColorTransform.class
        );

        Assertions.assertEquals(expectedColorTransform, colorTransform);
    }

    @Test
    void Check_Channel_Name_Extractor_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int channelIndex = sampleServer.nChannels() - 1;
        String channelName = sampleServer.getChannel(channelIndex).getName();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createChannelExtractor(channelName);

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_Channel_Name_Extractor_Not_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        String channelName = "channel not present in sample image server";
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createChannelExtractor(channelName);

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertFalse(supported);

        sampleServer.close();
    }

    @Test
    void Check_Channel_Name_Extractor_Pixels() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        int channelIndex = sampleServer.nChannels() - 2;
        String channelName = sampleServer.getChannel(channelIndex).getName();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createChannelExtractor(channelName);
        BufferedImage image = sampleServer.readRegion(1, 0, 0, 10, 10);
        float[] expectedPixels = new float[image.getWidth() * image.getHeight()];
        for (int y=0; y<image.getHeight(); y++) {
            for (int x=0; x<image.getWidth(); x++) {
                expectedPixels[y*image.getWidth() + x] = (float) SampleImageServer.getPixel(x, y, channelIndex);
            }
        }

        float[] pixels = colorTransform.extractChannel(sampleServer, image, null);

        Assertions.assertArrayEquals(expectedPixels, pixels);

        sampleServer.close();
    }

    @Test
    void Check_Channel_Name_Extractor_To_And_From_JSON() {
        ColorTransforms.ColorTransform expectedColorTransform = ColorTransforms.createChannelExtractor("some channel");
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter())
                .create();

        ColorTransforms.ColorTransform colorTransform = gson.fromJson(
                gson.toJson(expectedColorTransform),
                ColorTransforms.ColorTransform.class
        );

        Assertions.assertEquals(expectedColorTransform, colorTransform);
    }

    @Test
    void Check_Map_Linear_Combination_Channel_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createLinearCombinationChannelTransform(Map.of(
                sampleServer.getChannel(0).getName(), 0.5,
                sampleServer.getChannel(1).getName(), 0.1
        ));

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_Map_Linear_Combination_Channel_Not_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createLinearCombinationChannelTransform(Map.of(
                "channel not present in sample image server", 0.5,
                sampleServer.getChannel(1).getName(), 0.1
        ));

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertFalse(supported);

        sampleServer.close();
    }

    @Test
    void Check_Map_Linear_Combination_Channel_Pixels() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        Map<String, Double> coefficients = Map.of(
                sampleServer.getChannel(0).getName(), 0.4,
                sampleServer.getChannel(2).getName(), 0.6
        );
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createLinearCombinationChannelTransform(coefficients);
        BufferedImage image = sampleServer.readRegion(1, 0, 0, 10, 10);
        float[] expectedPixels = new float[image.getWidth() * image.getHeight()];
        for (int y=0; y<image.getHeight(); y++) {
            for (int x=0; x<image.getWidth(); x++) {
                for (Map.Entry<String, Double> entry: coefficients.entrySet()) {
                    int channelIndex = sampleServer.getMetadata().getChannels().stream()
                            .map(ImageChannel::getName)
                            .toList()
                            .indexOf(entry.getKey());

                    expectedPixels[y*image.getWidth() + x] += entry.getValue() * SampleImageServer.getPixel(x, y, channelIndex);
                }
            }
        }

        float[] pixels = colorTransform.extractChannel(sampleServer, image, null);

        Assertions.assertArrayEquals(expectedPixels, pixels, 0.001f);   // delta for rounding errors

        sampleServer.close();
    }

    @Test
    void Check_Map_Linear_Combination_Channel_To_And_From_JSON() {
        ColorTransforms.ColorTransform expectedColorTransform = ColorTransforms.createLinearCombinationChannelTransform(Map.of(
                "some channel", 0.5,
                "some other channel", 0.1
        ));
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter())
                .create();

        ColorTransforms.ColorTransform colorTransform = gson.fromJson(
                gson.toJson(expectedColorTransform),
                ColorTransforms.ColorTransform.class
        );

        Assertions.assertEquals(expectedColorTransform, colorTransform);
    }

    @Test
    void Check_List_Linear_Combination_Channel_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createLinearCombinationChannelTransform(
                sampleServer.getMetadata().getChannels().stream().map(c -> 1).toList()
        );

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_List_Linear_Combination_Channel_Not_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createLinearCombinationChannelTransform(
                Stream.concat(
                        sampleServer.getMetadata().getChannels().stream().map(c -> 1),
                        Stream.of(3)
                ).toList()
        );

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertFalse(supported);

        sampleServer.close();
    }

    @Test
    void Check_List_Linear_Combination_Channel_Pixels() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        List<Float> coefficients = List.of(0.2f, 0.5f, 2f);
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createLinearCombinationChannelTransform(coefficients);
        BufferedImage image = sampleServer.readRegion(1, 0, 0, 10, 10);
        float[] expectedPixels = new float[image.getWidth() * image.getHeight()];
        for (int y=0; y<image.getHeight(); y++) {
            for (int x=0; x<image.getWidth(); x++) {
                for (int i=0; i<coefficients.size(); i++) {
                    expectedPixels[y*image.getWidth() + x] += coefficients.get(i) * SampleImageServer.getPixel(x, y, i);
                }
            }
        }

        float[] pixels = colorTransform.extractChannel(sampleServer, image, null);

        Assertions.assertArrayEquals(expectedPixels, pixels, 0.001f);   // delta for rounding errors

        sampleServer.close();
    }

    @Test
    void Check_List_Linear_Combination_Channel_To_And_From_JSON() {
        ColorTransforms.ColorTransform expectedColorTransform = ColorTransforms.createLinearCombinationChannelTransform(List.of(
                5, 1
        ));
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter())
                .create();

        ColorTransforms.ColorTransform colorTransform = gson.fromJson(
                gson.toJson(expectedColorTransform),
                ColorTransforms.ColorTransform.class
        );

        Assertions.assertEquals(expectedColorTransform, colorTransform);
    }

    @Test
    void Check_Array_Linear_Combination_Channel_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createLinearCombinationChannelTransform(
                new double[sampleServer.nChannels()]
        );

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_Array_Linear_Combination_Channel_Not_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createLinearCombinationChannelTransform(
                new double[sampleServer.nChannels() + 1]
        );

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertFalse(supported);

        sampleServer.close();
    }

    @Test
    void Check_Array_Linear_Combination_Channel_Pixels() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        double[] coefficients = new double[] {0.2, 0.5, 2};
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createLinearCombinationChannelTransform(coefficients);
        BufferedImage image = sampleServer.readRegion(1, 0, 0, 10, 10);
        float[] expectedPixels = new float[image.getWidth() * image.getHeight()];
        for (int y=0; y<image.getHeight(); y++) {
            for (int x=0; x<image.getWidth(); x++) {
                for (int i=0; i<coefficients.length; i++) {
                    expectedPixels[y*image.getWidth() + x] += coefficients[i] * SampleImageServer.getPixel(x, y, i);
                }
            }
        }

        float[] pixels = colorTransform.extractChannel(sampleServer, image, null);

        Assertions.assertArrayEquals(expectedPixels, pixels, 0.001f);   // delta for rounding errors

        sampleServer.close();
    }

    @Test
    void Check_Array_Linear_Combination_Channel_To_And_From_JSON() {
        ColorTransforms.ColorTransform expectedColorTransform = ColorTransforms.createLinearCombinationChannelTransform(0.5, 0.1);
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter())
                .create();

        ColorTransforms.ColorTransform colorTransform = gson.fromJson(
                gson.toJson(expectedColorTransform),
                ColorTransforms.ColorTransform.class
        );

        Assertions.assertEquals(expectedColorTransform, colorTransform);
    }

    @Test
    void Check_Mean_Channel_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createMeanChannelTransform();

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_Mean_Channel_Pixels() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createMeanChannelTransform();
        BufferedImage image = sampleServer.readRegion(1, 0, 0, 10, 10);
        float[] expectedPixels = new float[image.getWidth() * image.getHeight()];
        for (int y=0; y<image.getHeight(); y++) {
            for (int x=0; x<image.getWidth(); x++) {
                for (int c=0; c<sampleServer.nChannels(); c++) {
                    expectedPixels[y*image.getWidth() + x] += SampleImageServer.getPixel(x, y, c);
                }
                expectedPixels[y*image.getWidth() + x] /= sampleServer.nChannels();
            }
        }

        float[] pixels = colorTransform.extractChannel(sampleServer, image, null);

        Assertions.assertArrayEquals(expectedPixels, pixels, 0.001f);   // delta for rounding errors

        sampleServer.close();
    }

    @Test
    void Check_Mean_Channel_To_And_From_JSON() {
        ColorTransforms.ColorTransform expectedColorTransform = ColorTransforms.createMeanChannelTransform();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter())
                .create();

        ColorTransforms.ColorTransform colorTransform = gson.fromJson(
                gson.toJson(expectedColorTransform),
                ColorTransforms.ColorTransform.class
        );

        Assertions.assertEquals(expectedColorTransform, colorTransform);
    }

    @Test
    void Check_Maximum_Channel_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createMaximumChannelTransform();

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_Maximum_Channel_Pixels() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createMaximumChannelTransform();
        BufferedImage image = sampleServer.readRegion(1, 0, 0, 10, 10);
        float[] expectedPixels = new float[image.getWidth() * image.getHeight()];
        for (int y=0; y<image.getHeight(); y++) {
            for (int x=0; x<image.getWidth(); x++) {
                expectedPixels[y*image.getWidth() + x] = Float.NEGATIVE_INFINITY;
                for (int c=0; c<sampleServer.nChannels(); c++) {
                    expectedPixels[y*image.getWidth() + x] = (float) Math.max(
                            expectedPixels[y*image.getWidth() + x],
                            SampleImageServer.getPixel(x, y, c)
                    );
                }
            }
        }

        float[] pixels = colorTransform.extractChannel(sampleServer, image, null);

        Assertions.assertArrayEquals(expectedPixels, pixels, 0.001f);   // delta for rounding errors

        sampleServer.close();
    }

    @Test
    void Check_Maximum_Channel_To_And_From_JSON() {
        ColorTransforms.ColorTransform expectedColorTransform = ColorTransforms.createMaximumChannelTransform();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter())
                .create();

        ColorTransforms.ColorTransform colorTransform = gson.fromJson(
                gson.toJson(expectedColorTransform),
                ColorTransforms.ColorTransform.class
        );

        Assertions.assertEquals(expectedColorTransform, colorTransform);
    }

    @Test
    void Check_Minimum_Channel_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createMinimumChannelTransform();

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_Minimum_Channel_Pixels() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createMinimumChannelTransform();
        BufferedImage image = sampleServer.readRegion(1, 0, 0, 10, 10);
        float[] expectedPixels = new float[image.getWidth() * image.getHeight()];
        for (int y=0; y<image.getHeight(); y++) {
            for (int x=0; x<image.getWidth(); x++) {
                expectedPixels[y*image.getWidth() + x] = Float.POSITIVE_INFINITY;
                for (int c=0; c<sampleServer.nChannels(); c++) {
                    expectedPixels[y*image.getWidth() + x] = (float) Math.min(
                            expectedPixels[y*image.getWidth() + x],
                            SampleImageServer.getPixel(x, y, c)
                    );
                }
            }
        }

        float[] pixels = colorTransform.extractChannel(sampleServer, image, null);

        Assertions.assertArrayEquals(expectedPixels, pixels, 0.001f);   // delta for rounding errors

        sampleServer.close();
    }

    @Test
    void Check_Minimum_Channel_To_And_From_JSON() {
        ColorTransforms.ColorTransform expectedColorTransform = ColorTransforms.createMinimumChannelTransform();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter())
                .create();

        ColorTransforms.ColorTransform colorTransform = gson.fromJson(
                gson.toJson(expectedColorTransform),
                ColorTransforms.ColorTransform.class
        );

        Assertions.assertEquals(expectedColorTransform, colorTransform);
    }

    @Test
    void Check_Color_Deconvolved_Channel_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleRGBImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createColorDeconvolvedChannel(
                new ColorDeconvolutionStains(),
                2
        );

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertTrue(supported);

        sampleServer.close();
    }

    @Test
    void Check_Color_Deconvolved_Channel_Not_Supported() throws Exception {
        ImageServer<BufferedImage> sampleServer = new SampleImageServer();
        ColorTransforms.ColorTransform colorTransform = ColorTransforms.createColorDeconvolvedChannel(
                new ColorDeconvolutionStains(),
                2
        );

        boolean supported = colorTransform.supportsImage(sampleServer);

        Assertions.assertFalse(supported);

        sampleServer.close();
    }

    @Test
    void Check_Color_Deconvolved_Channel_To_And_From_JSON() {
        ColorTransforms.ColorTransform expectedColorTransform = ColorTransforms.createColorDeconvolvedChannel(
                new ColorDeconvolutionStains(),
                2
        );
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter())
                .create();

        ColorTransforms.ColorTransform colorTransform = gson.fromJson(
                gson.toJson(expectedColorTransform),
                ColorTransforms.ColorTransform.class
        );

        Assertions.assertEquals(expectedColorTransform, colorTransform);
    }

    private static class SampleImageServer extends AbstractImageServer<BufferedImage> {

        private static final int IMAGE_WIDTH = 50;
        private static final int IMAGE_HEIGHT = 25;

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
                    .width(IMAGE_WIDTH)
                    .height(IMAGE_HEIGHT)
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
                    pixels[y*request.getWidth() + x] = getPixel(x + request.getX(), y + request.getY(), channel);
                }
            }

            return pixels;
        }

        private static double getPixel(int x, int y, int channel) {
            return channel + ((double) x / SampleImageServer.IMAGE_WIDTH + (double) y / SampleImageServer.IMAGE_HEIGHT) / 2;
        }
    }

    private static class SampleRGBImageServer extends AbstractImageServer<BufferedImage> {

        public SampleRGBImageServer() {
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
            return "Sample RGB server";
        }

        @Override
        public ImageServerMetadata getOriginalMetadata() {
            return new ImageServerMetadata.Builder()
                    .width(50)
                    .height(20)
                    .rgb(true)
                    .name("name")
                    .build();
        }
    }
}
