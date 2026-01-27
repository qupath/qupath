package qupath.lib.images.servers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.WritableRaster;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

public class TestFlippedImageServer {

    @Test
    void Check_Null_Server() {
        ImageServer<BufferedImage> inputServer = null;
        FlippedImageServer.Flip flip = FlippedImageServer.Flip.NONE;

        Assertions.assertThrows(NullPointerException.class, () -> new FlippedImageServer(inputServer, flip));
    }

    @Test
    void Check_Null_Flip() throws Exception {
        ImageServer<BufferedImage> inputServer = new WrappedBufferedImageServer(
                "some image",
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        );
        FlippedImageServer.Flip flip = null;

        Assertions.assertThrows(NullPointerException.class, () -> new FlippedImageServer(inputServer, flip));

        inputServer.close();
    }

    abstract static class GenericClient {

        @Test
        abstract void Check_No_Flip() throws Exception;

        @Test
        abstract void Check_Horizontal_Flip() throws Exception;

        @Test
        abstract void Check_Vertical_Flip() throws Exception;

        @Test
        abstract void Check_Both_Flip() throws Exception;
    }

    @Nested
    class DoubleImage extends GenericClient {

        @Test
        @Override
        void Check_No_Flip() throws Exception {
            DoubleSampleServer.PixelGetter pixelGetter = (downsample, x, y, c, z, t) ->
                    downsample + x + y + c + z + t;
            ImageServer<BufferedImage> doubleSampleServer = new DoubleSampleServer(
                    pixelGetter,
                    new int[] {12, 16, 2, 9, 7},
                    new double[] {1, 2, 4}
            );
            FlippedImageServer.Flip flip = FlippedImageServer.Flip.NONE;
            FlippedImageServer flippedImageServer = new FlippedImageServer(doubleSampleServer, flip);
            double downsample = 2;
            int z = 5;
            int t = 3;
            RegionRequest request = RegionRequest.createInstance(
                    flippedImageServer.getPath(),
                    downsample,
                    4,
                    6,
                    6,
                    8,
                    z,
                    t
            );
            /*
            The image at downsample 2 has the following indices:
            0,0 1,0 2,0 3,0 4,0 5,0
            0,1 1,1 2,1 3,1 4,1 5,1
            0,2 1,2 2,2 3,2 4,2 5,2
            0,3 1,3 2,3 3,3 4,3 5,3
            0,4 1,4 2,4 3,4 4,4 5,4
            0,5 1,5 2,5 3,5 4,5 5,5
            0,6 1,6 2,6 3,6 4,6 5,6
            0,7 1,7 2,7 3,7 4,7 5,7

            It is converted to:
            0,0 1,0 2,0 3,0 4,0 5,0
            0,1 1,1 2,1 3,1 4,1 5,1
            0,2 1,2 2,2 3,2 4,2 5,2
            0,3 1,3 2,3 3,3 4,3 5,3
            0,4 1,4 2,4 3,4 4,4 5,4
            0,5 1,5 2,5 3,5 4,5 5,5
            0,6 1,6 2,6 3,6 4,6 5,6
            0,7 1,7 2,7 3,7 4,7 5,7
             */
            BiFunction<Integer, Integer, Double> firstChannel = (x, y) ->
                    pixelGetter.get(downsample, x, y, 0, z, t);
            BiFunction<Integer, Integer, Double> secondChannel = (x, y) ->
                    pixelGetter.get(downsample, x, y, 1, z, t);
            double[][] expectedPixels = new double[][] {
                    new double[] {
                            firstChannel.apply(2,3), firstChannel.apply(3,3), firstChannel.apply(4,3),
                            firstChannel.apply(2,4), firstChannel.apply(3,4), firstChannel.apply(4,4),
                            firstChannel.apply(2,5), firstChannel.apply(3,5), firstChannel.apply(4,5),
                            firstChannel.apply(2,6), firstChannel.apply(3,6), firstChannel.apply(4,6),
                    },
                    new double[] {
                            secondChannel.apply(2,3), secondChannel.apply(3,3), secondChannel.apply(4,3),
                            secondChannel.apply(2,4), secondChannel.apply(3,4), secondChannel.apply(4,4),
                            secondChannel.apply(2,5), secondChannel.apply(3,5), secondChannel.apply(4,5),
                            secondChannel.apply(2,6), secondChannel.apply(3,6), secondChannel.apply(4,6),
                    },
            };

            BufferedImage image = flippedImageServer.readRegion(request);

            assertBufferedImagesEqual(expectedPixels, 3, image);

            flippedImageServer.close();
            doubleSampleServer.close();
        }

