package qupath.lib.images.servers;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.lib.color.ColorModelFactory;
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

public class TestZProjectedImageServer {

    abstract static class GenericImage {

        protected static ImageServer<BufferedImage> sampleServer;

        @AfterAll
        static void closeSampleServer() throws Exception {
            sampleServer.close();
        }

        @Test
        void Check_Number_Of_Z_Slices() throws Exception {
            int expectedNumberOfZSlices = 1;

            ImageServer<BufferedImage> zProjectedServer = new ZProjectedImageServer(sampleServer, ZProjectedImageServer.Projection.MEAN);

            Assertions.assertEquals(expectedNumberOfZSlices, zProjectedServer.nZSlices());

            zProjectedServer.close();
        }

        @Test
        void Check_Pixel_Type_With_Mean() throws Exception {
            PixelType expectedPixelType = sampleServer.getMetadata().getPixelType();

            ImageServer<BufferedImage> zProjectedServer = new ZProjectedImageServer(sampleServer, ZProjectedImageServer.Projection.MEAN);

            Assertions.assertEquals(expectedPixelType, zProjectedServer.getMetadata().getPixelType());

            zProjectedServer.close();
        }

        @Test
        void Check_Pixel_Type_With_Sum() throws Exception {
            PixelType expectedPixelType = getPixelTypeWithSum();

            ImageServer<BufferedImage> zProjectedServer = new ZProjectedImageServer(sampleServer, ZProjectedImageServer.Projection.SUM);

            Assertions.assertEquals(expectedPixelType, zProjectedServer.getMetadata().getPixelType());

            zProjectedServer.close();
        }

        @Test
        void Check_Mean_Projection() throws Exception {
            BufferedImage expectedImage = createImageFromPixels(
                    getAveragePixels(),
                    sampleServer.getMetadata()
            );
            ImageServer<BufferedImage> zProjectedServer = new ZProjectedImageServer(
                    sampleServer,
                    ZProjectedImageServer.Projection.MEAN
            );

            BufferedImage image = zProjectedServer.readRegion(RegionRequest.createInstance(sampleServer));

            assertBufferedImagesEqual(expectedImage, image);

            zProjectedServer.close();
        }

        @Test
        void Check_Min_Projection() throws Exception {
            BufferedImage expectedImage = createImageFromPixels(
                    getMinPixels(),
                    sampleServer.getMetadata()
            );
            ImageServer<BufferedImage> zProjectedServer = new ZProjectedImageServer(
                    sampleServer,
                    ZProjectedImageServer.Projection.MIN
            );

            BufferedImage image = zProjectedServer.readRegion(RegionRequest.createInstance(sampleServer));

            assertBufferedImagesEqual(expectedImage, image);

            zProjectedServer.close();
        }

        @Test
        void Check_Max_Projection() throws Exception {
            BufferedImage expectedImage = createImageFromPixels(
                    getMaxPixels(),
                    sampleServer.getMetadata()
            );
            ImageServer<BufferedImage> zProjectedServer = new ZProjectedImageServer(
                    sampleServer,
                    ZProjectedImageServer.Projection.MAX
            );

            BufferedImage image = zProjectedServer.readRegion(RegionRequest.createInstance(sampleServer));

            assertBufferedImagesEqual(expectedImage, image);

            zProjectedServer.close();
        }

        @Test
        void Check_Sum_Projection() throws Exception {
            BufferedImage expectedImage = createImageFromPixels(
                    getSumPixels(),
                    sampleServer.getMetadata()
            );
            ImageServer<BufferedImage> zProjectedServer = new ZProjectedImageServer(
                    sampleServer,
                    ZProjectedImageServer.Projection.SUM
            );

            BufferedImage image = zProjectedServer.readRegion(RegionRequest.createInstance(sampleServer));

            assertBufferedImagesEqual(expectedImage, image);

            zProjectedServer.close();
        }

