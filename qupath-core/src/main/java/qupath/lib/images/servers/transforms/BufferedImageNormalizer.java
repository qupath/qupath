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

import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.util.Objects;

/**
 * Interface for normalizing a BufferedImage.
 * <p>
 * Implementations should be stateless, thread-safe and JSON-serializable.
 *
 * @since v0.6.0
 */
public interface BufferedImageNormalizer extends BufferedImageOp {


    @Override
    default Rectangle2D getBounds2D(BufferedImage src) {
        return new Rectangle(src.getRaster().getBounds());
    }

    @Override
    default BufferedImage createCompatibleDestImage(BufferedImage src,
                                                   ColorModel destCM) {
        // If we have an 8-bit color source, we can just use the type - using the color model can result
        // in a BufferedImage.TYPE_CUSTOM, which is not what we want.
        if (BufferedImageTools.is8bitColorType(src.getType()) &&
                (destCM == null || Objects.equals(destCM, ColorModel.getRGBdefault())))
            return new BufferedImage(src.getWidth(), src.getHeight(), src.getType());

        if (destCM == null) {
            destCM = src.getColorModel();
        }
        return new BufferedImage(
                destCM,
                destCM.createCompatibleWritableRaster(src.getWidth(), src.getHeight()),
                destCM.isAlphaPremultiplied(),
                null
        );
    }

    @Override
    default Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
        return new Point2D.Double(srcPt.getX(), srcPt.getY());
    }

    @Override
    default RenderingHints getRenderingHints() {
        return null;
    }

}
