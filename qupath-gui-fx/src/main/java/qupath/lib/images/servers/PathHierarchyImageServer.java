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

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import qupath.lib.awt.color.ColorToolsAwt;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.objects.DefaultPathObjectConnectionGroup;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;


/**
 * A special ImageServer implementation that doesn't have a backing image, but rather
 * constructs tiles on request from a PathObjectHierarchy.
 * 
 * @author Pete Bankhead
 *
 */
public class PathHierarchyImageServer implements GeneratingImageServer<BufferedImage> {
	
//	public static String DEFAULT_PREFIX = "OVERLAY::";
	static long counter = 0;
	public static String DEFAULT_PREFIX = "OVERLAY";
	
	private String prefix;
	private ImageData<BufferedImage> imageData;
	private ImageServer<BufferedImage> server;
	private OverlayOptions options;
	private PathObjectHierarchy hierarchy;
//	private PathHierarchyPainter painter;
	
	public PathHierarchyImageServer(final ImageData<BufferedImage> imageData, final OverlayOptions options) {
		this(DEFAULT_PREFIX + " " + counter + "::", imageData, imageData.getServer(), imageData.getHierarchy(), options);
	}
	
//	public PathHierarchyImageServer(final ImageServer<BufferedImage> server, final PathObjectHierarchy hierarchy, final OverlayOptions options) {
//		this(DEFAULT_PREFIX + " " + counter + "::", server, hierarchy, options);
//	}
	
	PathHierarchyImageServer(final String prefix, final ImageData<BufferedImage> imageData, final ImageServer<BufferedImage> server, final PathObjectHierarchy hierarchy, final OverlayOptions options) {
		this.imageData = imageData;
		this.prefix = prefix;
		this.server = server;
		this.hierarchy = hierarchy;
//		this.painter = new PathHierarchyPainter(hierarchy);
		this.options = options;
	}
	
	@Override
	public String getPath() {
		return prefix + server.getPath();
	}

	@Override
	public String getShortServerName() {
		return prefix + server.getShortServerName();
	}

	@Override
	public double[] getPreferredDownsamples() {
		return new double[]{1, 4, 32, 64, 128};
//		return new double[]{4, 32, 1024};
//		return new double[]{1, 4, 32};
//		return server.getPreferredDownsamples();
	}

	@Override
	public double getPreferredDownsampleFactor(double requestedDownsample) {
		int ind = ServerTools.getClosestDownsampleIndex(getPreferredDownsamples(), requestedDownsample);
		return getPreferredDownsamples()[ind];
//		return server.getPreferredDownsampleFactor(requestedDownsample);
	}

	@Override
	public int getPreferredTileWidth() {
//		return 1024;
		return 256;
//		return server.getPreferredTileWidth();
	}

	@Override
	public int getPreferredTileHeight() {
//		return 1024;
		return 256;
//		return server.getPreferredTileHeight();
	}

	@Override
	public double getMagnification() {
		return server.getMagnification();
	}

	@Override
	public int getWidth() {
		return server.getWidth();
	}

	@Override
	public int getHeight() {
		return server.getHeight();
	}

	@Override
	public int nChannels() {
		return 3;
	}

	@Override
	public boolean isRGB() {
		return true;
	}

	@Override
	public int nZSlices() {
		return server.nZSlices();
	}

	@Override
	public int nTimepoints() {
		return server.nTimepoints();
	}

	@Override
	public double getTimePoint(int ind) {
		return server.getTimePoint(ind);
	}

	@Override
	public TimeUnit getTimeUnit() {
		return server.getTimeUnit();
	}

	@Override
	public double getZSpacingMicrons() {
		return server.getZSpacingMicrons();
	}

	@Override
	public double getPixelWidthMicrons() {
		return server.getPixelWidthMicrons();
	}

	@Override
	public double getPixelHeightMicrons() {
		return server.getPixelHeightMicrons();
	}

	@Override
	public double getAveragedPixelSizeMicrons() {
		return server.getAveragedPixelSizeMicrons();
	}

	@Override
	public boolean hasPixelSizeMicrons() {
		return server.hasPixelSizeMicrons();
	}

	@Override
	public BufferedImage getBufferedThumbnail(int maxWidth, int maxHeight, int zPosition) {
		return PathHierarchyPaintingHelper.createThumbnail(hierarchy, options, getWidth(), getHeight(), null, null);
	}

	@Override
	public PathImage<BufferedImage> readRegion(RegionRequest request) {
		throw new UnsupportedOperationException("Overlay image servers cannot return PathImages");
	}
	