        @Test
        void Check_Standard_Deviation_Projection() throws Exception {
            BufferedImage expectedImage = createImageFromPixels(
                    getStandardDeviationPixels(),
                    sampleServer.getMetadata()
            );
            ImageServer<BufferedImage> zProjectedServer = new ZProjectedImageServer(
                    sampleServer,
                    ZProjectedImageServer.Projection.STANDARD_DEVIATION
            );

            BufferedImage image = zProjectedServer.readRegion(RegionRequest.createInstance(sampleServer));

            assertBufferedImagesEqual(expectedImage, image);

            zProjectedServer.close();
        }

        @Test
        void Check_Median_Projection() throws Exception {
            BufferedImage expectedImage = createImageFromPixels(
                    getMedianPixels(),
                    sampleServer.getMetadata()
            );
            ImageServer<BufferedImage> zProjectedServer = new ZProjectedImageServer(
                    sampleServer,
                    ZProjectedImageServer.Projection.MEDIAN
            );

            BufferedImage image = zProjectedServer.readRegion(RegionRequest.createInstance(sampleServer));

            assertBufferedImagesEqual(expectedImage, image);

            zProjectedServer.close();
        }

        protected abstract PixelType getPixelTypeWithSum();

        protected abstract Object getAveragePixels();

        protected abstract Object getMinPixels();

        protected abstract Object getMaxPixels();

        protected abstract Object getSumPixels();

        protected abstract Object getStandardDeviationPixels();

        protected abstract Object getMedianPixels();
    }

    @Nested
    class DoubleImage extends GenericImage {

        @BeforeAll
        static void createSampleServer() {
            sampleServer = new DoubleSampleServer();
        }

        @Override
        protected PixelType getPixelTypeWithSum() {
            return PixelType.FLOAT64;
        }

        @Override
        protected Object getAveragePixels() {
            return new double[][] {
                    {5.74, 5.28, 3.825},
                    {7.83, 6.225, 3.995}
            };
        }

        @Override
        protected Object getMinPixels() {
            return new double[][] {
                    {3.14, 2.71, 1.23},
                    {5.67, 0.56, 3.21}
            };
        }

        @Override
        protected Object getMaxPixels() {
            return new double[][] {
                    {8.34, 7.85, 6.42},
                    {9.99, 11.89, 4.78}
            };
        }

        @Override
        protected Object getSumPixels() {
            return new double[][] {
                    {11.48, 10.56, 7.65},
                    {15.66, 12.45, 7.99}
            };
        }

        @Override
        protected Object getStandardDeviationPixels() {
            return new double[][] {
                    {2.6, 2.57, 2.595},
                    {2.16, 5.665, 0.785}
            };
        }

        @Override
        protected Object getMedianPixels() {
            return new double[][] {
                    {5.74, 5.28, 3.825},
                    {7.83, 6.225, 3.995}
            };
        }

        private static class DoubleSampleServer extends AbstractTileableImageServer {

            private final double[][][] pixels = {{
                    {3.14, 7.85, 1.23},
                    {9.99, 0.56, 4.78},
            }, {
                    {8.34, 2.71, 6.42},
                    {5.67, 11.89, 3.21},
            }}; // z, y, x

            @Override
            protected BufferedImage readTile(TileRequest tileRequest) {
                return createImageFromPixels(pixels[tileRequest.getZ()], getMetadata());
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
                        .sizeZ(pixels.length)
                        .channels(List.of(ImageChannel.RED))
                        .pixelType(PixelType.FLOAT64)
                        .build();
            }
        }
    }

    @Nested
    class Int16Image extends GenericImage {

        @BeforeAll
        static void createSampleServer() {
            sampleServer = new Int16SampleServer();
        }

        @Override
        protected PixelType getPixelTypeWithSum() {
            return PixelType.FLOAT32;
        }

        @Override
        protected Object getAveragePixels() {
            return new int[][] {
                    {15496, -2477, 11641},
                    {-21810, 5881, 18041}
            };
        }

        @Override
        protected Object getMinPixels() {
            return new int[][] {
                    {10035, -28963, -1246},
                    {-29674, -4466, 3983}
            };
        }

        @Override
        protected Object getMaxPixels() {
            return new int[][] {
                    {20957, 24008, 24528},
                    {-13946, 16228, 32099}
            };
        }

        @Override
        protected Object getSumPixels() {
            return new int[][] {
                    {30992, -4955, 23282},
                    {-43620, 11762, 36082}
            };
        }

