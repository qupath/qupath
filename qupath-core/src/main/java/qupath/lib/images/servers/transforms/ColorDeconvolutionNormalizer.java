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

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;

import java.awt.image.BufferedImage;

/**
 * Normalizes an image by applying color deconvolution to RGB input, rescaling intensities, and using color
 * 'reconvolution' to generate a new RGB image.
 *
 * @since v0.6.0
 */
public class ColorDeconvolutionNormalizer implements BufferedImageNormalizer {

    private final ColorDeconvolutionStains stainsInput;
    private final ColorDeconvolutionStains stainsOutput;
    private final double[] scales;

    private ColorDeconvolutionNormalizer(ColorDeconvolutionStains stainsInput, ColorDeconvolutionStains stainsOutput, double[] scales) {
        this.stainsInput = stainsInput;
        this.stainsOutput = stainsOutput;
        this.scales = scales == null || scales.length == 0 ? null : scales.clone();
    }

    /**
     * Create a normalizer using color deconvolution and reconvolution.
     * @param stainsInput stain vectors to apply to deconvolve the input image, which should relate to the original colors
     * @param stainsOutput stain vectors to apply for reconvolution, determining the output colors
     * @param scales optional array of scale factors to apply to each deconvolved channel.
     *               A scale factor of 1.0 will leave the channel unchanged, while a scale of 0.0 will suppress the channel.
     * @return
     */
    public static ColorDeconvolutionNormalizer create(ColorDeconvolutionStains stainsInput, ColorDeconvolutionStains stainsOutput, double... scales) {
        return new ColorDeconvolutionNormalizer(stainsInput, stainsOutput, scales);
    }

    @Override
    public BufferedImage filter(BufferedImage img, BufferedImage output) {
        if (output == null)
            output = createCompatibleDestImage(img, null);

        if (!BufferedImageTools.is8bitColorType(img.getType()) || !BufferedImageTools.is8bitColorType(output.getType()))
            throw new IllegalArgumentException("Color deconvolution normalizer only supports 8-bit RGB inputs and outputs");

        var rgb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        ColorTransformer.colorDeconvolveReconvolveRGBArray(
                rgb,
                stainsInput,
                stainsOutput,
                rgb,
                scales
        );
        output.setRGB(0, 0, img.getWidth(), img.getHeight(), rgb, 0, img.getWidth());
        return output;
    }
}