        @Test
        @Override
        void Check_Horizontal_Flip() throws Exception {
            DoubleSampleServer.PixelGetter pixelGetter = (downsample, x, y, c, z, t) ->
                    downsample + x + y + c + z + t;
            ImageServer<BufferedImage> doubleSampleServer = new DoubleSampleServer(
                    pixelGetter,
                    new int[] {12, 16, 2, 9, 7},
                    new double[] {1, 2, 4}
            );
            FlippedImageServer.Flip flip = FlippedImageServer.Flip.HORIZONTAL;
            FlippedImageServer flippedImageServer = new FlippedImageServer(doubleSampleServer, flip);
            double downsample = 2;
            int z = 5;
            int t = 3;
            RegionRequest request = RegionRequest.createInstance(
                    flippedImageServer.getPath(),
                    downsample,
                    4,
                    6,
                    6,
                    8,
                    z,
                    t
            );
            /*
            The image at downsample 2 has the following indices:
            0,0 1,0 2,0 3,0 4,0 5,0
            0,1 1,1 2,1 3,1 4,1 5,1
            0,2 1,2 2,2 3,2 4,2 5,2
            0,3 1,3 2,3 3,3 4,3 5,3
            0,4 1,4 2,4 3,4 4,4 5,4
            0,5 1,5 2,5 3,5 4,5 5,5
            0,6 1,6 2,6 3,6 4,6 5,6
            0,7 1,7 2,7 3,7 4,7 5,7

            It is converted to:
            5,0 4,0 3,0 2,0 1,0 0,0
            5,1 4,1 3,1 2,1 1,1 0,1
            5,2 4,2 3,2 2,2 1,2 0,2
            5,3 4,3 3,3 2,3 1,3 0,3
            5,4 4,4 3,4 2,4 1,4 0,4
            5,5 4,5 3,5 2,5 1,5 0,5
            5,6 4,6 3,6 2,6 1,6 0,6
            5,7 4,7 3,7 2,7 1,7 0,7
             */
            BiFunction<Integer, Integer, Double> firstChannel = (x, y) ->
                    pixelGetter.get(downsample, x, y, 0, z, t);
            BiFunction<Integer, Integer, Double> secondChannel = (x, y) ->
                    pixelGetter.get(downsample, x, y, 1, z, t);
            double[][] expectedPixels = new double[][] {
                    new double[] {
                            firstChannel.apply(3,3), firstChannel.apply(2,3), firstChannel.apply(1,3),
                            firstChannel.apply(3,4), firstChannel.apply(2,4), firstChannel.apply(1,4),
                            firstChannel.apply(3,5), firstChannel.apply(2,5), firstChannel.apply(1,5),
                            firstChannel.apply(3,6), firstChannel.apply(2,6), firstChannel.apply(1,6),
                    },
                    new double[] {
                            secondChannel.apply(3,3), secondChannel.apply(2,3), secondChannel.apply(1,3),
                            secondChannel.apply(3,4), secondChannel.apply(2,4), secondChannel.apply(1,4),
                            secondChannel.apply(3,5), secondChannel.apply(2,5), secondChannel.apply(1,5),
                            secondChannel.apply(3,6), secondChannel.apply(2,6), secondChannel.apply(1,6),
                    },
            };

            BufferedImage image = flippedImageServer.readRegion(request);

            assertBufferedImagesEqual(expectedPixels, 3, image);

            flippedImageServer.close();
            doubleSampleServer.close();
        }

