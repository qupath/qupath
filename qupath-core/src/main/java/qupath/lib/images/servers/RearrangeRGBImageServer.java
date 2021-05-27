/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.regions.RegionRequest;

/**
 * Simple image server to swap the red and blue channels of an RGB image.
 * This is intended for use whenever an image has erroneously been read as RGB or BGR.
 * 
 * @author Pete Bankhead
 */
public class RearrangeRGBImageServer extends TransformingImageServer<BufferedImage> {
	
	private static List<String> VALID_ORDERS = Arrays.asList(
			"RGB", "RBG", "GRB", "GBR", "BRG", "BGR"
			);
	
	private String order = "RGB";

	protected RearrangeRGBImageServer(ImageServer<BufferedImage> server, String order) {
		super(server);
		if (!server.isRGB())
			throw new IllegalArgumentException("Red and blue channels can only be swapped for an RGB image server!");
		this.order = order;
		if (!VALID_ORDERS.contains(order))
			throw new IllegalArgumentException("Unsupported order " + order);
	}
	
	@Override
	public BufferedImage readBufferedImage(RegionRequest request) throws IOException {
		var img = super.readBufferedImage(request);
		if (img == null)
			return null;
		BufferedImageTools.swapRGBOrder(img, order);
		return img;
	}

	@Override
	public String getServerType() {
		return super.getWrappedServer().getServerType() + " [reorder " + order + "]";
	}

	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return new ImageServers.ReorderRGBServerBuilder(getMetadata(), getWrappedServer().getBuilder(), order);
	}

	@Override
	protected String createID() {
		return getWrappedServer().getPath() + " [reorder " + order + "]";
	}
	
	/**
	 * Get a ServerBuilder that swaps red and blue channels for another (RGB) server.
	 * @param builder
	 * @param order
	 * @return
	 */
	public static ServerBuilder<BufferedImage> getSwapRedBlueBuilder(ServerBuilder<BufferedImage> builder, String order) {
		if (!VALID_ORDERS.contains(order))
			throw new IllegalArgumentException("Unsupported order " + order);
		return new ImageServers.ReorderRGBServerBuilder(null, builder, order);		
	}

}
