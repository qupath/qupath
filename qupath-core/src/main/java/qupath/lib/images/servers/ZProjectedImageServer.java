package qupath.lib.images.servers;

import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * An image server that converts a z-stack into a 2D image with a projection.
 * <p>
 * Note that if the z-stack has the RGB format, transparency is not taken into account
 * (all alpha values will be set to 255).
 */
public class ZProjectedImageServer extends AbstractTileableImageServer {

    private final ImageServer<BufferedImage> server;
    private final Projection projection;
    private final ImageServerMetadata metadata;
    /**
     * A type of projection on the Z-axis.
     */
    public enum Projection {
        /**
         * A mean projection on the z-stacks. If the image uses the integer format, the mean
         * will be rounded up to the nearest integer.
         */
        MEAN,
        /**
         * A minimum projection on the z-stacks.
         */
        MIN,
        /**
         * A maximum projection on the z-stacks.
         */
        MAX,
        /**
         * A sum projection on the z-stacks. If the z-stack image uses the integer format with a bits
         * depth of less than 32, the projection image server will use the {@link PixelType#FLOAT32}
         * to prevent overflows, unless the z-stack image has the RGB format, in which case overflowing
         * values will be set to 255.
         */
        SUM,
        /**
         * A standard deviation projection on the z-stacks. If the image uses the integer format, the standard
         * deviation will be rounded up to the nearest integer.
         */
        STANDARD_DEVIATION,
        /**
         * A median projection on the z-stacks. If the image uses the integer format, the median may be rounded
         * up to the nearest integer.
         */
        MEDIAN
    }

    /**
     * Create an image server that converts a z-stack into a 2D image.
     *
     * @param server the input server to create the projection from
     * @param projection the type of projection to use
     * @throws IllegalArgumentException if the pixel type of the provided server is {@link PixelType#INT8}
     * or {@link PixelType#UINT32}
     */
    ZProjectedImageServer(ImageServer<BufferedImage> server, Projection projection) {
        if (List.of(PixelType.INT8, PixelType.UINT32).contains(server.getMetadata().getPixelType())) {
            throw new IllegalArgumentException(String.format(
                    "The provided pixel type %s is not supported", server.getMetadata().getPixelType()
            ));
        }

        this.server = server;
        this.projection = projection;

        PixelType pixelType;
        if (projection.equals(Projection.SUM) &&
                !server.getMetadata().isRGB() &&
                List.of(PixelType.UINT8, PixelType.INT8, PixelType.UINT16, PixelType.INT16).contains(server.getMetadata().getPixelType())) {
            pixelType = PixelType.FLOAT32;
        } else {
            pixelType = server.getMetadata().getPixelType();
        }

        this.metadata = new ImageServerMetadata.Builder(server.getMetadata())
                .pixelType(pixelType)
                .sizeZ(1)
                .build();
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return new ImageServers.ZProjectedImageServerBuilder(
                getMetadata(),
                server.getBuilder(),
                projection
        );
    }

    @Override
    protected String createID() {
        return String.format("%s with %s projection on %s", getClass().getName(), projection, server.getPath());
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        if (getMetadata().isRGB()) {
            return getRgbTile(tileRequest);
        } else {
            return getNonRgbTile(tileRequest);
        }
    }

    @Override
    public Collection<URI> getURIs() {
        return server.getURIs();
    }

    @Override
    public String getServerType() {
        return String.format("%s projection image server", projection);
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return metadata;
    }

    private BufferedImage getRgbTile(TileRequest tileRequest) throws IOException {
        int width = tileRequest.getTileWidth();
        int height = tileRequest.getTileHeight();

        List<int[]> zStacks = new ArrayList<>();
        for (int z=0; z<server.getMetadata().getSizeZ(); z++) {
            zStacks.add(server
                    .readRegion(tileRequest.getRegionRequest().updateZ(z))
                    .getRGB(0, 0, width, height, null, 0, width)
            );
        }

        BufferedImage image = createDefaultRGBImage(width, height);

        int sizeZ = server.nZSlices();
        int numberOfPixels = width * height;
        image.setRGB(
                0,
                0,
                width,
                height,
                switch (projection) {
                    case MEAN -> getRgbMeanProjection(numberOfPixels, sizeZ, zStacks);
                    case MIN -> getRgbMinProjection(numberOfPixels, sizeZ, zStacks);
                    case MAX -> getRgbMaxProjection(numberOfPixels, sizeZ, zStacks);
                    case SUM -> getRgbSumProjection(numberOfPixels, sizeZ, zStacks);
                    case STANDARD_DEVIATION -> getRgbStandardDeviationProjection(
                            numberOfPixels,
                            sizeZ,
                            zStacks,
                            getMetadata().getSizeC() == 4
                    );
                    case MEDIAN -> getRgbMedianProjection(numberOfPixels, sizeZ, zStacks);
                },
                0,
                width
        );
        
        return image;
    }