        @Test
        @Override
        void Check_Vertical_Flip() throws Exception {
            DoubleSampleServer.PixelGetter pixelGetter = (downsample, x, y, c, z, t) ->
                    downsample + x + y + c + z + t;
            ImageServer<BufferedImage> doubleSampleServer = new DoubleSampleServer(
                    pixelGetter,
                    new int[] {12, 16, 2, 9, 7},
                    new double[] {1, 2, 4}
            );
            FlippedImageServer.Flip flip = FlippedImageServer.Flip.VERTICAL;
            FlippedImageServer flippedImageServer = new FlippedImageServer(doubleSampleServer, flip);
            double downsample = 2;
            int z = 5;
            int t = 3;
            RegionRequest request = RegionRequest.createInstance(
                    flippedImageServer.getPath(),
                    downsample,
                    4,
                    6,
                    6,
                    8,
                    z,
                    t
            );
            /*
            The image at downsample 2 has the following indices:
            0,0 1,0 2,0 3,0 4,0 5,0
            0,1 1,1 2,1 3,1 4,1 5,1
            0,2 1,2 2,2 3,2 4,2 5,2
            0,3 1,3 2,3 3,3 4,3 5,3
            0,4 1,4 2,4 3,4 4,4 5,4
            0,5 1,5 2,5 3,5 4,5 5,5
            0,6 1,6 2,6 3,6 4,6 5,6
            0,7 1,7 2,7 3,7 4,7 5,7

            It is converted to:
            0,7 1,7 2,7 3,7 4,7 5,7
            0,6 1,6 2,6 3,6 4,6 5,6
            0,5 1,5 2,5 3,5 4,5 5,5
            0,4 1,4 2,4 3,4 4,4 5,4
            0,3 1,3 2,3 3,3 4,3 5,3
            0,2 1,2 2,2 3,2 4,2 5,2
            0,1 1,1 2,1 3,1 4,1 5,1
            0,0 1,0 2,0 3,0 4,0 5,0
             */
            BiFunction<Integer, Integer, Double> firstChannel = (x, y) ->
                    pixelGetter.get(downsample, x, y, 0, z, t);
            BiFunction<Integer, Integer, Double> secondChannel = (x, y) ->
                    pixelGetter.get(downsample, x, y, 1, z, t);
            double[][] expectedPixels = new double[][] {
                    new double[] {
                            firstChannel.apply(2,4), firstChannel.apply(3,4), firstChannel.apply(4,4),
                            firstChannel.apply(2,3), firstChannel.apply(3,3), firstChannel.apply(4,3),
                            firstChannel.apply(2,2), firstChannel.apply(3,2), firstChannel.apply(4,2),
                            firstChannel.apply(2,1), firstChannel.apply(3,1), firstChannel.apply(4,1),
                    },
                    new double[] {
                            secondChannel.apply(2,4), secondChannel.apply(3,4), secondChannel.apply(4,4),
                            secondChannel.apply(2,3), secondChannel.apply(3,3), secondChannel.apply(4,3),
                            secondChannel.apply(2,2), secondChannel.apply(3,2), secondChannel.apply(4,2),
                            secondChannel.apply(2,1), secondChannel.apply(3,1), secondChannel.apply(4,1),
                    },
            };

            BufferedImage image = flippedImageServer.readRegion(request);

            assertBufferedImagesEqual(expectedPixels, 3, image);

            flippedImageServer.close();
            doubleSampleServer.close();
        }

