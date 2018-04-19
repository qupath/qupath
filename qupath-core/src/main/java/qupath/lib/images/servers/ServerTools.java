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

import qupath.lib.common.GeneralTools;
import qupath.lib.common.URLTools;

/**
 * Static methods helpful when dealing with ImageServers.
 * 
 * @author Pete Bankhead
 *
 */
public class ServerTools {

	/**
	 * Get the index of the closest downsample factor from an array of available factors.
	 * 
	 * The array is assumed to be sorted in ascending order.
	 * 
	 * @param preferredDownsamples
	 * @param downsampleFactor
	 * @return
	 */
	public static int getClosestDownsampleIndex(double[] preferredDownsamples, double downsampleFactor) {
		downsampleFactor = Math.max(downsampleFactor, 1.0);
		int bestDownsampleSeries = -1;
		double bestDownsampleDiff = Double.POSITIVE_INFINITY;
		for (int i = 0; i < preferredDownsamples.length; i++) {
			double downsampleDiff = downsampleFactor - preferredDownsamples[i];
			if (!Double.isNaN(downsampleDiff) && (downsampleDiff >= 0 || GeneralTools.almostTheSame(downsampleFactor, preferredDownsamples[i], 0.01)) && downsampleDiff < bestDownsampleDiff) {
				bestDownsampleSeries = i;
				bestDownsampleDiff = Math.abs(downsampleDiff);
			}
		}
		return bestDownsampleSeries;
	}

	/**
	 * Get the default shortened server name given the server's path.
	 * 
	 * @param path
	 * @return
	 */
	public static String getDefaultShortServerName(final String path) {
		try {
			String name = new File(path).getName().replaceFirst("[.][^.]+$", "");
			return name;
		} catch (Exception e) {}
		if (URLTools.checkURL(path))
			return URLTools.getNameFromBaseURL(path);
		return path;		
	}

	/**
	 * Calculate a downsample factor for a server given a preferred pixel size and the pixel size of the server itself.
	 * 
	 * Optionally ensure that the downsample is a power of 2 (i.e. the closest power of 2 available to the 'ideal' downsample).
	 * 
	 * @param serverPixelSizeMicrons
	 * @param requestedPixelSizeMicrons
	 * @param doLog2
	 * @return
	 */
	public static double getPreferredDownsampleForPixelSizeMicrons(double serverPixelSizeMicrons, double requestedPixelSizeMicrons, boolean doLog2) {
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
	 * Optionally ensure that the downsample is a power of 2 (i.e. the closest power of 2 available to the 'ideal' downsample).
	 * 
	 * @param server
	 * @param preferredPixelSizeMicrons
	 * @param doLog2
	 * @return
	 */
	public static double getDownsampleFactor(final ImageServer<?> server, final double preferredPixelSizeMicrons, boolean doLog2) {
		if (server == null)
			return Double.NaN;
		double downsampleFactor = getPreferredDownsampleForPixelSizeMicrons(server.getAveragedPixelSizeMicrons(), preferredPixelSizeMicrons, doLog2);
		if (Double.isNaN(downsampleFactor) || downsampleFactor < 1)
			downsampleFactor = 1;
		return downsampleFactor;
	}

}
