package qupath.lib.images.writers.ome.zarr;

import com.bc.zarr.ZarrGroup;
import loci.formats.gui.AWTImageTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility functions used by the zarr writers.
 */
class WriterUtils {

    private static final String OME_FOLDER_NAME = "OME";
    private static final String OME_METADATA_FILE_NAME = "METADATA.ome.xml";

    private WriterUtils() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Get a chunk size (i.e. width and height) according the provided parameters.
     *
     * @param tileSize the size that will be returned if none of the following parameters affect it
     * @param maxNumberOfChunks the maximum number of chunks to have on one dimension (X or Y). Can be negative or equal to 0 to
     *                          not define any maximum number
     * @param imageSize the size of the image that will contain the chunks. The returned chunk size won't be greater than this size
     * @return a chunk size that follow the specifications described above
     */
    public static int getChunkSize(int tileSize, int maxNumberOfChunks, int imageSize) {
        return Math.min(
                imageSize,
                maxNumberOfChunks > 0 ?
                        Math.max(tileSize, imageSize / maxNumberOfChunks) :
                        tileSize
        );
    }

    /**
     * Create an "OME" sub ground in the provided zarr group located in the provided path and create a "METADATA.ome.xml" file inside
     * it containing the June 2016 Open Microscopy Environment OME Schema applied to the provided metadata.
     *
     * @param group the zarr group in which the subgroup and the XML file should be written
     * @param path the local path of the provided zarr group
     * @param metadata the metadata that should be used to populate the XML file
     * @throws ParserConfigurationException if the XML document cannot be created
     * @throws org.w3c.dom.DOMException if an error occurs while creating the XML content
     * @throws IOException if an error occurs while writing the XML content to a file
     * @throws TransformerException if an error occurs while converting the XML content to a byte array
     * @throws SecurityException if the calling thread doesn't have the permission to write the XML file
     * @throws InvalidPathException if the XML file path cannot be created
     * @throws IllegalArgumentException if the provided metadata contains an unexpected entry (e.g. no channels)
     */
    public static void createOmeSubGroup(ZarrGroup group, Path path, ImageServerMetadata metadata) throws ParserConfigurationException, IOException, TransformerException {
        group.createSubGroup(OME_FOLDER_NAME);

        try (OutputStream outputStream = new FileOutputStream(Files.createFile(path.resolve(OME_FOLDER_NAME).resolve(OME_METADATA_FILE_NAME)).toString())) {
            outputStream.write(OMEXMLCreator.create(metadata));
        }
    }

    /**
     * Get an integer array representing the (5D or less) dimensions of the image represented by the provided metadata
     * at the provided downsample.
     * <p>
     * The order of the dimensions in the returned array is as follows: T, C, Z, Y, X.
     * <p>
     * If the number of timepoints, number of channels, or number of z-stacks is equal to 1, then the returned
     * array won't contain the corresponding dimension.
     *
     * @param metadata the metadata containing the dimensions to return
     * @param downsample the downsample to apply to the dimensions. It is only applied to the Y and X dimensions
     * @return the size of the provided image as described above
     */
    public static int[] getDimensionsOfImage(ImageServerMetadata metadata, double downsample) {
        List<Integer> dimensions = new ArrayList<>();
        if (metadata.getSizeT() > 1) {
            dimensions.add(metadata.getSizeT());
        }
        if (metadata.getSizeC() > 1) {
            dimensions.add(metadata.getSizeC());
        }
        if (metadata.getSizeZ() > 1) {
            dimensions.add(metadata.getSizeZ());
        }
        dimensions.add((int) (metadata.getHeight() / downsample));
        dimensions.add((int) (metadata.getWidth() / downsample));

        return dimensions.stream().mapToInt(i -> i).toArray();
    }

    /**
     * Get an integer array representing the (5D or less) dimensions of chunks dividing the provided image.
     * <p>
     * The order of the dimensions in the returned array is as follows: T, C, Z, Y, X. The dimension of the
     * T, C, and Z axes is always 1.
     * <p>
     * If the number of timepoints, number of channels, or number of z-stacks is equal to 1, then the returned
     * array won't contain the corresponding dimension.
     *
     * @param metadata the metadata of the image that contains the chunks
     * @param chunkWidth the width the chunks should have
     * @param chunkHeight the height the chunks should have
     * @return the size of the chunks as described above
     */
    public static int[] getDimensionsOfChunks(ImageServerMetadata metadata, int chunkWidth, int chunkHeight) {
        List<Integer> chunks = new ArrayList<>();
        if (metadata.getSizeT() > 1) {
            chunks.add(1);
        }
        if (metadata.getSizeC() > 1) {
            chunks.add(1);
        }
        if (metadata.getSizeZ() > 1) {
            chunks.add(1);
        }
        chunks.add(chunkHeight);
        chunks.add(chunkWidth);

        return chunks.stream().mapToInt(i -> i).toArray();
    }