    private BufferedImage getNonRgbTile(TileRequest tileRequest) throws IOException {
        List<WritableRaster> zStacks = new ArrayList<>();
        for (int z=0; z<server.getMetadata().getSizeZ(); z++) {
            zStacks.add(server.readRegion(tileRequest.getRegionRequest().updateZ(z)).getRaster());
        }

        DataBuffer dataBuffer = createDataBuffer(zStacks);
        return new BufferedImage(
                ColorModelFactory.createColorModel(getMetadata().getPixelType(), getMetadata().getChannels()),
                WritableRaster.createWritableRaster(
                        new BandedSampleModel(dataBuffer.getDataType(), getWidth(), getHeight(), nChannels()),
                        dataBuffer,
                        null
                ),
                false,
                null
        );
    }

    private static int[] getRgbMeanProjection(int numberOfPixels, int sizeZ, List<int[]> zStacks) {
        int[] argb = new int[numberOfPixels];

        for (int i=0; i<numberOfPixels; i++) {
            int sumAlpha = 0;
            int sumRed = 0;
            int sumGreen = 0;
            int sumBlue = 0;
            for (int z=0; z<sizeZ; z++) {
                int zValue = zStacks.get(z)[i];

                sumAlpha += ColorTools.alpha(zValue);
                sumRed += ColorTools.red(zValue);
                sumGreen += ColorTools.green(zValue);
                sumBlue += ColorTools.blue(zValue);
            }
            argb[i] = (Math.round((float) sumAlpha / sizeZ) & 0xFF) << 24 |
                    (Math.round((float) sumRed / sizeZ) & 0xFF) << 16 |
                    (Math.round((float) sumGreen / sizeZ) & 0xFF) << 8 |
                    (Math.round((float) sumBlue / sizeZ) & 0xFF);
        }

        return argb;
    }

    private static int[] getRgbMinProjection(int numberOfPixels, int sizeZ, List<int[]> zStacks) {
        int[] argb = new int[numberOfPixels];

        for (int i=0; i<numberOfPixels; i++) {
            int minAlpha = Integer.MAX_VALUE;
            int minRed = Integer.MAX_VALUE;
            int minGreen = Integer.MAX_VALUE;
            int minBlue = Integer.MAX_VALUE;
            for (int z=0; z<sizeZ; z++) {
                int zValue = zStacks.get(z)[i];

                minAlpha = Math.min(minAlpha, ColorTools.alpha(zValue));
                minRed = Math.min(minRed, ColorTools.red(zValue));
                minGreen = Math.min(minGreen, ColorTools.green(zValue));
                minBlue = Math.min(minBlue, ColorTools.blue(zValue));
            }
            argb[i] = (minAlpha & 0xFF) << 24 |
                    (minRed & 0xFF) << 16 |
                    (minGreen & 0xFF) << 8 |
                    (minBlue & 0xFF);
        }

        return argb;
    }

    private static int[] getRgbMaxProjection(int numberOfPixels, int sizeZ, List<int[]> zStacks) {
        int[] argb = new int[numberOfPixels];

        for (int i=0; i<numberOfPixels; i++) {
            int maxAlpha = Integer.MIN_VALUE;
            int maxRed = Integer.MIN_VALUE;
            int maxGreen = Integer.MIN_VALUE;
            int maxBlue = Integer.MIN_VALUE;
            for (int z=0; z<sizeZ; z++) {
                int zValue = zStacks.get(z)[i];

                maxAlpha = Math.max(maxAlpha, ColorTools.alpha(zValue));
                maxRed = Math.max(maxRed, ColorTools.red(zValue));
                maxGreen = Math.max(maxGreen, ColorTools.green(zValue));
                maxBlue = Math.max(maxBlue, ColorTools.blue(zValue));
            }
            argb[i] = (maxAlpha & 0xFF) << 24 |
                    (maxRed & 0xFF) << 16 |
                    (maxGreen & 0xFF) << 8 |
                    (maxBlue & 0xFF);
        }

        return argb;
    }

