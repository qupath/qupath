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

package qupath.lib.analysis.algorithms;

/**
 * Implementation of a SimpleImage backed by an array of floats.
 * 
 * @author Pete Bankhead
 *
 */
public class FloatArraySimpleImage implements SimpleModifiableImage {

	private float[] data;
	private int width;
	private int height;
	
	public FloatArraySimpleImage(float[] data, int width, int height) {
		this.data = data;
		this.width = width;
		this.height = height;
	}
	
	public FloatArraySimpleImage(int width, int height) {
		this.data = new float[width * height];
		this.width = width;
		this.height = height;
	}
	
	@Override
	public float getValue(int x, int y) {
		return data[y * width + x];
	}

	@Override
	public void setValue(int x, int y, float val) {
		data[y * width + x] = val;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

}
