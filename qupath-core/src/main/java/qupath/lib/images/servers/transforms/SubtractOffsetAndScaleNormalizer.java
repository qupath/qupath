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

/**
 * Normalizes the pixel values of a BufferedImage by subtracting and offset and multiplying by a scale factor.
 * <p>
 * An expected use is to subtract a constant background value in a fluorescence image, with optional channel
 * rescaling.
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
    public BufferedImage apply(BufferedImage img) {
        var raster = img.getRaster();
        int w = img.getWidth();
        int h = img.getHeight();
        double[] pixels = null;
        for (int b = 0; b < raster.getNumBands(); b++) {
            pixels = raster.getSamples(0, 0, w, h, b, pixels);
            double offset = offsetForChannel(b);
            double scale = scaleForChannel(b);
            if (offset != 0 || scale != 1) {
                for (int i = 0; i < pixels.length; i++) {
                    pixels[i] = GeneralTools.clipValue((pixels[i] - offset) * scale, minClip, maxClip);
                }
                raster.setSamples(0, 0, w, h, b, pixels);
            }
        }
        return img;
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
            return 1.0;
        if (channel < offsets.length)
            return offsets[channel];
        else if (offsets.length == 1)
            return offsets[0];
        else
            throw new IllegalArgumentException("Channel index out of bounds: " + channel);
    }

}
