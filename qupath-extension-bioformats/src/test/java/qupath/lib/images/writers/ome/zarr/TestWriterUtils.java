package qupath.lib.images.writers.ome.zarr;

import com.bc.zarr.ZarrGroup;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.ImageRegion;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public class TestWriterUtils {

    @Test
    void Check_Chunk_Size_With_Small_Image_Size() {
        int tileSize = 10;
        int maxNumberOfChunks = 10;
        int imageSize = 5;
        int expectedChunkSize = 5;

        int chunkSize = WriterUtils.getChunkSize(tileSize, maxNumberOfChunks, imageSize);

        Assertions.assertEquals(expectedChunkSize, chunkSize);
    }

    @Test
    void Check_Chunk_Size_With_Small_Max_Number_Of_Chunks() {
        int tileSize = 10;
        int maxNumberOfChunks = 2;
        int imageSize = 50;
        int expectedChunkSize = 25;

        int chunkSize = WriterUtils.getChunkSize(tileSize, maxNumberOfChunks, imageSize);

        Assertions.assertEquals(expectedChunkSize, chunkSize);
    }

    @Test
    void Check_Chunk_Size_With_No_Max_Number_Of_Chunks() {
        int tileSize = 10;
        int maxNumberOfChunks = 0;
        int imageSize = 50;
        int expectedChunkSize = 10;

        int chunkSize = WriterUtils.getChunkSize(tileSize, maxNumberOfChunks, imageSize);

        Assertions.assertEquals(expectedChunkSize, chunkSize);
    }

    @Test
    void Check_OME_Sub_Group_Written() throws IOException, ParserConfigurationException, TransformerException {
        Path path = Files.createTempDirectory(UUID.randomUUID().toString());
        Path groupPath = Paths.get(path.toString(), "group.ome.zarr");
        ZarrGroup group = ZarrGroup.create(groupPath);

        WriterUtils.createOmeSubGroup(group, groupPath, new ImageServerMetadata.Builder().width(1).height(1).rgb(true).build());

        Assertions.assertTrue(Files.exists(groupPath.resolve("OME").resolve("METADATA.ome.xml")));

        FileUtils.deleteDirectory(path.toFile());
    }

    @Test
    void Check_Dimensions_Of_5D_Image_With_Downsample() {
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(2)
                .height(4)
                .channels(List.of(ImageChannel.RED, ImageChannel.GREEN))
                .sizeZ(5)
                .sizeT(9)
                .build();
        double downsample = 2;
        int[] expectedDimensions = new int[] {9, 2, 5, 2, 1};

        int[] dimensions = WriterUtils.getDimensionsOfImage(metadata, downsample);

        Assertions.assertArrayEquals(expectedDimensions, dimensions);
    }

    @Test
    void Check_Dimensions_Of_5D_Image_Without_Downsample() {
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(2)
                .height(4)
                .channels(List.of(ImageChannel.RED, ImageChannel.GREEN))
                .sizeZ(5)
                .sizeT(9)
                .build();
        double downsample = 1;
        int[] expectedDimensions = new int[] {9, 2, 5, 4, 2};

        int[] dimensions = WriterUtils.getDimensionsOfImage(metadata, downsample);

        Assertions.assertArrayEquals(expectedDimensions, dimensions);
    }

    @Test
    void Check_Dimensions_Of_RGB_Image() {
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(2)
                .height(4)
                .rgb(true)
                .build();
        double downsample = 1;
        int[] expectedDimensions = new int[] {3, 4, 2};

        int[] dimensions = WriterUtils.getDimensionsOfImage(metadata, downsample);

        Assertions.assertArrayEquals(expectedDimensions, dimensions);
    }

    @Test
    void Check_Chunks_Dimensions_Of_5D_Image() {
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(2)
                .height(4)
                .channels(List.of(ImageChannel.RED, ImageChannel.GREEN))
                .sizeZ(5)
                .sizeT(9)
                .build();
        int chunkWidth = 1;
        int chunkHeight = 2;
        int[] expectedDimensions = new int[] {1, 1, 1, 2, 1};

        int[] dimensions = WriterUtils.getDimensionsOfChunks(metadata, chunkWidth, chunkHeight);

        Assertions.assertArrayEquals(expectedDimensions, dimensions);
    }

    @Test
    void Check_Chunks_Dimensions_Of_RGB_Image() {
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(2)
                .height(4)
                .rgb(true)
                .build();
        int chunkWidth = 1;
        int chunkHeight = 2;
        int[] expectedDimensions = new int[] {1, 2, 1};

        int[] dimensions = WriterUtils.getDimensionsOfChunks(metadata, chunkWidth, chunkHeight);

        Assertions.assertArrayEquals(expectedDimensions, dimensions);
    }

    @Test
    void Check_Tile_Dimensions_Of_5D_Image() {
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .channels(List.of(ImageChannel.RED, ImageChannel.GREEN))
                .sizeZ(5)
                .sizeT(9)
                .build();
        TileRequest tileRequest = TileRequest.createInstance(
                "5d server",
                0,
                1,
                ImageRegion.createInstance(1, 2, 3, 4, 4, 5)
        );
        int[] expectedDimensions = new int[] {1, 2, 1, 4, 3};

        int[] dimensions = WriterUtils.getDimensionsOfTile(metadata, tileRequest);

        Assertions.assertArrayEquals(expectedDimensions, dimensions);
    }

    @Test
    void Check_Tile_Dimensions_Of_RGB_Image() {
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .rgb(true)
                .build();
        TileRequest tileRequest = TileRequest.createInstance(
                "rgb server",
                0,
                1,
                ImageRegion.createInstance(1, 2, 3, 4, 0, 0)
        );
        int[] expectedDimensions = new int[] {3, 4, 3};

        int[] dimensions = WriterUtils.getDimensionsOfTile(metadata, tileRequest);

        Assertions.assertArrayEquals(expectedDimensions, dimensions);
    }

    @Test
    void Check_Tile_Offsets_Of_5D_Image() {
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .channels(List.of(ImageChannel.RED, ImageChannel.GREEN))
                .sizeZ(5)
                .sizeT(9)
                .build();
        TileRequest tileRequest = TileRequest.createInstance(
                "5d server",
                0,
                1,
                ImageRegion.createInstance(1, 2, 3, 4, 4, 5)
        );
        int[] expectedOffsets = new int[] {5, 0, 4, 2, 1};

        int[] offsets = WriterUtils.getOffsetsOfTile(metadata, tileRequest);

        Assertions.assertArrayEquals(expectedOffsets, offsets);
    }

    @Test
    void Check_Tile_Offsets_Of_RGB_Image() {
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .rgb(true)
                .build();
        TileRequest tileRequest = TileRequest.createInstance(
                "rgb server",
                0,
                1,
                ImageRegion.createInstance(1, 2, 3, 4, 0, 0)
        );
        int[] expectedOffsets = new int[] {0, 2, 1};

        int[] offsets = WriterUtils.getOffsetsOfTile(metadata, tileRequest);

        Assertions.assertArrayEquals(expectedOffsets, offsets);
    }

    @Test
    void Check_Converted_RGB_Image() {
        int width = 2;
        int height = 3;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(
                0,
                0,
                width,
                height,
                new int[] {
                        ColorTools.packRGB(1, 2, 3), ColorTools.packRGB(4, 5, 6),
                        ColorTools.packRGB(7, 8, 9), ColorTools.packRGB(10, 11, 12),
                        ColorTools.packRGB(13, 14, 15), ColorTools.packRGB(16, 17, 18)
                },
                0,
                width
        );
        int[] expectedPixels = new int[] {
                // red
                1, 4,
                7, 10,
                13, 16,
                // green
                2, 5,
                8, 11,
                14, 17,
                // blue
                3, 6,
                9, 12,
                15, 18
        };
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .rgb(true)
                .build();

        int[] pixels = (int[]) WriterUtils.convertBufferedImageToArray(image, metadata);

        Assertions.assertArrayEquals(expectedPixels, pixels);
    }

    @Test
    void Check_Converted_Uint8_Image() {
        int width = 2;
        int height = 3;
        List<ImageChannel> channels = List.of(ImageChannel.RED, ImageChannel.GREEN);
        PixelType pixelType = PixelType.UINT8;
        BufferedImage image = new BufferedImage(
                ColorModelFactory.createColorModel(
                        pixelType,
                        List.of(ImageChannel.RED, ImageChannel.GREEN)
                ),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(DataBuffer.TYPE_BYTE, width, height, channels.size()),
                        new DataBufferByte(
                                new byte[][] {
                                        new byte[] {
                                                1, 2,
                                                3, 4,
                                                5, 6
                                        },
                                        new byte[] {
                                                7, 8,
                                                9, 10,
                                                11, 12
                                        }
                                },
                                channels.size()
                        ),
                        null
                ),
                false,
                null
        );
        byte[] expectedPixels = new byte[] {
                // first channel
                1, 2,
                3, 4,
                5, 6,
                // second channel
                7, 8,
                9, 10,
                11, 12
        };
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .pixelType(pixelType)
                .channels(channels)
                .build();

        byte[] pixels = (byte[]) WriterUtils.convertBufferedImageToArray(image, metadata);

        Assertions.assertArrayEquals(expectedPixels, pixels);
    }

    @Test
    void Check_Converted_Int8_Image() {
        int width = 2;
        int height = 3;
        List<ImageChannel> channels = List.of(ImageChannel.RED, ImageChannel.GREEN);
        PixelType pixelType = PixelType.INT8;
        BufferedImage image = new BufferedImage(
                ColorModelFactory.createColorModel(
                        pixelType,
                        List.of(ImageChannel.RED, ImageChannel.GREEN)
                ),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(DataBuffer.TYPE_BYTE, width, height, channels.size()),
                        new DataBufferByte(
                                new byte[][] {
                                        new byte[] {
                                                1, 2,
                                                3, 4,
                                                5, 6
                                        },
                                        new byte[] {
                                                7, 8,
                                                9, 10,
                                                11, 12
                                        }
                                },
                                channels.size()
                        ),
                        null
                ),
                false,
                null
        );
        byte[] expectedPixels = new byte[] {
                // first channel
                1, 2,
                3, 4,
                5, 6,
                // second channel
                7, 8,
                9, 10,
                11, 12
        };
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .pixelType(pixelType)
                .channels(channels)
                .build();

        byte[] pixels = (byte[]) WriterUtils.convertBufferedImageToArray(image, metadata);

        Assertions.assertArrayEquals(expectedPixels, pixels);
    }

    @Test
    void Check_Converted_Uint16_Image() {
        int width = 2;
        int height = 3;
        List<ImageChannel> channels = List.of(ImageChannel.RED, ImageChannel.GREEN);
        PixelType pixelType = PixelType.UINT16;
        BufferedImage image = new BufferedImage(
                ColorModelFactory.createColorModel(
                        pixelType,
                        List.of(ImageChannel.RED, ImageChannel.GREEN)
                ),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(DataBuffer.TYPE_USHORT, width, height, channels.size()),
                        new DataBufferUShort(
                                new short[][] {
                                        new short[] {
                                                1, 2,
                                                3, 4,
                                                5, 6
                                        },
                                        new short[] {
                                                7, 8,
                                                9, 10,
                                                11, 12
                                        }
                                },
                                channels.size()
                        ),
                        null
                ),
                false,
                null
        );
        short[] expectedPixels = new short[] {
                // first channel
                1, 2,
                3, 4,
                5, 6,
                // second channel
                7, 8,
                9, 10,
                11, 12
        };
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .pixelType(pixelType)
                .channels(channels)
                .build();

        short[] pixels = (short[]) WriterUtils.convertBufferedImageToArray(image, metadata);

        Assertions.assertArrayEquals(expectedPixels, pixels);
    }

    @Test
    void Check_Converted_Int16_Image() {
        int width = 2;
        int height = 3;
        List<ImageChannel> channels = List.of(ImageChannel.RED, ImageChannel.GREEN);
        PixelType pixelType = PixelType.INT16;
        BufferedImage image = new BufferedImage(
                ColorModelFactory.createColorModel(
                        pixelType,
                        List.of(ImageChannel.RED, ImageChannel.GREEN)
                ),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(DataBuffer.TYPE_SHORT, width, height, channels.size()),
                        new DataBufferShort(
                                new short[][] {
                                        new short[] {
                                                1, 2,
                                                3, 4,
                                                5, 6
                                        },
                                        new short[] {
                                                7, 8,
                                                9, 10,
                                                11, 12
                                        }
                                },
                                channels.size()
                        ),
                        null
                ),
                false,
                null
        );
        short[] expectedPixels = new short[] {
                // first channel
                1, 2,
                3, 4,
                5, 6,
                // second channel
                7, 8,
                9, 10,
                11, 12
        };
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .pixelType(pixelType)
                .channels(channels)
                .build();

        short[] pixels = (short[]) WriterUtils.convertBufferedImageToArray(image, metadata);

        Assertions.assertArrayEquals(expectedPixels, pixels);
    }

    @Test
    void Check_Converted_Uint32_Image() {
        int width = 2;
        int height = 3;
        List<ImageChannel> channels = List.of(ImageChannel.RED, ImageChannel.GREEN);
        PixelType pixelType = PixelType.UINT32;
        BufferedImage image = new BufferedImage(
                ColorModelFactory.createColorModel(
                        pixelType,
                        List.of(ImageChannel.RED, ImageChannel.GREEN)
                ),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(DataBuffer.TYPE_INT, width, height, channels.size()),
                        new DataBufferInt(
                                new int[][] {
                                        new int[] {
                                                1, 2,
                                                3, 4,
                                                5, 6
                                        },
                                        new int[] {
                                                7, 8,
                                                9, 10,
                                                11, 12
                                        }
                                },
                                channels.size()
                        ),
                        null
                ),
                false,
                null
        );
        int[] expectedPixels = new int[] {
                // first channel
                1, 2,
                3, 4,
                5, 6,
                // second channel
                7, 8,
                9, 10,
                11, 12
        };
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .pixelType(pixelType)
                .channels(channels)
                .build();

        int[] pixels = (int[]) WriterUtils.convertBufferedImageToArray(image, metadata);

        Assertions.assertArrayEquals(expectedPixels, pixels);
    }

    @Test
    void Check_Converted_Int32_Image() {
        int width = 2;
        int height = 3;
        List<ImageChannel> channels = List.of(ImageChannel.RED, ImageChannel.GREEN);
        PixelType pixelType = PixelType.INT32;
        BufferedImage image = new BufferedImage(
                ColorModelFactory.createColorModel(
                        pixelType,
                        List.of(ImageChannel.RED, ImageChannel.GREEN)
                ),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(DataBuffer.TYPE_INT, width, height, channels.size()),
                        new DataBufferInt(
                                new int[][] {
                                        new int[] {
                                                1, 2,
                                                3, 4,
                                                5, 6
                                        },
                                        new int[] {
                                                7, 8,
                                                9, 10,
                                                11, 12
                                        }
                                },
                                channels.size()
                        ),
                        null
                ),
                false,
                null
        );
        int[] expectedPixels = new int[] {
                // first channel
                1, 2,
                3, 4,
                5, 6,
                // second channel
                7, 8,
                9, 10,
                11, 12
        };
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .pixelType(pixelType)
                .channels(channels)
                .build();

        int[] pixels = (int[]) WriterUtils.convertBufferedImageToArray(image, metadata);

        Assertions.assertArrayEquals(expectedPixels, pixels);
    }

    @Test
    void Check_Converted_Float32_Image() {
        int width = 2;
        int height = 3;
        List<ImageChannel> channels = List.of(ImageChannel.RED, ImageChannel.GREEN);
        PixelType pixelType = PixelType.FLOAT32;
        BufferedImage image = new BufferedImage(
                ColorModelFactory.createColorModel(
                        pixelType,
                        List.of(ImageChannel.RED, ImageChannel.GREEN)
                ),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(DataBuffer.TYPE_FLOAT, width, height, channels.size()),
                        new DataBufferFloat(
                                new float[][] {
                                        new float[] {
                                                1.1f, 2.2f,
                                                3.3f, 4.4f,
                                                5.5f, 6.6f
                                        },
                                        new float[] {
                                                7.7f, 8.8f,
                                                9.9f, 10.10f,
                                                11.11f, 12.12f
                                        }
                                },
                                channels.size()
                        ),
                        null
                ),
                false,
                null
        );
        float[] expectedPixels = new float[] {
                // first channel
                1.1f, 2.2f,
                3.3f, 4.4f,
                5.5f, 6.6f,
                // second channel
                7.7f, 8.8f,
                9.9f, 10.10f,
                11.11f, 12.12f
        };
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .pixelType(pixelType)
                .channels(channels)
                .build();

        float[] pixels = (float[]) WriterUtils.convertBufferedImageToArray(image, metadata);

        Assertions.assertArrayEquals(expectedPixels, pixels);
    }

    @Test
    void Check_Converted_Float64_Image() {
        int width = 2;
        int height = 3;
        List<ImageChannel> channels = List.of(ImageChannel.RED, ImageChannel.GREEN);
        PixelType pixelType = PixelType.FLOAT64;
        BufferedImage image = new BufferedImage(
                ColorModelFactory.createColorModel(
                        pixelType,
                        List.of(ImageChannel.RED, ImageChannel.GREEN)
                ),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(DataBuffer.TYPE_DOUBLE, width, height, channels.size()),
                        new DataBufferDouble(
                                new double[][] {
                                        new double[] {
                                                1.1f, 2.2f,
                                                3.3f, 4.4f,
                                                5.5f, 6.6f
                                        },
                                        new double[] {
                                                7.7f, 8.8f,
                                                9.9f, 10.10f,
                                                11.11f, 12.12f
                                        }
                                },
                                channels.size()
                        ),
                        null
                ),
                false,
                null
        );
        double[] expectedPixels = new double[] {
                // first channel
                1.1f, 2.2f,
                3.3f, 4.4f,
                5.5f, 6.6f,
                // second channel
                7.7f, 8.8f,
                9.9f, 10.10f,
                11.11f, 12.12f
        };
        ImageServerMetadata metadata = new ImageServerMetadata.Builder()
                .width(10)
                .height(10)
                .pixelType(pixelType)
                .channels(channels)
                .build();

        double[] pixels = (double[]) WriterUtils.convertBufferedImageToArray(image, metadata);

        Assertions.assertArrayEquals(expectedPixels, pixels);
    }
}
