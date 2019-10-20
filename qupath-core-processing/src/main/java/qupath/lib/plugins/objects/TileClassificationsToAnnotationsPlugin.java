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

package qupath.lib.plugins.objects;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractDetectionPlugin;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * Plugin to merge classified tiles into annotation objects.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class TileClassificationsToAnnotationsPlugin<T> extends AbstractDetectionPlugin<T> {
	
	private static Logger logger = LoggerFactory.getLogger(TileClassificationsToAnnotationsPlugin.class);
	
	private ParameterList params;
	private boolean parametersInitialized = false;
		
	

	@Override
	public String getName() {
		return "Tile classifications to annotations";
	}

	@Override
	public String getDescription() {
		return "Create annotations from classified tiles";
	}

	@Override
	public String getLastResultsDescription() {
		return "";
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		Collection<Class<? extends PathObject>> parentClasses = new ArrayList<>();
		parentClasses.add(TMACoreObject.class);
		parentClasses.add(PathAnnotationObject.class);
		parentClasses.add(PathRootObject.class);
		return parentClasses;
	}
	
	
	
	@Override
	protected void addRunnableTasks(final ImageData<T> imageData, final PathObject parentObject, List<Runnable> tasks) {
		ParameterList params = getParameterList(imageData);
		if (params.getBooleanParameterValue("clearAnnotations")) {
			PathObjectHierarchy hierarchy = imageData.getHierarchy();
			Collection<PathObject> annotations = PathObjectTools.getDescendantObjects(parentObject, null, PathAnnotationObject.class);
			hierarchy.removeObjects(annotations, true);
		}
		tasks.add(new ClassificationToAnnotationRunnable(params, imageData, parentObject));
	}
	
	
	@Override
	public ParameterList getDefaultParameterList(final ImageData<T> imageData) {
		if (!parametersInitialized) {
			Set<PathClass> pathClasses = PathClassifierTools.getRepresentedPathClasses(imageData.getHierarchy(), PathTileObject.class);
			List<PathClass> choices = new ArrayList<>(pathClasses);
			Collections.sort(choices, new Comparator<PathClass>() {
	
				@Override
				public int compare(PathClass pc1, PathClass pc2) {
					return pc1.getName().compareTo(pc2.getName());
				}
				
			});
			PathClass allClasses = PathClassFactory.getPathClass("All classes");
			PathClass defaultChoice = allClasses;
			choices.add(0, allClasses);
//			PathClass classTumor = PathClassFactory.getDefaultPathClass(PathClasses.TUMOR); // Tumor is the most likely choice, so default to it if available
//			PathClass defaultChoice = choices.contains(classTumor) ? classTumor : choices.get(0);
			params = new ParameterList();
			
			params.addChoiceParameter("pathClass", "Choose class", defaultChoice, choices, "Choose PathClass to create annotations from")
					.addBooleanParameter("deleteTiles", "Delete existing child objects", false, "Delete the tiles that were used for creating annotations - further training will not be possible after these are deleted")
					.addBooleanParameter("clearAnnotations", "Clear existing annotations", true, "Remove all existing annotations (often a good idea if they were used to train a classifier, but are no longer needed)")
					.addBooleanParameter("splitAnnotations", "Split new annotations", false, "Split newly-created annotations into distinct regions (rather than have one large, possibly-discontinuous object)");
	//				.addDoubleParameter("simplify", "Simplify shapes", 0);
		}
		return params;
	}

	@Override
	protected Collection<PathObject> getParentObjects(final PluginRunner<T> runner) {
		PathObjectHierarchy hierarchy = runner.getImageData().getHierarchy();
		List<PathObject> parents = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());

		// Deal with nested objects - the code is clumsy, but the idea is to take the highest
		// object in the hierarchy in instances where tiles are nested within other objects
		List<PathObject> tempList = new ArrayList<>(parents);
		for (PathObject temp : tempList) {
			Iterator<PathObject> iter = parents.iterator();
			while (iter.hasNext()) {
				if (PathObjectTools.isAncestor(iter.next(), temp))
					iter.remove();
			}
		}
		
		
		return parents;
	}
	
	
	
	
	static class ClassificationToAnnotationRunnable implements PathTask {
		
		private ParameterList params;
		private PathObject parentObject;
		private ImageData<?> imageData;
		private List<PathObject> pathAnnotations = new ArrayList<>();
		private String resultsString;
		
		public ClassificationToAnnotationRunnable(final ParameterList params, final ImageData<?> imageData, final PathObject parentObject) {
			this.params = params;
			this.parentObject = parentObject;
			this.imageData = imageData;
		}

		@Override
		public void run() {
			long startTime = System.currentTimeMillis();
			
			PathClass choice = (PathClass)params.getChoiceParameterValue("pathClass");
			Collection<PathClass> pathClasses = "All classes".equals(choice.getName()) ? 
					parentObject.getChildObjects().stream().map(p -> p.getPathClass()).collect(Collectors.toSet()) : 
						Collections.singletonList(choice);
			
			boolean doSplit = params.getBooleanParameterValue("splitAnnotations");
			boolean deleteTiles = params.getBooleanParameterValue("deleteTiles");

			for (PathClass pathClass : pathClasses) {
				PathObject pathSingleAnnotation = null;
				List<PathObject> tiles = new ArrayList<>();
				if (pathClass != null && !PathClassTools.isIgnoredClass(pathClass)) {
					Path2D path = null;
					for (PathObject pathObject : parentObject.getChildObjectsAsArray()) {
						if ((pathObject instanceof PathTileObject) && (RoiTools.isShapeROI(pathObject.getROI())) && pathClass.equals(pathObject.getPathClass())) {
							ROI pathShape = pathObject.getROI();
							if (path == null)
								path = new Path2D.Float(RoiTools.getShape(pathShape));
							else
								path.append(RoiTools.getShape(pathShape), false);
							tiles.add(pathObject);
						}
					}
					if (!tiles.isEmpty()) {
						ROI pathROINew = null;
						ROI parentROI = parentObject.getROI();
						if (parentROI != null)
							pathROINew = RoiTools.getShapeROI(new Area(path), parentROI.getImagePlane());
						else
							pathROINew = RoiTools.getShapeROI(new Area(path), ImagePlane.getDefaultPlane());
						pathSingleAnnotation = PathObjects.createAnnotationObject(pathROINew, pathClass);
						if (!deleteTiles)
							pathSingleAnnotation.addPathObjects(tiles);
					}
				}
				
				if (pathSingleAnnotation == null) {
					continue;
				}
				
				// Split if necessary
				if (doSplit) {
					ROI pathShape = pathSingleAnnotation.getROI();
					Area area = RoiTools.getArea(pathShape);
					if (area.isSingular()) {
						pathAnnotations.add(pathSingleAnnotation);
//						resultsString = "Created 1 annotation from " + tiles.size() + " tiles: " + pathSingleAnnotation;
					}
					else {
						PolygonROI[][] polygons = RoiTools.splitAreaToPolygons(area, pathShape.getC(), pathShape.getZ(), pathShape.getT());
						for (PolygonROI poly : polygons[1]) {
							ROI shape = poly;
							Iterator<PathObject> iter = tiles.iterator();
							List<PathObject> children = new ArrayList<>();
							if (!deleteTiles) {
								while (iter.hasNext()) {
									PathObject next = iter.next();
									ROI roi = next.getROI();
									if (poly.contains(roi.getCentroidX(), roi.getCentroidY())) {
										iter.remove();
										children.add(next);
									}
								}
							}
							
							for (PolygonROI hole : polygons[0]) {
								if (PathObjectTools.containsROI(poly, hole))
									shape = RoiTools.combineROIs(shape, hole, RoiTools.CombineOp.SUBTRACT);
							}
//							PathObjectTools.containsObject(pathSingleAnnotation, childObject)
							PathObject annotation = PathObjects.createAnnotationObject(shape, pathClass);
							if (!deleteTiles)
								annotation.addPathObjects(children);
							pathAnnotations.add(annotation);
						}
					}
				}
				else {
					pathAnnotations.add(pathSingleAnnotation);
//					resultsString = "Created annotation from " + tiles.size() + " tiles: " + pathSingleAnnotation;
				}
			}
			
			if (resultsString == null) {
				if (pathAnnotations.isEmpty())
					resultsString = "No annotation created!";
				else if (pathAnnotations.size() == 1)
					resultsString = "Created 1 annotation";
				else
					resultsString = "Created " + pathAnnotations.size() + " annotations";
			}
			
			long endTime = System.currentTimeMillis();
			logger.info(parentObject + String.format(" processed in %.2f seconds", (endTime-startTime)/1000.));
		}
		
		@Override
		public void taskComplete(boolean wasCancelled) {
			if (!wasCancelled && !Thread.currentThread().isInterrupted()) {
				if (params.getBooleanParameterValue("deleteTiles"))
					parentObject.clearPathObjects();
				if (pathAnnotations != null && !pathAnnotations.isEmpty())
					parentObject.addPathObjects(pathAnnotations);
				imageData.getHierarchy().fireHierarchyChangedEvent(parentObject);
			}
			pathAnnotations = null;
		}
		
		@Override
		public String getLastResultsDescription() {
			return resultsString;
		}
		
	}

}
