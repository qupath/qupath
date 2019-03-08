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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.ROI;


/**
 * Abstract plugin used for detection tasks that support breaking large regions into smaller ones,
 * and analyzing these in parallel - optionally with overlaps.
 * 
 * Particularly useful for tasks such as cell detection.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class AbstractTileableDetectionPlugin<T> extends AbstractDetectionPlugin<T> {

	/**
	 * Get the preferred pixel size that would be used for the specified ImageData and ParameterList.  
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
	 * 
	 * If the overlap is 0, then tile boundaries are likely to be visible in the results.
	 * 
	 * If the overlap is &gt; 0, then the overlap should also be &gt; the expected largest size of a detected object -
	 * otherwise objects may be lost of trimmed when overlaps are resolved.  This is because (currently) 
	 * the resolution of overlapping detections involves taking the largest one, rather than (for example) merging them.
	 * 
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
	 * 
	 * TODO: Avoid hard-coding what is considered a 'manageable size' or a preferred size for parallel tiles.
	 * 
	 */
	@Override
	protected void addRunnableTasks(ImageData<T> imageData, PathObject parentObject, List<Runnable> tasks) {
//		if (detector == null || detector.pathROI != parentObject.getROI())
//			detector = new CellDetector();
		
		if (imageData == null)
			return;
		
		ParameterList params = getParameterList(imageData);

		// Determine appropriate sizes - get a downsample factor that is a power of 2
		double downsampleFactor = ServerTools.getDownsampleFactor(imageData.getServer(), getPreferredPixelSizeMicrons(imageData, params), true);
		int preferred = (int)(2048 * downsampleFactor);
//		int preferred = (int)(1536 * downsampleFactor);
//		int max = (int)(4096 * downsampleFactor);
		int max = (int)(3072 * downsampleFactor);
//		int max = (int)(2048 * downsampleFactor);
		ImmutableDimension sizePreferred = new ImmutableDimension(preferred, preferred);
		ImmutableDimension sizeMax = new ImmutableDimension(max, max);
		
		parentObject.clearPathObjects();
		
		// No tasks to complete
		Collection<? extends ROI> pathROIs = PathROIToolsAwt.computeTiledROIs(imageData, parentObject, sizePreferred, sizeMax, false, getTileOverlap(imageData, params));
		if (pathROIs.isEmpty())
			return;
		
		// Exactly one task to complete
		if (pathROIs.size() == 1 && pathROIs.iterator().next() == parentObject.getROI()) {
			tasks.add(DetectionPluginTools.createRunnableTask(createDetector(imageData, params), getParameterList(imageData), imageData, parentObject));
			return;
		}
		
		List<ParallelTileObject> tileList = new ArrayList<>();
		AtomicInteger countdown = new AtomicInteger(pathROIs.size());
		for (ROI pathROI : pathROIs) {
			ParallelTileObject tile = new ParallelTileObject(pathROI, imageData.getHierarchy(), countdown);
			parentObject.addPathObject(tile);
			for (ParallelTileObject tileTemp : tileList) {
				if (tileTemp.suggestNeighbor(tile))
					tile.suggestNeighbor(tileTemp);
			}
			tileList.add(tile);
			tasks.add(DetectionPluginTools.createRunnableTask(createDetector(imageData, params), params, imageData, tile));
		}
		imageData.getHierarchy().fireHierarchyChangedEvent(this);
	}
	
	
}