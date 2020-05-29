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

package qupath.lib.regions;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.roi.interfaces.ROI;

/**
 * Class for defining an image region that can also be used to request these region from an {@link ImageServer}.
 * <p>
 * In addition to the information contained within an {@link ImageRegion}, also contains the path to the image
 * (and optionally an additional image name stored within it) and a downsample value.
 * 
 * @author Pete Bankhead
 *
 */
public class RegionRequest extends ImageRegion {
	
	private static DecimalFormat df = new DecimalFormat("#.##");
	
	private static Map<String, String> strings = new HashMap<>();
	
	private final String path;
	
	private final double downsample;
	
	@Override
	public String toString() {
		String prefix = path;
		return prefix + ": x=" + getX() + ", y=" + getY() + ", w=" + getWidth() + ", h=" + getHeight() + ", z=" + getZ() + ", t=" + getT() + ", downsample=" + df.format(downsample);
	}
	
	
	RegionRequest(String path, double downsample, int x, int y, int width, int height, int z, int t) {
		super(x, y, width, height, z, t);
		// Using String.intern() can be a performance issue, so use a map instead
		String interned = strings.putIfAbsent(path, path);
		if (interned == null)
			interned = path;
		this.path = path;
		this.downsample = downsample;
	}
	
	/**
	 * Create a request for the full width and height of an {@link ImageServer}, for the default plane (first z-slice, time point) 
	 * and first resolution level downsample (usually 1, but not always).
	 * @param server
	 * @return
	 */
	public static RegionRequest createInstance(ImageServer<?> server) {
		return createInstance(server, server.getDownsampleForResolution(0));
	}
	
