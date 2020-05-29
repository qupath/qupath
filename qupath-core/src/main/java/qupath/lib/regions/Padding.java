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

package qupath.lib.regions;

/**
 * Requested padding for a 2D image.
 * 
 * @author Pete Bankhead
 */
public class Padding {
	
	private int x1, x2, y1, y2;
	
	/**
	 * Get the first horizontal padding (left of the image), in pixels.
	 * @return
	 */
	public int getX1() {
		return x1;
	}

	/**
	 * Get the second horizontal padding (right of the image), in pixels.
	 * @return
	 */
	public int getX2() {
		return x2;
	}
	
	/**
	 * Get the total horizontal padding (sum of x1 and x2).
	 * @return
	 */
	public int getXSum() {
		return getX1() + getX2();
	}
	
	/**
	 * Get the first vertical padding (top of the image), in pixels.
	 * @return
	 */
	public int getY1() {
		return y1;
	}

	/**
	 * Get the second vertical padding (bottom of the image), in pixels.
	 * @return
	 */
	public int getY2() {
		return y2;
	}
	
	/**
	 * Get the total vertical padding (sum of x1 and x2).
	 * @return
	 */
	public int getYSum() {
		return getY1() + getY2();
	}
	
	@Override
	public String toString() {
		return String.format(
				"Padding (x=[%d, %d], y=[%d, %d])",
				getX1(), getX2(), getY1(), getY2()
				);
	}

	/**
	 * Returns true of the padding is identical on all sides (x1 == x2 == y1 == y2).
	 * @return
	 */
	public boolean isSymmetric() {
		return x1 == x2 && x2 == y1 && y1 == y2;
	}
	
	/**
	 * Returns true of the padding is zero.
	 * @return
	 */
	public boolean isEmpty() {
		return x1 == 0 && isSymmetric();
	}
	
	/**
	 * Add this padding to another. This padding is unchanged.
	 * @param padding
	 * @return a {@link Padding} where the padding on all sides is the sum of the corresponding padding of both objects.
	 */
	public Padding add(Padding padding) {
		if (isEmpty())
			return padding;
		else if (padding.isEmpty())
			return this;
		return getPadding(
				x1 + padding.x1,
				x2 + padding.x2,
				y1 + padding.y1,
				y2 + padding.y2
				);
	}
	
	/**
	 * Add another padding from this one. This padding is unchanged.
	 * @param padding
	 * @return a {@link Padding} where the padding on all sides is result of subtracting another padding from this one.
	 *         Note that all values from the padding being subtracted must be &le; the corresponding values of this padding.
	 */
	public Padding subtract(Padding padding) {
		if (isEmpty())
			return padding;
		else if (padding.isEmpty())
			return this;
		return getPadding(
				x1 - padding.x1,
				x2 - padding.x2,
				y1 - padding.y1,
				y2 - padding.y2
				);
	}
	
	/**
	 * Compare two paddings, and take the larger padding value on all sides.
	 * @param padding
	 * @return
	 */
	public Padding max(Padding padding) {
		if (isEmpty())
			return padding;
		else if (padding.isEmpty())
			return this;
		return getPadding(
				Math.max(x1, padding.x1),
				Math.max(x2, padding.x2),
				Math.max(y1, padding.y1),
				Math.max(y2, padding.y2)
				);
	}
	
	private Padding(int x1, int x2, int y1, int y2) {
		this.x1 = x1;
		this.x2 = x2;
		this.y1 = y1;
		this.y2 = y2;
		if (x1 < 0 || x2 < 0 || y1 < 0 || y2 < 0)
			throw new IllegalArgumentException("Padding must be >= 0! Requested " + toString());
	}
	
	private Padding(int pad) {
		this(pad, pad, pad, pad);
	}
	
	private static Padding[] symmetric = new Padding[64];
	
	static {
		for (int i = 0; i < symmetric.length; i++)
			symmetric[i] = new Padding(i);
	}
	
	/**
	 * Get a padding object with 'pad' pixels on all sides.
	 * @param pad the padding for x1, x2, y1 and y2
	 * @return
	 */
	public static Padding symmetric(int pad) {
		if (pad <= symmetric.length)
			return symmetric[pad];
		return new Padding(pad);
	}
	
	/**
	 * Get an padding object 'x' pixels to the left and right, and 'y' pixels above and below.
	 * @param x the padding for x1 and x2
	 * @param y the padding for y1 and y2
	 * @return
	 */
	public static Padding getPadding(int x, int y) {
		if (x == y)
			return symmetric(x);
		return getPadding(x, x, y ,y);
	}
	
	/**
	 * Get an empty padding object (0 on all sides).
	 * @return
	 */
	public static Padding empty() {
		return symmetric[0];
	}
	
	/**
	 * Get a padding object that may have different padding on each side.
	 * @param x1
	 * @param x2
	 * @param y1
	 * @param y2
	 * @return
	 */
	public static Padding getPadding(int x1, int x2, int y1, int y2) {
		if (x1 == x2 && x1 == y1 && x1 == y2)
			return symmetric(x1);
		return new Padding(x1, x2, y1, y2);
	}

}