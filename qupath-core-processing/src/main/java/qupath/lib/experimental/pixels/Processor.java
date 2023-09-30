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

import java.io.IOException;

/**
 * A generic processor designed to work with {@link Parameters} and provide an output.
 * <p>
 * The intended use is to apply a local image processing task, such as thresholding, to a region of an image
 * corresponding to an object.
 * @param <S>
 * @param <T>
 * @param <U>
 * @since v0.5.0
 */
@FunctionalInterface
public interface Processor<S, T, U> {

    /**
     * Processing method.
     * @param params the parameters; these can be queried to access the parent object, image data, masks etc.
     * @return the output
     * @see OutputHandler
     * @see ImageSupplier
     * @see MaskSupplier
     * @throws IOException if there is a problem reading the image
     */
    U process(Parameters<S, T> params) throws IOException;

}
