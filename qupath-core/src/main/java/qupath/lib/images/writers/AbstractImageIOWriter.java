/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.images.writers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

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
	public boolean supportsImageType(ImageServer<BufferedImage> server) {
		return server.isRGB() || (server.nChannels() == 1 && server.getPixelType() == PixelType.UINT8);
	}
	
	@Override
	public boolean supportsRGB() {
		return true;
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
	public void writeImage(ImageServer<BufferedImage> server, RegionRequest request, OutputStream stream) throws IOException {
		BufferedImage img = server.readBufferedImage(request);
		writeImage(img, stream);
	}

	@Override
	public void writeImage(BufferedImage img, OutputStream stream) throws IOException {
		String ext = getDefaultExtension();
		if (!ImageIO.write(img, ext, stream))
			throw new IOException("Unable to write using ImageIO with extension " + ext);
	}
	
	@Override
	public void writeImage(ImageServer<BufferedImage> server, OutputStream stream) throws IOException {
		writeImage(server, RegionRequest.createInstance(server), stream);
	}
	
}