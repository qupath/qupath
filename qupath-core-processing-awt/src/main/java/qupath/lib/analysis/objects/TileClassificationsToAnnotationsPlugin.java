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

package qupath.lib.analysis.objects;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.PathClassificationLabellingHelper;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.PathClasses;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractDetectionPlugin;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.PathShape;

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
	
	
	public TileClassificationsToAnnotationsPlugin() {}
	
	

	@Override
	public String getName() {
		return "Create annotations from classified tiles";
	}

	@Override
	public String getDescription() {
		return "Apply weighted smoothing to feature measurements, and append to the end of a list";
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
		parentClasses.add(PathROIObject.class);
		return parentClasses;
	}
	
	
	
	@Override
	protected void addRunnableTasks(final ImageData<T> imageData, final PathObject parentObject, List<Runnable> tasks) {
		ParameterList params = getParameterList(imageData);
		if (params.getBooleanParameterValue("clearAnnotations")) {
			PathObjectHierarchy hierarchy = imageData.getHierarchy();
			List<PathObject> annotations = hierarchy.getObjects(null, PathAnnotationObject.class);
			hierarchy.removeObjects(annotations, true);
		}
		tasks.add(new ClassificationToAnnotationRunnable(params, imageData, parentObject));
	}
	
	
	@Override
	public ParameterList getDefaultParameterList(final ImageData<T> imageData) {
		Set<PathClass> pathClasses = PathClassificationLabellingHelper.getRepresentedPathClasses(imageData.getHierarchy(), PathTileObject.class);
		List<PathClass> choices = new ArrayList<>(pathClasses);
		Collections.sort(choices, new Comparator<PathClass>() {

			@Override
			public int compare(PathClass pc1, PathClass pc2) {
				return pc1.getName().compareTo(pc2.getName());
			}
			
		});
		PathClass classTumor = PathClassFactory.getDefaultPathClass(PathClasses.TUMOR); // Tumor is the most likely choice, so default to it if available
		PathClass defaultChoice = choices.contains(classTumor) ? classTumor : choices.get(0);
		params = new ParameterList().addChoiceParameter("pathClass", "Choose class", defaultChoice, choices, "Choose PathClass to create annotations from")
				.addBooleanParameter("deleteTiles", "Delete existing child objects", false, "Delete the tiles that were used for creating annotations - further training will not be possible after these are deleted")
				.addBooleanParameter("clearAnnotations", "Clear existing annotations", true, "Remove all existing annotations (often a good idea if they were used to train a classifier, but are no longer needed)")
				.addBooleanParameter("splitAnnotations", "Split new annotations", true, "Split newly-created annotations into distinct regions (rather than have one large, possibly-discontinuous object)");
//				.addDoubleParameter("simplify", "Simplify shapes", 0);
		return params;
	}

	@Override
	protected Collection<PathObject> getParentObjects(final PluginRunner<T> runner) {
		PathObjectHierarchy hierarchy = runner.getImageData().getHierarchy();
		Set<PathObject> parents = new HashSet<>();
		for (PathObject tile : hierarchy.getObjects(null, PathTileObject.class)) {
			parents.add(tile.getParent());
		}
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
		private List<PathObject> pathAnnotations;
		private String resultsString;
		
		public ClassificationToAnnotationRunnable(final ParameterList params, final ImageData<?> imageData, final PathObject parentObject) {
			this.params = params;
			this.parentObject = parentObject;
			this.imageData = imageData;
		}

		@Override
		public void run() {
			long startTime = System.currentTimeMillis();
			
			int counter = 0;
			PathClass pathClass = (PathClass)params.getChoiceParameterValue("pathClass");
			boolean doSplit = params.getBooleanParameterValue("splitAnnotations");
//			int ind = 0;
			PathObject pathSingleAnnotation = null;
			if (pathClass != null) {
				Path2D path = null;
				for (PathObject pathObject : parentObject.getChildObjects()) {
//					ind++;
					if (!pathObject.getPathClass().getName().equals("Stroma"))
						System.out.println(pathObject.getPathClass().getName());
//					if ((pathObject instanceof PathTileObject) && (pathObject.getROI() instanceof PathShape) && pathClass.equals(pathObject.getPathClass())) {
					if ((pathObject instanceof PathTileObject) && (pathObject.getROI() instanceof PathShape) && pathClass.equals(pathObject.getPathClass())) {
//						System.out.println(pathObject);
						PathShape pathShape = (PathShape)pathObject.getROI();
						if (path == null)
							path = new Path2D.Float(PathROIToolsAwt.getShape(pathShape));
						else
							path.append(PathROIToolsAwt.getShape(pathShape), false);
						counter++;
						System.out.println("Counter: " + counter);
					}
				}
				if (counter > 0) {
					ROI pathROINew = null;
					ROI parentROI = parentObject.getROI();
					if (parentROI != null)
						pathROINew = PathROIToolsAwt.getShapeROI(new Area(path), parentROI.getC(), parentROI.getZ(), parentROI.getT());
					else
						pathROINew = PathROIToolsAwt.getShapeROI(new Area(path), -1, 0, 0);
					pathSingleAnnotation = new PathAnnotationObject(pathROINew, pathClass);
				}
			}
			
			if (pathSingleAnnotation == null) {
				resultsString = "No annotation created!";
				return;
			}
			
			// Split if necessary
			if (doSplit) {
				PathShape pathShape = (PathShape)pathSingleAnnotation.getROI();
				Area area = PathROIToolsAwt.getArea(pathShape);
				if (area.isSingular()) {
					pathAnnotations = Collections.singletonList(pathSingleAnnotation);
					resultsString = "Created 1 annotation from " + counter + " tiles: " + pathSingleAnnotation;
				}
				else {
					PolygonROI[][] polygons = PathROIToolsAwt.splitAreaToPolygons(area);
					pathAnnotations = new ArrayList<>();
					for (PolygonROI poly : polygons[1]) {
						PathShape shape = poly;
						for (PolygonROI hole : polygons[0]) {
							if (PathObjectTools.containsROI(poly, hole))
								shape = PathROIToolsAwt.combineROIs(shape, hole, PathROIToolsAwt.CombineOp.SUBTRACT);
						}
						pathAnnotations.add(new PathAnnotationObject(shape, pathClass));
					}
				}
			}
			else {
				pathAnnotations = Collections.singletonList(pathSingleAnnotation);
				resultsString = "Created annotation from " + counter + " tiles: " + pathSingleAnnotation;
			}
			
			if (resultsString == null) {
				if (pathAnnotations.size() == 1)
					resultsString = "Created 1 annotation";
				else
					resultsString = "Created " + pathAnnotations.size() + " annotations";
			}
			
			long endTime = System.currentTimeMillis();
			logger.info(parentObject + String.format(" processed in %.2f seconds", (endTime-startTime)/1000.));
		}
		
		@Override
		public void taskComplete() {
			if (!Thread.currentThread().isInterrupted()) {
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
