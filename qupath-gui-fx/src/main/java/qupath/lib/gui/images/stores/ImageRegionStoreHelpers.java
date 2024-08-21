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

package qupath.lib.gui.images.stores;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * Helper methods related to image region storage.
 * 
 * Provides a standard method of tiling an image, which are used by the viewer.
 * 
 * In cases where viewing tiles are cached, this makes it possible to find out
 * what the tile boundaries are... and perhaps adjust requests accordingly to match with cached tiles.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageRegionStoreHelpers {
	
	
	
	
	static double distanceToCentroid(ImageRegion r, double x, double y) {
		double dx = x - (r.getMinX() + r.getWidth() / 2.0);
		double dy = y - (r.getMinY() + r.getHeight() / 2.0);
		return (dx * dx) + (dy * dy);
	}
	
	
	/**
	 * Given ImageServer, determine the boundaries of the image tiles that would be needed to paint
	 * a specified shape (defined by coordinates in the full-resolution image space).
	 * The downsampleFactor is used to determine the resolution at which to request the tiles.
	 * 
	 * @param server The ImageServer from which the tiles would be requested
	 * @param clipShape The requested shape, defined in the full-resolution image space
	 * @param downsampleFactor The downsampleFactor determining the resolution at which tiles should be requested
	 * @param zPosition The zPosition from which to request tiles
	 * @param tPosition The tPosition from which to request tiles
	 * @param regions regions The list to which requests should be added, or null if a new list should be created
	 * @return The list of requests - identical to the one provided as an input parameter, unless this was null
	 */
	public static List<RegionRequest> getTilesToRequest(ImageServer<?> server, Shape clipShape, double downsampleFactor, int zPosition, int tPosition, List<RegionRequest> regions) {
		
		if (server == null)
			return Collections.emptyList();

		var request = RegionRequest.createInstance(server.getPath(), downsampleFactor, AwtTools.getImageRegion(clipShape, zPosition, tPosition));
		
		if (regions == null)
			regions = new ArrayList<>();
		
		for (var tile : server.getTileRequestManager().getTileRequests(request))
			regions.add(tile.getRegionRequest());
		
		double x = request.getX() + request.getWidth() / 2.0;
		double y = request.getY() + request.getHeight() / 2.0;

		regions.sort((r1, r2) -> Double.compare(distanceToCentroid(r1, x, y), distanceToCentroid(r2, x, y)));
		
		return regions;
	}

	/**
	 * Given an {@code ImageServer}, determine the boundaries of the image tile that contains specified x, y coordinates.
	 * The downsampleFactor is used to determine the resolution at which to request the tiles.
	 * 
	 * @param server the {@code ImageServer} from which the tiles would be requested
	 * @param x
	 * @param y
	 * @param downsampleFactor	the downsampleFactor determining the resolution at which tiles should be requested
	 * @param zPosition
	 * @param tPosition
	 * @return
	 */
	public static RegionRequest getTileRequest(ImageServer<BufferedImage> server, double x, double y, double downsampleFactor, int zPosition, int tPosition) {

		int serverWidth = server.getWidth();
		int serverHeight = server.getHeight();
		if (x < 0 || y < 0 || x >= serverWidth || y >= serverHeight)
			return null;

		double downsamplePreferred = ServerTools.getPreferredDownsampleFactor(server, downsampleFactor);

		// Determine what the tile size will be in the original image space for the requested downsample
		// Aim for a round number - preferred downsamples can be a bit off due to rounding
		int tileWidth = server.getMetadata().getPreferredTileWidth();
		int tileHeight = server.getMetadata().getPreferredTileHeight();
		if (tileWidth < 0)
			tileWidth = 256;
		if (tileHeight < 0)
			tileHeight = 256;

		int tileWidthForLevel;
		int tileHeightForLevel;
		if (GeneralTools.almostTheSame(downsamplePreferred, (int)(downsamplePreferred + .5), 0.001)) {
			tileWidthForLevel = (int)(tileWidth * (int)(downsamplePreferred + .5) + .5);
			tileHeightForLevel = (int)(tileHeight * (int)(downsamplePreferred + .5) + .5);
		}
		else {
			tileWidthForLevel = (int)(tileWidth * downsamplePreferred + .5);
			tileHeightForLevel = (int)(tileHeight * downsamplePreferred + .5);
		}

		// Get the starting indices, shifted to actual tile boundaries
		int xx = (int)(x / tileWidthForLevel) * tileWidthForLevel;
		int yy = (int)(y / tileHeightForLevel) * tileHeightForLevel;

		RegionRequest request = RegionRequest.createInstance(server.getPath(), downsamplePreferred, xx, yy, (int)Math.min(serverWidth, (xx+tileWidthForLevel))-xx,
				(int)Math.min(serverHeight, (yy+tileHeightForLevel))-yy, zPosition, tPosition);
		return request;
	}

}
