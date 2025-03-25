/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

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
import java.util.stream.IntStream;

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
     * Constant indicating that a full z-projection should be made using all slices, rather than a running z-projection
     * made across a group of adjacent slices.
     */
    public static final int NO_RUNNING_OFFSET = -1;

    /**
     * Optional offset to apply to calculate a grouped z-projection.
     * This is a projection between z-runningOffset and z+runningOffset slices (inclusive, 2 x runningOffset + 1 slices in total).
     */
    private final int runningOffset;

    /**
     * Create an image server that converts a z-stack into a 2D image using a z-projection.
     *
     * @param server the input server to create the projection from
     * @param projection the type of projection to use
     * @throws IllegalArgumentException if the pixel type of the provided server is {@link PixelType#INT8}
     * or {@link PixelType#UINT32}
     */
    ZProjectedImageServer(ImageServer<BufferedImage> server, Projection projection) {
        this(server, projection, NO_RUNNING_OFFSET);
    }

    /**
     * Create an image server that converts a z-stack into a 2D image using a full z-projection (all z-slices)
     * or a running z-projection (adjacent slices).
     *
     * @param server the input server to create the projection from
     * @param projection the type of projection to use
     * @param runningOffset optional offset to apply to calculate a z-projection using adjacent slices.
     *                      If the offset is &lt; 0, the projection will be made using only the current slice.
     *                      If the offset is 0, the projection will be made using {@code runningOffset} slices above
     *                      and below the current slice (2 x runningOffset + 1 slices in total, truncated to the
     *                      available number of slices).
     *                      An offset of 0 is not supported, as this would indicate a projection from one slice only.
     * @throws IllegalArgumentException if the pixel type of the provided server is {@link PixelType#INT8}
     * or {@link PixelType#UINT32}, or if the runningOffset is 0
     */
    ZProjectedImageServer(ImageServer<BufferedImage> server, Projection projection, int runningOffset) {
        if (List.of(PixelType.INT8, PixelType.UINT32).contains(server.getMetadata().getPixelType())) {
            throw new IllegalArgumentException(String.format(
                    "The provided pixel type %s is not supported", server.getMetadata().getPixelType()
            ));
        }

        this.server = server;
        this.projection = projection;
        this.runningOffset = runningOffset;

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
                .sizeZ(runningOffset >= 0 ? server.nZSlices() : 1)
                .build();
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return new ImageServers.ZProjectedImageServerBuilder(
                getMetadata(),
                server.getBuilder(),
                projection,
                runningOffset
        );
    }

    @Override
    protected String createID() {
        return String.format("%s with %s projection on %s (group offset=%d)", getClass().getName(),
                projection, server.getPath(), runningOffset);
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
        // Updating the path is important to avoid inadvertently pulling the wrong tile from the cache
        var region = tileRequest.getRegionRequest().updatePath(server.getPath());
        for (int z=0; z<server.getMetadata().getSizeZ(); z++) {
            zStacks.add(server
                    .readRegion(region.updateZ(z))
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

    private Projector getProjector(Projection projection) {
        return switch (projection) {
            case MEAN -> new MeanProjector();
            case MIN -> new MinProjector();
            case MAX -> new MaxProjector();
            case SUM -> new SumProjector();
            case STANDARD_DEVIATION -> new StdDevProjector();
            case MEDIAN -> new MedianProjector();
        };
    }

    private BufferedImage getNonRgbTile(TileRequest tileRequest) throws IOException {
        List<WritableRaster> zStacks = new ArrayList<>();

        // Figure out which slices we need to read
        int zStart = 0;
        int zEnd = server.nZSlices();
        if (runningOffset >= 0) {
            zStart = Math.max(0, tileRequest.getZ()-runningOffset);
            zEnd = Math.min(server.nZSlices(), tileRequest.getZ()+runningOffset+1);
        }

        var dataBuffer = createDataBuffer(tileRequest, zStart, zEnd);
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

    private DataBuffer createDataBuffer(TileRequest tile, int zStart, int zEnd) throws IOException{
        int width = tile.getTileWidth();
        int height = tile.getTileHeight();
        int numberOfPixels = width * height;
        int nChannels = nChannels();
        PixelType pixelType = getMetadata().getPixelType();

        DataBuffer dataBuffer = switch (pixelType) {
            case UINT8 -> new DataBufferByte(numberOfPixels, nChannels);
            case UINT16 -> new DataBufferUShort(numberOfPixels, nChannels);
            case INT16 -> new DataBufferShort(numberOfPixels, nChannels);
            case INT32 -> new DataBufferInt(numberOfPixels, nChannels);
            case FLOAT32 -> new DataBufferFloat(numberOfPixels, nChannels);
            case FLOAT64 -> new DataBufferDouble(numberOfPixels, nChannels);
            default -> throw new UnsupportedOperationException("Unsupported pixel type: " + pixelType);
        };
        // Updating the path is important to avoid inadvertently pulling the wrong tile from the cache
        var region = tile.getRegionRequest().updatePath(server.getPath());

        // Loop through z-slices and updated projectors for each channel.
        // We use this approach so that we don't have to store all tiles in memory, which could be expensive for
        // large, untiled z-stacks.
        double[] samples = new double[numberOfPixels];
        Projector[] projectors = IntStream.range(0, nChannels).mapToObj(c -> getProjector(projection)).toArray(Projector[]::new);
        for (int z=zStart; z<zEnd; z++) {
            var raster = server.readRegion(region.updateZ(z)).getRaster();
            for (int c = 0; c < nChannels; c++) {
                var projector = projectors[c];
                raster.getSamples(0, 0, width, height, c, samples);
                projector.accumulate(samples);
            }
        }

        // Loop through projectors to set pixels
        for (int c = 0; c < nChannels; c++) {
            var projector = projectors[c];
            if (pixelType.isFloatingPoint()) {
                for (int i = 0; i < numberOfPixels; i++) {
                    dataBuffer.setElemDouble(c, i, projector.getResult(i));
                }
            } else {
                for (int i = 0; i < numberOfPixels; i++) {
                    dataBuffer.setElemDouble(c, i, Math.round(projector.getResult(i)));
                }
            }
        }
        return dataBuffer;
    }


    private interface Projector {

        void accumulate(double[] values);

        double getResult(int i);

    }

    private static class SumProjector implements Projector {

        private double[] results;

        @Override
        public void accumulate(double[] values) {
            if (results == null) {
                results = values.clone();
            } else {
                for (int i=0; i<values.length; i++) {
                    results[i] += values[i];
                }
            }
        }

        @Override
        public double getResult(int i) {
            return results[i];
        }

    }

    private static class MeanProjector extends SumProjector {

        private int n = 0;

        @Override
        public void accumulate(double[] values) {
            n++;
            super.accumulate(values);
        }

        @Override
        public double getResult(int i) {
            return super.getResult(i) / n;
        }

    }

    private static class MinProjector implements Projector {

        private double[] results;

        @Override
        public void accumulate(double[] values) {
            if (results == null) {
                results = values.clone();
            } else {
                for (int i=0; i<values.length; i++) {
                    results[i] = Math.min(results[i], values[i]);
                }
            }
        }

        @Override
        public double getResult(int i) {
            return results[i];
        }

    }

    private static class MaxProjector implements Projector {

        private double[] results;

        @Override
        public void accumulate(double[] values) {
            if (results == null) {
                results = values.clone();
            } else {
                for (int i=0; i<values.length; i++) {
                    results[i] = Math.max(results[i], values[i]);
                }
            }
        }

        @Override
        public double getResult(int i) {
            return results[i];
        }

    }

    private static class MedianProjector implements Projector {

        private final List<double[]> allValues = new ArrayList<>();
        private double[] cache;

        @Override
        public void accumulate(double[] values) {
            allValues.add(values.clone());
        }

        @Override
        public double getResult(int i) {
            int n = allValues.size();
            if (cache == null || cache.length != n) {
                cache = new double[n];
            }
            for (int j=0; j<n; j++) {
                cache[j] = allValues.get(j)[i];
            }
            Arrays.sort(cache);
            if (n % 2 == 0) {
                return (cache[n / 2] + cache[n / 2 - 1]) / 2;
            } else {
                return cache[n / 2];
            }
        }

    }

    private static class StdDevProjector extends SumProjector {

        private double[] sum;
        private double[] sumOfSquares;
        private int n;

        @Override
        public void accumulate(double[] values) {
            n++;
            if (sum == null) {
                sum = values.clone();
                sumOfSquares = new double[values.length];
                for (int i=0; i<values.length; i++) {
                    double v = values[i];
                    sumOfSquares[i] += v*v;
                }
            } else {
                for (int i=0; i<values.length; i++) {
                    double v = values[i];
                    sum[i] += v;
                    sumOfSquares[i] += v*v;
                }
            }
        }

        @Override
        public double getResult(int i) {
            double mean = sum[i] / n;
            return Math.sqrt(sumOfSquares[i] / n - mean*mean);
        }

    }

}