    private static int[] getRgbSumProjection(int numberOfPixels, int sizeZ, List<int[]> zStacks) {
        int[] argb = new int[numberOfPixels];

        for (int i=0; i<numberOfPixels; i++) {
            int sumAlpha = 0;
            int sumRed = 0;
            int sumGreen = 0;
            int sumBlue = 0;
            for (int z=0; z<sizeZ; z++) {
                int zValue = zStacks.get(z)[i];

                sumAlpha += ColorTools.alpha(zValue);
                sumRed += ColorTools.red(zValue);
                sumGreen += ColorTools.green(zValue);
                sumBlue += ColorTools.blue(zValue);
            }
            argb[i] = (Math.min(sumAlpha, 255) & 0xFF) << 24 |
                    (Math.min(sumRed, 255) & 0xFF) << 16 |
                    (Math.min(sumGreen, 255) & 0xFF) << 8 |
                    (Math.min(sumBlue, 255) & 0xFF);
        }

        return argb;
    }

    private static int[] getRgbStandardDeviationProjection(int numberOfPixels, int sizeZ, List<int[]> zStacks, boolean computeAlpha) {
        int[] argb = new int[numberOfPixels];

        for (int i=0; i<numberOfPixels; i++) {
            int[][] argbValues = new int[sizeZ][4];
            for (int z=0; z<sizeZ; z++) {
                int zValue = zStacks.get(z)[i];

                argbValues[z][0] = ColorTools.alpha(zValue);
                argbValues[z][1] = ColorTools.red(zValue);
                argbValues[z][2] = ColorTools.green(zValue);
                argbValues[z][3] = ColorTools.blue(zValue);
            }

            int sumAlpha = 0;
            int sumRed = 0;
            int sumGreen = 0;
            int sumBlue = 0;
            for (int z=0; z<sizeZ; z++) {
                sumAlpha += argbValues[z][0];
                sumRed += argbValues[z][1];
                sumGreen += argbValues[z][2];
                sumBlue += argbValues[z][3];
            }
            float meanAlpha = (float) sumAlpha / sizeZ;
            float meanRed = (float) sumRed / sizeZ;
            float meanGreen = (float) sumGreen / sizeZ;
            float meanBlue = (float) sumBlue / sizeZ;

            float varianceAlpha = 0;
            float varianceRed = 0;
            float varianceGreen = 0;
            float varianceBlue = 0;
            for (int z = 0; z < sizeZ; z++) {
                varianceAlpha += (float) Math.pow(argbValues[z][0] - meanAlpha, 2);
                varianceRed += (float) Math.pow(argbValues[z][1] - meanRed, 2);
                varianceGreen += (float) Math.pow(argbValues[z][2] - meanGreen, 2);
                varianceBlue += (float) Math.pow(argbValues[z][3] - meanBlue, 2);
            }
            varianceAlpha *= 1f / sizeZ;
            varianceRed *= 1f / sizeZ;
            varianceGreen *= 1f / sizeZ;
            varianceBlue *= 1f / sizeZ;

            if (!computeAlpha) {
                varianceAlpha = (float) Math.pow(255, 2);
            }

            argb[i] = (Math.round((float) Math.sqrt(varianceAlpha)) & 0xFF) << 24 |
                    (Math.round((float) Math.sqrt(varianceRed)) & 0xFF) << 16 |
                    (Math.round((float) Math.sqrt(varianceGreen)) & 0xFF) << 8 |
                    (Math.round((float) Math.sqrt(varianceBlue)) & 0xFF);
        }

        return argb;
    }

