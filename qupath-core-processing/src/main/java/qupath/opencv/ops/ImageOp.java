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

import java.util.List;

import org.bytedeco.opencv.opencv_core.Mat;

import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.Padding;

/**
 * An operation that may be applied to a {@link Mat}.
 * <p>
 * This is intended to apply simple transforms to an image (e.g. color transforms, channel extraction, filtering, normalization), 
 * which may impact the number and type of image channels - but not other aspects of the image, with the exception of removing any padding.
 * <p>
 * Operations may be chained
 */
public interface ImageOp {
	
	/**
	 * Apply operation to the image. The input may be modified (and the operation applied in-place), 
	 * therefore should be duplicated if a copy is required to be kept.
	 * <p>
	 * Note that any non-empty padding will be removed, potentially giving an output image smaller than 
	 * the input. If this is not desirable use {@link ImageOps#padAndApply(ImageOp, Mat)}.
	 * 
	 * @param input input image
	 * @return output image, which may be the same as the input image
	 * @see #getPadding()
	 * @see ImageOps#padAndApply(ImageOp, Mat, int)
	 * @see ImageOps#padAndApply(ImageOp, Mat)
	 */
	public Mat apply(Mat input);
	
	/**
	 * Get the padding requested for this operation.
	 * 
	 * The default is to return {@link Padding#empty()}.
	 * <p>
	 * Subclasses that perform neighborhood operations should override this.
	 * If the padding is non-empty, it will be removed when {@link #apply(Mat)} is called - 
	 * and so the {@link Mat} that is output will be smaller than the {@link Mat} that was input.
	 * 
	 * @return the padding requested by this operation
	 */
	public default Padding getPadding() {
		return Padding.empty();
	}
	
	/**
	 * Get appropriate channels to reflect the output of this transform.
	 * 
	 * The default is to return the input list unchanged.
	 * <p>
	 * Classes that change the meaning or number of channels should override this.
	 * In particular, the number of channels in the output list should match the 
	 * number of channels output by this transformer, given the input channels.
	 * 
	 * @param channels 
	 * 
	 * @return
	 */
	public default List<ImageChannel> getChannels(List<ImageChannel> channels) {
		return channels;
	}
	
	/**
	 * Get the output pixel type.
	 * 
	 * The default is to return the pixel type unchanged.
	 * @param inputType the input pixel type
	 * @return the output pixel type
	 */
	public default PixelType getOutputType(PixelType inputType) {
		return inputType;
	}

}