/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2017 - 2018 the QuPath contributors.
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


package qupath.lib.regions;

import qupath.lib.roi.interfaces.ROI;

/**
 * Helper class to store z-slice and time point indices, optionally along with a channel index as well.
 * <p>
 * These values are frequently required together, such as with {@link ROI ROIs} and {@link RegionRequest RegionRequests}. 
 * It is more convenient (and less error-prone) to use a single ImagePlane instance rather than passing the indices as 
 * separate integers each time they are needed.
 * 
 * @author Pete Bankhead
 *
 */
public class ImagePlane implements Comparable<ImagePlane> {
	
	private final int c, z, t;
	
	private static ImagePlane DEFAULT_PLANE = new ImagePlane(-1, 0, 0);
	
	private final static int NUM_DEFAULTS = 10;
	private final static ImagePlane[][] DEFAULTS_WITHOUT_CHANNEL = new ImagePlane[NUM_DEFAULTS][NUM_DEFAULTS];
	private final static ImagePlane[][][] DEFAULTS_WITH_CHANNEL = new ImagePlane[NUM_DEFAULTS][NUM_DEFAULTS][NUM_DEFAULTS];
	
	static {
		for (int t = 0; t < NUM_DEFAULTS; t++) {
			for (int z = 0; z < NUM_DEFAULTS; z++) {
				DEFAULTS_WITHOUT_CHANNEL[t][z] = new ImagePlane(-1, z, t);
				for (int c = 0; c < NUM_DEFAULTS; c++) {
					DEFAULTS_WITH_CHANNEL[c][t][z] = new ImagePlane(c, z, t);
				}	
			}	
		}	
	}
	
		
	private ImagePlane(final int c, final int z, final int t) {
		this.c = c;
		this.z = z;
		this.t = t;
	}
	
	/**
	 * Get the channel index. This may be -1 to indicate no channel is specified.
	 * @return
	 */
	public int getC() {
		return c;
	}
	
	/**
	 * Get the z-slice index.
	 * @return
	 */
	public int getZ() {
		return z;
	}
	
	/**
	 * Get the time point index.
	 * @return
	 */
	public int getT() {
		return t;
	}
	
	/**
	 * Compare with another ImagePlane, in the order t, z, c.
	 */
	@Override
	public int compareTo(ImagePlane o) {
		int tCompare = Integer.compare(t, o.t);
		if (tCompare != 0)
			return tCompare;
		int zCompare = Integer.compare(z, o.z);
		if (zCompare != 0)
			return zCompare;
		return Integer.compare(c, o.c);
	}
	
	
	/**
	 * Similar to {@code compareTo}, but ignoring the channel, in the order t, z.
	 * 
	 * @see #compareTo
	 */
	int compareToWithoutChannel(ImagePlane o) {
		int tCompare = Integer.compare(t, o.t);
		if (tCompare != 0)
			return tCompare;
		return Integer.compare(z, o.z);
	}
	
	@Override
	public String toString() {
		return "ImagePlane (z=" + z + ", t=" + t + ", c=" + c + ")";
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + c;
		result = prime * result + t;
		result = prime * result + z;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ImagePlane other = (ImagePlane) obj;
		if (c != other.c)
			return false;
		if (t != other.t)
			return false;
		if (z != other.z)
			return false;
		return true;
	}
	
	/**
	 * Returns an ImagePlus, where the z-slice and time point can be specified and 
	 * channel is ignored (i.e. set to -1).
	 * 
	 * @param z
	 * @param t
	 * @return
	 */
	public static ImagePlane getPlane(final int z, final int t) {
		if (z == 0 && t == 0)
			return DEFAULT_PLANE;
		return getPlaneWithChannel(-1, z, t);
	}
	
	/**
	 * Returns an ImagePlane with channel -1, z of 0 and t of 0.
	 * 
	 * <p>Use of this method is discouraged; it is appropriate only for 2D images. 
	 * 
	 * @return
	 */
	public static ImagePlane getDefaultPlane() {
		return DEFAULT_PLANE;
	}
	
	/**
	 * Returns an ImagePlane with channel, z and t set from an existing ROI.
	 * 
	 * @return
	 */
	public static ImagePlane getPlaneWithChannel(final ROI roi) {
		return getPlaneWithChannel(roi.getC(), roi.getZ(), roi.getT());
	}
	
	/**
	 * Returns an ImagePlane with channel -1, and z and t set from an existing ROI.
	 * 
	 * @return
	 */
	public static ImagePlane getPlane(final ROI roi) {
		return getPlane(roi.getZ(), roi.getT());
	}

	/**
	 * Returns an ImagePlane with channel -1 and z and t set from an existing ImageRegion.
	 * 
	 * @return
	 */
	public static ImagePlane getPlane(final ImageRegion region) {
		return getPlane(region.getZ(), region.getT());
	}

	/**
	 * Returns an ImagePlane, where the channel, z-slice and time point can be specified.
	 * 
	 * @param c
	 * @param z
	 * @param t
	 * @return
	 */
	public static ImagePlane getPlaneWithChannel(final int c, final int z, final int t) {
		// Most of the time, we'll want this
		if (c == -1 && z == 0 && t == 0)
			return DEFAULT_PLANE;
		
		if (z < 0 || t < 0)
			throw new IllegalArgumentException("Both z and t need to be >= 0, values provided are " + z + ", " + t);
		if (c < -1)
			throw new IllegalArgumentException("Channel needs to be -1 (for no channel), or >= 0, value provided is " + c);

		// Simple array lookups for common cases
		if (z < NUM_DEFAULTS && t < NUM_DEFAULTS) {
			if (c == -1) {
				return DEFAULTS_WITHOUT_CHANNEL[t][z];
			} else if (c < NUM_DEFAULTS)
				return DEFAULTS_WITH_CHANNEL[c][t][z];
		}
		return new ImagePlane(c, z, t);
	}

}
