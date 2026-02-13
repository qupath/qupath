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

package qupath.lib.images.servers;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.images.servers.RotatedImageServer.Rotation;
import qupath.lib.images.servers.transforms.BufferedImageNormalizer;
import qupath.lib.images.servers.transforms.ColorDeconvolutionNormalizer;
import qupath.lib.images.servers.transforms.SubtractOffsetAndScaleNormalizer;
import qupath.lib.regions.ImageRegion;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Helper class for creating an {@link ImageServer} that applies one or more transforms to another (wrapped) {@link ImageServer}.
 * <p>
 * Note: This is an early-stage experimental class, which may well change!
 * 
 * @author Pete Bankhead
 */
public class TransformedServerBuilder {
	
	private ImageServer<BufferedImage> server;
	
	/**
	 * Create a transformed {@link ImageServer}.
	 * @param baseServer the initial server that will be transformed.
	 */
	public TransformedServerBuilder(ImageServer<BufferedImage> baseServer) {
		this.server = baseServer;
	}
	
	/**
	 * Crop a specified region based on a bounding box.
	 *
	 * @param region the region to crop
	 * @return this builder
	 */
	public TransformedServerBuilder crop(ImageRegion region) {
		server = new CroppedImageServer(server, region);
		return this;
	}

	/**
	 * Slice a specific region along the z or the t axis.
	 *
	 * @param zStart the inclusive 0-based index of the first slice to consider
	 * @param zEnd the exclusive 0-based index of the last slide to consider
	 * @param tStart the inclusive 0-based index of the first timepoint to consider
	 * @param tEnd the exclusive 0-based index of the last timepoint to consider
	 * @return this builder
	 * @throws IllegalArgumentException when a start index is greater than its corresponding end index
	 */
	public TransformedServerBuilder slice(int zStart, int zEnd, int tStart, int tEnd) {
		return slice(zStart, zEnd, 1, tStart, tEnd, 1);
	}

	/**
	 * Slice a specific region along the z or the t axis with a step.
	 *
	 * @param zStart the inclusive 0-based index of the first slice to consider
	 * @param zEnd the exclusive 0-based index of the last slide to consider
	 * @param zStep a step to indicate which slides to consider
	 * @param tStart the inclusive 0-based index of the first timepoint to consider
	 * @param tEnd the exclusive 0-based index of the last timepoint to consider
	 * @param tStep a step to indicate which timepoints to consider
	 * @return this builder
	 * @throws IllegalArgumentException when a start index is greater than its corresponding end index,
	 * or when a step is less than or equal to 0
	 */
	public TransformedServerBuilder slice(int zStart, int zEnd, int zStep, int tStart, int tEnd, int tStep) {
		server = new SlicedImageServer(server, zStart, zEnd, zStep, tStart, tEnd, tStep);
		return this;
	}

	/**
	 * Apply a mean Z-projection to the image.
	 * The projection will be calculated from all z-slices, and the resulting image will have a single z-slice.
	 *
	 * @return this builder
	 */
	public TransformedServerBuilder zProjectMean() {
		return zProject(ZProjectedImageServer.Projection.MEAN);
	}

	/**
	 * Apply a minimum Z-projection to the image.
	 * The projection will be calculated from all z-slices, and the resulting image will have a single z-slice.
	 *
	 * @return this builder
	 */
	public TransformedServerBuilder zProjectMin() {
		return zProject(ZProjectedImageServer.Projection.MIN);
	}

	/**
	 * Apply a maximum Z-projection to the image.
	 * The projection will be calculated from all z-slices, and the resulting image will have a single z-slice.
	 *
	 * @return this builder
	 */
	public TransformedServerBuilder zProjectMax() {
		return zProject(ZProjectedImageServer.Projection.MAX);
	}

