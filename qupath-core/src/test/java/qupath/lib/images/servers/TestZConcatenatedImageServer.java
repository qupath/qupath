package qupath.lib.images.servers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.WritableRaster;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestZConcatenatedImageServer {

    @Test
    void Check_No_Image_Provided() {
        List<ImageServer<BufferedImage>> servers = List.of();

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Width() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .width(9234)
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Height() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .height(345)
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Pixel_Calibration() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .pixelSizeMicrons(4.65, 78.78)
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Rgb_Format() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .rgb(!sampleMetadata.isRGB())
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Pixel_Type() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .pixelType(PixelType.FLOAT64)
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Time_Points() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .sizeT(4865)
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Channels() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .channels(List.of(ImageChannel.getInstance("channel1", 5), ImageChannel.getInstance("channel2", 456)))
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Channel_Type() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .channelType(ImageServerMetadata.ChannelType.FEATURE)
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Classification_Labels() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .classificationLabels(Map.of(45, PathClass.getInstance("class")))
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Provided_Images_Not_Similar_Because_Of_Magnification() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(new ImageServerMetadata.Builder(sampleMetadata)
                        .magnification(4.67768)
                        .build()
                )
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_More_Than_One_Z_Slice() throws Exception {
        ImageServerMetadata sampleMetadata = new ImageServerMetadata.Builder(getSampleMetadata())
                .sizeZ(4)
                .build();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(sampleMetadata)
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> new ZConcatenatedImageServer(servers, null));

        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Number_Of_Z_Slices() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(sampleMetadata)
        );
        int expectedSizeZ = servers.size();

        ImageServer<BufferedImage> zConcatenatedServer = new ZConcatenatedImageServer(servers, null);

        Assertions.assertEquals(expectedSizeZ, zConcatenatedServer.getMetadata().getSizeZ());

        zConcatenatedServer.close();
        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Z_Spacing() throws Exception {
        ImageServerMetadata sampleMetadata = getSampleMetadata();
        List<ImageServer<BufferedImage>> servers = List.of(
                new SampleServer(sampleMetadata),
                new SampleServer(sampleMetadata)
        );
        double expectedZSpacingMicrons = 6.345;

        ImageServer<BufferedImage> zConcatenatedServer = new ZConcatenatedImageServer(servers, expectedZSpacingMicrons);

        Assertions.assertEquals(expectedZSpacingMicrons, zConcatenatedServer.getMetadata().getZSpacingMicrons());

        zConcatenatedServer.close();
        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_First_Slice_Of_Rgb_Server() throws Exception {
        List<ImageServer<BufferedImage>> servers = List.of(
                new RgbSampleServer(new int[][][][]{{
                        {{39, 148, 248}, {43, 45, 250}, {246, 61, 214}},
                        {{208, 252, 134}, {219, 117, 48}, {118, 136, 2}}
                }, {
                        {{185, 27, 91}, {110, 124, 76}, {211, 36, 38}},
                        {{38, 60, 71}, {98, 236, 63}, {11, 106, 244}}
                }}),
                new RgbSampleServer(new int[][][][]{{
                        {{1, 87, 79}, {34, 141, 168}, {233, 195, 54}},
                        {{70, 56, 116}, {44, 207, 226}, {226, 145, 24}},
                }, {
                        {{95, 212, 104}, {10, 7, 213}, {35, 238, 213}},
                        {{225, 14, 173}, {63, 160, 0}, {196, 70, 228}}
                }})
        );
        int z = 0;
        int t = 1;
        int[][][] expectedPixels = new int[][][]{
                {{185, 27, 91}, {110, 124, 76}, {211, 36, 38}},
                {{38, 60, 71}, {98, 236, 63}, {11, 106, 244}}
        };
        ImageServer<BufferedImage> zConcatenatedServer = new ZConcatenatedImageServer(servers, null);
        BufferedImage expectedImage = createImageFromPixels(
                expectedPixels,
                servers.getFirst().getMetadata()
        );

        BufferedImage image = zConcatenatedServer.readRegion(RegionRequest.createInstance(zConcatenatedServer.getPath(), 1, 0, 0, 3, 2, z, t));

        assertBufferedImagesEqual(expectedImage, image);

        zConcatenatedServer.close();
        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Second_Slice_Of_Rgb_Server() throws Exception {
        List<ImageServer<BufferedImage>> servers = List.of(
                new RgbSampleServer(new int[][][][]{{
                        {{39, 148, 248}, {43, 45, 250}, {246, 61, 214}},
                        {{208, 252, 134}, {219, 117, 48}, {118, 136, 2}}
                }, {
                        {{185, 27, 91}, {110, 124, 76}, {211, 36, 38}},
                        {{38, 60, 71}, {98, 236, 63}, {11, 106, 244}}
                }}),
                new RgbSampleServer(new int[][][][]{{
                        {{1, 87, 79}, {34, 141, 168}, {233, 195, 54}},
                        {{70, 56, 116}, {44, 207, 226}, {226, 145, 24}},
                }, {
                        {{95, 212, 104}, {10, 7, 213}, {35, 238, 213}},
                        {{225, 14, 173}, {63, 160, 0}, {196, 70, 228}}
                }})
        );
        int z = 1;
        int t = 1;
        int[][][] expectedPixels = new int[][][]{
                {{95, 212, 104}, {10, 7, 213}, {35, 238, 213}},
                {{225, 14, 173}, {63, 160, 0}, {196, 70, 228}}
        };
        ImageServer<BufferedImage> zConcatenatedServer = new ZConcatenatedImageServer(servers, null);
        BufferedImage expectedImage = createImageFromPixels(
                expectedPixels,
                servers.getFirst().getMetadata()
        );

        BufferedImage image = zConcatenatedServer.readRegion(RegionRequest.createInstance(zConcatenatedServer.getPath(), 1, 0, 0, 3, 2, z, t));

        assertBufferedImagesEqual(expectedImage, image);

        zConcatenatedServer.close();
        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_First_Slice_Of_Double_Server() throws Exception {
        List<ImageServer<BufferedImage>> servers = List.of(
                new DoubleSampleServer(new double[][][]{{
                        {11.48, 10.56, 7.65},
                        {15.66, 12.45, 7.99}
                }, {
                        {5.74, 5.28, 3.825},
                        {7.83, 6.225, 3.995}
                }}),
                new DoubleSampleServer(new double[][][]{{
                        {3.14, 7.85, 1.23},
                        {9.99, 0.56, 4.78},
                }, {
                        {8.34, 2.71, 6.42},
                        {5.67, 11.89, 3.21}
                }})
        );
        int z = 0;
        int t = 1;
        double[][] expectedPixels = new double[][]{
                {5.74, 5.28, 3.825},
                {7.83, 6.225, 3.995}
        };
        ImageServer<BufferedImage> zConcatenatedServer = new ZConcatenatedImageServer(servers, null);
        BufferedImage expectedImage = createImageFromPixels(
                expectedPixels,
                servers.getFirst().getMetadata()
        );

        BufferedImage image = zConcatenatedServer.readRegion(RegionRequest.createInstance(zConcatenatedServer.getPath(), 1, 0, 0, 3, 2, z, t));

        assertBufferedImagesEqual(expectedImage, image);

        zConcatenatedServer.close();
        for (var server: servers) {
            server.close();
        }
    }

    @Test
    void Check_Second_Slice_Of_Double_Server() throws Exception {
        List<ImageServer<BufferedImage>> servers = List.of(
                new DoubleSampleServer(new double[][][]{{
                        {11.48, 10.56, 7.65},
                        {15.66, 12.45, 7.99}
                }, {
                        {5.74, 5.28, 3.825},
                        {7.83, 6.225, 3.995}
                }}),
                new DoubleSampleServer(new double[][][]{{
                        {3.14, 7.85, 1.23},
                        {9.99, 0.56, 4.78},
                }, {
                        {8.34, 2.71, 6.42},
                        {5.67, 11.89, 3.21}
                }})
        );
        int z = 1;
        int t = 1;
        double[][] expectedPixels = new double[][]{
                {8.34, 2.71, 6.42},
                {5.67, 11.89, 3.21}
        };
        ImageServer<BufferedImage> zConcatenatedServer = new ZConcatenatedImageServer(servers, null);
        BufferedImage expectedImage = createImageFromPixels(
                expectedPixels,
                servers.getFirst().getMetadata()
        );

        BufferedImage image = zConcatenatedServer.readRegion(RegionRequest.createInstance(zConcatenatedServer.getPath(), 1, 0, 0, 3, 2, z, t));

        assertBufferedImagesEqual(expectedImage, image);

        zConcatenatedServer.close();
        for (var server: servers) {
            server.close();
        }
    }

    private static class SampleServer extends AbstractTileableImageServer {

        private final ImageServerMetadata metadata;

        public SampleServer(ImageServerMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        protected BufferedImage readTile(TileRequest tileRequest) {
            return null;
        }

        @Override
        protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
            return null;
        }

        @Override
        protected String createID() {
            return SampleServer.class.getName();
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
    }

    private static class RgbSampleServer extends AbstractTileableImageServer {

        private final int[][][][] pixels;

        /**
         * @param pixels in that order: t, y, x, c
         */
        public RgbSampleServer(int[][][][] pixels) {
            this.pixels = pixels;
        }

        @Override
        protected BufferedImage readTile(TileRequest tileRequest) {
            return createImageFromPixels(pixels[tileRequest.getT()], getMetadata());
        }

        @Override
        protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
            return null;
        }

        @Override
        protected String createID() {
            return RgbSampleServer.class.getName();
        }

        @Override
        public Collection<URI> getURIs() {
            return List.of();
        }

        @Override
        public String getServerType() {
            return "RGB sample server";
        }

        @Override
        public ImageServerMetadata getOriginalMetadata() {
            return new ImageServerMetadata.Builder()
                    .width(pixels[0][0].length)
                    .height(pixels[0].length)
                    .sizeT(pixels.length)
                    .channels(ImageChannel.getDefaultRGBChannels())
                    .pixelType(PixelType.UINT8)
                    .rgb(true)
                    .build();
        }
    }

    private static class DoubleSampleServer extends AbstractTileableImageServer {

        private final double[][][] pixels;

        /**
         * @param pixels in that order: t, y, x
         */
        public DoubleSampleServer(double[][][] pixels) {
            this.pixels = pixels;
        }

        @Override
        protected BufferedImage readTile(TileRequest tileRequest) {
            return createImageFromPixels(pixels[tileRequest.getT()], getMetadata());
        }

        @Override
        protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
            return null;
        }

        @Override
        protected String createID() {
            return DoubleSampleServer.class.getName();
        }

        @Override
        public Collection<URI> getURIs() {
            return List.of();
        }

        @Override
        public String getServerType() {
            return "Double sample server";
        }

        @Override
        public ImageServerMetadata getOriginalMetadata() {
            return new ImageServerMetadata.Builder()
                    .width(pixels[0][0].length)
                    .height(pixels[0].length)
                    .sizeT(pixels.length)
                    .channels(List.of(ImageChannel.getInstance("Channel", 1)))
                    .pixelType(PixelType.FLOAT64)
                    .build();
        }
    }

    private static ImageServerMetadata getSampleMetadata() {
        return new ImageServerMetadata.Builder()
                .width(10)
                .height(15)
                .pixelSizeMicrons(2.4, 9.7)
                .zSpacingMicrons(9.65)
                .timepoints(TimeUnit.SECONDS, 2.34, 5.55)
                .rgb(false)
                .pixelType(PixelType.UINT8)
                .sizeT(2)
                .channels(List.of(ImageChannel.getInstance("some channel", 2)))
                .channelType(ImageServerMetadata.ChannelType.DENSITY)
                .classificationLabels(Map.of(1, PathClass.getInstance("some class")))
                .magnification(4.67)
                .build();
    }

    private static BufferedImage createImageFromPixels(Object pixels, ImageServerMetadata metadata) {
        if (metadata.isRGB()) {
            int[][][] rgbPixels = (int[][][]) pixels;

            int[] rgb = new int[metadata.getWidth() * metadata.getHeight()];
            for (int y=0; y<metadata.getHeight(); y++) {
                for (int x=0; x<metadata.getWidth(); x++) {
                    rgb[x + y*metadata.getWidth()] = (255 << 24) |
                            (rgbPixels[y][x][0] & 0xFF) << 16 |
                            (rgbPixels[y][x][1] & 0xFF) << 8 |
                            (rgbPixels[y][x][2] & 0xFF);
                }
            }
            BufferedImage image = new BufferedImage(
                    metadata.getWidth(),
                    metadata.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
            image.setRGB(0, 0, metadata.getWidth(), metadata.getHeight(), rgb, 0, metadata.getWidth());
            return image;
        } else {
            DataBuffer dataBuffer = switch (pixels) {
                case double[][] doublePixels -> {
                    double[][] array = new double[1][metadata.getWidth() * metadata.getHeight()];

                    for (int y = 0; y < metadata.getHeight(); y++) {
                        System.arraycopy(doublePixels[y], 0, array[0], y * metadata.getWidth(), metadata.getWidth());
                    }
                    yield new DataBufferDouble(array, metadata.getWidth() * metadata.getHeight() / 8);
                }
                case int[][] intPixels -> {
                    int[][] array = new int[1][metadata.getWidth() * metadata.getHeight()];

                    for (int y = 0; y < metadata.getHeight(); y++) {
                        System.arraycopy(intPixels[y], 0, array[0], y * metadata.getWidth(), metadata.getWidth());
                    }
                    yield new DataBufferInt(array, metadata.getWidth() * metadata.getHeight() / 4);
                }
                case short[][] shortPixels -> {
                    short[][] array = new short[1][metadata.getWidth() * metadata.getHeight()];

                    for (int y = 0; y < metadata.getHeight(); y++) {
                        System.arraycopy(shortPixels[y], 0, array[0], y * metadata.getWidth(), metadata.getWidth());
                    }
                    yield new DataBufferShort(array, metadata.getWidth() * metadata.getHeight() / 2);
                }
                case null, default -> null;
            };

            if (dataBuffer == null) {
                throw new IllegalArgumentException(String.format("Unexpected pixel type: %s", pixels));
            }
            return new BufferedImage(
                    ColorModelFactory.createColorModel(metadata.getPixelType(), metadata.getChannels()),
                    WritableRaster.createWritableRaster(
                            new BandedSampleModel(dataBuffer.getDataType(), metadata.getWidth(), metadata.getHeight(), metadata.getSizeC()),
                            dataBuffer,
                            null
                    ),
                    false,
                    null
            );
        }
    }

    private static void assertBufferedImagesEqual(BufferedImage expectedImage, BufferedImage actualImage) {
        if (expectedImage == null || actualImage == null) {
            Assertions.assertEquals(expectedImage, actualImage);
            return;
        }

        Assertions.assertEquals(expectedImage.getWidth(), actualImage.getWidth());
        Assertions.assertEquals(expectedImage.getHeight(), actualImage.getHeight());

        double[] expectedPixels = new double[expectedImage.getSampleModel().getNumBands()];
        double[] actualPixels = new double[actualImage.getSampleModel().getNumBands()];
        for (int x = 0; x < expectedImage.getWidth(); x++) {
            for (int y = 0; y < expectedImage.getHeight(); y++) {
                Assertions.assertArrayEquals(
                        expectedImage.getRaster().getPixel(x, y, expectedPixels),
                        actualImage.getRaster().getPixel(x, y, actualPixels),
                        0.000000000001
                );
            }
        }
    }
}
