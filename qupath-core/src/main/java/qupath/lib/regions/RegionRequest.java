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

package qupath.lib.regions;

import java.text.DecimalFormat;

import qupath.lib.roi.interfaces.ROI;

/**
 * Class for defining an image region that can also be used to request these region from a PathImageServer.
 * In addition to the information contained within an ImageRegion, also contains the path to the image
 * (and optionally an additional image name stored within it) and a downsample value.
 * 
 * @author Pete Bankhead
 *
 */
public class RegionRequest extends ImageRegion {
	
	private static DecimalFormat df = new DecimalFormat("#.##");
	
	private final String path;
	
	private final double downsample;
	
	@Override
	public String toString() {
		String prefix = path;
		return prefix + ": x=" + getX() + ", y=" + getY() + ", w=" + getWidth() + ", h=" + getHeight() + ", z=" + getZ() + ", t=" + getT() + ", downsample=" + df.format(downsample);
	}
	
	
	RegionRequest(String path, double downsample, int x, int y, int width, int height, int z, int t) {
		super(x, y, width, height, z, t);
		this.path = path.intern();
		this.downsample = downsample;
	}

	public static RegionRequest createInstance(String path, double downsample, ROI pathROI) {
		return createInstance(path, downsample, ImageRegion.createInstance(pathROI));
	}

	public static RegionRequest createInstance(String path, double downsample, ImageRegion region) {
		return new RegionRequest(path, downsample, region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getZ(), region.getT());
	}
	
	public static RegionRequest createInstance(String path, double downsample, int x, int y, int width, int height, int z, int t) {
		return new RegionRequest(path, downsample, x, y, width, height, z, t);
	}
	
	public static RegionRequest createInstance(String path, double downsample, int x, int y, int width, int height) {
		return createInstance(path, downsample, x, y, width, height, 0, 0);
	}
	
	/**
	 * Returns true if the region specified by this request overlaps with that of another request.
	 * The test includes insuring that they refer to the same image.
	 * 
	 * @param request
	 * @return
	 */
	public boolean overlapsRequest(RegionRequest request) {
		if (path.equals(request.getPath()))
			return path.equals(request.getPath()) &&
					super.intersects(request);
		return false;
	}


	public String getPath() {
		return path;
	}
	
	public double getDownsample() {
		return downsample;
	}


	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		long temp;
		temp = Double.doubleToLongBits(downsample);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		return result;
	}


	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		RegionRequest other = (RegionRequest) obj;
		if (Double.doubleToLongBits(downsample) != Double
				.doubleToLongBits(other.downsample))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}
	
}
