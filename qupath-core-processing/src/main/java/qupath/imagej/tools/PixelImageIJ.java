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

package qupath.imagej.tools;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.lib.analysis.images.SimpleModifiableImage;

/**
 * Very simple wrapper that allows some non-ImageJ-oriented QuPath commands to access ImageProcessor pixel values.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelImageIJ implements SimpleModifiableImage {
	
	private ImageProcessor ip;
	
	/**
	 * Constructor.
	 * @param ip ImageProcessor to wrap
	 */
	public PixelImageIJ(ImageProcessor ip) {
		this.ip = ip;
	}

	@Override
	public float getValue(int x, int y) {
		return ip.getf(x, y);
	}

	@Override
	public void setValue(int x, int y, float val) {
		ip.setf(x, y, val);
	}

	@Override
	public int getWidth() {
		return ip.getWidth();
	}

	@Override
	public int getHeight() {
		return ip.getHeight();
	}

	@Override
	public float[] getArray(boolean direct) {
		if (ip instanceof FloatProcessor) {
			float[] pixels = (float[])ip.getPixels();
			if (direct)
				return pixels;
			return pixels.clone();
		}
		int n = ip.getWidth() * ip.getHeight();
		float[] pixels = new float[n];
		for (int i = 0; i < n; i++) {
			pixels[i] = ip.getf(i);
		}
		return pixels;
	}

}