        @Test
        @Override
        void Check_Both_Flip() throws Exception {
            DoubleSampleServer.PixelGetter pixelGetter = (downsample, x, y, c, z, t) ->
                    downsample + x + y + c + z + t;
            ImageServer<BufferedImage> doubleSampleServer = new DoubleSampleServer(
                    pixelGetter,
                    new int[] {12, 16, 2, 9, 7},
                    new double[] {1, 2, 4}
            );
            FlippedImageServer.Flip flip = FlippedImageServer.Flip.BOTH;
            FlippedImageServer flippedImageServer = new FlippedImageServer(doubleSampleServer, flip);
            double downsample = 2;
            int z = 5;
            int t = 3;
            RegionRequest request = RegionRequest.createInstance(
                    flippedImageServer.getPath(),
                    downsample,
                    4,
                    6,
                    6,
                    8,
                    z,
                    t
            );
            /*
            The image at downsample 2 has the following indices:
            0,0 1,0 2,0 3,0 4,0 5,0
            0,1 1,1 2,1 3,1 4,1 5,1
            0,2 1,2 2,2 3,2 4,2 5,2
            0,3 1,3 2,3 3,3 4,3 5,3
            0,4 1,4 2,4 3,4 4,4 5,4
            0,5 1,5 2,5 3,5 4,5 5,5
            0,6 1,6 2,6 3,6 4,6 5,6
            0,7 1,7 2,7 3,7 4,7 5,7

            It is converted to:
            5,7 4,7 3,7 2,7 1,7 0,7
            5,6 4,6 3,6 2,6 1,6 0,6
            5,5 4,5 3,5 2,5 1,5 0,5
            5,4 4,4 3,4 2,4 1,4 0,4
            5,3 4,3 3,3 2,3 1,3 0,3
            5,2 4,2 3,2 2,2 1,2 0,2
            5,1 4,1 3,1 2,1 1,1 0,1
            5,0 4,0 3,0 2,0 1,0 0,0
             */
            BiFunction<Integer, Integer, Double> firstChannel = (x, y) ->
                    pixelGetter.get(downsample, x, y, 0, z, t);
            BiFunction<Integer, Integer, Double> secondChannel = (x, y) ->
                    pixelGetter.get(downsample, x, y, 1, z, t);
            double[][] expectedPixels = new double[][] {
                    new double[] {
                            firstChannel.apply(3,4), firstChannel.apply(2,4), firstChannel.apply(1,4),
                            firstChannel.apply(3,3), firstChannel.apply(2,3), firstChannel.apply(1,3),
                            firstChannel.apply(3,2), firstChannel.apply(2,2), firstChannel.apply(1,2),
                            firstChannel.apply(3,1), firstChannel.apply(2,1), firstChannel.apply(1,1),
                    },
                    new double[] {
                            secondChannel.apply(3,4), secondChannel.apply(2,4), secondChannel.apply(1,4),
                            secondChannel.apply(3,3), secondChannel.apply(2,3), secondChannel.apply(1,3),
                            secondChannel.apply(3,2), secondChannel.apply(2,2), secondChannel.apply(1,2),
                            secondChannel.apply(3,1), secondChannel.apply(2,1), secondChannel.apply(1,1),
                    },
            };

            BufferedImage image = flippedImageServer.readRegion(request);

            assertBufferedImagesEqual(expectedPixels, 3, image);

            flippedImageServer.close();
            doubleSampleServer.close();
        }

        private static class DoubleSampleServer extends AbstractTileableImageServer {

            @FunctionalInterface
            public interface PixelGetter {
                /**
                 * Coordinates at the downsample resolution
                 */
                double get(double downsample, int x, int y, int c, int z, int t);
            }
            private final PixelGetter pixelGetter;
            private final ImageServerMetadata metadata;

            /**
             * @param sizes [width, height, nChannels, sizeZ, sizeT]
             */
            public DoubleSampleServer(PixelGetter pixelGetter, int[] sizes, double[] downsamples) {
                this.pixelGetter = pixelGetter;
                this.metadata = new ImageServerMetadata.Builder()
                        .width(sizes[0])
                        .height(sizes[1])
                        .channels(ImageChannel.getDefaultChannelList(sizes[2]))
                        .sizeZ(sizes[3])
                        .sizeT(sizes[4])
                        .levelsFromDownsamples(downsamples)
                        .pixelType(PixelType.FLOAT64)
                        .build();
            }

            @Override
            protected BufferedImage readTile(TileRequest tileRequest) {
                int tileWidth = tileRequest.getTileWidth();
                int tileHeight = tileRequest.getTileHeight();

                double[][] array = new double[nChannels()][tileWidth * tileHeight];

                for (int c=0; c<array.length; c++) {
                    for (int y=0; y<tileHeight; y++) {
                        for (int x=0; x<tileWidth; x++) {
                            array[c][x + tileWidth*y] = pixelGetter.get(
                                    tileRequest.getDownsample(),
                                    tileRequest.getTileX() + x,
                                    tileRequest.getTileX() + y,
                                    c,
                                    tileRequest.getZ(),
                                    tileRequest.getT()
                            );
                        }
                    }
                }
                DataBuffer dataBuffer = new DataBufferDouble(array, tileWidth * tileHeight / 8);

                return new BufferedImage(
                        ColorModelFactory.createColorModel(metadata.getPixelType(), metadata.getChannels()),
                        WritableRaster.createWritableRaster(
                                new BandedSampleModel(dataBuffer.getDataType(), tileWidth, tileHeight, metadata.getSizeC()),
                                dataBuffer,
                                null
                        ),
                        false,
                        null
                );
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
                return metadata;
            }
        }