	/**
	 * Apply a sum Z-projection to the image.
	 * The projection will be calculated from all z-slices, and the resulting image will have a single z-slice.
	 *
	 * @return this builder
	 */
	public TransformedServerBuilder zProjectSum() {
		return zProject(ZProjectedImageServer.Projection.SUM);
	}

	/**
	 * Apply a standard deviation Z-projection to the image.
	 * The projection will be calculated from all z-slices, and the resulting image will have a single z-slice.
	 *
	 * @return this builder
	 */
	public TransformedServerBuilder zProjectStandardDeviation() {
		return zProject(ZProjectedImageServer.Projection.STANDARD_DEVIATION);
	}

	/**
	 * Apply a median Z-projection to the image.
	 * The projection will be calculated from all z-slices, and the resulting image will have a single z-slice.
	 *
	 * @return this builder
	 */
	public TransformedServerBuilder zProjectMedian() {
		return zProject(ZProjectedImageServer.Projection.MEDIAN);
	}

	/**
	 * Apply a Z-projection.
	 * The projection will be calculated from all z-slices, and the resulting image will have a single z-slice.
	 *
	 * @param projection a type of projection to convert the multiple z-stacks into one
	 * @return this builder
	 */
	public TransformedServerBuilder zProject(ZProjectedImageServer.Projection projection) {
		server = new ZProjectedImageServer(server, projection, -1);
		return this;
	}

	/**
	 * Apply a Z-projection, either from all slices or using a running projection from adjacent slices.
	 * <p>
	 * If {@code offset} is {@link ZProjectedImageServer#NO_RUNNING_OFFSET}, this is equivalent to
	 * {@link #zProject(ZProjectedImageServer.Projection)} and the resulting image will have a single z-slice.
	 * <p>
	 * Otherwise, if {@code offset} is greater than 0, the resulting image will have the same number of z-slices
	 * as the input, where each slice is a projection using up to {@code offset} slices both above and below
	 * the current slice.
	 * This means that offset x 2 + 1 slices will be used for each projection, except at the edges where fewer slices
	 * will be used.
	 *
	 * @param projection a type of projection to convert the multiple z-stacks into one
	 * @param offset the number of slices to use for the running projection (ignored for non-running projections).
	 * @return this builder
	 */
	public TransformedServerBuilder zProject(ZProjectedImageServer.Projection projection, int offset) {
		server = new ZProjectedImageServer(server, projection, offset);
		return this;
	}

	/**
	 * Apply a running maximum Z-projection to the image.
	 *
	 * @param offset the number of slices to use for the running projection, or
	 *               {@link ZProjectedImageServer#NO_RUNNING_OFFSET} to create a single projection from all slices.
	 * @return this builder
	 * @see #zProject(ZProjectedImageServer.Projection, int)
	 */
	public TransformedServerBuilder zProjectMax(int offset) {
		return zProject(ZProjectedImageServer.Projection.MAX, offset);
	}

	/**
	 * Apply a running minimum Z-projection to the image.
	 *
	 * @param offset the number of slices to use for the running projection, or
	 *               {@link ZProjectedImageServer#NO_RUNNING_OFFSET} to create a single projection from all slices.
	 * @return this builder
	 * @see #zProject(ZProjectedImageServer.Projection, int)
	 */
	public TransformedServerBuilder zProjectMin(int offset) {
		return zProject(ZProjectedImageServer.Projection.MIN, offset);
	}

	/**
	 * Apply a running mean Z-projection to the image.
	 *
	 * @param offset the number of slices to use for the running projection, or
	 *               {@link ZProjectedImageServer#NO_RUNNING_OFFSET} to create a single projection from all slices.
	 * @return this builder
	 * @see #zProject(ZProjectedImageServer.Projection, int)
	 */
	public TransformedServerBuilder zProjectMean(int offset) {
		return zProject(ZProjectedImageServer.Projection.MEAN, offset);
	}