    /**
     * Get an integer array representing the (5D or less) dimensions of the provided tile on the provided image.
     * <p>
     * The order of the dimensions in the returned array is as follows: T, C, Z, Y, X.
     * <p>
     * If the number of timepoints, number of channels, or number of z-stacks is equal to 1, then the returned
     * array won't contain the corresponding dimension.
     *
     * @param metadata the metadata of the image the tile corresponds to
     * @param tileRequest the tile whose dimensions should be computed
     * @return the size of the tile as described above
     */
    public static int[] getDimensionsOfTile(ImageServerMetadata metadata, TileRequest tileRequest) {
        List<Integer> dimensions = new ArrayList<>();
        if (metadata.getSizeT() > 1) {
            dimensions.add(1);
        }
        if (metadata.getSizeC() > 1) {
            dimensions.add(metadata.getSizeC());
        }
        if (metadata.getSizeZ() > 1) {
            dimensions.add(1);
        }
        dimensions.add(tileRequest.getTileHeight());
        dimensions.add(tileRequest.getTileWidth());

        return dimensions.stream().mapToInt(i -> i).toArray();
    }

    /**
     * Get an integer array representing the (5D or less) offsets of the provided tile on the provided image.
     * <p>
     * The order of the dimensions in the returned array is as follows: T, C, Z, Y, X.
     * <p>
     * If the number of timepoints, number of channels, or number of z-stacks is equal to 1, then the returned
     * array won't contain the corresponding dimension.
     *
     * @param metadata the metadata of the image the tile corresponds to
     * @param tileRequest the tile whose offsets should be computed
     * @return the offsets of the tile as described above
     */
    public static int[] getOffsetsOfTile(ImageServerMetadata metadata, TileRequest tileRequest) {
        List<Integer> offset = new ArrayList<>();
        if (metadata.getSizeT() > 1) {
            offset.add(tileRequest.getT());
        }
        if (metadata.getSizeC() > 1) {
            offset.add(0);
        }
        if (metadata.getSizeZ() > 1) {
            offset.add(tileRequest.getZ());
        }
        offset.add(tileRequest.getTileY());
        offset.add(tileRequest.getTileX());

        return offset.stream().mapToInt(i -> i).toArray();
    }

    /**
     * Convert the provided image to a one-dimensional array. See {@link AWTImageTools#getPixels(BufferedImage)} for
     * more information.
     *
     * @param image the image to convert
     * @return a one-dimensional array as described above. The pixel located at coordinates [x; y; c] can be accessed with
     * returnedArray[x + imageWidth * y + imageWidth * imageHeight * c]
     */
    public static Object convertBufferedImageToArray(BufferedImage image) {
        int channelSize = image.getWidth() * image.getHeight();

        if (BufferedImageTools.is8bitColorType(image.getType())) {
            int[] rgb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());

            byte[] pixels = new byte[image.getWidth() * image.getHeight() * 3];
            for (int i = 0; i < channelSize; i++) {
                int val = rgb[i];
                pixels[i] = (byte) ColorTools.red(val);
                pixels[channelSize + i] = (byte) ColorTools.green(val);
                pixels[channelSize*2 + i] = (byte) ColorTools.blue(val);
            }

            return pixels;
        }

        Object pixels = AWTImageTools.getPixels(image);

        // No need to copy for single array
        int nChannels = Array.getLength(pixels);
        if (nChannels == 1) {
            return Array.get(pixels, 0);
        }

        return switch (pixels) {
            case byte[][] data -> {
                byte[] output = new byte[nChannels * channelSize];
                for (int c = 0; c < nChannels; c++) {
                    System.arraycopy(data[c], 0, output, c * channelSize, channelSize);
                }
                yield output;
            }
            case short[][] data -> {
                short[] output = new short[nChannels * channelSize];
                for (int c = 0; c < nChannels; c++) {
                    System.arraycopy(data[c], 0, output, c * channelSize, channelSize);
                }
                yield output;
            }
            case int[][] data -> {
                int[] output = new int[nChannels * channelSize];
                for (int c = 0; c < nChannels; c++) {
                    System.arraycopy(data[c], 0, output, c * channelSize, channelSize);
                }
                yield output;
            }
            case float[][] data -> {
                float[] output = new float[nChannels * channelSize];
                for (int c = 0; c < nChannels; c++) {
                    System.arraycopy(data[c], 0, output, c * channelSize, channelSize);
                }
                yield output;
            }
            case double[][] data -> {
                double[] output = new double[nChannels * channelSize];
                for (int c = 0; c < nChannels; c++) {
                    System.arraycopy(data[c], 0, output, c * channelSize, channelSize);
                }
                yield output;
            }
            case null, default -> throw new UnsupportedOperationException("Unknown data type");
        };
    }
}
