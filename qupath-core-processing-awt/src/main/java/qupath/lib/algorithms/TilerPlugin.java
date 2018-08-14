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

package qupath.lib.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.AbstractDetectionPlugin;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;

/**
 * Simple plugin to create square tiles, which may later have measurements added.
 * 
 * @author Pete Bankhead
 *
 */
public class TilerPlugin<T> extends AbstractDetectionPlugin<T> {
	
	private ParameterList params;

	public TilerPlugin() {
		// Set up initial parameters
		params = new ParameterList();
		
		params.addTitleParameter("Tile options");
		params.addDoubleParameter("tileSizeMicrons", "Tile size", 100, GeneralTools.micrometerSymbol(), "Specify tile width and height, in " + GeneralTools.micrometerSymbol());
		params.addDoubleParameter("tileSizePx", "Tile size", 200, "px", "Specify tile width and height, in pixels");
		params.addBooleanParameter("trimToROI", "Trim to ROI", true, "Trim tiles to match the parent ROI shape, rather than overlap boundaries with full squares");
		
		params.addTitleParameter("Annotation options");
		params.addBooleanParameter("makeAnnotations", "Make annotation tiles", false, "Create annotation objects, rather than tile objects");
		params.addBooleanParameter("removeParentAnnotation", "Remove parent annotation", false, "Remove the parent object, if it was an annotation; has no effect if 'Make annotation tiles' is not selected, or the parent object is not an annotation");
	}

	
	
	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		return Arrays.asList(
				TMACoreObject.class,
				PathAnnotationObject.class
				);
	}
	
	
	static class TileCreator implements PathTask {
	
		private ParameterList params;
		private PathObject parentObject;
		private ImageData<?> imageData;
				
		private List<PathObject> tiles;
		
		private String lastMessage = null;
	
		TileCreator(final ImageData<?> imageData, final PathObject parentObject, final ParameterList params) {
			this.parentObject = parentObject;
			this.imageData = imageData;
			this.params = params;
		}
		
		
		public static <T> ImmutableDimension getPreferredTileSizePixels(final ParameterList params, final ImageServer<T> server) {
			// Determine tile size
			int tileWidth, tileHeight;
			if (server.hasPixelSizeMicrons()) {
				double tileSize = params.getDoubleParameterValue("tileSizeMicrons");
				tileWidth = (int)(tileSize / server.getPixelWidthMicrons() + .5);
				tileHeight = (int)(tileSize / server.getPixelHeightMicrons() + .5);
			} else {
				tileWidth = (int)(params.getDoubleParameterValue("tileSizePx") + .5);
				tileHeight = tileWidth;
			}
			return new ImmutableDimension(tileWidth, tileHeight);
		}
		
	
		private PathObject createTile(ROI pathROI, ParameterList params, ImageServer<?> server) throws InterruptedException {
			return Boolean.TRUE.equals(params.getBooleanParameterValue("makeAnnotations")) ? new PathAnnotationObject(pathROI) : new PathTileObject(pathROI);
			
		}

		@Override
		public String getLastResultsDescription() {
			return lastMessage;
		}


		@Override
		public void run() {
			
			ROI roi = parentObject.getROI();
			if (!(roi instanceof PathArea)) {
				lastMessage = "Cannot tile ROI " + roi + " - does not represent an area region of interest";
				return;
			}

			
			// Determine tile size
			ImageServer<?> server = imageData.getServer();
			ImmutableDimension tileSize = getPreferredTileSizePixels(params, server);
			int tileWidth = tileSize.width;
			int tileHeight = tileSize.height;
			boolean trimToROI = params.getBooleanParameterValue("trimToROI");

			List<ROI> pathROIs = PathROIToolsAwt.makeTiles((PathArea)roi, tileWidth, tileHeight, trimToROI);

			tiles = new ArrayList<>(pathROIs.size());

			Iterator<ROI> iter = pathROIs.iterator();
			int idx = 0;
			while (iter.hasNext()) {
				try {
					PathObject tile = createTile(iter.next(), params, server);
					if (tile != null) {
						idx++;
						tile.setName("Tile " + idx);
						tiles.add(tile);
					}
				} catch (InterruptedException e) {
					lastMessage = "Tile creation interrupted for " + parentObject;
					return;
				} catch (Exception e) {
					iter.remove();
				}
			}
			
			lastMessage = tiles.size() + " tiles created";
		}


		@Override
		public void taskComplete() {
			parentObject.clearPathObjects();
			parentObject.addPathObjects(tiles);
			if (parentObject.isAnnotation())
				((PathAnnotationObject)parentObject).setLocked(true);
			imageData.getHierarchy().fireHierarchyChangedEvent(this, parentObject);
			if (params.getBooleanParameterValue("removeParentAnnotation") && params.getBooleanParameterValue("makeAnnotations") && parentObject.isAnnotation()) {
				imageData.getHierarchy().removeObject(parentObject, true);
			}
		}
		
		
		
	}

	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<T> imageData) {
		
		boolean pixelsInMicrons = imageData.getServer().hasPixelSizeMicrons();
		params.getParameters().get("tileSizeMicrons").setHidden(!pixelsInMicrons);
		params.getParameters().get("tileSizePx").setHidden(pixelsInMicrons);
		
		return params;
	}

	@Override
	public String getName() {
		return "Create tiles";
	}

	@Override
	public String getLastResultsDescription() {
		return null;
	}

	public PathPlugin<T> makePluginCopy() {
		return new TilerPlugin<>();
	}

	@Override
	protected void addRunnableTasks(ImageData<T> imageData,	PathObject parentObject, List<Runnable> tasks) {
		tasks.add(new TileCreator(imageData, parentObject, params));
	}
	
	
	@Override
	public String getDescription() {
		return "Create square tiles to use later for feature measurements";
	}
	

}
