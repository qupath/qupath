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

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * A functional interface for supplying an image region for processing.
 * @param <S> the type of image
 * @since v0.5.0
 */
@FunctionalInterface
public interface ImageSupplier<S> {

    /**
     * Get the image to process.
     * This should correspond to the region request and image server stored in the parameters.
     * <p>
     * This has access to the parameters, but must not call {@code parameters.getImage()} or
     * {@code parameters.getMask()} to avoid the risk of infinite recursion.
     *
     * @param parameters
     * @return an image based upon the stored parameters
     */
    S getImage(Parameters<S, ?> parameters) throws IOException;

    /**
     * Create a image supplier that returns a buffered image.
     * @return
     */
    static ImageSupplier<BufferedImage> createBufferedImageSupplier() {
        return (parameters) -> parameters.getServer().readRegion(parameters.getRegionRequest());
    }

}