	/**
	 * Apply a running median Z-projection to the image.
	 *
	 * @param offset the number of slices to use for the running projection, or
	 *               {@link ZProjectedImageServer#NO_RUNNING_OFFSET} to create a single projection from all slices.
	 * @return this builder
	 * @see #zProject(ZProjectedImageServer.Projection, int)
	 */
	public TransformedServerBuilder zProjectMedian(int offset) {
		return zProject(ZProjectedImageServer.Projection.MEDIAN, offset);
	}

	/**
	 * Apply a running standard deviation Z-projection to the image.
	 *
	 * @param offset the number of slices to use for the running projection, or
	 *               {@link ZProjectedImageServer#NO_RUNNING_OFFSET} to create a single projection from all slices.
	 * @return this builder
	 * @see #zProject(ZProjectedImageServer.Projection, int)
	 */
	public TransformedServerBuilder zProjectStandardDeviation(int offset) {
		return zProject(ZProjectedImageServer.Projection.STANDARD_DEVIATION, offset);
	}
  
  /**
	 * Concatenate a list of additional servers along the 'z' dimension (iteration order is used).
	 *
	 * @param servers the servers to concatenate
	 * @return this builder
	 * @throws IllegalArgumentException if the provided images are not similar (see
	 * {@link ZConcatenatedImageServer#ZConcatenatedImageServer(List, Number)}), or if one of them have more than one z-stack
	 */
	public TransformedServerBuilder concatToZStack(List<ImageServer<BufferedImage>> servers) {
		return concatToZStack(servers, null);
	}

	/**
	 * Concatenate a list of additional servers along the 'z' dimension (iteration order is used).
	 *
	 * @param servers the servers to concatenate
	 * @param zSpacingMicrons the spacing in microns there should be between the combined images. Can be null to use the default value
	 * @return this builder
	 * @throws IllegalArgumentException if the provided images are not similar (see
	 * {@link ZConcatenatedImageServer#ZConcatenatedImageServer(List, Number)}), or if one of them have more than one z-stack
	 */
	public TransformedServerBuilder concatToZStack(List<ImageServer<BufferedImage>> servers, Number zSpacingMicrons) {
		List<ImageServer<BufferedImage>> allServers = new ArrayList<>(servers);
		if (!allServers.contains(server)) {
			allServers.addFirst(server);
		}

		server = new ZConcatenatedImageServer(allServers, zSpacingMicrons);
		return this;
  }
	
	/**
	 * Apply an {@link AffineTransform} to the server. 
	 * Note that the transform must be invertible, otherwise and {@link IllegalArgumentException} will be thrown.
	 *
	 * @param transform the transform to apply to the image
	 * @return this builder
	 */
	public TransformedServerBuilder transform(AffineTransform transform) {
		try {
			server = new AffineTransformImageServer(server, transform);
		} catch (NoninvertibleTransformException e) {
			throw new IllegalArgumentException(e);
		}
		return this;
	}
	
	/**
	 * Apply color deconvolution to the brightfield image, so that deconvolved stains behave as separate channels.
	 *
	 * @param stains the stains to apply for color deconvolution
	 * @param stainNumbers the indices of the stains that should be use (an array compressing values that are 1, 2 or 3); if not specified, all 3 stains will be used.
	 * @return this builder
	 */
	public TransformedServerBuilder deconvolveStains(ColorDeconvolutionStains stains, int...stainNumbers) {
		server = new ColorDeconvolutionImageServer(server, stains, stainNumbers);
		return this;
	}
	
	/**
	 * Rearrange the channel order of an RGB image.
	 * This is intended for cases where an image has wrongly been interpreted as RGB or BGR.
	 *
	 * @param order a text containing the letters R, G, and B in any order
	 * @return this builder
	 */
	public TransformedServerBuilder reorderRGB(String order) {
		server = new RearrangeRGBImageServer(server, order);
		return this;
	}
	
