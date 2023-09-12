package qupath.lib.images.servers.bioformats;

import loci.common.DataTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;

import java.awt.image.*;
import java.nio.*;
import java.util.List;

public class OMEPixelParser {

    private final boolean isInterleaved;
    private final List<ImageChannel> channels;
    private final PixelType pixelType;
    private final ByteOrder byteOrder;
    private final boolean normalizeFloats;
    private final int effectiveNChannels;
    private final int[] samplesPerPixel;

    private OMEPixelParser(Builder builder) {
        this.isInterleaved = builder.isInterleaved;
        this.channels = builder.channels;
        this.pixelType = builder.pixelType;
        this.byteOrder = builder.byteOrder;
        this.normalizeFloats = builder.normalizeFloats;
        this.effectiveNChannels = builder.effectiveNChannels;
        this.samplesPerPixel = builder.samplesPerPixel;
    }

    public BufferedImage parse(byte[][] pixels, int width, int height, int nChannels) {
        DataBuffer dataBuffer = bytesToDataBuffer(pixels);
        SampleModel sampleModel = createSampleModel(width, height, nChannels, dataBuffer.getDataType());
        WritableRaster raster = WritableRaster.createWritableRaster(sampleModel, dataBuffer, null);

        return new BufferedImage(
                createColorModel(nChannels),
                raster,
                false,
                null
        );
    }

    private DataBuffer bytesToDataBuffer(byte[][] bytes) {
        return switch (pixelType) {
            case UINT8 -> new DataBufferByte(bytes, bytes[0].length);
            case UINT16, INT16 -> {
                short[][] array = new short[bytes.length][];
                for (int i = 0; i < bytes.length; i++) {
                    ShortBuffer buffer = ByteBuffer.wrap(bytes[i]).order(byteOrder).asShortBuffer();
                    array[i] = new short[buffer.limit()];
                    buffer.get(array[i]);
                }
                yield pixelType == PixelType.UINT16 ?
                        new DataBufferUShort(array, bytes[0].length / 2) :
                        new DataBufferShort(array, bytes[0].length / 2);
            }
            case INT32 -> {
                int[][] array = new int[bytes.length][];
                for (int i = 0; i < bytes.length; i++) {
                    IntBuffer buffer = ByteBuffer.wrap(bytes[i]).order(byteOrder).asIntBuffer();
                    array[i] = new int[buffer.limit()];
                    buffer.get(array[i]);
                }
                yield new DataBufferInt(array, bytes[0].length / 4);
            }
            case FLOAT32 -> {
                float[][] array = new float[bytes.length][];
                for (int i = 0; i < bytes.length; i++) {
                    FloatBuffer buffer = ByteBuffer.wrap(bytes[i]).order(byteOrder).asFloatBuffer();
                    array[i] = new float[buffer.limit()];
                    buffer.get(array[i]);

                    if (normalizeFloats) {
                        array[i] = DataTools.normalizeFloats(array[i]);
                    }
                }
                yield new DataBufferFloat(array, bytes[0].length / 4);
            }
            case FLOAT64 -> {
                double[][] array = new double[bytes.length][];
                for (int i = 0; i < bytes.length; i++) {
                    DoubleBuffer buffer = ByteBuffer.wrap(bytes[i]).order(byteOrder).asDoubleBuffer();
                    array[i] = new double[buffer.limit()];
                    buffer.get(array[i]);
                    if (normalizeFloats) {
                        array[i] = DataTools.normalizeDoubles(array[i]);
                    }
                }
                yield new DataBufferDouble(array, bytes[0].length / 8);
            }
            case INT8, UINT32 -> throw new UnsupportedOperationException("Unsupported pixel type " + pixelType);
        };
    }

    private SampleModel createSampleModel(int width, int height, int nChannels, int datatype) {
        if (effectiveNChannels == 1 && nChannels > 1) {
            // Handle channels stored in the same plane
            int[] offsets = new int[nChannels];
            if (isInterleaved) {
                for (int b = 0; b < nChannels; b++)
                    offsets[b] = b;
                return new PixelInterleavedSampleModel(datatype, width, height, nChannels, nChannels*width, offsets);
            } else {
                for (int b = 0; b < nChannels; b++)
                    offsets[b] = b * width * height;
                return new ComponentSampleModel(datatype, width, height, 1, width, offsets);
            }
        } else if (nChannels > effectiveNChannels) {
            // Handle multiple bands, but still interleaved
            // See https://forum.image.sc/t/qupath-cant-open-polarized-light-scans/65951
            int[] offsets = new int[nChannels];
            int[] bandInds = new int[nChannels];
            int ind = 0;

            for (int cInd = 0; cInd < samplesPerPixel.length; cInd++) {
                int nSamples = samplesPerPixel[cInd];
                for (int s = 0; s < nSamples; s++) {
                    bandInds[ind] = cInd;
                    if (isInterleaved) {
                        offsets[ind] = s;
                    } else {
                        offsets[ind] = s * width * height;
                    }
                    ind++;
                }
            }
            // TODO: Check this! It works for the only test image I have... (2 channels with 3 samples each)
            // I would guess it fails if pixelStride does not equal nSamples, and if nSamples is different for different 'channels' -
            // but I don't know if this occurs in practice.
            // If it does, I don't see a way to use a ComponentSampleModel... which could complicate things quite a bit
            int pixelStride = nChannels / effectiveNChannels;
            int scanlineStride = pixelStride*width;
            return new ComponentSampleModel(datatype, width, height, pixelStride, scanlineStride, bandInds, offsets);
        } else {
            // Merge channels on different planes
            return new BandedSampleModel(datatype, width, height, nChannels);
        }
    }

    private ColorModel createColorModel(int nChannels) {
        if (nChannels == 3 && pixelType == PixelType.UINT8 && channels.equals(ImageChannel.getDefaultRGBChannels())) {
            return ColorModel.getRGBdefault();
        } else {
            return ColorModelFactory.createColorModel(pixelType, channels);
        }
    }

    public static class Builder {

        private boolean isInterleaved = false;
        private List<ImageChannel> channels;
        private PixelType pixelType;
        private ByteOrder byteOrder;
        private boolean normalizeFloats;
        private int effectiveNChannels;
        private int[] samplesPerPixel;

        public Builder isInterleaved(boolean isInterleaved) {
            this.isInterleaved = isInterleaved;
            return this;
        }

        public Builder channels(List<ImageChannel> channels) {
            this.channels = channels;
            return this;
        }

        public Builder pixelType(PixelType pixelType) {
            this.pixelType = pixelType;
            return this;
        }

        public Builder byteOrder(ByteOrder byteOrder) {
            this.byteOrder = byteOrder;
            return this;
        }

        public Builder effectiveNChannels(int effectiveNChannels) {
            this.effectiveNChannels = effectiveNChannels;
            return this;
        }

        public Builder normalizeFloats(boolean normalizeFloats) {
            this.normalizeFloats = normalizeFloats;
            return this;
        }

        public Builder samplesPerPixel(int[] samplesPerPixel) {
            this.samplesPerPixel = samplesPerPixel;
            return this;
        }

        public OMEPixelParser build() {
            return new OMEPixelParser(this);
        }
    }
}
