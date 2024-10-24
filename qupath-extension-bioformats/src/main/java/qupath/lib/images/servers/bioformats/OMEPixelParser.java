/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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


package qupath.lib.images.servers.bioformats;

import loci.common.DataTools;
import qupath.lib.images.servers.PixelType;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;


/**
 * This class can parse raw bytes into a {@link BufferedImage}.
 * It is intended for use with non-RGB images; {@link loci.formats.gui.AWTImageTools} can be used for RGB images.
 *
 * @implNote INT8 and UINT32 images are currently not supported.
 */
public class OMEPixelParser {

    private final boolean isInterleaved;
    private final PixelType pixelType;
    private final ByteOrder byteOrder;
    private final boolean normalizeFloats;
    private final int effectiveNChannels;
    private final int[] samplesPerPixel;

    private OMEPixelParser(Builder builder) {
        this.isInterleaved = builder.isInterleaved;
        this.pixelType = builder.pixelType;
        this.byteOrder = builder.byteOrder;
        this.normalizeFloats = builder.normalizeFloats;
        this.effectiveNChannels = builder.effectiveNChannels;
        this.samplesPerPixel = builder.samplesPerPixel;
    }

    /**
     * Creates a {@link BufferedImage} from a 2-dimensional byte array.
     *
     * @param pixels  the byte array containing the pixel values. The first dimension of the
     *                array refers to the channel and the second dimension refers to the position
     *                of the pixel
     * @param width  the width in pixels of the image
     * @param height  the height in pixels of the image
     * @param nChannels  the number of channels of this image
     * @param colorModel  the color model to use when creating the image
     * @return the corresponding image
     */
    public BufferedImage parse(byte[][] pixels, int width, int height, int nChannels, ColorModel colorModel) {
        DataBuffer dataBuffer;
        WritableRaster raster;
        if (pixelType == PixelType.UINT8 && colorModel.equals(ColorModel.getRGBdefault()) && (pixels.length == 3 || pixels.length == 4)) {
            // Special case where we need to convert UINT8 RGB(A) to packed ARGB
            var argb = bytesToPackedARGB(pixels);
            var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, width, height, argb, 0, width);
            return img;
        } else {
            dataBuffer = bytesToDataBuffer(pixels);
            var sampleModel = createSampleModel(width, height, nChannels, dataBuffer.getDataType());
            raster = WritableRaster.createWritableRaster(sampleModel, dataBuffer, null);
            return new BufferedImage(
                    colorModel,
                    raster,
                    false,
                    null
            );
        }
    }

    /**
     * Convert a byte array to ARGB pixel values.
     * @param pixels
     * @return
     */
    private static int[] bytesToPackedARGB(byte[][] pixels) {
        // Special case for RGB images - we want a packed byte array
        int n = pixels[0].length;
        int[] argb = new int[n];
        if (pixels.length == 3) {
            // We assume RGB (no alpha)
            for (int i = 0; i < n; i++) {
                argb[i] = (255 << 24) | (pixels[0][i] & 0xFF) << 16 | (pixels[1][i] & 0xFF) << 8 | (pixels[2][i] & 0xFF);
            }
        } else if (pixels.length == 4) {
            // We assume alpha is last (RGBA)
            for (int i = 0; i < n; i++) {
                argb[i] = (pixels[3][i] & 0xFF) << 24 | (pixels[0][i] & 0xFF) << 16 | (pixels[1][i] & 0xFF) << 8 | (pixels[2][i] & 0xFF);
            }
        }
        return argb;
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

    /**
     * Builder for instances of {@link OMEPixelParser}.
     */
    public static class Builder {

        private boolean isInterleaved = false;
        private PixelType pixelType;
        private ByteOrder byteOrder;
        private boolean normalizeFloats;
        private int effectiveNChannels;
        private int[] samplesPerPixel;

        /**
         * @param isInterleaved  whether pixel values are interleaved
         * @return the current builder
         */
        public Builder isInterleaved(boolean isInterleaved) {
            this.isInterleaved = isInterleaved;
            return this;
        }

        /**
         * @param pixelType  the bit-depth of the image pixels
         * @return the current builder
         */
        public Builder pixelType(PixelType pixelType) {
            this.pixelType = pixelType;
            return this;
        }

        /**
         * @param byteOrder  the byte order of each pixel
         * @return the current builder
         */
        public Builder byteOrder(ByteOrder byteOrder) {
            this.byteOrder = byteOrder;
            return this;
        }

        /**
         * @param effectiveNChannels  the effective size of the C dimension of the image. This is not always
         *                            the number of channels, for example RGB values can be stored in one effective channel
         * @return the current builder
         */
        public Builder effectiveNChannels(int effectiveNChannels) {
            this.effectiveNChannels = effectiveNChannels;
            return this;
        }

        /**
         * @param normalizeFloats  whether float data should be normalized
         * @return the current builder
         */
        public Builder normalizeFloats(boolean normalizeFloats) {
            this.normalizeFloats = normalizeFloats;
            return this;
        }

        /**
         * @param samplesPerPixel  an array containing the number of samples per pixel for each channel.
         *                         For example, samplesPerPixel[i] should contain the number of samples
         *                         per pixel for channel i
         * @return the current builder
         */
        public Builder samplesPerPixel(int[] samplesPerPixel) {
            this.samplesPerPixel = samplesPerPixel;
            return this;
        }

        /**
         * Creates a new {@link OMEPixelParser} instance.
         *
         * @return the current builder
         */
        public OMEPixelParser build() {
            return new OMEPixelParser(this);
        }
    }
}