    private static int[] getRgbMedianProjection(int numberOfPixels, int sizeZ, List<int[]> zStacks) {
        int[] argb = new int[numberOfPixels];

        for (int i=0; i<numberOfPixels; i++) {
            int[] zValuesAlpha = new int[sizeZ];
            int[] zValuesRed = new int[sizeZ];
            int[] zValuesGreen = new int[sizeZ];
            int[] zValuesBlue = new int[sizeZ];
            for (int z = 0; z < sizeZ; z++) {
                int zValue = zStacks.get(z)[i];

                zValuesAlpha[z] = ColorTools.alpha(zValue);
                zValuesRed[z] = ColorTools.red(zValue);
                zValuesGreen[z] = ColorTools.green(zValue);
                zValuesBlue[z] = ColorTools.blue(zValue);
            }

            Arrays.sort(zValuesAlpha);
            Arrays.sort(zValuesRed);
            Arrays.sort(zValuesGreen);
            Arrays.sort(zValuesBlue);

            int medianAlpha;
            if (zValuesAlpha.length % 2 == 0) {
                medianAlpha = Math.round(
                        (float) (zValuesAlpha[zValuesAlpha.length / 2] + zValuesAlpha[zValuesAlpha.length / 2 - 1]) / 2
                );
            } else {
                medianAlpha = zValuesAlpha[zValuesAlpha.length / 2];
            }
            int medianRed;
            if (zValuesRed.length % 2 == 0) {
                medianRed = Math.round(
                        (float) (zValuesRed[zValuesRed.length / 2] + zValuesRed[zValuesRed.length / 2 - 1]) / 2
                );
            } else {
                medianRed = zValuesRed[zValuesRed.length / 2];
            }
            int medianGreen;
            if (zValuesGreen.length % 2 == 0) {
                medianGreen = Math.round(
                        (float) (zValuesGreen[zValuesGreen.length / 2] + zValuesGreen[zValuesGreen.length / 2 - 1]) / 2
                );
            } else {
                medianGreen = zValuesGreen[zValuesGreen.length / 2];
            }
            int medianBlue;
            if (zValuesBlue.length % 2 == 0) {
                medianBlue = Math.round(
                        (float) (zValuesBlue[zValuesBlue.length / 2] + zValuesBlue[zValuesBlue.length / 2 - 1]) / 2
                );
            } else {
                medianBlue = zValuesBlue[zValuesBlue.length / 2];
            }

            argb[i] = (medianAlpha & 0xFF) << 24 |
                    (medianRed & 0xFF) << 16 |
                    (medianGreen & 0xFF) << 8 |
                    (medianBlue & 0xFF);
        }

        return argb;
    }

