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

package qupath.lib.images.servers;

import java.util.Collection;
import java.util.LinkedHashSet;

import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * A wrapper for a {@link RegionRequest}, useful to precisely specify image tiles at a particular resolution.
 * <p>
 * Why?
 * <p>
 * Because downsamples can be defined with floating point precision, and are not always unambiguous when 
 * calculated as the ratios of pyramid level dimensions (i.e. different apparent horizontal and vertical scaling), 
 * a RegionRequest is too fuzzy a way to refer to a specific rectangle of pixels from a specific pyramid level. 
 * Rounding errors can easily occur, and different image readers might respond differently to the same request.
 * <p>
 * Consequently, TileRequests provide a means to reproducibly define coordinates at pyramid levels and not only 
 * the full resolution image space.  They wrap a RegionRequest, because this is still used for caching purposes. 
 */
public class TileRequest {
			
	private final int level;
	private final ImageRegion tileRegion;
	private final RegionRequest request;

	/**
	 * Request a collection of <i>all</i> tiles that this server must be capable of returning. 
	 * The default implementation provides a dense grid of all expected tiles across all resolutions, 
	 * time points, z-slices and x,y coordinates.
	 * 
	 * @return
	 */
	static Collection<TileRequest> getAllTileRequests(ImageServer<?> server) {
		var set = new LinkedHashSet<TileRequest>();
		
		var tileWidth = server.getMetadata().getPreferredTileWidth();
		var tileHeight = server.getMetadata().getPreferredTileHeight();
		var downsamples = server.getPreferredDownsamples();
		
		for (int level = 0; level < downsamples.length; level++) {
			var resolutionLevel = server.getMetadata().getLevel(level);
			int height = resolutionLevel.getHeight();
			int width = resolutionLevel.getWidth();
			for (int t = 0; t < server.nTimepoints(); t++) {
				for (int z = 0; z < server.nZSlices(); z++) {
					for (int y = 0; y < height; y += tileHeight) {
						int th = tileHeight;
						if (y + th > height)
							th = height - y;
						
						for (int x = 0; x < width; x += tileWidth) {
							int tw = tileWidth;
							if (x + tw > width)
								tw = width - x;
							
							var tile = TileRequest.createInstance(server, level, 
									ImageRegion.createInstance(
											x, y, tw, th, z, t)
									);
							set.add(tile);
						}
					}					
				}
			}
		}
		return set;
	}
	
	/**
	 * Create a new tile request for a specified region and resolution level.
	 * @param server
	 * @param level
	 * @param tileRegion
	 * @return
	 */
	private static TileRequest createInstance(ImageServer<?> server, int level, ImageRegion tileRegion) {
		double downsample = server.getDownsampleForResolution(level);
		return new TileRequest(
				getRegionRequest(server.getPath(), downsample, tileRegion),
				level,
				tileRegion
				);
	}

	private static RegionRequest getRegionRequest(String path, double downsample, ImageRegion tileRegion) {
		double x1 = tileRegion.getX() * downsample;
		double y1 = tileRegion.getY() * downsample;
		double x2 = (tileRegion.getX() + tileRegion.getWidth()) * downsample;
		double y2 = (tileRegion.getY() + tileRegion.getHeight()) * downsample;
		return RegionRequest.createInstance(path, downsample,
				(int)Math.round(x1),
				(int)Math.round(y1),
				(int)Math.round(x2 - Math.round(x1)),
				(int)Math.round(y2 - Math.round(y1)),
				tileRegion.getZ(),
				tileRegion.getT());
	}
	
	/**
	 * Get the RegionRequest that this tile represents.
	 * @return
	 */
	public RegionRequest getRegionRequest() {
		return request;
	}
	
	private TileRequest(final RegionRequest request, final int level, final ImageRegion tileRegion) {
		this.request = request;
		this.level = level;
		this.tileRegion = tileRegion;
	}

	/**
	 * Get the downsample value for this tile.
	 * @return
	 */
	public double getDownsample() {
		return request.getDownsample();
	}
	
	/**
	 * Get the resolution level.
	 * @return
	 */
	public int getLevel() {
		return level;
	}
	
	/**
	 * Get the x-coordinate of the bounding box for this tile in the full resolution image.
	 * @return
	 */
	public int getImageX() {
		return request.getX();
	}
	
	/**
	 * Get the y-coordinate of the bounding box for this tile in the full resolution image.
	 * @return
	 */
	public int getImageY() {
		return request.getY();
	}
	
	/**
	 * Get the width of the bounding box for this tile in the full resolution image.
	 * @return
	 */
	public int getImageWidth() {
		return request.getWidth();
	}
	
	/**
	 * Get the height of the bounding box for this tile in the full resolution image.
	 * @return
	 */
	public int getImageHeight() {
		return request.getHeight();
	}
	
	/**
	 * Get the x-coordinate of the bounding box for this tile at the tile resolution.
	 * @return
	 */
	public int getTileX() {
		return tileRegion.getX();
	}
	
	/**
	 * Get the y-coordinate of the bounding box for this tile at the tile resolution.
	 * @return
	 */
	public int getTileY() {
		return tileRegion.getY();
	}
	
	/**
	 * Get the width of the bounding box for this tile at the tile resolution.
	 * @return
	 */
	public int getTileWidth() {
		return tileRegion.getWidth();
	}
	
	/**
	 * Get the height of the bounding box for this tile at the tile resolution.
	 * @return
	 */
	public int getTileHeight() {
		return tileRegion.getHeight();
	}
	
	/**
	 * Get the z-slice index for this request.
	 * @return
	 */
	public int getZ() {
		return tileRegion.getZ();
	}
	
	/**
	 * Get the time point index for this request.
	 * @return
	 */
	public int getT() {
		return tileRegion.getT();
	}
	
	/**
	 * Get the ImagePlane for this request.
	 * @return
	 */
	public ImagePlane getPlane() {
		return tileRegion.getPlane();
	}
	
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + level;
		result = prime * result + ((request == null) ? 0 : request.hashCode());
		result = prime * result + ((tileRegion == null) ? 0 : tileRegion.hashCode());
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
		TileRequest other = (TileRequest) obj;
		if (level != other.level)
			return false;
		if (request == null) {
			if (other.request != null)
				return false;
		} else if (!request.equals(other.request))
			return false;
		if (tileRegion == null) {
			if (other.tileRegion != null)
				return false;
		} else if (!tileRegion.equals(other.tileRegion))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("Tile: level=%d, bounds=(%d, %d, %d, %d), %s", level,
				tileRegion.getX(), tileRegion.getY(), tileRegion.getWidth(), tileRegion.getHeight(), request.toString());
	}
	
}