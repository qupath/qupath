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

package qupath.lib.images.servers;

import java.io.File;
import java.net.URI;

/**
 * Static methods helpful when dealing with ImageServers.
 * 
 * @author Pete Bankhead
 *
 */
public class ServerTools {

	/**
	 * Get the default shortened server name given the server's path.
	 * 
	 * @param path
	 * @return
	 */
	public static String getDefaultShortServerName(final String path) {
		try {
			if (path.startsWith("file") || path.startsWith("http")) {
				var uri = new URI(path);
				String path2 = uri.getPath();
				int ind = path2.lastIndexOf("/") + 1;
				return path2.substring(ind);
			}
			String name = new File(path).getName().replaceFirst("[.][^.]+$", "");
			return name;
		} catch (Exception e) {
			int ind = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
			if (ind > 0 || ind < path.length()-1)
				return path.substring(ind+1);
			return path;
		}
	}
	
	/**
	 * Calculate a downsample factor for a server given a preferred pixel size and the pixel size of the server itself.
	 * <p>
	 * Optionally ensure that the downsample is a power of 2 (i.e. the closest power of 2 available to the 'ideal' downsample).
	 * 
	 * @param serverPixelSizeMicrons
	 * @param requestedPixelSizeMicrons
	 * @param doLog2
	 * @return
	 */
	private static double getPreferredDownsampleForPixelSizeMicrons(double serverPixelSizeMicrons, double requestedPixelSizeMicrons, boolean doLog2) {
		// If we have NaN input, we have NaN output
		if (Double.isNaN(serverPixelSizeMicrons + requestedPixelSizeMicrons))
			return Double.NaN;
		// If we want the nearest downsample from 1, 2, 4, 8, 16 etc. calculate this, otherwise just divide
		// TODO: Ensure downsample calculation is being used / implemented correctly
		if (doLog2) {
			return Math.pow(2, Math.round(Math.log(requestedPixelSizeMicrons / serverPixelSizeMicrons) / Math.log(2)));
		} else
			return requestedPixelSizeMicrons / serverPixelSizeMicrons;
	}

	/**
	 * Calculate a downsample factor for a server given a preferred pixel size.
	 * 
	 * @param server
	 * @param preferredPixelSizeMicrons
	 * @return
	 */
	public static double getDownsampleFactor(final ImageServer<?> server, final double preferredPixelSizeMicrons) {
		if (server == null)
			return Double.NaN;
		double downsampleFactor = getPreferredDownsampleForPixelSizeMicrons(server.getPixelCalibration().getAveragedPixelSizeMicrons(), preferredPixelSizeMicrons, false);
		if (Double.isNaN(downsampleFactor) || downsampleFactor < 1)
			downsampleFactor = 1;
		return downsampleFactor;
	}

}
