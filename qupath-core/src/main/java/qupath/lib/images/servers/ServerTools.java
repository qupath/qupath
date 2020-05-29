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

package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;

import qupath.lib.common.GeneralTools;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;

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
	 * @param server 
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
	
	
	
    /**
	 * Get a raster, padded by the specified amount, to the left, right, above and below.
	 * <p>
	 * Note that the padding is defined in terms of the <i>destination</i> pixels.
	 * <p>
	 * In other words, a specified padding of 5 should actually result in 20 pixels being added in each dimension 
	 * if the {@code request.getDownsample() == 4}.
     * 
     * @param server
     * @param request
     * @param padding
     * @return
     * @throws IOException
     */
	public static BufferedImage getPaddedRequest(ImageServer<BufferedImage> server, RegionRequest request, Padding padding) throws IOException {
		// If we don't have any padding, just return directly
		if (padding.isEmpty())
			return server.readBufferedImage(request);
		// Get the expected bounds
		double downsample = request.getDownsample();
		int x = (int)Math.round(request.getX() - padding.getX1() * downsample);
		int y = (int)Math.round(request.getY() - padding.getY1() * downsample);
		int x2 = (int)Math.round((request.getX() + request.getWidth()) + padding.getX2() * downsample);
		int y2 = (int)Math.round((request.getY() + request.getHeight()) + padding.getY2() * downsample);
		// If we're out of range, we'll need to work a bit harder
		int padLeft = 0, padRight = 0, padUp = 0, padDown = 0;
		boolean outOfRange = false;
		if (x < 0) {
			padLeft = (int)Math.round(-x/downsample);
			x = 0;
			outOfRange = true;
		}
		if (y < 0) {
			padUp = (int)Math.round(-y/downsample);
			y = 0;
			outOfRange = true;
		}
		if (x2 > server.getWidth()) {
			padRight  = (int)Math.round((x2 - server.getWidth())/downsample);
			x2 = server.getWidth();
			outOfRange = true;
		}
		if (y2 > server.getHeight()) {
			padDown  = (int)Math.round((y2 - server.getHeight())/downsample);
			y2 = server.getHeight();
			outOfRange = true;
		}
		// If everything is within range, this should be relatively straightforward
		RegionRequest request2 = RegionRequest.createInstance(request.getPath(), downsample, x, y, x2-x, y2-y, request.getZ(), request.getT());
		BufferedImage img = server.readBufferedImage(request2);
		if (outOfRange) {
			WritableRaster raster = img.getRaster();
			WritableRaster rasterPadded = raster.createCompatibleWritableRaster(
					raster.getWidth() + padLeft + padRight,
					raster.getHeight() + padUp + padDown);
			rasterPadded.setRect(padLeft, padUp, raster);
			// Add padding above
			if (padUp > 0) {
				WritableRaster row = raster.createWritableChild(0, 0, raster.getWidth(), 1, 0, 0, null);
				for (int r = 0; r < padUp; r++)
					rasterPadded.setRect(padLeft, r, row);
			}
			// Add padding below
			if (padDown > 0) {
				WritableRaster row = raster.createWritableChild(0, raster.getHeight()-1, raster.getWidth(), 1, 0, 0, null);
				for (int r = padUp + raster.getHeight(); r < rasterPadded.getHeight(); r++)
					rasterPadded.setRect(padLeft, r, row);
			}
			// Add padding to the left
			if (padLeft > 0) {
				WritableRaster col = rasterPadded.createWritableChild(padLeft, 0, 1, rasterPadded.getHeight(), 0, 0, null);
				for (int c = 0; c < padLeft; c++)
					rasterPadded.setRect(c, 0, col);
			}
			// Add padding to the right
			if (padRight > 0) {
				WritableRaster col = rasterPadded.createWritableChild(rasterPadded.getWidth()-padRight-1, 0, 1, rasterPadded.getHeight(), 0, 0, null);
				for (int c = padLeft + raster.getWidth(); c < rasterPadded.getWidth(); c++)
					rasterPadded.setRect(c, 0, col);
			}
			// TODO: The padding seems to work - but something to be cautious with...
			img = new BufferedImage(img.getColorModel(), rasterPadded, img.isAlphaPremultiplied(), null);
		}
		return img;
	}
	

}
