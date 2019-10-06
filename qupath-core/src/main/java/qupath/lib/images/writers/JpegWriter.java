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

import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * ImageWriter implementation to write JPEG images using ImageIO.
 * 
 * @author Pete Bankhead
 *
 */
public class JpegWriter extends AbstractImageIOWriter {

	@Override
	public String getName() {
		return "JPEG";
	}

	@Override
	public String getDetails() {
		return "Write image as JPEG using ImageIO (lossy compression). Only supports 8-bit single-channel or RGB images, and loses image metadata (e.g. pixel calibration).";
	}
	
	@Override
	public void writeImage(BufferedImage img, String pathOutput) throws IOException {
		// If the image isn't opaque, make it so
		if (img.getTransparency() != Transparency.OPAQUE) {
			BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = img2.createGraphics();
			g2d.drawImage(img, 0, 0, null);
			g2d.dispose();
			img = img2;
		}
		super.writeImage(img, pathOutput);
	}

	@Override
	public Collection<String> getExtensions() {
		return Arrays.asList("jpg", "jpeg");
	}

}