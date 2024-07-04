/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.servers.transforms;

import qupath.lib.common.GeneralTools;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;

/**
 * Normalizes the pixel values of a BufferedImage by subtracting and offset and multiplying by a scale factor.
 * <p>
 * An expected use is to subtract a constant background value in a fluorescence image, with optional channel
 * rescaling.
 * <p>
 * Note that the result is necessarily clipped to the range of the output data type, and non-integer values
 * are rounded if necessary.
 *
 * @since v0.6.0
 */
public class SubtractOffsetAndScaleNormalizer implements BufferedImageNormalizer {

    private final double[] offsets;
    private final double[] scales;
    private final double minClip;
    private final double maxClip;

    private SubtractOffsetAndScaleNormalizer(double[] offsets, double[] scales, double minClip, double maxClip) {
        this.scales = scales == null ? null : scales.clone();
        this.offsets = offsets == null ? null : offsets.clone();
        this.minClip = minClip;
        this.maxClip = maxClip;
    }

    /**
     * Create a normalizer that scales each channel by a constant.
     * @param scales
     * @return
     */
    public static SubtractOffsetAndScaleNormalizer createScaled(double... scales) {
        return createWithClipRange(null, scales, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    /**
     * Create a normalizer that subtracts a constant from each channel, without clipping.
     * @param offsets
     * @return
     */
    public static SubtractOffsetAndScaleNormalizer createSubtractOffset(double... offsets) {
        return createWithClipRange(offsets, null, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    /**
     * Create a normalizer that subtracts a constant from each channel, clipping the lower bound to 0.
     * @param offsets
     * @return
     */
    public static SubtractOffsetAndScaleNormalizer createSubtractOffsetAndClipZero(double... offsets) {
        return createWithClipRange(offsets, null, 0, Double.POSITIVE_INFINITY);
    }

    /**
     * Create a normalizer that subtracts a constant from each channel, then multiples the result by a scale factor.
     * The result is not clipped.
     * @param offsets
     * @param scales
     * @return
     */
    public static SubtractOffsetAndScaleNormalizer create(double[] offsets, double[] scales) {
        return createWithClipRange(offsets, scales, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    /**
     * Create a normalizer that subtracts a constant from each channel, then multiples the result by a scale factor -
     * clipping the result to a defined range.
     * @param offsets
     * @param scales
     * @param minClip
     * @param maxClip
     * @return
     */
    public static SubtractOffsetAndScaleNormalizer createWithClipRange(double[] offsets, double[] scales, double minClip, double maxClip) {
        return new SubtractOffsetAndScaleNormalizer(offsets, scales, minClip, maxClip);
    }

    @Override
    public BufferedImage filter(BufferedImage img, BufferedImage output) {
        if (output == null)
            output = createCompatibleDestImage(img, img.getColorModel());
        var raster = img.getRaster();
        int w = img.getWidth();
        int h = img.getHeight();
        double[] pixels = null;
        // Clip to the range of the output data type
        var outputRaster = output.getRaster();
        double minClip = getMinClip(outputRaster.getDataBuffer().getDataType());
        double maxClip = getMaxClip(outputRaster.getDataBuffer().getDataType());
        boolean doRounding = isIntegerType(outputRaster.getDataBuffer().getDataType());
        for (int b = 0; b < raster.getNumBands(); b++) {
            pixels = raster.getSamples(0, 0, w, h, b, pixels);
            double offset = offsetForChannel(b);
            double scale = scaleForChannel(b);
            if (offset != 0 || scale != 1) {
                for (int i = 0; i < pixels.length; i++) {
                    double val = GeneralTools.clipValue((pixels[i] - offset) * scale, minClip, maxClip);
                    if (doRounding)
                        val = Math.round(val);
                    pixels[i] = val;
                }
            }
            outputRaster.setSamples(0, 0, w, h, b, pixels);
        }
        return output;
    }

    boolean isIntegerType(int dataType) {
        switch (dataType) {
            case DataBuffer.TYPE_BYTE:
            case DataBuffer.TYPE_SHORT:
            case DataBuffer.TYPE_USHORT:
            case DataBuffer.TYPE_INT:
                return true;
            default:
                return false;
        }
    }

    double getMinClip(int dataType) {
        switch (dataType) {
            case DataBuffer.TYPE_BYTE:
            case DataBuffer.TYPE_USHORT:
                return Math.max(0, minClip);
            case DataBuffer.TYPE_INT:
                return Math.max(Integer.MIN_VALUE, minClip);
            case DataBuffer.TYPE_SHORT:
                return Math.max(Short.MIN_VALUE, minClip);
            default:
                return minClip;
        }
    }

    double getMaxClip(int dataType) {
        switch (dataType) {
            case DataBuffer.TYPE_BYTE:
                return Math.min(255, maxClip);
            case DataBuffer.TYPE_USHORT:
                return Math.min(65535, maxClip);
            case DataBuffer.TYPE_INT:
                return Math.min(Integer.MAX_VALUE, maxClip);
            case DataBuffer.TYPE_SHORT:
                return Math.min(Short.MAX_VALUE, maxClip);
            default:
                return maxClip;
        }
    }

    private double scaleForChannel(int channel) {
        if (scales == null)
            return 1.0;
        if (channel < scales.length)
            return scales[channel];
        else if (scales.length == 1)
            return scales[0];
        else
            throw new IllegalArgumentException("Channel index out of bounds: " + channel);
    }

    private double offsetForChannel(int channel) {
        if (offsets == null)
            return 0.0;
        if (channel < offsets.length)
            return offsets[channel];
        else if (offsets.length == 1)
            return offsets[0];
        else
            throw new IllegalArgumentException("Channel index out of bounds: " + channel);
    }

}