	private Collection<PathObject> getObjectsToPaint(RegionRequest request) {
//		Rectangle region = request.getBounds();
		return hierarchy.getObjectsForRegion(PathDetectionObject.class, request, null);
	}
	
	
	/**
	 * Returns true if there are no objects to be painted within the requested region.
	 */
	@Override
	public boolean isEmptyRegion(RegionRequest request) {
		return !hierarchy.hasObjectsForRegion(PathDetectionObject.class, request) && (!options.getShowConnections() || imageData.getProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS) == null);
	}
	

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) {
//		long startTime = System.currentTimeMillis();
		
		// Get connections
		Object o = options.getShowConnections() ? imageData.getProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS) : null;
		PathObjectConnections connections = (o instanceof PathObjectConnections) ? (PathObjectConnections)o : null;
		
		Collection<PathObject> pathObjects = getObjectsToPaint(request);
		if (pathObjects == null || pathObjects.isEmpty()) {
			// We can only return null if no connections - otherwise we might still need to draw something
			if (connections == null) {
				return null;
			}
		}
		
		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
		double downsampleFactor = request.getDownsample();
		int width = (int)(request.getWidth() / downsampleFactor);
		int height = (int)(request.getHeight() / downsampleFactor);
		BufferedImage img = gc.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
		Graphics2D g2d = img.createGraphics();
		g2d.setClip(0, 0, width, height);
//		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		double scale = 1.0/downsampleFactor;
		
//		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.scale(scale, scale);
		g2d.translate(-request.getX(), -request.getY());
		// Note we don't want to pass a selection model, as selections shouldn't be included
		if (pathObjects != null && !pathObjects.isEmpty())
			PathHierarchyPaintingHelper.paintSpecifiedObjects(g2d, AwtTools.getBounds(request), pathObjects, options, null, downsampleFactor);
		
		// See if we have any connections to draw
		if (connections != null) {
			PathHierarchyPaintingHelper.paintConnections(connections, hierarchy, g2d, imageData.isFluorescence() ? ColorToolsAwt.TRANSLUCENT_WHITE : ColorToolsAwt.TRANSLUCENT_BLACK, downsampleFactor);
		}
		
		
		g2d.dispose();
//		long endTime = System.currentTimeMillis();
//		System.out.println("Number of objects: " + pathObjects.size());
//		System.out.println("Single tile image creation time: " + (endTime - startTime)/1000.);
		return img;
	}

	@Override
	public int getBitsPerPixel() {
		return 8; // Only 8-bit RGB images provided
	}

	@Override
	public void close() {}

	@Override
	public String getServerType() {
		return "Overlay";
	}

	@Override
	public List<String> getSubImageList() {
		return Collections.emptyList();
	}

	@Override
	public String getDisplayedImageName() {
		return prefix + server.getDisplayedImageName();
	}

	@Override
	public boolean containsSubImages() {
		return server.containsSubImages();
	}


	@Override
	public boolean usesBaseServer(ImageServer<?> server) {
		return this == server;
	}

	@Override
	public Integer getDefaultChannelColor(int channel) {
		if (nChannels() == 1)
			return ColorTools.makeRGB(255, 255, 255);
		switch (channel) {
		case 0: return ColorTools.makeRGB(255, 0, 0);
		case 1: return ColorTools.makeRGB(0, 255, 0);
		case 2: return ColorTools.makeRGB(0, 0, 255);
		default:
			return ColorTools.makeRGB(255, 255, 255);
		}
	}

	@Override
	public List<String> getAssociatedImageList() {
		return Collections.emptyList();
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		throw new IllegalArgumentException("No associated image with name '" + name + "' for " + getPath());
	}
	
	/**
	 * Currently, this always returns null.  May change in the future if hierarchies are read from files (although probably won't).
	 */
	@Override
	public File getFile() {
		return null;
	}

	@Override
	public String getSubImagePath(String imageName) {
		throw new RuntimeException("Cannot construct sub-image with name " + imageName + " for " + getClass().getSimpleName());
	}

	@Override
	public ImageServerMetadata getMetadata() {
		return new ImageServerMetadata.Builder(server.getMetadata()).setSizeC(3).build(); // Convert to always 3-channel
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return new ImageServerMetadata.Builder(server.getOriginalMetadata()).setSizeC(3).build(); // Convert to always 3-channel
	}

	@Override
	public boolean usesOriginalMetadata() {
		return server.usesOriginalMetadata();
	}
	
	/**
	 * Throws an exception - metadata should not be set for a hierarchy image server directly.  Any changes should be made to the underlying
	 * image server for which this server represents an object hierarchy.
	 */
	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		throw new RuntimeException("Metadata cannot be set for a hierarchy image server!");
	}
	
//	@Override
//	public ImagePixels readPixels(RegionRequest request) {
//		return new BufferedImagePixels(readBufferedImage(request));
//	}

}