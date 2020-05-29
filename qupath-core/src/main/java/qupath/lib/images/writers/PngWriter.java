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
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;

/**
 * ImageWriter implementation to write PNG images using ImageIO.
 * 
 * @author Pete Bankhead
 *
 */
public class PngWriter extends AbstractImageIOWriter {

	@Override
	public String getName() {
		return "PNG";
	}

	@Override
	public Collection<String> getExtensions() {
		return Collections.singleton("png");
	}
	
	@Override
	public void writeImage(BufferedImage img, String pathOutput) throws IOException {
		// If writing an indexed image with ImageIO, we need to drop the alpha channel
		if (img.getTransparency() == BufferedImage.BITMASK && img.getColorModel() instanceof IndexColorModel) {
			var cm = (IndexColorModel)img.getColorModel();
			int n = cm.getMapSize();
			byte[] reds = new byte[n];
			byte[] greens = new byte[n];
			byte[] blues = new byte[n];
			cm.getReds(reds);
			cm.getGreens(greens);
			cm.getBlues(blues);
			var cmNew = new IndexColorModel(
					cm.getPixelSize(), n, reds, greens, blues);
			img = new BufferedImage(cmNew, img.getRaster(), cmNew.isAlphaPremultiplied(), null);
		}
		super.writeImage(img, pathOutput);
	}

	@Override
	public String getDetails() {
		return "Write image as PNG using ImageIO (lossless compression). Only supports 8-bit single-channel or RGB images, and loses image metadata (e.g. pixel calibration).";
	}
	
	@Override
	public boolean supportsImageType(ImageServer<BufferedImage> server) {
		return super.supportsImageType(server) || isIndexedColor(server);
	}
	
	private static boolean isIndexedColor(ImageServer<BufferedImage> server) {
		return server.getMetadata().getChannelType() == ChannelType.CLASSIFICATION;
	}

}