    private DataBuffer createDataBuffer(List<? extends Raster> rasters) {
        int width = rasters.getFirst().getWidth();
        int height = rasters.getFirst().getHeight();
        int numberOfPixels = width * height;
        int nChannels = nChannels();
        int sizeZ = server.nZSlices();
        PixelType pixelType = getMetadata().getPixelType();

        return switch (pixelType) {
            case UINT8 -> {
                byte[][] array = new byte[nChannels][numberOfPixels];
                int[][] samples = new int[sizeZ][numberOfPixels];

                for (int c = 0; c < nChannels; c++) {
                    for (int z=0;z<sizeZ; z++) {
                        rasters.get(z).getSamples(0, 0, width, height, c, samples[z]);
                    }

                    if (projection.equals(Projection.MEAN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (byte) getMean(samples, i);
                        }
                    } else if (projection.equals(Projection.MIN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (byte) getMin(samples, i);
                        }
                    } else if (projection.equals(Projection.MAX)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (byte) getMax(samples, i);
                        }
                    } else if (projection.equals(Projection.SUM)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (byte) getSum(samples, i);
                        }
                    } else if (projection.equals(Projection.STANDARD_DEVIATION)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (byte) getStandardDeviation(samples, i);
                        }
                    } else {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (byte) getMedian(samples, i);
                        }
                    }
                }

                yield new DataBufferByte(array, numberOfPixels);
            }
            case UINT16, INT16 -> {
                short[][] array = new short[nChannels][numberOfPixels];
                int[][] samples = new int[sizeZ][numberOfPixels];

                for (int c = 0; c < nChannels; c++) {
                    for (int z=0;z<sizeZ; z++) {
                        rasters.get(z).getSamples(0, 0, width, height, c, samples[z]);
                    }

                    if (projection.equals(Projection.MEAN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (short) getMean(samples, i);
                        }
                    } else if (projection.equals(Projection.MIN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (short) getMin(samples, i);
                        }
                    } else if (projection.equals(Projection.MAX)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (short) getMax(samples, i);
                        }
                    } else if (projection.equals(Projection.SUM)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (short) getSum(samples, i);
                        }
                    } else if (projection.equals(Projection.STANDARD_DEVIATION)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (short) getStandardDeviation(samples, i);
                        }
                    } else {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = (short) getMedian(samples, i);
                        }
                    }
                }

                yield pixelType == PixelType.UINT16 ?
                        new DataBufferUShort(array, numberOfPixels / 2) :
                        new DataBufferShort(array, numberOfPixels / 2);
            }
            case INT32 -> {
                int[][] array = new int[nChannels][numberOfPixels];
                int[][] samples = new int[sizeZ][numberOfPixels];

                for (int c = 0; c < nChannels; c++) {
                    for (int z=0;z<sizeZ; z++) {
                        rasters.get(z).getSamples(0, 0, width, height, c, samples[z]);
                    }

                    if (projection.equals(Projection.MEAN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = getMean(samples, i);
                        }
                    } else if (projection.equals(Projection.MIN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = getMin(samples, i);
                        }
                    } else if (projection.equals(Projection.MAX)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = getMax(samples, i);
                        }
                    } else if (projection.equals(Projection.SUM)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = getSum(samples, i);
                        }
                    } else if (projection.equals(Projection.STANDARD_DEVIATION)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = getStandardDeviation(samples, i);
                        }
                    } else {
                        for (int i=0; i<numberOfPixels; i++) {
                            array[c][i] = getMedian(samples, i);
                        }
                    }
                }

                yield new DataBufferInt(array, numberOfPixels / 4);
            }
            case FLOAT32 -> {
                float[][] array = new float[nChannels][numberOfPixels];
                float[][] samples = new float[sizeZ][numberOfPixels];

                for (int c = 0; c < nChannels; c++) {
                    for (int z=0;z<sizeZ; z++) {
                        rasters.get(z).getSamples(0, 0, width, height, c, samples[z]);
                    }

                    if (projection.equals(Projection.MEAN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            float sum = 0;
                            for (int z=0; z<sizeZ; z++) {
                                sum += samples[z][i];
                            }
                            array[c][i] = sum / sizeZ;
                        }
                    } else if (projection.equals(Projection.MIN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            float min = Float.MAX_VALUE;
                            for (int z = 0; z < sizeZ; z++) {
                                min = Math.min(min, samples[z][i]);
                            }
                            array[c][i] = min;
                        }
                    } else if (projection.equals(Projection.MAX)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            float max = Float.MIN_VALUE;
                            for (int z = 0; z < sizeZ; z++) {
                                max = Math.max(max, samples[z][i]);
                            }
                            array[c][i] = max;
                        }
                    } else if (projection.equals(Projection.SUM)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            float sum = 0;
                            for (int z = 0; z < sizeZ; z++) {
                                sum += samples[z][i];
                            }
                            array[c][i] = sum;
                        }
                    } else if (projection.equals(Projection.STANDARD_DEVIATION)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            float sum = 0;
                            for (int z = 0; z < sizeZ; z++) {
                                sum += samples[z][i];
                            }
                            float mean = sum / sizeZ;

                            float variance = 0;
                            for (int z = 0; z < sizeZ; z++) {
                                variance += (float) Math.pow(samples[z][i] - mean, 2);
                            }
                            variance *= 1f / sizeZ;

                            array[c][i] = (float) Math.sqrt(variance);
                        }
                    } else {
                        for (int i=0; i<numberOfPixels; i++) {
                            float[] zValues = new float[sizeZ];
                            for (int z = 0; z < sizeZ; z++) {
                                zValues[z] = samples[z][i];
                            }

                            Arrays.sort(zValues);

                            if (zValues.length % 2 == 0) {
                                array[c][i] = (zValues[zValues.length / 2] + zValues[zValues.length / 2 - 1]) / 2;
                            } else {
                                array[c][i] = zValues[zValues.length / 2];
                            }
                        }
                    }
                }

                yield new DataBufferFloat(array, numberOfPixels / 4);
            }
            case FLOAT64 -> {
                double[][] array = new double[nChannels][numberOfPixels];
                double[][] samples = new double[sizeZ][numberOfPixels];

                for (int c = 0; c < nChannels; c++) {
                    for (int z=0;z<sizeZ; z++) {
                        rasters.get(z).getSamples(0, 0, width, height, c, samples[z]);
                    }

                    if (projection.equals(Projection.MEAN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            double sum = 0;
                            for (int z=0; z<sizeZ; z++) {
                                sum += samples[z][i];
                            }
                            array[c][i] = sum / sizeZ;
                        }
                    } else if (projection.equals(Projection.MIN)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            double min = Double.MAX_VALUE;
                            for (int z = 0; z < sizeZ; z++) {
                                min = Math.min(min, samples[z][i]);
                            }
                            array[c][i] = min;
                        }
                    } else if (projection.equals(Projection.MAX)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            double max = Double.MIN_VALUE;
                            for (int z = 0; z < sizeZ; z++) {
                                max = Math.max(max, samples[z][i]);
                            }
                            array[c][i] = max;
                        }
                    } else if (projection.equals(Projection.SUM)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            double sum = 0;
                            for (int z = 0; z < sizeZ; z++) {
                                sum += samples[z][i];
                            }
                            array[c][i] = sum;
                        }
                    } else if (projection.equals(Projection.STANDARD_DEVIATION)) {
                        for (int i=0; i<numberOfPixels; i++) {
                            double sum = 0;
                            for (int z = 0; z < sizeZ; z++) {
                                sum += samples[z][i];
                            }
                            double mean = sum / sizeZ;

                            double variance = 0;
                            for (int z = 0; z < sizeZ; z++) {
                                variance += Math.pow(samples[z][i] - mean, 2);
                            }
                            variance *= 1d / sizeZ;

                            array[c][i] = Math.sqrt(variance);
                        }
                    } else {
                        for (int i=0; i<numberOfPixels; i++) {
                            double[] zValues = new double[sizeZ];
                            for (int z = 0; z < sizeZ; z++) {
                                zValues[z] = samples[z][i];
                            }

                            Arrays.sort(zValues);

                            if (zValues.length % 2 == 0) {
                                array[c][i] = (zValues[zValues.length / 2] + zValues[zValues.length / 2 - 1]) / 2;
                            } else {
                                array[c][i] = zValues[zValues.length / 2];
                            }
                        }
                    }
                }

                yield new DataBufferDouble(array, numberOfPixels / 8);
            }
            case INT8, UINT32 -> throw new UnsupportedOperationException(String.format(
                    "Unsupported pixel type: %s", pixelType
            ));
        };
    }

    private static int getMean(int[][] samples, int pixelIndex) {
        return Math.round((float) getSum(samples, pixelIndex) / samples.length);
    }

    private static int getMin(int[][] samples, int pixelIndex) {
        int min = Integer.MAX_VALUE;
        for (int[] sample : samples) {
            min = Math.min(min, sample[pixelIndex]);
        }
        return min;
    }

    private static int getMax(int[][] samples, int pixelIndex) {
        int max = Integer.MIN_VALUE;
        for (int[] sample : samples) {
            max = Math.max(max, sample[pixelIndex]);
        }
        return max;
    }

    private static int getSum(int[][] samples, int pixelIndex) {
        int sum = 0;
        for (int[] sample : samples) {
            sum += sample[pixelIndex];
        }
        return sum;
    }

    private static int getStandardDeviation(int[][] samples, int pixelIndex) {
        int sum = getSum(samples, pixelIndex);
        float mean = (float) sum / samples.length;

        float variance = 0;
        for (int[] sample : samples) {
            variance += (float) Math.pow(sample[pixelIndex] - mean, 2);
        }
        variance *= 1f / samples.length;

        return Math.round((float) Math.sqrt(variance));
    }

    private static int getMedian(int[][] samples, int pixelIndex) {
        int[] zValues = new int[samples.length];
        for (int z = 0; z < samples.length; z++) {
            zValues[z] = samples[z][pixelIndex];
        }

        Arrays.sort(zValues);

        if (zValues.length % 2 == 0) {
            return Math.round(
                    (float) (zValues[zValues.length / 2] + zValues[zValues.length / 2 - 1]) / 2
            );
        } else {
            return zValues[zValues.length / 2];
        }
    }
}
