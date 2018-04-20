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

package qupath.lib.scripting;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.workflow.RunSavedClassifierWorkflowStep;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * Collection of static methods that are useful for scripting.
 * <p>
 * Prior to running a script, the {@code ImageData} should be set so that the script can make use of it.
 * <p>
 * A different {@code ImageData} may be stored for different threads.
 * <p>
 * Note: This design may change in the future, to enable a non-static class to encapsulate 
 * the context for a running script.  The limited ability to subclass a class containing static methods 
 * makes this design a bit problematic, while its package location means it cannot have access to GUI features 
 * (which it shouldn't have, because of the need to run headless... but sometimes the GUI is needed, e.g. to 
 * export images with markup).
 * 
 * @author Pete Bankhead
 *
 */
public class QP {
	
	final private static Logger logger = LoggerFactory.getLogger(QP.class);
	
	final public static ImageData.ImageType BRIGHTFIELD_H_DAB = ImageData.ImageType.BRIGHTFIELD_H_DAB;
	final public static ImageData.ImageType BRIGHTFIELD_H_E = ImageData.ImageType.BRIGHTFIELD_H_E;
	final public static ImageData.ImageType BRIGHTFIELD_OTHER = ImageData.ImageType.BRIGHTFIELD_OTHER;
	final public static ImageData.ImageType FLUORESCENCE = ImageData.ImageType.FLUORESCENCE;
	final public static ImageData.ImageType OTHER = ImageData.ImageType.OTHER;
	
	private static WeakHashMap<Thread, ImageData<?>> batchImageData = new WeakHashMap<>();
	
	
	/**
	 * Set the ImageData to use for batch processing.  This will be local for the current thread.
	 * @param imageData
	 * @return
	 */
	public static ImageData<?> setBatchImageData(final ImageData<?> imageData) {
		Thread thread = Thread.currentThread();
		logger.trace("Setting image data for {} to {}", thread, imageData);
		return batchImageData.put(thread, imageData);
	}
	
	
	/**
	 * Set the ImageData to use for batch processing.  This will be local for the current thread.
	 * <p>
	 * @return The ImageData set with setBatchImageData, or null if no ImageData has been set for the current thread.
	 */
	public static ImageData<?> getBatchImageData() {
		return batchImageData.get(Thread.currentThread());
	}
	
//	public static ImageData<?> getCurrentImageData() {
//		// Try the batch image data first
//		ImageData<?> imageData = getBatchImageData();
//		if (imageData != null)
//			return imageData;
//		QuPathGUI instance = getInstance();
//		if (instance == null)
//			return null;
//		return instance.getImageData();
//	}
	
	
	/**
	 * Trigger an update for the current hierarchy.
	 * <p>
	 * This should be called after any (non-standard) modifications are made to the hierarchy 
	 * to ensure that all listeners are notified (including for any GUI).
	 * <p>
	 * It is common to call it at the end of any script that does any direct modification of objects 
	 * (e.g. adding/removing measurements, setting classifications).
	 */
	public static void fireHierarchyUpdate() {
		fireHierarchyUpdate(getCurrentHierarchy());
	}

	/**
	 * Trigger an update for the specified hierarchy.
	 * <p>
	 * This should be called after any (non-standard) modifications are made to the hierarchy 
	 * to ensure that all listeners are notified (including for any GUI).
	 * <p>
	 * It is common to call it at the end of any script that does any direct modification of objects 
	 * (e.g. adding/removing measurements, setting classifications).
	 * 
	 * @param hierarchy
	 */
	public static void fireHierarchyUpdate(final PathObjectHierarchy hierarchy) {
		if (hierarchy != null)
			hierarchy.fireHierarchyChangedEvent(QP.class);
	}
	
	
	/**
	 * Create a new packed-int representation of an RGB color.
	 * <p>
	 * @param v A value between 0 and 255.  If a single value is give, the result will be
	 * a shade of gray (RGB all with that value).  Otherwise, 3 or 4 values may be given to generate 
	 * either an RGB or RGBA color.  Note: values are expected in order RGBA, but Java's packed ints are really ARGB.
	 * @return
	 */
	public static Integer getColorRGB(final int... v) {
		if (v.length == 1)
			return ColorTools.makeRGB(v[0], v[0], v[0]);
		if (v.length == 3)
			return ColorTools.makeRGB(v[0], v[1], v[2]);
		if (v.length == 4)
			return ColorTools.makeRGBA(v[0], v[1], v[2], v[3]);
		throw new IllegalArgumentException("Input to getColorRGB must be either 1, 3 or 4 integer values, between 0 and 255!");
	}
	
	/**
	 * Get the path to the {@code ImageServer} of the current {@code ImageData}.
	 * @return
	 * 
	 * @see #getCurrentImageData()
	 */
	public static String getCurrentServerPath() {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return null;
		return imageData.getServerPath();
	}
	
	/**
	 * Get the path to the current {@code ImageData}.
	 * <p>
	 * In this implementation, it is the same as calling {@code getBatchImageData()}.
	 * 
	 * @return
	 * 
	 * @see #getBatchImageData()
	 */
	public static ImageData<?> getCurrentImageData() {
		return getBatchImageData();
	}
	
	/**
	 * Get the {@code PathObjectHierarchy} of the current {@code ImageData}.
	 * 
	 * @return
	 * 
	 * @see #getCurrentImageData()
	 */
	public static PathObjectHierarchy getCurrentHierarchy() {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return null;
		return imageData.getHierarchy();
	}
	
