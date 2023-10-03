/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.experimental.pixels;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * A functional interface for supplying a mask corresponding to an image.
 * @param <S> the type of image
 * @param <T> the type of mask
 * @since v0.5.0
 */
@FunctionalInterface
public interface MaskSupplier<S, T> {

    /**
     * Get a mask corresponding to the image, depicting a specified ROI.
     * In contrast to an {@link ImageSupplier}, this may be given a ROI that differs from the main ROI of the parent
     * object stored in the {@link Parameters}. This is useful for cases where different masks might be required,
     * e.g. to represent a cell nucleus or cytoplasm.
     * <p>
     * When the mask is an image, it should be the same size as the result of {@code parameters.getImage()}.
     * It is valid for this method to call {@code parameters.getImage()} to ensure this.
     * @param parameters
     * @param roi
     * @return
     * @see ImageSupplier
     */
    T getMask(Parameters<S, T> parameters, ROI roi) throws IOException;


    /**
     * Create a image supplier that returns a buffered image.
     * @return
     */
    static MaskSupplier<BufferedImage, BufferedImage> createBufferedImageMaskSupplier() {
        return (parameters, roi) -> {
            var image = parameters.getImage();
            var request = parameters.getRegionRequest();
            return BufferedImageTools.createROIMask(image.getWidth(), image.getHeight(), roi, request);
        };
    }

}