        @Override
        protected Object getStandardDeviationPixels() {
            return new int[][] {
                    {5461, 26486, 12887},
                    {7864, 10347, 14058}
            };
        }

        @Override
        protected Object getMedianPixels() {
            return new int[][] {
                    {15496, -2477, 11641},
                    {-21810, 5881, 18041}
            };
        }

        private static class Int16SampleServer extends AbstractTileableImageServer {

            private final short[][][] pixels = {{
                    {20957, 24008, 24528},
                    {-13946, -4466, 32099},
            }, {
                    {10035, -28963, -1246},
                    {-29674, 16228, 3983},
            }}; // z, y, x

            @Override
            protected BufferedImage readTile(TileRequest tileRequest) {
                return createImageFromPixels(pixels[tileRequest.getZ()], getMetadata());
            }

            @Override
            protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
                return null;
            }

            @Override
            protected String createID() {
                return Int16SampleServer.class.getName();
            }

            @Override
            public Collection<URI> getURIs() {
                return List.of();
            }

            @Override
            public String getServerType() {
                return "Int16 sample server";
            }

            @Override
            public ImageServerMetadata getOriginalMetadata() {
                return new ImageServerMetadata.Builder()
                        .width(pixels[0][0].length)
                        .height(pixels[0].length)
                        .sizeZ(pixels.length)
                        .channels(List.of(ImageChannel.RED))
                        .pixelType(PixelType.INT16)
                        .build();
            }
        }
    }

    @Nested
    class RgbImage extends GenericImage {

        @BeforeAll
        static void createSampleServer() {
            sampleServer = new RgbSampleServer();
        }

        @Override
        protected PixelType getPixelTypeWithSum() {
            return PixelType.UINT8;
        }

        @Override
        protected Object getAveragePixels() {
            return new int[][][] {
                    {{112, 88, 170}, {77, 85, 163}, {229, 49, 126}},
                    {{123, 156, 103}, {159, 177, 56}, {65, 121, 123}}
            };
        }

        @Override
        protected Object getMinPixels() {
            return new int[][][] {
                    {{39, 27, 91}, {43, 45, 76}, {211, 36, 38}},
                    {{38, 60, 71}, {98, 117, 48}, {11, 106, 2}}
            };
        }

        @Override
        protected Object getMaxPixels() {
            return new int[][][] {
                    {{185, 148, 248}, {110, 124, 250}, {246, 61, 214}},
                    {{208, 252, 134}, {219, 236, 63}, {118, 136, 244}}
            };
        }

        @Override
        protected Object getSumPixels() {
            return new int[][][] {
                    {{224, 175, 255}, {153, 169, 255}, {255, 97, 252}},
                    {{246, 255, 205}, {255, 255, 111}, {129, 242, 246}}
            };
        }

        @Override
        protected Object getStandardDeviationPixels() {
            return new int[][][] {
                    {{73, 61, 79}, {34, 40, 87}, {18, 13, 88}},
                    {{85, 96, 32}, {61, 60, 8}, {54, 15, 121}}
            };
        }

        @Override
        protected Object getMedianPixels() {
            return new int[][][] {
                    {{112, 88, 170}, {77, 85, 163}, {229, 49, 126}},
                    {{123, 156, 103}, {159, 177, 56}, {65, 121, 123}}
            };
        }

        private static class RgbSampleServer extends AbstractTileableImageServer {

            private final int[][][][] pixels = {{
                    {{39, 148, 248}, {43, 45, 250}, {246, 61, 214}},
                    {{208, 252, 134}, {219, 117, 48}, {118, 136, 2}},
            }, {
                    {{185, 27, 91}, {110, 124, 76}, {211, 36, 38}},
                    {{38, 60, 71}, {98, 236, 63}, {11, 106, 244}},
            }}; // z, y, x, c

            @Override
            protected BufferedImage readTile(TileRequest tileRequest) {
                return createImageFromPixels(pixels[tileRequest.getZ()], getMetadata());
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
                        .sizeZ(pixels.length)
                        .channels(ImageChannel.getDefaultRGBChannels())
                        .pixelType(PixelType.UINT8)
                        .rgb(true)
                        .build();
            }
        }
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