        private static void assertBufferedImagesEqual(double[][] expectedPixels, int expectedWidth, BufferedImage actualImage) {
            int expectedNChannels = expectedPixels.length;
            int expectedHeight = expectedPixels[0].length / expectedWidth;

            Assertions.assertEquals(expectedWidth, actualImage.getWidth());
            Assertions.assertEquals(expectedHeight, actualImage.getHeight());
            Assertions.assertEquals(expectedNChannels, actualImage.getRaster().getNumBands());

            for (int c = 0; c < expectedNChannels; c++) {
                for (int y = 0; y < expectedHeight; y++) {
                    for (int x = 0; x < expectedWidth; x++) {
                        Assertions.assertEquals(
                                expectedPixels[c][x + expectedWidth*y],
                                actualImage.getRaster().getSampleDouble(x, y, c)
                        );
                    }
                }
            }
        }
    }

    @Nested
    class RgbImage extends GenericClient {

        @Test
        @Override
        void Check_No_Flip() throws Exception {
            RgbSampleServer.PixelGetter pixelGetter = (x, y) -> ColorTools.packRGB(x, y, 0);
            ImageServer<BufferedImage> rgbSampleServer = new RgbSampleServer(
                    pixelGetter,
                    new int[] {3, 2}
            );
            FlippedImageServer.Flip flip = FlippedImageServer.Flip.NONE;
            FlippedImageServer flippedImageServer = new FlippedImageServer(rgbSampleServer, flip);
            RegionRequest request = RegionRequest.createInstance(
                    flippedImageServer.getPath(),
                    1,
                    0,
                    0,
                    3,
                    2
            );
            /*
            The image has the following indices:
            0,0 1,0 2,0
            0,1 1,1 2,1

            It is converted to:
            0,0 1,0 2,0
            0,1 1,1 2,1
             */
            int[] expectedPixels = new int[] {
                    pixelGetter.get(0, 0), pixelGetter.get(1, 0), pixelGetter.get(2, 0),
                    pixelGetter.get(0, 1), pixelGetter.get(1, 1), pixelGetter.get(2, 1),
            };

            BufferedImage image = flippedImageServer.readRegion(request);

            assertBufferedImagesEqual(expectedPixels, 3, image);

            flippedImageServer.close();
            rgbSampleServer.close();
        }

        @Test
        @Override
        void Check_Horizontal_Flip() throws Exception {
            RgbSampleServer.PixelGetter pixelGetter = (x, y) -> ColorTools.packRGB(x, y, 0);
            ImageServer<BufferedImage> rgbSampleServer = new RgbSampleServer(
                    pixelGetter,
                    new int[] {3, 2}
            );
            FlippedImageServer.Flip flip = FlippedImageServer.Flip.HORIZONTAL;
            FlippedImageServer flippedImageServer = new FlippedImageServer(rgbSampleServer, flip);
            RegionRequest request = RegionRequest.createInstance(
                    flippedImageServer.getPath(),
                    1,
                    0,
                    0,
                    3,
                    2
            );
            /*
            The image has the following indices:
            0,0 1,0 2,0
            0,1 1,1 2,1

            It is converted to:
            2,0 1,0 0,0
            2,1 1,1 0,1
             */
            int[] expectedPixels = new int[] {
                    pixelGetter.get(2, 0), pixelGetter.get(1, 0), pixelGetter.get(0, 0),
                    pixelGetter.get(2, 1), pixelGetter.get(1, 1), pixelGetter.get(0, 1),
            };

            BufferedImage image = flippedImageServer.readRegion(request);

            assertBufferedImagesEqual(expectedPixels, 3, image);

            flippedImageServer.close();
            rgbSampleServer.close();
        }