	/**
	 * Create a request for the full width and height of an {@link ImageServer}, for the default plane (first z-slice, time point).
	 * @param server
	 * @param downsample
	 * @return
	 */
	public static RegionRequest createInstance(ImageServer<?> server, double downsample) {
		return createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight());
	}
	
	/**
	 * Create requests for the full width and height of an {@link ImageServer}, for all planes (z-slices and time points).
	 * @param server
	 * @param downsample
	 * @return
	 */
	public static List<RegionRequest> createAllRequests(ImageServer<?> server, double downsample) {
		List<RegionRequest> requests = new ArrayList<>();
		for (int t = 0; t < server.nTimepoints(); t++) {
			for (int z = 0; z < server.nZSlices(); z++) {
				requests.add(RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight(), z, t));
			}			
		}
		return requests;
	}

	/**
	 * Create a request that contains the pixels of the specified {@link ROI}.
	 * This is calculated using the {@link ROI} bounding box.
	 * @param path
	 * @param downsample
	 * @param roi
	 * @return
	 */
	public static RegionRequest createInstance(String path, double downsample, ROI roi) {
		return createInstance(path, downsample, ImageRegion.createInstance(roi));
	}
	
	/**
	 * Create a request that contains the pixels of the specified {@link ROI}s.
	 * This is calculated using the {@link ROI} bounding boxes.
	 * @param path
	 * @param downsample
	 * @param rois
	 * @return
	 * @throws IllegalArgumentException if the {@link ROI}s do not all fall on the same {@link ImagePlane}.
	 */
	public static RegionRequest createInstance(String path, double downsample, Collection<? extends ROI> rois) {
		return createInstance(path, downsample, ImageRegion.createInstance(rois));
	}
	
	/**
	 * Create a request that matches another request but with a different path.
	 * @param path
	 * @param request
	 * @return
	 */
	public static RegionRequest createInstance(String path, RegionRequest request) {
		return createInstance(path, request.getDownsample(), request);
	}

	/**
	 * Create a request corresponding to a specified {@link ImageRegion}.
	 * This may also be used to construct a request based on an existing request, but changing either the path or downsample.
	 * @param path
	 * @param downsample
	 * @param region
	 * @return
	 */
	public static RegionRequest createInstance(String path, double downsample, ImageRegion region) {
		return new RegionRequest(path, downsample, region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getZ(), region.getT());
	}
	
	/**
	 * Create a request for a region specified in terms of its bounding box, z-slice and time point.
	 * @param path
	 * @param downsample
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param z
	 * @param t
	 * @return
	 */
	public static RegionRequest createInstance(String path, double downsample, int x, int y, int width, int height, int z, int t) {
		return new RegionRequest(path, downsample, x, y, width, height, z, t);
	}
	
	/**
	 * Create a request for a region specified in terms of its bounding box, using the first z-slice and time point.
	 * @param path
	 * @param downsample
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @return
	 */
	public static RegionRequest createInstance(String path, double downsample, int x, int y, int width, int height) {
		return createInstance(path, downsample, x, y, width, height, 0, 0);
	}
	
	/**
	 * Returns true if the region specified by this request overlaps with that of another request.
	 * The test includes insuring that they refer to the same image by checking the paths are the same.
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

	/**
	 * Get the requested ImageServer path, used as an identifier.
	 * @return
	 * 
	 * @see ImageServer#getPath()
	 */
	public String getPath() {
		return path;
	}
	
	/**
	 * Get the requested downsample factor, defining the resolution at which pixels should be returned.
	 * @return
	 */
	public double getDownsample() {
		return downsample;
	}
	
	/**
	 * Intersect to the specified 2D region, ignoring z and t.
	 * @param region the region defining the x, y, width and height of the maximum permitted bounding box.
	 * @return the clipped {@link RegionRequest}, which may be this if no clipping is required.
	 */
	public RegionRequest intersect2D(ImageRegion region) {
		if (getX() >= region.getX() && getY() >= region.getY() && getMaxY() <= region.getMaxY() && getMaxX() <= region.getMaxX())
			return this;
		int x = Math.max(getMinX(), region.getMinX());
		int y = Math.max(getMinY(), region.getMinY());
		int x2 = Math.min(getMaxX(), region.getMaxX());
		int y2 = Math.min(getMaxY(), region.getMaxY());
		return RegionRequest.createInstance(
				getPath(),
				getDownsample(),
				x, y, Math.max(x2-x, 0), Math.max(y2-y, 0),
				getZ(), getT());
	}
	
	/**
	 * Intersect to the specified 2D region.
	 * @param x x-coordinate of the second region's bounding box
	 * @param y y-coordinate of the second region's bounding box
	 * @param width width of the second region's bounding box
	 * @param height height of the second region's bounding box
	 * @return the clipped {@link RegionRequest}, which may be this if no clipping is required.
	 */
	public RegionRequest intersect2D(int x, int y, int width, int height) {
		return intersect2D(ImageRegion.createInstance(x, y, width, height, getZ(), getT()));
	}
	
	/**
	 * Create a {@link RegionRequest} equivalent to this one with the updated z value.
	 * @param z requested z position
	 * @return {@link RegionRequest} with the specified z value (may be this object unchanged).
	 */
	public RegionRequest updateZ(int z) {
		if (getZ() == z)
			return this;
		return RegionRequest.createInstance(getPath(), getDownsample(), getX(), getY(), getWidth(), getHeight(), z, getT());
	}
	
	/**
	 * Create a {@link RegionRequest} equivalent to this one with the updated t value.
	 * @param t requested t position
	 * @return {@link RegionRequest} with the specified t value (may be this object unchanged).
	 */
	public RegionRequest updateT(int t) {
		if (getT() == t)
			return this;
		return RegionRequest.createInstance(getPath(), getDownsample(), getX(), getY(), getWidth(), getHeight(), getZ(), t);
	}
	
	/**
	 * Create a {@link RegionRequest} equivalent to this one with the updated downsample value.
	 * @param downsample requested downsample position
	 * @return {@link RegionRequest} with the specified downsample value (may be this object unchanged).
	 */
	public RegionRequest updateDownsample(double downsample) {
		if (getDownsample() == downsample)
			return this;
		return RegionRequest.createInstance(getPath(), downsample, getX(), getY(), getWidth(), getHeight(), getZ(), getT());
	}
	
	/**
	 * Create a {@link RegionRequest} equivalent to this one with the updated path.
	 * @param path requested path position
	 * @return {@link RegionRequest} with the specified path value (may be this object unchanged).
	 */
	public RegionRequest updatePath(String path) {
		Objects.requireNonNull(path);
		if (getPath().equals(path))
			return this;
		return RegionRequest.createInstance(path, getDownsample(), getX(), getY(), getWidth(), getHeight(), getZ(), getT());
	}
	
	/**
	 * Add symmetric padding to the x and y dimensions of a request.
	 * @param xPad padding to add along the x dimension; the width will be adjusted by {@code xPad * 2}
	 * @param yPad padding to add along the y dimension; the height will be adjusted by {@code yPad * 2}
	 * @return {@link RegionRequest} with the specified padding (may be this object unchanged if the padding is zero).
	 */
	public RegionRequest pad2D(int xPad, int yPad) {
		if (xPad == 0 && yPad == 0)
			return this;
		return RegionRequest.createInstance(getPath(), getDownsample(), getX()-xPad, getY()-yPad, getWidth()+xPad*2, getHeight()+yPad*2, getZ(), getT());
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
