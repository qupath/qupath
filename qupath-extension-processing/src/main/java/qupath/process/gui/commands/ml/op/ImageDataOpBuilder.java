/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.process.gui.commands.ml.op;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.opencv.ops.ImageDataOp;

import java.awt.image.BufferedImage;

/**
 * Helper class capable of building (or returning) a {@link ImageDataOp}.
 * 
 * @author Pete Bankhead
 */
public interface ImageDataOpBuilder {

	/**
	 * Build an {@link ImageDataOp} for the specified image and resolution.
	 * @param imageData the image data
	 * @param resolution the resolution at which the op should be applied
	 * @return a new {@link ImageDataOp}
	 */
	ImageDataOp build(ImageData<BufferedImage> imageData, PixelCalibration resolution);

	/**
	 * Query whether the current op supports the provided image and resolution,
	 * with the current settings.
	 * <p>
	 * Note that this method may return {@code false}, but could be address by a call to
	 * <p>
	 * The default implementation always returns true; it is recommended that subclasses
	 * implement proper checks.
	 * {@link #doCustomize(ImageData)}.
	 * @param imageData the image data
	 * @param resolution the resolution to check
	 * @return true if a compatible op can be built, false otherwise
	 */
	default boolean supportsImage(ImageData<BufferedImage> imageData, PixelCalibration resolution) {
		return true;
	}

	/**
	 * Get the name of the builder.
	 * The default implementation uses the class name.
	 * @return
	 */
	default String getName() {
		String name = getClass().getSimpleName();
		if (name.isBlank())
			return "Unnamed data op builder";
		else
			return name;
	}

	/**
	 * Query if the builder can be customized for the given image.
	 * For example, customization could involve selecting different channels or features.
	 * <p>
	 * The default implementation returns false.
	 * @param imageData the image that would be used for customization
	 * @return
	 */
	default boolean canCustomize(ImageData<BufferedImage> imageData) {
		return false;
	}

	/**
	 * Request that the builder is customized.
	 * This typically means showing a dialog with appropriate user-adjustable parameters.
	 * @param imageData the current image that is used for the customization
	 * @return
	 */
	default boolean doCustomize(ImageData<BufferedImage> imageData) {
		throw new UnsupportedOperationException("Cannot customize this feature calculator!");
	}

}