        @Test
        @Override
        void Check_Vertical_Flip() throws Exception {
            RgbSampleServer.PixelGetter pixelGetter = (x, y) -> ColorTools.packRGB(x, y, 0);
            ImageServer<BufferedImage> rgbSampleServer = new RgbSampleServer(
                    pixelGetter,
                    new int[] {3, 2}
            );
            FlippedImageServer.Flip flip = FlippedImageServer.Flip.VERTICAL;
            FlippedImageServer flippedImageServer = new FlippedImageServer(rgbSampleServer, flip);
            RegionRequest request = RegionRequest.createInstance(
                    flippedImageServer.getPath(),
                    1,
                    0,
                    0,
                    3,
                    2
            );
            /*
            The image has the following indices:
            0,0 1,0 2,0
            0,1 1,1 2,1

            It is converted to:
            0,1 1,1 2,1
            0,0 1,0 2,0
             */
            int[] expectedPixels = new int[] {
                    pixelGetter.get(0, 1), pixelGetter.get(1, 1), pixelGetter.get(2, 1),
                    pixelGetter.get(0, 0), pixelGetter.get(1, 0), pixelGetter.get(2, 0),
            };

            BufferedImage image = flippedImageServer.readRegion(request);

            assertBufferedImagesEqual(expectedPixels, 3, image);

            flippedImageServer.close();
            rgbSampleServer.close();
        }

        @Test
        @Override
        void Check_Both_Flip() throws Exception {
            RgbSampleServer.PixelGetter pixelGetter = (x, y) -> ColorTools.packRGB(x, y, 0);
            ImageServer<BufferedImage> rgbSampleServer = new RgbSampleServer(
                    pixelGetter,
                    new int[] {3, 2}
            );
            FlippedImageServer.Flip flip = FlippedImageServer.Flip.BOTH;
            FlippedImageServer flippedImageServer = new FlippedImageServer(rgbSampleServer, flip);
            RegionRequest request = RegionRequest.createInstance(
                    flippedImageServer.getPath(),
                    1,
                    0,
                    0,
                    3,
                    2
            );
            /*
            The image has the following indices:
            0,0 1,0 2,0
            0,1 1,1 2,1

            It is converted to:
            2,1 1,1 0,1
            2,0 1,0 0,0
             */
            int[] expectedPixels = new int[] {
                    pixelGetter.get(2, 1), pixelGetter.get(1, 1), pixelGetter.get(0, 1),
                    pixelGetter.get(2, 0), pixelGetter.get(1, 0), pixelGetter.get(0, 0),
            };

            BufferedImage image = flippedImageServer.readRegion(request);

            assertBufferedImagesEqual(expectedPixels, 3, image);

            flippedImageServer.close();
            rgbSampleServer.close();
        }

        private static class RgbSampleServer extends AbstractTileableImageServer {

            @FunctionalInterface
            public interface PixelGetter {
                int get(int x, int y);
            }
            private final PixelGetter pixelGetter;
            private final ImageServerMetadata metadata;

            /**
             * @param sizes [width, height]
             */
            public RgbSampleServer(PixelGetter pixelGetter, int[] sizes) {
                this.pixelGetter = pixelGetter;
                this.metadata = new ImageServerMetadata.Builder()
                        .width(sizes[0])
                        .height(sizes[1])
                        .channels(ImageChannel.getDefaultRGBChannels())
                        .pixelType(PixelType.UINT8)
                        .rgb(true)
                        .build();
            }

            @Override
            protected BufferedImage readTile(TileRequest tileRequest) {
                int tileWidth = tileRequest.getTileWidth();
                int tileHeight = tileRequest.getTileHeight();

                BufferedImage image = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_ARGB);

                for (int y=0; y<tileHeight; y++) {
                    for (int x=0; x<tileWidth; x++) {
                        int imageX = x + tileRequest.getImageX();
                        int imageY = y + tileRequest.getImageY();

                        image.setRGB(imageX, imageY, pixelGetter.get(imageX, imageY));
                    }
                }

                return image;
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
                return metadata;
            }
        }

        private static void assertBufferedImagesEqual(int[] expectedPixels, int expectedWidth, BufferedImage actualImage) {
            int expectedHeight = expectedPixels.length / expectedWidth;

            Assertions.assertEquals(expectedWidth, actualImage.getWidth());
            Assertions.assertEquals(expectedHeight, actualImage.getHeight());

            for (int y = 0; y < expectedHeight; y++) {
                for (int x = 0; x < expectedWidth; x++) {
                    Assertions.assertEquals(
                            expectedPixels[x + expectedWidth*y],
                            actualImage.getRGB(x, y)
                    );
                }
            }
        }
    }
}
