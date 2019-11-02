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

package qupath.lib.plugins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;


/**
 * Abstract plugin used for detection tasks that support breaking large regions into smaller ones,
 * and analyzing these in parallel - optionally with overlaps.
 * <p>
 * Particularly useful for tasks such as cell detection.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class AbstractTileableDetectionPlugin<T> extends AbstractDetectionPlugin<T> {
	
	private static int PREFERRED_TILE_SIZE = 2048;
	private static int MAX_TILE_SIZE = 3072;

	/**
	 * Get the preferred pixel size that would be used for the specified ImageData and ParameterList.
	 * <p>  
	 * This is useful in deciding whether to break large regions into smaller, parallelizable tiles.
	 * 
	 * @param imageData
	 * @param params
	 * @return
	 */
	protected abstract double getPreferredPixelSizeMicrons(ImageData<T> imageData, ParameterList params);
	
	/**
	 * Create a new ObjectDetector, compatible with the specified ImageData and ParameterList.
	 * 
	 * @param imageData
	 * @param params
	 * @return
	 */
	protected abstract ObjectDetector<T> createDetector(final ImageData<T> imageData, final ParameterList params);

	/**
	 * Get an appropriate overlap, in pixels, if analysis of the specified ImageData will be tiled.
	 * <p>
	 * If the overlap is 0, then tile boundaries are likely to be visible in the results.
	 * <p>
	 * If the overlap is &gt; 0, then the overlap should also be &gt; the expected largest size of a detected object -
	 * otherwise objects may be lost of trimmed when overlaps are resolved.  This is because (currently) 
	 * the resolution of overlapping detections involves taking the largest one, rather than (for example) merging them.
	 * <p>
	 * (Merging may be permitted in later versions, but only where measurements are not made by the plugin -
	 * since merged objects may require different measurements, e.g. for area or mean than can be easily computed
	 * in a general way from the individual objects being merged).
	 * 
	 * @param imageData
	 * @param params
	 * @return The overlap size in pixels, or 0 if overlapped tiles are not supported.
	 */
	protected abstract int getTileOverlap(final ImageData<T> imageData, final ParameterList params);

	
	/**
	 * Intercepts the 'standard' addRunnableTasks to (if necessary) insert ParallelTileObjects along the way,
	 * thereby breaking an excessively-large parentObject into more manageable pieces.
	 * <p>
	 * TODO: Avoid hard-coding what is considered a 'manageable size' or a preferred size for parallel tiles.
	 * 
	 */
	@Override
	protected void addRunnableTasks(ImageData<T> imageData, PathObject parentObject, List<Runnable> tasks) {
		if (imageData == null)
			return;
		
		ParameterList params = getParameterList(imageData);

		// Determine appropriate sizes
		// Note, for v0.1.2 and earlier the downsample was restricted to be a power of 2
		double downsampleFactor = ServerTools.getDownsampleFactor(imageData.getServer(), getPreferredPixelSizeMicrons(imageData, params));
		int preferred = (int)(PREFERRED_TILE_SIZE * downsampleFactor);
		int max = (int)(MAX_TILE_SIZE * downsampleFactor);
		ImmutableDimension sizePreferred = ImmutableDimension.getInstance(preferred, preferred);
		ImmutableDimension sizeMax = ImmutableDimension.getInstance(max, max);
		
//		parentObject.clearPathObjects();
		
		// Extract (or create) suitable ROI
		ROI parentROI = parentObject.getROI();
		if (parentROI == null)
			parentROI = ROIs.createRectangleROI(0, 0, imageData.getServer().getWidth(), imageData.getServer().getHeight(), ImagePlane.getDefaultPlane());

		// Make tiles
		Collection<? extends ROI> pathROIs = RoiTools.computeTiledROIs(parentROI, sizePreferred, sizeMax, false, getTileOverlap(imageData, params));
		
		// No tasks to complete
		if (pathROIs.isEmpty())
			return;
		
//		// Exactly one task to complete
//		if (pathROIs.size() == 1 && pathROIs.iterator().next() == parentObject.getROI()) {
//			tasks.add(DetectionPluginTools.createRunnableTask(createDetector(imageData, params), getParameterList(imageData), imageData, parentObject));
//			return;
//		}
		
		ParallelDetectionTileManager manager = new ParallelDetectionTileManager(parentObject); 
		
		List<ParallelTileObject> tileList = new ArrayList<>();
		AtomicInteger countdown = new AtomicInteger(pathROIs.size());
		for (ROI pathROI : pathROIs) {
			ParallelTileObject tile = new ParallelTileObject(manager, pathROI, imageData.getHierarchy(), countdown);
			parentObject.addPathObject(tile);
			for (ParallelTileObject tileTemp : tileList) {
				if (tileTemp.suggestNeighbor(tile))
					tile.suggestNeighbor(tileTemp);
			}
			tileList.add(tile);
			tasks.add(DetectionPluginTools.createRunnableTask(createDetector(imageData, params), params, imageData, tile));
		}
		manager.setTiles(tileList);
		
		imageData.getHierarchy().fireHierarchyChangedEvent(this);
	}
	
	
	static class ParallelDetectionTileManager {
		
		private PathObject parent;
		private List<PathObject> originalChildObjects;
		
		private boolean wasCancelled = false;
		
		private AtomicInteger countdown;
		private List<ParallelTileObject> tiles = new ArrayList<>();
		
		ParallelDetectionTileManager(PathObject parent) {
			this.parent = parent;
			this.originalChildObjects = new ArrayList<>(parent.getChildObjects());
		}
		
		public void setTiles(Collection<ParallelTileObject> tiles) {
			this.tiles = new ArrayList<>(tiles);
			countdown = new AtomicInteger(tiles.size());
			this.parent.clearPathObjects();
			this.parent.addPathObjects(tiles);
		}
		
		public void tileComplete(PathObject tile, boolean wasCancelled) {
			if (wasCancelled)
				this.wasCancelled = true;
			int remaining = countdown.decrementAndGet();
			if (remaining == 0)
				postprocess();
		}
		
		private void postprocess() {
			parent.clearPathObjects();
			if (wasCancelled) {
				// If anything was cancelled, then replace the original objects
				parent.addPathObjects(originalChildObjects);
			} else {
				// Add the objects from all the children
				for (var tile : tiles) {
					tile.resolveOverlaps();
					parent.addPathObjects(tile.getChildObjects());
				}
				if (parent.hasChildren())
					parent.setLocked(true);
			}
//			hierarchy.fireObjectsChangedEvent(this, Collections.singletonList(parent));
		}
		
	}
	
	
}