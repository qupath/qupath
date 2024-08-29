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

import org.junit.jupiter.api.Test;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.ColorTools;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestColorDeconvolutionNormalizer {

    private final ColorDeconvolutionStains stainsHE = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(ColorDeconvolutionStains.DefaultColorDeconvolutionStains.H_E);
    private final ColorDeconvolutionStains stainsDAB = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(ColorDeconvolutionStains.DefaultColorDeconvolutionStains.H_DAB);

    private final ColorDeconvolutionNormalizer normHE = ColorDeconvolutionNormalizer.create(
            stainsHE, stainsHE, 1, 1, 1
    );
    private final ColorDeconvolutionNormalizer normHEScaled = ColorDeconvolutionNormalizer.create(
            stainsHE, stainsHE, 1.5, 0.5, 0
    );
    private final ColorDeconvolutionNormalizer normHE2DAB = ColorDeconvolutionNormalizer.create(
            stainsHE, stainsDAB, 1, 1, 1
    );

    @Test
    public void testTypeCheck() {
        var imgRGB = createImage(BufferedImage.TYPE_INT_RGB);
        var imgARGB = createImage(BufferedImage.TYPE_INT_ARGB);
        var imgBGR = createImage(BufferedImage.TYPE_3BYTE_BGR);

        // Check our RGB images
        for (var img : Arrays.asList(imgRGB, imgARGB, imgBGR)) {
            assertTrue(similarRGB(img, doNormalize(img, normHE), 1, true));
            assertFalse(similarRGB(img, doNormalize(img, normHE2DAB), 1, true));
            assertFalse(similarRGB(img, doNormalize(img, normHEScaled), 1, true));
            // Results should be the same, even when type is different
            assertTrue(similarRGB(imgRGB, doNormalize(img, normHE), 1, false));
        }
    }

    @Test
    public void testNonRGBException() {
        // Check the non-RGB fails
        var imgIndexed = createImage(BufferedImage.TYPE_BYTE_GRAY);
        assertThrows(IllegalArgumentException.class, () -> doNormalize(imgIndexed, normHE));
    }

    @Test
    public void testBufferedOp() {
        // Check we can use a BufferedImageOp
        // (Note that TYPE_INT_ARGB fails but TYPE_INT_ARGB_PRE passes when converting in this way)
        var img = createImage(BufferedImage.TYPE_INT_RGB);

        var img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g2d = img2.createGraphics();
        g2d.drawImage(img, normHE2DAB, 0, 0);
        g2d.dispose();

        assertTrue(similarRGB(img2, doNormalize(img, normHE2DAB), 1, true));
    }


    /**
     * Check if the RGB values are similar within a specified tolerance.
     * @param img
     * @param img2
     * @param tol
     * @return
     */
    private static boolean similarRGB(BufferedImage img, BufferedImage img2, int tol, boolean checkAlpha) {
        if (Objects.equals(img, img2))
            throw new IllegalArgumentException("Images are the same!");
        var rgb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        var rgb2 = img2.getRGB(0, 0, img2.getWidth(), img2.getHeight(), null, 0, img2.getWidth());
        for (int i = 0; i < rgb.length; i++) {
            var v = rgb[i];
            var v2 = rgb2[i];
            if (v == v2)
                continue;
            else if (tol > 0) {
                if (Math.abs(ColorTools.red(v) - ColorTools.red(v2)) > tol)
                    return false;
                if (Math.abs(ColorTools.green(v) - ColorTools.green(v2)) > tol)
                    return false;
                if (Math.abs(ColorTools.blue(v) - ColorTools.blue(v2)) > tol)
                    return false;
                if (checkAlpha && Math.abs(ColorTools.alpha(v) - ColorTools.alpha(v2)) > tol)
                    return false;
            } else
                return false;
        }
        return true;
    }

    private static BufferedImage doNormalize(BufferedImage img, ColorDeconvolutionNormalizer normalizer) {
        return normalizer.filter(img, null);
    }

    private BufferedImage createImage(int type) {
        var img = new BufferedImage(64, 64, type);
        var g2d = img.createGraphics();
        g2d.setColor(Color.CYAN);
        g2d.drawRect(5, 5, img.getWidth()/2, img.getHeight()/2);
        g2d.setColor(Color.MAGENTA);
        g2d.fillOval(0, 0, img.getWidth(), img.getHeight());
        g2d.dispose();
        return img;
    }


}