	/**
	 * Rotate the image, using an increment of 90 degrees.
	 *
	 * @param rotation the rotation to apply
	 * @return this image
	 */
	public TransformedServerBuilder rotate(Rotation rotation) {
		server = new RotatedImageServer(server, rotation);
		return this;
	}

	/**
	 * Flip the image.
	 *
	 * @param flip the type of flip to apply
	 * @return this image
	 */
	public TransformedServerBuilder flip(FlippedImageServer.Flip flip) {
		server = new FlippedImageServer(server, flip);
		return this;
	}
	
	/**
	 * Extract specified channels for an image.
	 *
	 * @param channels indices (0-based) of channels to extract.
	 * @return this builder
	 */
	public TransformedServerBuilder extractChannels(int... channels) {
		var transforms = new ArrayList<ColorTransform>();
		for (int c : channels) {
			transforms.add(ColorTransforms.createChannelExtractor(c));
		}
		server = new ChannelTransformFeatureServer(server, transforms);
		return this;
	}
	
	/**
	 * Extract specified channels for an image.
	 *
	 * @param names names of channels to extract.
	 * @return this builder
	 */
	public TransformedServerBuilder extractChannels(String... names) {
		var transforms = new ArrayList<ColorTransform>();
		for (String n : names) {
			transforms.add(ColorTransforms.createChannelExtractor(n));
		}
		server = new ChannelTransformFeatureServer(server, transforms);
		return this;
	}
	
	/**
	 * Perform a maximum projection of the channels.
	 *
	 * @return this builder
	 */
	public TransformedServerBuilder maxChannelProject() {
		server = new ChannelTransformFeatureServer(server, List.of(ColorTransforms.createMaximumChannelTransform()));
		return this;
	}
	
	/**
	 * Perform an average (mean) projection of the channels.
	 *
	 * @return this builder
	 */
	public TransformedServerBuilder averageChannelProject() {
		server = new ChannelTransformFeatureServer(server, List.of(ColorTransforms.createMeanChannelTransform()));
		return this;
	}
	
	/**
	 * Perform a minimum projection of the channels.
	 *
	 * @return this builder
	 */
	public TransformedServerBuilder minChannelProject() {
		server = new ChannelTransformFeatureServer(server, List.of(ColorTransforms.createMinimumChannelTransform()));
		return this;
	}
	
	/**
	 * Concatenate a collection of additional servers along the 'channels' dimension (iteration order is used).
	 *
	 * @param additionalChannels additional servers that will be applied as channels; note that these should be
	 *                           of an appropriate type and dimension for concatenation.
	 * @return this builder
	 */
	public TransformedServerBuilder concatChannels(Collection<ImageServer<BufferedImage>> additionalChannels) {
		List<ImageServer<BufferedImage>> allChannels = new ArrayList<>(additionalChannels);
		// Make sure that the current server is included
		if (!allChannels.contains(server))
			allChannels.add(0, server);
		server = new ConcatChannelsImageServer(server, allChannels);
		return this;
	}
	
	/**
	 * Concatenate additional servers along the 'channels' dimension.
	 *
	 * @param additionalChannels additional servers from which channels will be added; note that the servers should be
	 *                           of an appropriate type and dimension for concatenation.
	 * @return this builder
	 */
	public TransformedServerBuilder concatChannels(ImageServer<BufferedImage>... additionalChannels) {
		for (var temp : additionalChannels) {
			if (!BufferedImage.class.isAssignableFrom(temp.getImageClass()))
				throw new IllegalArgumentException("Unsupported ImageServer type, required BufferedImage.class but server supports " + temp.getImageClass());
		}
		return concatChannels(Arrays.asList(additionalChannels));
	}

	/**
	 * Subtract a constant offset from all channels, without clipping.
	 * @param offsets a single offset to subtract from all channels, or an array of offsets to subtract from each channel.
	 * @return
	 * @since v0.6.0
	 */
	public TransformedServerBuilder subtractOffset(double... offsets) {
		return normalize(SubtractOffsetAndScaleNormalizer.createSubtractOffset(offsets));
	}

