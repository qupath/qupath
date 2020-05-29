/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.classifiers.pixel;

import java.awt.image.BufferedImage;
import java.io.IOException;

import qupath.lib.images.ImageData;
import qupath.lib.regions.RegionRequest;

/**
 * Interface defining a pixel classifier.
 * <p>
 * Pixel classifiers can be applied directly to an image, typically using colors and textures.
 * 
 * @author Pete Bankhead
 *
 */
public interface PixelClassifier {
	
	/**
	 * Query whether the classifier supports a particular image.
	 * It may not if the number of channels (for example) is incompatible.
	 * @param imageData
	 * @return
	 */
	public boolean supportsImage(ImageData<BufferedImage> imageData);

    /**
     * Apply pixel classifier to a specified region of an image.
     * <p>
     * An {@code ImageData} and {@code RegionRequest} are supplied, rather 
     * than a {@code BufferedImage} directly, because there may be a need to adapt 
     * to the image resolution and/or incorporate padding to reduce boundary effects.
     * <p>
     * There is no guarantee that the returned {@code BufferedImage} will be the same size 
     * as the input region (after downsampling), but rather that it should contain the full 
     * classification information for the specified region.
     * <p>
     * Practically, this means that there may be fewer pixels in the output because the classification 
     * inherently involves downsampling.
     *
     * @param server
     * @param request
     * @return a {@code BufferedImage} representing the pixel classifications as separate bands.
     * @throws IOException if unable to read pixels from {@code server}
     */
    public BufferedImage applyClassification(ImageData<BufferedImage> server, RegionRequest request) throws IOException;

    /**
     * Get metadata that describes how the classifier should be called, and the kind of output it provides.
     *
     * @return
     */
    public PixelClassifierMetadata getMetadata();

}