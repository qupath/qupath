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

package qupath.lib.color;

import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * A { @ColorModel } for times when you don't really want a { @ColorModel }.
 * <p>
 * Just a placeholder, pixels will be displayed as black, regardless of values.
 * 
 * @author Pete Bankhead
 *
 */
class DummyColorModel extends ColorModel {

	DummyColorModel(final int nBits) {
		super(nBits);
	}

	@Override
	public int getRed(int pixel) {
		return 0;
	}

	@Override
	public int getGreen(int pixel) {
		return 0;
	}

	@Override
	public int getBlue(int pixel) {
		return 0;
	}

	@Override
	public int getAlpha(int pixel) {
		return 0;
	}

	@Override
	public boolean isCompatibleRaster(Raster raster) {
		// We accept everything...
		return true;
	}

	@Override
	public ColorModel coerceData(WritableRaster raster, boolean isAlphaPremultiplied) {
		// Don't do anything
		return this;
	}

}