	/**
	 * Subtract a constant offset from all channels, clipping the result to be &geq; 0.
	 * @param offsets a single offset to subtract from all channels, or an array of offsets to subtract from each channel.
	 * @return
	 * @since v0.6.0
	 */
	public TransformedServerBuilder subtractOffsetAndClipZero(double... offsets) {
		return normalize(SubtractOffsetAndScaleNormalizer.createSubtractOffsetAndClipZero(offsets));
	}

	/**
	 * Subtract a constant offset from all channels, then multiply the result by a scale factor.
	 * @param offsets a single offset to subtract from all channels, or an array of offsets to subtract from each channel.
	 * @param scales a single scale factor to apply to all channels, or an array of scale factors to apply to each channel.
	 * @return
	 * @since v0.6.0
	 */
	public TransformedServerBuilder subtractOffsetAndScale(double[] offsets, double[] scales) {
		return normalize(SubtractOffsetAndScaleNormalizer.create(offsets, scales));
	}

	/**
	 * Scale all channels by a constant factor.
	 * @param scales a single scale factor to apply to all channels, or an array of scale factors to apply to each channel.
	 * @return
	 * @since v0.6.0
	 */
	public TransformedServerBuilder scaleChannels(double... scales) {
		return normalize(SubtractOffsetAndScaleNormalizer.create(null, scales));
	}

	/**
	 * Normalize stains using color deconvolution and reconvolution.
	 * @param stainsInput stain vectors to apply to deconvolve the input image, which should relate to the original colors
	 * @param stainsOutput stain vectors to apply for reconvolution, determining the output colors
	 * @param scales optional array of scale factors to apply to each deconvolved channel.
	 *               A scale factor of 1.0 will leave the channel unchanged, while a scale of 0.0 will suppress the channel.
	 * @return
	 * @since v0.6.0
	 */
	public TransformedServerBuilder stainNormalize(ColorDeconvolutionStains stainsInput, ColorDeconvolutionStains stainsOutput, double... scales) {
		return normalize(ColorDeconvolutionNormalizer.create(stainsInput, stainsOutput, scales));
	}

	/**
	 * Normalize the image using the provided normalizer.
	 * @param normalizer
	 * @return
	 * @implNote To use this method to create an image that can be added to a project, the normalizers must be JSON-serializable
	 *           and registered under {@link ImageServers#getNormalizerFactory()}.
	 * @since v0.6.0
	 */
	public TransformedServerBuilder normalize(BufferedImageNormalizer normalizer) {
		this.server = new NormalizedImageServer(server, normalizer);
		return this;
	}

	/**
	 * Apply color transforms to the image.
	 *
	 * @param transforms the transforms to apply
	 * @return this builder
	 * @since v0.6.0
	 */
	public TransformedServerBuilder applyColorTransforms(Collection<? extends ColorTransform> transforms) {
		server = new ChannelTransformFeatureServer(server, new ArrayList<>(transforms));
		return this;
	}

	/**
	 * Apply color transforms to the image.
	 *
	 * @param transforms the transforms to apply
	 * @return this builder
	 * @since v0.6.0
	 */
	public TransformedServerBuilder applyColorTransforms(ColorTransform... transforms) {
		return applyColorTransforms(Arrays.asList(transforms));
	}

	/**
	 * Convert to the specified pixel type.
	 *
	 * @param pixelType the target pixel type
	 * @return this builder
	 * @since v0.6.0
	 */
	public TransformedServerBuilder convertType(PixelType pixelType) {
		server = new TypeConvertImageServer(server, pixelType);
		return this;
	}

	
	/**
	 * Get the {@link ImageServer} that applies the requested transforms sequentially.
	 */
	public ImageServer<BufferedImage> build() {
		return server;
	}

}