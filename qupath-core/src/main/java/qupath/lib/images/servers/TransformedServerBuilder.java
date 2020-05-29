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

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.images.servers.RotatedImageServer.Rotation;
import qupath.lib.regions.ImageRegion;

/**
 * Helper class for creating an {@link ImageServer} that applies one or more transforms to another (wrapped) {@link ImageServer}.
 * <p>
 * Note: This is an early-stage experimental class, which may well change!
 * 
 * @author Pete Bankhead
 *
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
	 * Crop an specified region based on a bounding box.
	 * @param region
	 * @return
	 */
	public TransformedServerBuilder crop(ImageRegion region) {
		server = new CroppedImageServer(server, region);
		return this;
	}
	
	/**
	 * Apply an {@link AffineTransform} to the server. 
	 * Note that the transform must be invertible, otherwise and {@link IllegalArgumentException} will be thrown.
	 * @param transform
	 * @return
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
	 * Apply color deconvolution to the brightfield image, so that deconvolved stains behave as separate channels/
	 * @param stains the stains to apply for color deconvolution
	 * @param stainNumbers the indices of the stains that should be use (an array compressing values that are 1, 2 or 3); if not specified, all 3 stains will be used.
	 * @return
	 */
	public TransformedServerBuilder deconvolveStains(ColorDeconvolutionStains stains, int...stainNumbers) {
		server = new ColorDeconvolutionImageServer(server, stains, stainNumbers);
		return this;
	}
	
	/**
	 * Rotate the image, using an increment of 90 degrees.
	 * @param rotation
	 * @return
	 */
	public TransformedServerBuilder rotate(Rotation rotation) {
		server = new RotatedImageServer(server, rotation);
		return this;
	}
	
	/**
	 * Extract specified channels for an image.
	 * @param channels indices (0-based) of channels to extract.
	 * @return
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
	 * @param names names of channels to extract.
	 * @return
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
	 * @return
	 */
	public TransformedServerBuilder maxChannelProject() {
		server = new ChannelTransformFeatureServer(server, 
				Arrays.asList(ColorTransforms.createMaximumChannelTransform()));
		return this;
	}
	
	/**
	 * Perform an average (mean) projection of the channels.
	 * @return
	 */
	public TransformedServerBuilder averageChannelProject() {
		server = new ChannelTransformFeatureServer(server, 
				Arrays.asList(ColorTransforms.createMeanChannelTransform()));
		return this;
	}
	
	/**
	 * Perform a minimum projection of the channels.
	 * @return
	 */
	public TransformedServerBuilder minChannelProject() {
		server = new ChannelTransformFeatureServer(server, 
				Arrays.asList(ColorTransforms.createMinimumChannelTransform()));
		return this;
	}
	
	/**
	 * Concatenate a collection of additional servers along the 'channels' dimension (iteration order is used).
	 * @param additionalChannels additional servers that will be applied as channels; note that these should be 
	 * 								of an appropriate type and dimension for concatenation.
	 * @return
	 */
	public TransformedServerBuilder concatChannels(Collection<ImageServer<BufferedImage>> additionalChannels) {
//		// Try to avoid wrapping more than necessary
//		if (server instanceof ConcatChannelsImageServer) {
//			var temp = new ArrayList<>(((ConcatChannelsImageServer)server).getAllServers());
//			temp.addAll(additionalChannels);
//			server = new ConcatChannelsImageServer(((ConcatChannelsImageServer)server).getWrappedServer(), temp);
//		} else
		
		List<ImageServer<BufferedImage>> allChannels = new ArrayList<>(additionalChannels);
		// Make sure that the current server is included
		if (!allChannels.contains(server))
			allChannels.add(0, server);
		server = new ConcatChannelsImageServer(server, allChannels);
		return this;
	}
	
	/**
	 * Concatenate additional servers along the 'channels' dimension.
	 * @param additionalChannels additional servers from which channels will be added; note that the servers should be 
	 * 								of an appropriate type and dimension for concatenation.
	 * @return
	 */
	public TransformedServerBuilder concatChannels(ImageServer<BufferedImage>... additionalChannels) {
		for (var temp : additionalChannels) {
			if (!BufferedImage.class.isAssignableFrom(temp.getImageClass()))
				throw new IllegalArgumentException("Unsupported ImageServer type, required BufferedImage.class but server supports " + temp.getImageClass());
		}
		return concatChannels(Arrays.asList(additionalChannels));
	}
	
//	/**
//	 * Concatenate additional server along the 'channels' dimension.
//	 * @param additionalChannel additional server from which channels will be added; note that the server should be 
//	 * 								of an appropriate type and dimension for concatenation.
//	 * @return
//	 */
//	public TransformedServerBuilder concatChannel(ImageServer<BufferedImage> additionalChannel) {
//		return concatChannels(Arrays.asList(additionalChannel));
//	}
	
	/**
	 * Get the {@link ImageServer} that applies all the requested transforms.
	 * @return
	 */
	public ImageServer<BufferedImage> build() {
		return server;
	}

}