	/**
	 * Get the selected objects within the current {@code PathObjectHierarchy}.
	 * <p>
	 * Note: this implementation returns the selected objects directly.  The returned collection 
	 * may not be modifiable.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy()
	 */
	public static Collection<PathObject> getSelectedObjects() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return null;
		return hierarchy.getSelectionModel().getSelectedObjects();
	}
	
	/**
	 * Get the primary selected object within the current {@code PathObjectHierarchy}.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy()
	 * @see #getSelectedObjects()
	 */
	public static PathObject getSelectedObject() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return null;
		return hierarchy.getSelectionModel().getSelectedObject();
	}
	
	/**
	 * Get the {@code ROI} for the primary selected object within the current {@code PathObjectHierarchy}.
	 * <p>
	 * This is really a convenience method where the selection indicates (for example) a region that should be extracted.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy()
	 * @see #getSelectedObject()
	 */
	public static ROI getSelectedROI() {
		PathObject pathObject = getSelectedObject();
		if (pathObject != null)
			return pathObject.getROI();
		return null;
	}
	
	/**
	 * Clear the selected objects for the current {@code PathObjectHierarchy}.
	 */
	public static void resetSelection() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;		
		hierarchy.getSelectionModel().clearSelection();
	}
	
	/**
	 * Set the selected object for the current {@code PathObjectHierarchy}.
	 * 
	 * @param pathObject the object to select.
	 * @return
	 * 
	 * @see qupath.lib.objects.hierarchy.events.PathObjectSelectionModel#setSelectedObject
	 */
	public static boolean setSelectedObject(PathObject pathObject) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return false;
		hierarchy.getSelectionModel().setSelectedObject(pathObject);
		return true;
	}
	
	/**
	 * Add the specified object to the current {@code PathObjectHierarchy}.
	 * <p>
	 * This will trigger a hierarchy changed event.
	 * 
	 * @param pathObject
	 */
	public static void addObject(PathObject pathObject) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;
		hierarchy.addPathObject(pathObject, true);
	}
	
	/**
	 * Add the specified array of objects to the current {@code PathObjectHierarchy}.
	 * <p>
	 * This will trigger a hierarchy changed event.
	 * 
	 * @param pathObjects
	 */
	public static void addObjects(PathObject[] pathObjects) {
		addObjects(Arrays.asList(pathObjects));
	}
	
	
	/**
	 * Add the specified collection of objects to the current {@code PathObjectHierarchy}.
	 * <p>
	 * This will trigger a hierarchy changed event.
	 * 
	 * @param pathObjects
	 */
	public static void addObjects(Collection<PathObject> pathObjects) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;
		hierarchy.addPathObjects(pathObjects, true);
	}
	
	/**
	 * Remove the specified object from the current {@code PathObjectHierarchy}, 
	 * optionally keeping or removing descendant objects.
	 * 
	 * @param pathObject
	 * @param keepChildren
	 */
	public static void removeObject(PathObject pathObject, boolean keepChildren) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;
		hierarchy.removeObject(pathObject, keepChildren);
	}
	
	/**
	 * Remove the specified array of objects from the current {@code PathObjectHierarchy}, 
	 * optionally keeping or removing descendant objects.
	 * 
	 * @param pathObjects
	 * @param keepChildren
	 */
	public static void removeObjects(PathObject[] pathObjects, boolean keepChildren) {
		removeObjects(Arrays.asList(pathObjects), keepChildren);
	}
	
	/**
	 * Get a count of the total number of objects in the current hierarchy.
	 * 
	 * @return
	 * 
	 * @see qupath.lib.objects.hierarchy.PathObjectHierarchy#nObjects
	 */
	public static int nObjects() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return 0;
		return hierarchy.nObjects();
	}
	
	/**
	 * Remove the specified collection of objects from the current {@code PathObjectHierarchy}, 
	 * optionally keeping or removing descendant objects.
	 * 
	 * @param pathObjects
	 * @param keepChildren
	 */
	public static void removeObjects(Collection<PathObject> pathObjects, boolean keepChildren) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;
		hierarchy.removeObjects(pathObjects, keepChildren);
	}
	
	
	/**
	 * Returns {@code true} if TMA cores are available.
	 * 
	 * @return
	 */
	public static boolean isTMADearrayed() {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return false;
		return imageData.getHierarchy().getTMAGrid() != null && imageData.getHierarchy().getTMAGrid().nCores() > 0;
	}
	
	
	/**
	 * Remove all the objects in the current {@code PathObjectHierarchy}, and clear the selection.
	 * 
	 * @see #getCurrentHierarchy
	 */
	public static void clearAllObjects() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;
		hierarchy.clearAll();
		hierarchy.getSelectionModel().clearSelection();
	}
	
	/**
	 * Remove all the objects of a specified Java class.
	 * 
	 * @param cls the class, e.g. {@code PathAnnotationObject.class}, {@code PathDetectionObject.class}, or
	 * 			  {@code null} if all objects should be removed.
	 * 
	 * @see #getCurrentHierarchy
	 * @see qupath.lib.objects.hierarchy.PathObjectHierarchy#getObjects
	 */
	public static void clearAllObjects(final Class<? extends PathObject> cls) {
		if (cls == null) {
			clearAllObjects();
			return;
		}
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;
		List<PathObject> pathObjects = hierarchy.getObjects(null, cls);
		hierarchy.removeObjects(pathObjects, true);
		
		PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
		if (selected != null && selected.getClass().isAssignableFrom(cls))
			hierarchy.getSelectionModel().setSelectedObject(null);
	}
	
	/**
	 * Remove all the annotation objects from the current {@code PathObjectHierarchy}.
	 * 
	 * @see #getCurrentHierarchy
	 * @see #clearAllObjects
	 */
	public static void clearAnnotations() {
		clearAllObjects(PathAnnotationObject.class);
	}
	
	/**
	 * Remove all the detection objects from the current {@code PathObjectHierarchy}.
	 * 
	 * @see #getCurrentHierarchy
	 * @see #clearAllObjects
	 */
	public static void clearDetections() {
		clearAllObjects(PathDetectionObject.class);
	}
	
	/**
	 * Remove the TMA grid from the current {@code PathObjectHierarchy}.
	 * 
	 * @see #getCurrentHierarchy
	 */
	public static void clearTMAGrid() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;
		hierarchy.setTMAGrid(null);
		PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
		if (selected instanceof TMACoreObject)
			hierarchy.getSelectionModel().setSelectedObject(null);
	}
	
	/**
	 * Run the specified plugin on the current {@code ImageData}.
	 * 
	 * @param className
	 * @param args
	 * @return
	 * @throws InterruptedException
	 * 
	 * @see #getCurrentImageData
	 */
	public static boolean runPlugin(String className, String args)  throws InterruptedException {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return false;
		return runPlugin(className, imageData, args);
	}
	
	
	/**
	 * Run the specified plugin on the specified {@code ImageData}.
	 * 
	 * @param className
	 * @param args
	 * @return
	 * @throws InterruptedException
	 */
	@SuppressWarnings("unchecked")
	public static boolean runPlugin(final String className, final ImageData<?> imageData, final String args) throws InterruptedException {
		if (imageData == null)
			return false;
		
		try {
			Class<?> cPlugin = QP.class.getClassLoader().loadClass(className);
			Constructor<?> cons = cPlugin.getConstructor();
			final PathPlugin plugin = (PathPlugin)cons.newInstance();
			return plugin.runPlugin(new CommandLinePluginRunner<>(imageData, true), args);
		} catch (Exception e) {
			logger.error("Unable to run plugin " + className, e);
			return false;
		}
	}
	
	
	/**
	 * Get the list of TMA core objects for the current hierarchy.
	 * 
	 * @return the list of {@code TMACoreObject}s, or an empty list if there is no TMA grid present.
	 * 
	 * @see #getCurrentHierarchy
	 */
	public static List<TMACoreObject> getTMACoreList() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null || hierarchy.getTMAGrid() == null)
			return Collections.emptyList();
		return hierarchy.getTMAGrid().getTMACoreList();
	}

	/**
	 * Get a array of the current annotation objects.
	 * <p>
	 * This has been deprecated, because Groovy gives ways to quickly switch between arrays and lists 
	 * using {@code as}, so in most scripts it should not really be needed as a separate method.
	 * 
	 * @return
	 */
	@Deprecated
	public static PathObject[] getAnnotationObjectsAsArray() {
		return getAnnotationObjects().toArray(new PathObject[0]);
	}
	
	/**
	 * Get a list of the current annotation objects.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy
	 */
	public static List<PathObject> getAnnotationObjects() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		return hierarchy.getObjects(null, PathAnnotationObject.class);
	}

	/**
	 * Get a array of the current detection objects.
	 * <p>
	 * This has been deprecated, because Groovy gives ways to quickly switch between arrays and lists 
	 * using {@code as}, so in most scripts it should not really be needed as a separate method.
	 * 
	 * @return
	 */
	@Deprecated
	public static PathObject[] getDetectionObjectsAsArray() {
		return getDetectionObjects().toArray(new PathObject[0]);
	}
	
	/**
	 * Get a list of the current detection objects.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy
	 */
	public static List<PathObject> getDetectionObjects() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		return hierarchy.getObjects(null, PathDetectionObject.class);
	}
	
	/**
	 * Get a list of the current cell objects.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy
	 */
	public static List<PathObject> getCellObjects() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		return hierarchy.getObjects(null, PathCellObject.class);
	}

	/**
	 * Get an array of all objects in the current hierarchy.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy
	 */
	public static PathObject[] getAllObjects() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return new PathObject[0];
		return hierarchy.getFlattenedObjectList(null).toArray(new PathObject[0]);
	}
	
	public static boolean setImageType(final String typeName) {
		if (typeName == null)
			return setImageType(ImageData.ImageType.UNSET);
		for (ImageData.ImageType typeTemp : ImageData.ImageType.values()) {
			if (typeTemp.toString().equalsIgnoreCase(typeName) || typeTemp.name().equalsIgnoreCase(typeName))
				return setImageType(typeTemp);
		}
		logger.error("Image type could not be parsed from {}", typeName);
		return false;
	}

	public static boolean setImageType(final ImageData.ImageType type) {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return false;
		if (type == null)
			imageData.setImageType(ImageData.ImageType.UNSET);
		else
			imageData.setImageType(type);
		return true;
	}
	
	
	public static boolean setColorDeconvolutionStains(final String arg) {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return false;
		ColorDeconvolutionStains stains = ColorDeconvolutionStains.parseColorDeconvolutionStainsArg(arg);
		imageData.setColorDeconvolutionStains(stains);
		return true;
	}
	
	
	
	public static void runClassifier(final ImageData<?> imageData, final PathObjectClassifier classifier) {
		if (imageData != null)
			runClassifier(imageData.getHierarchy(), classifier);
	}
	
	public static void runClassifier(final PathObjectHierarchy hierarchy, final PathObjectClassifier classifier) {
		PathClassifierTools.runClassifier(hierarchy, classifier);
	}
	
	
	public static void runClassifier(final String path) {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return;
		PathObjectClassifier classifier = PathClassifierTools.loadClassifier(new File(path));
		if (classifier == null) {
			logger.error("Could not load classifier from {}", path);
			return;
		}
		runClassifier(imageData, classifier);
		
		// Log the step
		imageData.getHistoryWorkflow().addStep(new RunSavedClassifierWorkflowStep(path));
	}
	
	
	public static void classifyDetection(final Predicate<PathObject> p, final String className) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;
		List<PathObject> reclassified = new ArrayList<>();
		PathClass pathClass = PathClassFactory.getPathClass(className);
		for (PathObject pathObject : hierarchy.getObjects(null, PathDetectionObject.class)) {
			if (p.test(pathObject) && pathObject.getPathClass() != pathClass) {
				pathObject.setPathClass(pathClass);
				reclassified.add(pathObject);
			}
		}
		if (!reclassified.isEmpty())
			hierarchy.fireObjectClassificationsChangedEvent(QP.class, reclassified);
	}
	
	
	
	public static void createSelectAllObject(final boolean setSelected) {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return;
		ImageServer<?> server = imageData.getServer();
		PathObject pathObject = new PathAnnotationObject(new RectangleROI(0, 0, server.getWidth(), server.getHeight(), 0, 0, 0));
		imageData.getHierarchy().addPathObject(pathObject, false);
		if (setSelected)
			imageData.getHierarchy().getSelectionModel().setSelectedObject(pathObject);
	}

	public static void resetTMAMetadata(final boolean includeMeasurements) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		resetTMAMetadata(hierarchy, includeMeasurements);
	}

	public static void resetTMAMetadata(final PathObjectHierarchy hierarchy, final boolean includeMeasurements) {
		if (hierarchy == null || hierarchy.getTMAGrid() == null)
			return;
		for (TMACoreObject core : hierarchy.getTMAGrid().getTMACoreList()) {
			core.clearMetadata();
			if (includeMeasurements) {
				core.getMeasurementList().clear();
			}
		}
		hierarchy.fireObjectsChangedEvent(QP.class, hierarchy.getTMAGrid().getTMACoreList());
	}
	
	
	/**
	 * Relabel a TMA grid.  This will only be effective if enough labels are supplied for the full grid - otherwise no changes will be made.
	 * 
	 * For a TMA core at column c and row r, the label format will be 'Hc-Vr' or 'Hc-Vr', where H is the horizontal label and V the vertical label, 
	 * depending upon the status of the 'rowFirst' flag.
	 * 
	 * An examples of label would be 'A-1', 'A-2', 'B-1', 'B-2' etc.
	 * 
	 * @param hierarchy The hierarchy containing the TMA grid to be relabelled.
	 * @param labelsHorizontal A String containing labels for each TMA column, separated by spaces, or a numeric or alphabetic range (e.g. 1-10, or A-G)
	 * @param labelsVertical A String containing labels for each TMA row, separated by spaces, or a numeric or alphabetic range (e.g. 1-10, or A-G)
	 * @param rowFirst TRUE if the horizontal label should be added before the vertical label, FALSE otherwise
	 * @return TRUE if there were sufficient horizontal and vertical labels to label the entire grid, FALSE otherwise.
	 */
	public static boolean relabelTMAGrid(final PathObjectHierarchy hierarchy, final String labelsHorizontal, final String labelsVertical, final boolean rowFirst) {
		if (hierarchy == null || hierarchy.getTMAGrid() == null) {
			logger.error("Cannot relabel TMA grid - no grid found!");
			return false;
		}
		
		TMAGrid grid = hierarchy.getTMAGrid();
		String[] columnLabels = PathObjectTools.parseTMALabelString(labelsHorizontal);
		String[] rowLabels = PathObjectTools.parseTMALabelString(labelsVertical);
		if (columnLabels.length < grid.getGridWidth()) {
			logger.error("Cannot relabel full TMA grid - not enough column labels specified!");
			return false;			
		}
		if (rowLabels.length < grid.getGridHeight()) {
			logger.error("Cannot relabel full TMA grid - not enough row labels specified!");
			return false;			
		}
		
		for (int r = 0; r < grid.getGridHeight(); r++) {
			for (int c = 0; c < grid.getGridWidth(); c++) {
				String name;
				if (rowFirst)
					name = rowLabels[r] + "-" + columnLabels[c];
				else
					name = columnLabels[c] + "-" + rowLabels[r];
				grid.getTMACore(r, c).setName(name);
			}			
		}
		hierarchy.fireObjectsChangedEvent(null, new ArrayList<>(grid.getTMACoreList()));
		return true;
	}
	
	
	public static boolean relabelTMAGrid(final String labelsHorizontal, final String labelsVertical, final boolean rowFirst) {
		return relabelTMAGrid(getCurrentHierarchy(), labelsHorizontal, labelsVertical, rowFirst);
	}
	
	
	/**
	 * Reset the PathClass for all objects of the specified type in the current hierarchy.
	 * 
	 * @param cls
	 */
	public static void resetClassifications(final Class<? extends PathObject> cls) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		resetClassifications(hierarchy, cls);
	}
	
	/**
	 * Reset the PathClass for all objects of the specified type in the specified hierarchy.
	 * 
	 * @param hierarchy
	 * @param cls
	 */
	public static void resetClassifications(final PathObjectHierarchy hierarchy, final Class<? extends PathObject> cls) {
		if (hierarchy == null)
			return;
		List<PathObject> objects = hierarchy.getObjects(null, cls);
		if (objects.isEmpty()) {
			logger.warn("No objects to reset classifications!");
			return;
		}
		for (PathObject pathObject : objects) {
			if (pathObject.getPathClass() != null)
				pathObject.setPathClass(null);
		}
		hierarchy.fireObjectClassificationsChangedEvent(QP.class, objects);
	}
	
	/**
	 * Reset the PathClass for all detection objects in the current hierarchy.
	 */
	public static void resetDetectionClassifications() {
//		resetClassifications(PathAnnotationObject.class);
		resetClassifications(PathDetectionObject.class);
	}
	
	/**
	 * Test whether a PathObject has a specified measurement in its measurement list.
	 * 
	 * @param pathObject
	 * @param measurementName
	 * @return
	 */
	public static boolean hasMeasurement(final PathObject pathObject, final String measurementName) {
		return pathObject != null && pathObject.getMeasurementList().containsNamedMeasurement(measurementName);
	}

	/**
	 * Extract the specified measurement from a PathObject.
	 * 
	 * @param pathObject
	 * @param measurementName
	 * @return
	 */
	public static double measurement(final PathObject pathObject, final String measurementName) {
		return pathObject == null ? Double.NaN : pathObject.getMeasurementList().getMeasurementValue(measurementName);
	}

	
	public static void pathPrint(String message) {
		logger.info(message);
	}
	
	
	/**
	 * Clear selected objects, but keep child (descendant) objects.
	 */
	public static void clearSelectedObjects() {
		clearSelectedObjects(true);
	}
	
	
	/**
	 * Delete the selected objects from the current hierarchy, optoonally keeping their child (descendant) objects.
	 * 
	 * @param keepChildren
	 */
	public static void clearSelectedObjects(boolean keepChildren) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return;
		Collection<PathObject> selectedRaw = hierarchy.getSelectionModel().getSelectedObjects();
		List<PathObject> selected = selectedRaw.stream().filter(p -> !(p instanceof TMACoreObject)).collect(Collectors.toList());
		hierarchy.removeObjects(selected, keepChildren);
		hierarchy.getSelectionModel().clearSelection();
	}
	
	/**
	 * Get a list of all objects in the current hierarchy according to a specified predicate.
	 * 
	 * @param predicate
	 * @return
	 */
	public static List<PathObject> getObjects(final Predicate<PathObject> predicate) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			return hierarchy.getFlattenedObjectList(null).stream().filter(predicate).collect(Collectors.toList());
		return Collections.emptyList();
	}

	/**
	 * Set selected objects to contain (only) all objects in the current hierarchy according to a specified predicate.
	 * 
	 * @param predicate
	 */
	public static void selectObjects(final Predicate<PathObject> predicate) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			hierarchy.getSelectionModel().setSelectedObjects(getObjects(hierarchy, predicate), null);
	}

	/**
	 * Get a list of all objects in the specified hierarchy according to a specified predicate.
	 * 
	 * @param predicate
	 * @return
	 */
	public static List<PathObject> getObjects(final PathObjectHierarchy hierarchy, final Predicate<PathObject> predicate) {
		return hierarchy.getFlattenedObjectList(null).stream().filter(predicate).collect(Collectors.toList());
	}

	/**
	 * Set selected objects to contain (only) all objects in the specified hierarchy according to a specified predicate.
	 * 
	 * @param predicate
	 */
	public static void selectObjects(final PathObjectHierarchy hierarchy, final Predicate<PathObject> predicate) {
		hierarchy.getSelectionModel().setSelectedObjects(getObjects(hierarchy, predicate), null);
	}
	
	/**
	 * Set objects that are a subclass of a specified class.
	 * 
	 * @param cls
	 */
	public static void selectObjectsByClass(final Class<? extends PathObject> cls) {
		selectObjects(p -> cls.isInstance(p));
	}
	
	/**
	 * Set objects that are a subclass of a specified class.
	 * 
	 * @param cls
	 */
	public static void selectObjectsByClass(final PathObjectHierarchy hierarchy, final Class<? extends PathObject> cls) {
		selectObjects(hierarchy, p -> cls.isInstance(p));
	}
	
	/**
	 * Select all annotation objects in the specified hierarchy.
	 * @param hierarchy
	 */
	public static void selectAnnotations(final PathObjectHierarchy hierarchy) {
		selectObjectsByClass(hierarchy, PathAnnotationObject.class);
	}
	
	/**
	 * Select all TMA core objects in the specified hierarchy, excluding missing cores.
	 * @param hierarchy
	 */
	public static void selectTMACores(final PathObjectHierarchy hierarchy) {
		selectTMACores(hierarchy, false);
	}
	
	/**
	 * Select all TMA core objects in the specified hierarchy, optionally including missing cores.
	 * @param hierarchy
	 */
	public static void selectTMACores(final PathObjectHierarchy hierarchy, final boolean includeMissing) {
		hierarchy.getSelectionModel().setSelectedObjects(PathObjectTools.getTMACoreObjects(hierarchy, includeMissing), null);
	}

	/**
	 * Select all detection objects in the specified hierarchy.
	 * @param hierarchy
	 */
	public static void selectDetections(final PathObjectHierarchy hierarchy) {
		selectObjectsByClass(hierarchy, PathDetectionObject.class);
	}
	
	/**
	 * Select all cell objects in the specified hierarchy.
	 * @param hierarchy
	 */
	public static void selectCells(final PathObjectHierarchy hierarchy) {
		selectObjectsByClass(hierarchy, PathCellObject.class);
	}
	
	/**
	 * Select all annotation objects in the current hierarchy.
	 */
	public static void selectAnnotations() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			selectAnnotations(hierarchy);
	}
	
	/**
	 * Select all TMA core objects in the current hierarchy, excluding missing cores.
	 */
	public static void selectTMACores() {
		selectTMACores(false);
	}
	
	/**
	 * Select all TMA core objects in the current hierarchy, optionally including missing cores.
	 */
	public static void selectTMACores(final boolean includeMissing) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			selectTMACores(hierarchy, includeMissing);
	}

	/**
	 * Select all detection objects in the current hierarchy.
	 */
	public static void selectDetections() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			selectDetections(hierarchy);
	}
	
	/**
	 * Select all cell objects in the current hierarchy.
	 */
	public static void selectCells() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			selectCells(hierarchy);
	}
	
	
	
	// TODO: Update parsePredicate to something more modern... a proper DSL
	@Deprecated
	public static Predicate<PathObject> parsePredicate(final String command) throws NoSuchElementException {
		String s = command.trim();
		if (s.length() == 0)
			throw new NoSuchElementException("No command provided!");
		Scanner scanner = new Scanner(s);

		try {
			Map<String, Predicate<Integer>> mapComparison = new HashMap<>();
			mapComparison.put(">=", v -> v >= 0);
			mapComparison.put("<=", v -> v <= 0);
			mapComparison.put(">", v -> v > 0);
			mapComparison.put("<", v -> v < 0);
			mapComparison.put("=", v -> v == 0);
			mapComparison.put("==", v -> v == 0);
			mapComparison.put("!=", v -> v != 0);
			mapComparison.put("~=", v -> v != 0);

			Predicate<PathObject> predicate = null;
			Pattern comparePattern = Pattern.compile(">=|<=|==|!=|~=|=|>|<");
			Pattern combinePattern = Pattern.compile("and|AND|or|OR");
			Pattern notPattern = Pattern.compile("not|NOT");
			while (scanner.hasNext()) {
				String combine = null;
				scanner.reset();
				if (predicate != null) {
					if (scanner.hasNext(combinePattern))
						combine = scanner.next(combinePattern).trim().toUpperCase();
					else
						throw new NoSuchElementException("Missing combiner (AND, OR) between comparisons!");
				}

				boolean negate = false;
				if (scanner.hasNext(notPattern)) {
					negate = true;
					scanner.next(notPattern);
				}

				scanner.useDelimiter(comparePattern);
				String measurement = scanner.next().trim();
				scanner.reset();
				if (!scanner.hasNext(comparePattern))
					throw new NoSuchElementException("Missing comparison operator (<, >, <=, >=, ==) for measurement \"" + measurement + "\"");
				String comparison = scanner.next(comparePattern).trim();
				
				if (!scanner.hasNextDouble())
					throw new NoSuchElementException("Missing comparison value after \"" + measurement + " " + comparison + "\"");
				double value = scanner.nextDouble();

				Predicate<PathObject> predicateNew = p -> {
					double v = p.getMeasurementList().getMeasurementValue(measurement);
					return !Double.isNaN(v) && mapComparison.get(comparison).test(Double.compare(p.getMeasurementList().getMeasurementValue(measurement), value));
				};
				if (negate)
					predicateNew = predicateNew.negate();

				if (predicate == null) {
					predicate = predicateNew;
				} else {
					if ("AND".equals(combine))
						predicate = predicate.and(predicateNew);
					else if ("OR".equals(combine))
						predicate = predicate.or(predicateNew);
					else
						throw new NoSuchElementException("Unrecognised combination of predicates: " + combine);
				}
			}

			return predicate;
		} finally {
			scanner.close();
		}
	}



	/**
	 * Select objects based on a specified measurement.
	 * 
	 * @param imageData
	 * @param command
	 */
	@Deprecated
	public static void selectObjectsByMeasurement(final ImageData<?> imageData, final String command) {
		selectObjects(imageData.getHierarchy(), parsePredicate(command));
	}
	
	@Deprecated
	public static void selectObjectsByMeasurement(final String command) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			selectObjects(hierarchy, parsePredicate(command));
	}
	
	/**
	 * Set the classification of the selected objects in the current hierarchy.
	 * 
	 * @param pathClassName
	 */
	public static void classifySelected(final String pathClassName) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			classifySelected(hierarchy, pathClassName);
	}
	
	/**
	 * Set the classification of the selected objects.
	 * 
	 * @param hierarchy
	 * @param pathClassName
	 */
	public static void classifySelected(final PathObjectHierarchy hierarchy, final String pathClassName) {
		if (!PathClassFactory.pathClassExists(pathClassName)) {
			logger.error("No class exists called {}", pathClassName);
			return;
		}
		PathClass pathClass = PathClassFactory.getPathClass(pathClassName);
		Collection<PathObject> selected = hierarchy.getSelectionModel().getSelectedObjects();
		if (selected.isEmpty()) {
			logger.info("No objects selected");
			return;
		}
		for (PathObject pathObject : selected) {
			pathObject.setPathClass(pathClass);
		}
		if (selected.size() == 1)
			logger.info("{} object classified as {}", selected.size(), pathClassName);
		else
			logger.info("{} objects classified as {}", selected.size(), pathClassName);
		hierarchy.fireObjectClassificationsChangedEvent(null, selected);
	}

	
	/**
	 * Clear the selection for the current hierarchy, so that no objects of any kind are selected.
	 * 
	 */
	public static void deselectAll() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			deselectAll(hierarchy);
	}
	
	/**
	 * Clear the selection, so that no objects of any kind are selected.
	 * 
	 * @param hierarchy
	 */
	public static void deselectAll(final PathObjectHierarchy hierarchy) {
		hierarchy.getSelectionModel().clearSelection();
	}

	
	/**
	 * Get a PathClass with the specified name.
	 * 
	 * @param name
	 * @return
	 */
	public static PathClass getPathClass(final String name) {
		return PathClassFactory.getPathClass(name);
	}

	/**
	 * Get a PathClass with the specified name and color.
	 * 
	 * Note that only one instance of any PathClass can exist at any time, therefore any existing 
	 * PathClass with the same description will always be returned instead of creating a new one.
	 * In this case, the color attribute of the existing PathClass will not be changed.
	 * Therefore the color only has an effect when a new PathClass is created.
	 * 
	 * @param name
	 * @param rgb
	 * @return
	 * 
	 * @see ColorTools#makeRGB
	 */
	public static PathClass getPathClass(final String name, final Integer rgb) {
		return PathClassFactory.getPathClass(name, rgb);
	}

	/**
	 * Get a PathClass with the specified name, derived from another PathClass.
	 * 
	 * An example would be a 'positive' class derived from a 'Tumor' class, e.g.
	 * <code>getDerivedPathClass(getPathClass("Tumor"), "Positive")</code>
	 * 
	 * @param baseClass
	 * @param name
	 * @return
	 */
	public static PathClass getDerivedPathClass(final PathClass baseClass, final String name) {
		return getDerivedPathClass(baseClass, name, null);
	}
	
	/**
	 * Get a PathClass with the specified name, derived from another PathClass.
	 * 
	 * An example would be a 'positive' class derived from a 'Tumor' class, e.g.
	 * <code>getDerivedPathClass(getPathClass("Tumor"), "Positive", getColorRGB(255, 0, 0))</code>
	 * 
	 * Note that only one instance of any PathClass can exist at any time, therefore any existing 
	 * PathClass with the same description will always be returned instead of creating a new one.
	 * In this case, the color attribute of the existing PathClass will not be changed.
	 * Therefore the color only has an effect when a new PathClass is created.
	 * 
	 * @param baseClass
	 * @param name
	 * @param rgb
	 * @return
	 */
	public static PathClass getDerivedPathClass(final PathClass baseClass, final String name, final Integer rgb) {
		return PathClassFactory.getDerivedPathClass(baseClass, name, rgb);
	}

	
	public static void removeMeasurements(final Class<? extends PathObject> cls, final String... measurementNames) {
		removeMeasurements(getCurrentHierarchy(), cls, measurementNames);
	}

	public static void removeMeasurements(final PathObjectHierarchy hierarchy, final Class<? extends PathObject> cls, final String... measurementNames) {
		if (hierarchy == null)
			return;
		List<PathObject> pathObjects = hierarchy.getObjects(null, cls);
		for (PathObject pathObject : pathObjects) {
			// A little check, to handle possible subclasses being returned
			if (pathObject.getClass() != cls)
				continue;
			// Remove the measurements
			pathObject.getMeasurementList().removeMeasurements(measurementNames);
			pathObject.getMeasurementList().closeList();
		}
		hierarchy.fireObjectMeasurementsChangedEvent(null, pathObjects);
	}
	
	
	
	
	
	
	
	
	
	/**
	 * Get a base class - which is either a valid PathClass which is *not* an intensity class, or else null.
	 * 
	 * This will be null if pathObject.getPathClass() == null.
	 * 
	 * Otherwise, it will be pathObject.getPathClass().getBaseClass() assuming the result isn't an intensity class - or null otherwise.
	 * 
	 * @param pathObject
	 * @return
	 */
	public static PathClass getBasePathClass(final PathObject pathObject) {
		PathClass baseClass = pathObject.getPathClass();
		if (baseClass != null) {
			baseClass = baseClass.getBaseClass();
			// Check our base isn't an intensity class
			if (PathClassFactory.isPositiveOrPositiveIntensityClass(baseClass) || PathClassFactory.isNegativeClass(baseClass))
				baseClass = null;
		}
		return baseClass;
	}

	
	/**
	 * Get the first ancestor class of pathObject.getPathClass() that is not an intensity class (i.e. not negative, positive, 1+, 2+ or 3+).
	 * 
	 * This will return null if pathClass is null, or if no non-intensity classes are found.
	 * 
	 * @param pathObject
	 * @return
	 */
	public static PathClass getNonIntensityAncestorPathClass(final PathObject pathObject) {
		return PathClassFactory.getNonIntensityAncestorClass(pathObject.getPathClass());
	}
	
	
	/**
	 * Assign cell classifications as positive or negative based upon a specified measurement, using up to 3 intensity bins.
	 * 
	 * An IllegalArgumentException is thrown if &lt; 1 or &gt; 3 intensity thresholds are provided.
	 * 
	 * @param pathObject 		the object to classify.
	 * @param measurementName 	the name of the measurement to use for thresholding.
	 * @param thresholds 		between 1 and 3 intensity thresholds, used to indicate negative/positive, or negative/1+/2+/3+
	 * @return 					the PathClass of the object after running this method.
	 */
	public static PathClass setIntensityClassification(final PathObject pathObject, final String measurementName, final double... thresholds) {
		if (thresholds.length == 0 || thresholds.length > 3)
			throw new IllegalArgumentException("Between 1 and 3 intensity thresholds required!");
		
		PathClass baseClass = getNonIntensityAncestorPathClass(pathObject);
		double estimatedSpots = pathObject.getMeasurementList().getMeasurementValue(measurementName);
		
		boolean singleThreshold = thresholds.length == 1;
		
		if (estimatedSpots < thresholds[0]) {
			pathObject.setPathClass(PathClassFactory.getNegative(baseClass, null));
		} else {
			if (singleThreshold)
				pathObject.setPathClass(PathClassFactory.getPositive(baseClass, null));
			else if (thresholds.length >= 3 && estimatedSpots >= thresholds[2])
				pathObject.setPathClass(PathClassFactory.getThreePlus(baseClass, null));				
			else if (thresholds.length >= 2 && estimatedSpots >= thresholds[1])
				pathObject.setPathClass(PathClassFactory.getTwoPlus(baseClass, null));				
			else if (estimatedSpots >= thresholds[0])
				pathObject.setPathClass(PathClassFactory.getOnePlus(baseClass, null));				
		}
		return pathObject.getPathClass();
	}
	
	public static void setIntensityClassifications(final Collection<PathObject> pathObjects, final String measurementName, final double... thresholds) {
		for (PathObject pathObject : pathObjects)
			setIntensityClassification(pathObject, measurementName, thresholds);
	}
	
	/**
	 * Set intensity classifications for all selected (detection) objects.
	 * 
	 * @param hierarchy
	 * @param measurementName
	 * @param thresholds
	 */
	public static void setIntensityClassificationsForSelected(final PathObjectHierarchy hierarchy, final String measurementName, final double... thresholds) {
		// Get all selected detections
		List<PathObject> pathObjects = hierarchy.getSelectionModel().getSelectedObjects()
				.stream().filter(p -> p.isDetection()).collect(Collectors.toList());
		setIntensityClassifications(pathObjects, measurementName, thresholds);
		hierarchy.fireObjectClassificationsChangedEvent(QP.class, pathObjects);
	}
	
	public static void setIntensityClassifications(final PathObjectHierarchy hierarchy, final Class<? extends PathObject> cls, final String measurementName, final double... thresholds) {
		List<PathObject> pathObjects = hierarchy.getObjects(null, cls);
		setIntensityClassifications(pathObjects, measurementName, thresholds);
		hierarchy.fireObjectClassificationsChangedEvent(QP.class, pathObjects);
	}
	
	public static void setIntensityClassifications(final Class<? extends PathObject> cls, final String measurementName, final double... thresholds) {
		setIntensityClassifications(getCurrentHierarchy(), cls, measurementName, thresholds);
	}
	
	public static void setCellIntensityClassifications(final String measurementName, final double... thresholds) {
		setCellIntensityClassifications(getCurrentHierarchy(), measurementName, thresholds);
	}
	
	public static void setCellIntensityClassifications(final PathObjectHierarchy hierarchy, final String measurementName, final double... thresholds) {
		setIntensityClassifications(hierarchy, PathCellObject.class, measurementName, thresholds);
	}	
	
	
	/**
	 * Reset the intensity classifications for all specified objects.
	 * 
	 * This means setting the classification to the result of <code>getNonIntensityAncestorPathClass(pathObject)</code>
	 * 
	 * @param pathObjects
	 */
	public static void resetIntensityClassifications(final Collection<PathObject> pathObjects) {
		for (PathObject pathObject : pathObjects) {
			PathClass currentClass = pathObject.getPathClass();
			if (PathClassFactory.isPositiveOrPositiveIntensityClass(currentClass) || PathClassFactory.isNegativeClass(currentClass))
				pathObject.setPathClass(getNonIntensityAncestorPathClass(pathObject));
		}
	}
	
	/**
	 * Reset the intensity classifications for all detections in the specified hierarchy.
	 * 
	 * This means setting the classification to the result of <code>getNonIntensityAncestorPathClass(pathObject)</code>
	 * 
	 * @param hierarchy
	 */
	public static void resetIntensityClassifications(final PathObjectHierarchy hierarchy) {
		List<PathObject> pathObjects = hierarchy.getObjects(null, PathDetectionObject.class);
		resetIntensityClassifications(pathObjects);
		hierarchy.fireObjectClassificationsChangedEvent(QP.class, pathObjects);
	}

	/**
	 * Reset the intensity classifications for all detections in the current hierarchy.
	 * 
	 * This means setting the classification to the result of <code>getNonIntensityAncestorPathClass(pathObject)</code>
	 * 
	 */
	public static void resetIntensityClassifications() {
		resetIntensityClassifications(getCurrentHierarchy());
	}


}
