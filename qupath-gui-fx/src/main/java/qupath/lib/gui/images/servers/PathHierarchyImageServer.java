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

package qupath.lib.gui.images.servers;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.gui.viewer.overlays.HierarchyOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.GeneratingImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ImageResolutionLevel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
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
public class PathHierarchyImageServer extends AbstractTileableImageServer implements GeneratingImageServer<BufferedImage> {
	
//	public static String DEFAULT_PREFIX = "OVERLAY::";
	static long counter = 0;
	
	/**
	 * Prefix used for the path of all instances of this class.
	 * This can be used to help with caching (and in particular with clearing caches selectively).
	 */
	public static String DEFAULT_PREFIX = "OVERLAY::";
	
	private ImageServerMetadata originalMetadata;
	
	private String prefix;
	private ImageData<BufferedImage> imageData;
	private ImageServer<BufferedImage> server;
	private OverlayOptions options;
	private PathObjectHierarchy hierarchy;
	
	/**
	 * Constructor.
	 * @param imageData the image data
	 * @param options options defining how objects will be painted
	 */
	public PathHierarchyImageServer(final ImageData<BufferedImage> imageData, final OverlayOptions options) {
		this(DEFAULT_PREFIX + " " + counter + "::", imageData, options);
	}
	
//	public PathHierarchyImageServer(final ImageServer<BufferedImage> server, final PathObjectHierarchy hierarchy, final OverlayOptions options) {
//		this(DEFAULT_PREFIX + " " + counter + "::", server, hierarchy, options);
//	}
	
	
	private PathHierarchyImageServer(final String prefix, final ImageData<BufferedImage> imageData, final OverlayOptions options) {
		super();
		this.imageData = imageData;
		this.prefix = prefix;
		this.server = imageData.getServer();
		this.hierarchy = imageData.getHierarchy();
		this.options = options;
		
		double minDim = Math.min(server.getWidth(), server.getHeight());
//		double maxDim = Math.max(server.getWidth(), server.getHeight());
		double nextDownsample = 1.0;
		var levelBuilder = new ImageResolutionLevel.Builder(server.getWidth(), server.getHeight());
		do {
			levelBuilder.addLevelByDownsample(nextDownsample);
			nextDownsample *= 4.0;
		} while ((minDim / nextDownsample) >= 2048);
		
//		String path = getPath();
//		cache.entrySet().removeIf(r -> path.equals(r.getKey().getPath()));
		
		// Set metadata, using the underlying server as a basis
		this.originalMetadata = new ImageServerMetadata.Builder(server.getOriginalMetadata())
				.preferredTileSize(256, 256)
				.levels(levelBuilder.build())
				.pixelType(PixelType.UINT8)
				.channels(ImageChannel.getDefaultRGBChannels())
				.rgb(true)
				.build();
	}
	
	/**
	 * Returns null (does not support ServerBuilders).
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return null;
	}
	
	@Override
	public Collection<URI> getURIs() {
		return Collections.emptyList();
	}
	
	/**
	 * Returns a UUID.
	 */
	@Override
	protected String createID() {
		return UUID.randomUUID().toString();
	}
	
	@Override
	public String getPath() {
		return prefix + server.getPath();
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
	public void close() {}

	@Override
	public String getServerType() {
		return "Overlay";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}

	/**
	 * Throws an exception - metadata should not be set for a hierarchy image server directly.  Any changes should be made to the underlying
	 * image server for which this server represents an object hierarchy.
	 */
	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		throw new IllegalArgumentException("Metadata cannot be set for a hierarchy image server!");
	}

	@Override
	protected BufferedImage createDefaultRGBImage(int width, int height) {
//		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
//		return gc.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
		return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}
	
	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		RegionRequest request = tileRequest.getRegionRequest();
//		long startTime = System.currentTimeMillis();
		
		// Get connections
		Object o = options.getShowConnections() ? imageData.getProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS) : null;
		PathObjectConnections connections = (o instanceof PathObjectConnections) ? (PathObjectConnections)o : null;
		
		List<PathObject> pathObjects = new ArrayList<>(getObjectsToPaint(request));
		if (pathObjects == null || pathObjects.isEmpty()) {
			// We can only return null if no connections - otherwise we might still need to draw something
			if (connections == null) {
				return null;
			}
		}
		
		Collections.sort(pathObjects, new HierarchyOverlay.DetectionComparator());
		
		double downsampleFactor = request.getDownsample();
		int width = tileRequest.getTileWidth();
		int height = tileRequest.getTileHeight();
		BufferedImage img = createDefaultRGBImage(width, height);
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

}