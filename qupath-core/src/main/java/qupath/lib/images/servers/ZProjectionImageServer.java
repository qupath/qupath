package qupath.lib.images.servers;

import qupath.lib.color.ColorModelFactory;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An image server that converts a z-stack into a 2D image with a projection.
 * If the z-stack image uses the integer format with a bits depth of less than 32,
 * the projection image server will use the {@link PixelType#INT32} to prevent overflows.
 */
public class ZProjectionImageServer extends TransformingImageServer<BufferedImage> {

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
        AVERAGE,
        /**
         * A minimum projection on the z-stacks.
         */
        MIN,
        /**
         * A maximum projection on the z-stacks.
         */
        MAX,
        /**
         * A sum projection on the z-stacks.
         */
        SUM,
        /**
         * A standard deviation projection on the z-stacks. If the image uses the integer format, the standard
         * deviation will be rounded up to the nearest integer.
         */
        STANDARD_DEVIATION,
        /**
         * A median projection on the z-stacks.
         */
        MEDIAN
    }

    /**
     * Create an image server that converts a z-stack into a 2D image.
     *
     * @param server the input server to create the projection from
     * @param projection the type of projection to use
     */
    ZProjectionImageServer(ImageServer<BufferedImage> server, Projection projection) {
        super(server);

        this.projection = projection;
        this.metadata = new ImageServerMetadata.Builder(server.getMetadata())
                .pixelType(switch (server.getMetadata().getPixelType()) {
                    case UINT8, INT8, UINT16, INT16, UINT32, INT32 -> PixelType.INT32;
                    case FLOAT32 -> PixelType.FLOAT32;
                    case FLOAT64 -> PixelType.FLOAT64;
                })
                .sizeZ(1)
                .build();
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return new ImageServers.ZProjectionImageServerBuilder(
                getMetadata(),
                getWrappedServer().getBuilder(),
                projection
        );
    }

    @Override
    protected String createID() {
        return String.format("%s with %s projection on %s", getClass().getName(), projection, getWrappedServer().getPath());
    }

    @Override
    public BufferedImage readRegion(RegionRequest request) throws IOException {
        List<WritableRaster> zStacks = new ArrayList<>();
        for (int z=0; z<getWrappedServer().getMetadata().getSizeZ(); z++) {
            zStacks.add(getWrappedServer().readRegion(request.updateZ(z)).getRaster());
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

    @Override
    public String getServerType() {
        return String.format("%s projection image server", projection);
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return metadata;
    }

    private DataBuffer createDataBuffer(List<? extends Raster> rasters) {
        int width = rasters.getFirst().getWidth();
        int height = rasters.getFirst().getHeight();
        int numberOfPixels = width * height;
        int nChannels = nChannels();
        int sizeZ = getWrappedServer().nZSlices();
        PixelType pixelType = getMetadata().getPixelType();

        return switch (pixelType) {
            case UINT8, INT8, UINT16, INT16, UINT32, INT32 -> {
                int[][] array = new int[nChannels][numberOfPixels];
                int[][] samples = new int[sizeZ][numberOfPixels];

                for (int c = 0; c < nChannels; c++) {
                    for (int z=0;z<sizeZ; z++) {
                        rasters.get(z).getSamples(0, 0, width, height, c, samples[z]);
                    }

                    for (int i=0; i<numberOfPixels; i++) {
                        array[c][i] = switch (projection) {
                            case AVERAGE -> {
                                int sum = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    sum += samples[z][i];
                                }
                                yield Math.round((float) sum / sizeZ);
                            }
                            case MIN -> {
                                int min = Integer.MAX_VALUE;
                                for (int z=0; z<sizeZ; z++) {
                                    min = Math.min(min, samples[z][i]);
                                }
                                yield min;
                            }
                            case MAX -> {
                                int max = Integer.MIN_VALUE;
                                for (int z=0; z<sizeZ; z++) {
                                    max = Math.max(max, samples[z][i]);
                                }
                                yield max;
                            }
                            case SUM -> {
                                int sum = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    sum += samples[z][i];
                                }
                                yield sum;
                            }
                            case STANDARD_DEVIATION -> {
                                int sum = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    sum += samples[z][i];
                                }
                                float mean = (float) sum / sizeZ;

                                float variance = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    variance += (float) Math.pow(samples[z][i] - mean, 2);
                                }
                                variance *= 1f/sizeZ;

                                yield Math.round((float) Math.sqrt(variance));
                            }
                            case MEDIAN -> {
                                int[] zValues = new int[sizeZ];
                                for (int z=0; z<sizeZ; z++) {
                                    zValues[z] = samples[z][i];
                                }

                                Arrays.sort(zValues);

                                if (zValues.length % 2 == 0) {
                                    yield (zValues[zValues.length / 2] + zValues[zValues.length / 2 - 1]) / 2;
                                } else {
                                    yield zValues[zValues.length / 2];
                                }
                            }
                        };
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

                    for (int i=0; i<numberOfPixels; i++) {
                        array[c][i] = switch (projection) {
                            case AVERAGE -> {
                                float sum = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    sum += samples[z][i];
                                }
                                yield sum / sizeZ;
                            }
                            case MIN -> {
                                float min = Float.MAX_VALUE;
                                for (int z=0; z<sizeZ; z++) {
                                    min = Math.min(min, samples[z][i]);
                                }
                                yield min;
                            }
                            case MAX -> {
                                float max = Float.MIN_VALUE;
                                for (int z=0; z<sizeZ; z++) {
                                    max = Math.max(max, samples[z][i]);
                                }
                                yield max;
                            }
                            case SUM -> {
                                float sum = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    sum += samples[z][i];
                                }
                                yield sum;
                            }
                            case STANDARD_DEVIATION -> {
                                float sum = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    sum += samples[z][i];
                                }
                                float mean = sum / sizeZ;

                                float variance = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    variance += (float) Math.pow(samples[z][i] - mean, 2);
                                }
                                variance *= 1f/sizeZ;

                                yield (float) Math.sqrt(variance);
                            }
                            case MEDIAN -> {
                                float[] zValues = new float[sizeZ];
                                for (int z=0; z<sizeZ; z++) {
                                    zValues[z] = samples[z][i];
                                }

                                Arrays.sort(zValues);

                                if (zValues.length % 2 == 0) {
                                    yield (zValues[zValues.length / 2] + zValues[zValues.length / 2 - 1]) / 2;
                                } else {
                                    yield zValues[zValues.length / 2];
                                }
                            }
                        };
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

                    for (int i=0; i<numberOfPixels; i++) {
                        array[c][i] = switch (projection) {
                            case AVERAGE -> {
                                double sum = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    sum += samples[z][i];
                                }
                                yield sum / sizeZ;
                            }
                            case MIN -> {
                                double min = Double.MAX_VALUE;
                                for (int z=0; z<sizeZ; z++) {
                                    min = Math.min(min, samples[z][i]);
                                }
                                yield min;
                            }
                            case MAX -> {
                                double max = Double.MIN_VALUE;
                                for (int z=0; z<sizeZ; z++) {
                                    max = Math.max(max, samples[z][i]);
                                }
                                yield max;
                            }
                            case SUM -> {
                                double sum = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    sum += samples[z][i];
                                }
                                yield sum;
                            }
                            case STANDARD_DEVIATION -> {
                                double sum = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    sum += samples[z][i];
                                }
                                double mean = sum / sizeZ;

                                double variance = 0;
                                for (int z=0; z<sizeZ; z++) {
                                    variance += Math.pow(samples[z][i] - mean, 2);
                                }
                                variance *= 1d/sizeZ;

                                yield Math.sqrt(variance);
                            }
                            case MEDIAN -> {
                                double[] zValues = new double[sizeZ];
                                for (int z=0; z<sizeZ; z++) {
                                    zValues[z] = samples[z][i];
                                }

                                Arrays.sort(zValues);

                                if (zValues.length % 2 == 0) {
                                    yield (zValues[zValues.length / 2] + zValues[zValues.length / 2 - 1]) / 2;
                                } else {
                                    yield zValues[zValues.length / 2];
                                }
                            }
                        };
                    }
                }

                yield new DataBufferDouble(array, numberOfPixels / 8);
            }
        };
    }
}
