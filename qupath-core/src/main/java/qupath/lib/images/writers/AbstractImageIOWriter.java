/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.images.writers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.RegionRequest;

/**
 * Abstract ImageWriter to use Java's ImageIO.
 * 
 * @author Pete Bankhead
 *
 */
abstract class AbstractImageIOWriter implements ImageWriter<BufferedImage> {

	@Override
	public Class<BufferedImage> getImageClass() {
		return BufferedImage.class;
	}
	
	@Override
	public boolean supportsT() {
		return false;
	}

	@Override
	public boolean supportsZ() {
		return false;
	}

	@Override
	public boolean supportsPyramidal() {
		return false;
	}

	@Override
	public boolean supportsPixelSize() {
		return false;
	}
	
	@Override
	public boolean suportsImageType(ImageServer<BufferedImage> server) {
		return server.isRGB() || (server.nChannels() == 1 && server.getPixelType() == PixelType.UINT8);
	}

	@Override
	public void writeImage(ImageServer<BufferedImage> server, RegionRequest request, String pathOutput) throws IOException {
		BufferedImage img = server.readBufferedImage(request);
		writeImage(img, pathOutput);
	}

	@Override
	public void writeImage(BufferedImage img, String pathOutput) throws IOException {
		File file = new File(pathOutput);
		String ext = getDefaultExtension();
		if (!ImageIO.write(img, ext, file))
			throw new IOException("Unable to write using ImageIO with extension " + ext);
	}
	
	@Override
	public void writeImage(ImageServer<BufferedImage> server, String pathOutput) throws IOException {
		writeImage(server, RegionRequest.createInstance(server), pathOutput);
	}

	@Override
	public boolean supportsRGB() {
		return true;
	}
	
}