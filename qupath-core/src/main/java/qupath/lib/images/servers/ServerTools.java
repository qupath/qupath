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

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;

import qupath.lib.common.GeneralTools;

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
	 * @param uri
	 * @return
	 */
	public static String getDefaultShortServerName(final URI uri) {
		Path path = GeneralTools.toPath(uri);
		if (path != null)
			return path.getFileName().toString();
		String path2 = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
		int ind = path2.lastIndexOf("/") + 1;
		return path2.substring(ind);
	}
	
	/**
	 * Get the index of the first channel of a server with a specified channel name.
	 * @param server
	 * @param channelName
	 * @return index (0-based) of the first channel with a name matching channelName, or -1 if no channel is found
	 */
	public static int getChannelIndex(ImageServer<?> server, String channelName) {
		int i = 0;
		for (var channel : server.getMetadata().getChannels()) {
			if (channelName.equals(channel.getName()))
				return i;
			i++;
		}
		return -1;
	}
	
	
	/**
	 * Get the preferred resolution level to request regions from an ImageServer at a specified downsample level.
	 * @param server
	 * @param requestedDownsample
	 * @return
	 * 
	 * @see #getPreferredDownsampleFactor(ImageServer, double)
	 */
	public static int getPreferredResolutionLevel(ImageServer<?> server, double requestedDownsample) {
		var metadata = server.getMetadata();
		double downsampleFactor = Math.max(requestedDownsample, metadata.getDownsampleForLevel(0));
		int n = metadata.nLevels();
		int bestDownsampleSeries = -1;
		double bestDownsampleDiff = Double.POSITIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			double d = metadata.getDownsampleForLevel(i);
			double downsampleDiff = downsampleFactor - d;
			if (!Double.isNaN(downsampleDiff) && (downsampleDiff >= 0 || GeneralTools.almostTheSame(downsampleFactor, d, 0.01)) && downsampleDiff < bestDownsampleDiff) {
				bestDownsampleSeries = i;
				bestDownsampleDiff = Math.abs(downsampleDiff);
			}
		}
		return bestDownsampleSeries;
	}
	
	/**
	 * Get the downsample factor supported by the server that is the best match for the requested downsample.
	 * <p>
	 * Generally, this will be &lt;= the requested downsample (but it may be slightly more if the error introduced
	 * would be very small, i.e. if 4 is requested and 4.0001 is available, 4.0001 would be returned).
	 * 
	 * @param requestedDownsample
	 * @return
	 * 
	 * @see #getPreferredResolutionLevel(ImageServer, double)
	 */
	public static double getPreferredDownsampleFactor(ImageServer<?> server, double requestedDownsample) {
		int level = getPreferredResolutionLevel(server, requestedDownsample);
		return server.getDownsampleForResolution(level);
	}
	
	/**
	 * Get an ImageServer name suitable for displaying.
	 * If the server is null, "No image" is returned. Otherwise, the name 
	 * stored in the metadata is returned if not null, otherwise {@code server.getShortServerName()} is used.
	 * @param server
	 * @return
	 */
	public static String getDisplayableImageName(ImageServer<?> server) {
		if (server == null)
			return "No image";
		String name = server.getMetadata().getName();
		if (name == null) {
			name = server.getURIs().stream().map(uri -> getDefaultShortServerName(uri)).collect(Collectors.joining(", "));
			if (name != null && !name.isBlank())
				return name;
			return server.getPath();
		} else
			return name;
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
