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

package qupath.opencv.ops;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

/**
 * An {@link ImageServer} that wraps an {@link ImageData}.
 * This can be used if the server requires additional information within the {@link ImageData}, such as {@link ColorDeconvolutionStains}.
 * <p>
 * Warning: because many properties of the {@link ImageData} are mutable, yet {@link ImageServer}s generally are not (apart from their metadata), 
 * this interface should be used sparingly - and only temporarily (e.g. during a single processing operation).
 * 
 * @author Pete Bankhead
 * @param <T>
 */
public interface ImageDataServer<T> extends ImageServer<T> {
	
	/**
	 * Get the {@link ImageData} wrapped by the {@link ImageDataServer}.
	 * @return
	 */
	public ImageData<T> getImageData();
	
}