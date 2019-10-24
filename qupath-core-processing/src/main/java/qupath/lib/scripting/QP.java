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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.DistanceTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.workflow.RunSavedClassifierWorkflowStep;
import qupath.lib.projects.Projects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.tools.OpenCVTools;

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
	
	/**
	 * Brightfield image type with hematoxylin and DAB staining
	 */
	final public static ImageData.ImageType BRIGHTFIELD_H_DAB = ImageData.ImageType.BRIGHTFIELD_H_DAB;
	
	/**
	 * Brightfield image type with hematoxylin and eosin staining
	 */
	final public static ImageData.ImageType BRIGHTFIELD_H_E = ImageData.ImageType.BRIGHTFIELD_H_E;
	
	/**
	 * Brightfield image type
	 */
	final public static ImageData.ImageType BRIGHTFIELD_OTHER = ImageData.ImageType.BRIGHTFIELD_OTHER;
	
	/**
	 * Fluorescence image type
	 */
	final public static ImageData.ImageType FLUORESCENCE = ImageData.ImageType.FLUORESCENCE;
	
	/**
	 * Any other image type (neither brightfield nor fluorescence)
	 */
	final public static ImageData.ImageType OTHER = ImageData.ImageType.OTHER;
	
	/**
	 * Store ImageData accessible to the script thread
	 */
	private static Map<Thread, ImageData<?>> batchImageData = new WeakHashMap<>();
	
	
	private final static List<Class<?>> CORE_CLASSES = Collections.unmodifiableList(Arrays.asList(
			// Core datastructures
			//ImageData.class,
			//ImageServer.class,
			//PathObject.class,
			//PathObjectHierarchy.class,
			//PathClass.class,
			ImageRegion.class,
			RegionRequest.class,
			ImagePlane.class,
			
			// Static constructors
			PathObjects.class,
			ROIs.class,
			PathClassFactory.class,
			Projects.class,
			
			// Tools and static classes
			PathObjectTools.class,
			RoiTools.class,
			GsonTools.class,
			BufferedImageTools.class,
			PathClassifierTools.class,
			ColorTools.class,
			GeneralTools.class,
//			ImageWriter.class,
			ImageWriterTools.class,
			PathClassTools.class,
			GeometryTools.class,
			IJTools.class,
			OpenCVTools.class,
			GeometryTools.class,
			
			// External classes
			BufferedImage.class
			));
	
	/**
	 * List the fields and methods of a specified object.
	 * @param o
	 * @return
	 */
	public static String describe(Object o) {
		return describe(o instanceof Class ? (Class<?>)o : o.getClass());
	}
	
	/**
	 * List the fields and methods of a specified class.
	 * @param cls
	 * @return
	 */
	public static String describe(Class<?> cls) {
		var sb = new StringBuilder(cls.getName());
		var fields = getPublicFields(cls);
		if (!fields.isEmpty()) {
			sb.append("\n").append("  Fields:");
			for (var f : fields)
				sb.append("\n").append("    ").append(getString(f));
		}
		
		var methods = getPublicMethods(cls);
		if (!methods.isEmpty()) {
			sb.append("\n").append("  Methods:");
			for (var m : methods)
				sb.append("\n").append("    ").append(getString(m));
		}
		return sb.toString();
	}
	
	
	private static List<Method> getPublicMethods(Object o) {
		Class<?> cls = o instanceof Class ? (Class<?>)o : o.getClass();
		return Arrays.stream(cls.getMethods())
				.filter(m -> {
					return !Object.class.equals(m.getDeclaringClass()) && isPublic(m) && m.getAnnotation(Deprecated.class) == null;
					})
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.collect(Collectors.toList());
	}
	
	private static List<Field> getPublicFields(Object o) {
		Class<?> cls = o instanceof Class ? (Class<?>)o : o.getClass();
		return Arrays.stream(cls.getFields())
				.filter(f -> {
					return !Object.class.equals(f.getDeclaringClass()) && isPublic(f) && f.getAnnotation(Deprecated.class) == null;
					})
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.collect(Collectors.toList());
	}
	
	private static boolean isPublic(Member m) {
		return Modifier.isPublic(m.getModifiers());
	}
	
	private static String getString(Method m) {
		var sb = new StringBuilder();
		sb.append(m.getReturnType().getSimpleName());
		sb.append(" ");
		sb.append(m.getName());
		sb.append('(');
		sb.append(Stream.of(m.getParameterTypes()).map(t -> t.getSimpleName()).
				collect(Collectors.joining(", ")));
		sb.append(')');
		var exceptions = m.getExceptionTypes();
		if (exceptions.length > 0) {
			sb.append(Stream.of(exceptions).map(t -> t.getSimpleName()).
					collect(Collectors.joining(", ", " throws ", "")));
		}
		return sb.toString();

		//		return m.toString();
		//			sb.append("\n").append("  ")
		//				.append(m.getReturnType().getSimpleName())
		//				.append(" ")
		//				.append(m.getName())
		//				.append("(")
		//				.append(m.toString())
		//				.append(")");
	}
	
	private static String getString(Field f) {
		return f.toString();
	}
	
	
	/**
	 * Get a list of core classes that are likely to be useful for scripting.
	 * The purpose of this is to allow users to find classes they are likely to need, 
	 * or to import these automatically at the beginning of scripts.
	 * @return
	 */
	public static List<Class<?>> getCoreClasses() {
		return CORE_CLASSES;
	}
	
	
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
	 * Get the {@code ImageServer} of the current {@code ImageData}.
	 * 
	 * @return
	 * 
	 * @see #getCurrentImageData()
	 */
	public static ImageServer<?> getCurrentServer() {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return null;
		return imageData.getServer();
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
		hierarchy.addPathObject(pathObject);
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
		hierarchy.addPathObjects(pathObjects);
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
		Collection<PathObject> pathObjects = hierarchy.getObjects(null, cls);
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
	 * Set the channel names for the current ImageData.
	 * 
	 * @param names
	 * @see #setChannelNames(ImageData, String...)
	 */
	public static void setChannelNames(String... names) {
		setChannelNames(getCurrentImageData(), names);
	}
	
	/**
	 * Set the channel names for the specified ImageData.
	 * It is not essential to pass names for all channels: 
	 * by passing n values, the first n channel names will be set.
	 * Any name that is null will be left unchanged.
	 * 
	 * @param imageData
	 * @param names
	 */
	public static void setChannelNames(ImageData<?> imageData, String... names) {
		List<ImageChannel> oldChannels = imageData.getServer().getMetadata().getChannels();
		List<ImageChannel> newChannels = new ArrayList<>(oldChannels);
		for (int i = 0; i < names.length; i++) {
			String name = names[i];
			if (name == null)
				continue;
			newChannels.set(i, ImageChannel.getInstance(name, newChannels.get(i).getColor()));
			if (i >= newChannels.size()) {
				logger.warn("Too many channel names specified, only {} of {} will be used", newChannels.size(), names.length);
				break;
			}
		}
		setChannels(imageData, newChannels.toArray(ImageChannel[]::new));
	}
	
	/**
	 * Set the channel colors for the current ImageData.
	 * 
	 * @param colors
	 * @see #setChannelColors(ImageData, Integer...)
	 * @see #setChannelNames(ImageData, String...)
	 */
	public static void setChannelColors(Integer... colors) {
		setChannelColors(getCurrentImageData(), colors);
	}
	
	/**
	 * Set the channel colors for the specified ImageData.
	 * It is not essential to pass names for all channels: 
	 * by passing n values, the first n channel names will be set.
	 * Any name that is null will be left unchanged.
	 * 
	 * @param colors
	 * @see #setChannelNames(ImageData, String...)
	 */
	public static void setChannelColors(ImageData<?> imageData, Integer... colors) {
		List<ImageChannel> oldChannels = imageData.getServer().getMetadata().getChannels();
		List<ImageChannel> newChannels = new ArrayList<>(oldChannels);
		for (int i = 0; i < colors.length; i++) {
			Integer color = colors[i];
			if (color == null)
				continue;
			newChannels.set(i, ImageChannel.getInstance(newChannels.get(i).getName(), color));
			if (i >= newChannels.size()) {
				logger.warn("Too many channel colors specified, only {} of {} will be used", newChannels.size(), colors.length);
				break;
			}
		}
		setChannels(imageData, newChannels.toArray(ImageChannel[]::new));
	}
	
	/**
	 * Set the channels for the current ImageData.
	 * 
	 * @param channels
	 * @see #setChannels(ImageData, ImageChannel...)
	 */
	public static void setChannels(ImageChannel... channels) {
		setChannels(getCurrentImageData(), channels);
	}
	
	/**
	 * Set the channels for the specified ImageData.
	 * Note that number of channels provided must match the number of channels of the current image.
	 * <p>
	 * Also, currently it is not possible to set channels for RGB images - attempting to do so 
	 * will throw an IllegalArgumentException.
	 * 
	 * @param channels
	 * @see #setChannelNames(ImageData, String...)
	 * @see #setChannelColors(ImageData, Integer...)
	 */
	public static void setChannels(ImageData<?> imageData, ImageChannel... channels) {
		ImageServer<?> server = imageData.getServer();
		if (server.isRGB()) {
			throw new IllegalArgumentException("Cannot set channels for RGB images");
		}
		List<ImageChannel> oldChannels = server.getMetadata().getChannels();
		List<ImageChannel> newChannels = Arrays.asList(channels);
		if (oldChannels.equals(newChannels)) {
			logger.trace("Setting channels to the same values (no changes)");
			return;
		}
		if (oldChannels.size() != newChannels.size())
			throw new IllegalArgumentException("Cannot set channels - require " + oldChannels.size() + " channels but you provided " + channels.length);
		
		// Set the metadata
		var metadata = server.getMetadata();
		var metadata2 = new ImageServerMetadata.Builder(metadata)
				.channels(newChannels)
				.build();
		imageData.updateServerMetadata(metadata2);
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
			return plugin.runPlugin(new CommandLinePluginRunner<>(imageData), args);
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
	public static Collection<PathObject> getAnnotationObjects() {
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
	public static Collection<PathObject> getDetectionObjects() {
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
	public static Collection<PathObject> getCellObjects() {
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
	
	/**
	 * Set the image type for the current image data, using a String to represent the enum {@link ImageType}
	 * @param typeName
	 * @return
	 */
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

	/**
	 * Set the image type for the current image data
	 * @param type
	 * @return
	 */
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
	
	
	/**
	 * Set the color deconvolution stains for hte current image data using a (JSON) String representation
	 * 
	 * @param arg
	 * @return
	 */
	public static boolean setColorDeconvolutionStains(final String arg) {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return false;
		ColorDeconvolutionStains stains = ColorDeconvolutionStains.parseColorDeconvolutionStainsArg(arg);
		imageData.setColorDeconvolutionStains(stains);
		return true;
	}
	
	
	/**
	 * Run an detection object classifier for the specified image data
	 * @param imageData
	 * @param classifier
	 */
	public static void runClassifier(final ImageData<?> imageData, final PathObjectClassifier classifier) {
		if (imageData != null)
			runClassifier(imageData.getHierarchy(), classifier);
	}
	
	/**
	 * Run a detection object classifier for the specified image hierarchy
	 * @param hierarchy
	 * @param classifier
	 */
	public static void runClassifier(final PathObjectHierarchy hierarchy, final PathObjectClassifier classifier) {
		PathClassifierTools.runClassifier(hierarchy, classifier);
	}
	
	/**
	 * Run a detection object classifier for the current image data, reading the classifier from a specified path
	 * @param path
	 */
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
	
	
//	public static void classifyDetection(final Predicate<PathObject> p, final String className) {
//		PathObjectHierarchy hierarchy = getCurrentHierarchy();
//		if (hierarchy == null)
//			return;
//		List<PathObject> reclassified = new ArrayList<>();
//		PathClass pathClass = PathClassFactory.getPathClass(className);
//		for (PathObject pathObject : hierarchy.getObjects(null, PathDetectionObject.class)) {
//			if (p.test(pathObject) && pathObject.getPathClass() != pathClass) {
//				pathObject.setPathClass(pathClass);
//				reclassified.add(pathObject);
//			}
//		}
//		if (!reclassified.isEmpty())
//			hierarchy.fireObjectClassificationsChangedEvent(QP.class, reclassified);
//	}
	
	/**
	 * Create an annotation for the entire width and height of the current image data, on the default plane (z-slice, time point).
	 * 
	 * @param setSelected if true, select the object that was created after it is added to the hierarchy
	 */
	public static void createSelectAllObject(final boolean setSelected) {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return;
		ImageServer<?> server = imageData.getServer();
		PathObject pathObject = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getDefaultPlane())
				);
		imageData.getHierarchy().addPathObject(pathObject);
		if (setSelected)
			imageData.getHierarchy().getSelectionModel().setSelectedObject(pathObject);
	}

	/**
	 * Remove all TMA metadata from the current TMA grid.
	 * @param includeMeasurements remove measurements in addition to textual metadata
	 */
	public static void resetTMAMetadata(final boolean includeMeasurements) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		resetTMAMetadata(hierarchy, includeMeasurements);
	}

	/**
	 * Remove all TMA metadata from the TMA grid of the specified hierarchy.
	 * @param hierarchy
	 * @param includeMeasurements remove measurements in addition to textual metadata
	 */
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
	 * <p>
	 * For a TMA core at column c and row r, the label format will be 'Hc-Vr' or 'Hc-Vr', where H is the horizontal label and V the vertical label, 
	 * depending upon the status of the 'rowFirst' flag.
	 * <p>
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
	
	/**
	 * Relabel the current TMA grid. See {@link #relabelTMAGrid(PathObjectHierarchy, String, String, boolean)}
	 * @param labelsHorizontal
	 * @param labelsVertical
	 * @param rowFirst
	 * @return
	 */
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
		Collection<PathObject> objects = hierarchy.getObjects(null, cls);
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
	 * Set all objects in a collection to be selected, without any being chosen as the main object.
	 * @param pathObjects
	 */
	public static void selectObjects(final Collection<PathObject> pathObjects) {
		selectObjects(pathObjects, null);
	}
	
	/**
	 * Set all objects in a collection to be selected, including a specified main selected object.
	 * @param pathObjects
	 * @param mainSelection
	 */
	public static void selectObjects(final Collection<PathObject> pathObjects, PathObject mainSelection) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			hierarchy.getSelectionModel().setSelectedObjects(pathObjects, mainSelection);
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
	private static Predicate<PathObject> parsePredicate(final String command) throws NoSuchElementException {
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
	private static void selectObjectsByMeasurement(final String command) {
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

	/**
	 * Remove measurements from objects of a specific class for the current image data.
	 * @param cls
	 * @param measurementNames
	 */
	public static void removeMeasurements(final Class<? extends PathObject> cls, final String... measurementNames) {
		removeMeasurements(getCurrentHierarchy(), cls, measurementNames);
	}

	/**
	 * Remove measurements from objects of a specific class for the specified hierarchy.
	 * @param hierarchy
	 * @param cls
	 * @param measurementNames
	 */
	public static void removeMeasurements(final PathObjectHierarchy hierarchy, final Class<? extends PathObject> cls, final String... measurementNames) {
		if (hierarchy == null)
			return;
		Collection<PathObject> pathObjects = hierarchy.getObjects(null, cls);
		for (PathObject pathObject : pathObjects) {
			// A little check, to handle possible subclasses being returned
			if (pathObject.getClass() != cls)
				continue;
			// Remove the measurements
			pathObject.getMeasurementList().removeMeasurements(measurementNames);
			pathObject.getMeasurementList().close();
		}
		hierarchy.fireObjectMeasurementsChangedEvent(null, pathObjects);
	}
	
	
	
	/**
	 * Clear the measurement lists for specified objects within a hierarchy.
	 * @param hierarchy used to fire a hierarchy update, if specified (may be null if no update should be fired)
	 * @param pathObjects collection of objects that should have their measurement lists cleared
	 */
	public static void clearMeasurements(final PathObjectHierarchy hierarchy, final PathObject... pathObjects) {
		clearMeasurements(hierarchy, Arrays.asList(pathObjects));
	}
	
	/**
	 * Clear the measurement lists for specified objects within a hierarchy.
	 * @param hierarchy used to fire a hierarchy update, if specified (may be null if no update should be fired)
	 * @param pathObjects collection of objects that should have their measurement lists cleared
	 */
	public static void clearMeasurements(final PathObjectHierarchy hierarchy, final Collection<PathObject> pathObjects) {
		for (PathObject pathObject : pathObjects) {
			// Remove all measurements
			pathObject.getMeasurementList().clear();
			pathObject.getMeasurementList().close();
		}
		if (hierarchy != null)
			hierarchy.fireObjectMeasurementsChangedEvent(null, pathObjects);
	}
	
	/**
	 * Clear the measurement lists for all annotations in a hierarchy.
	 * @param hierarchy
	 */
	public static void clearAnnotationMeasurements(PathObjectHierarchy hierarchy) {
		if (hierarchy != null)
			clearMeasurements(hierarchy, hierarchy.getAnnotationObjects());
	}
	
	/**
	 * Clear the measurement lists for all annotations in the current hierarchy.
	 */
	public static void clearAnnotationMeasurements() {
		clearAnnotationMeasurements(getCurrentHierarchy());
	}
	
	/**
	 * Clear the measurement lists for all detections in a hierarchy.
	 * @param hierarchy
	 */
	public static void clearDetectionMeasurements(PathObjectHierarchy hierarchy) {
		if (hierarchy != null)
			clearMeasurements(hierarchy, hierarchy.getDetectionObjects());
	}
	
	/**
	 * Clear the measurement lists for all detections in the current hierarchy.
	 */
	public static void clearDetectionMeasurements() {
		clearDetectionMeasurements(getCurrentHierarchy());
	}
	
	
	
	
	
	/**
	 * Get a base class - which is either a valid PathClass which is *not* an intensity class, or else null.
	 * 
	 * This will be null if {@code pathObject.getPathClass() == null}.
	 * 
	 * Otherwise, it will be {@code pathObject.getPathClass().getBaseClass()} assuming the result isn't an intensity class - or null otherwise.
	 * 
	 * @param pathObject
	 * @return
	 */
	public static PathClass getBasePathClass(final PathObject pathObject) {
		PathClass baseClass = pathObject.getPathClass();
		if (baseClass != null) {
			baseClass = baseClass.getBaseClass();
			// Check our base isn't an intensity class
			if (PathClassTools.isPositiveOrGradedIntensityClass(baseClass) || PathClassTools.isNegativeClass(baseClass))
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
		return PathClassTools.getNonIntensityAncestorClass(pathObject.getPathClass());
	}
	
	
	/**
	 * Set the intensity classifications for the specified objects.
	 * 
	 * @param pathObjects
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
	public static void setIntensityClassifications(final Collection<PathObject> pathObjects, final String measurementName, final double... thresholds) {
		PathClassifierTools.setIntensityClassifications(pathObjects, measurementName, thresholds);
	}
	
	/**
	 * Set intensity classifications for all selected (detection) objects in the specified hierarchy.
	 * 
	 * @param hierarchy
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
	public static void setIntensityClassificationsForSelected(final PathObjectHierarchy hierarchy, final String measurementName, final double... thresholds) {
		// Get all selected detections
		List<PathObject> pathObjects = hierarchy.getSelectionModel().getSelectedObjects()
				.stream().filter(p -> p.isDetection()).collect(Collectors.toList());
		setIntensityClassifications(pathObjects, measurementName, thresholds);
		hierarchy.fireObjectClassificationsChangedEvent(QP.class, pathObjects);
	}
	
	/**
	 * Set the intensity classifications for objects of the specified class in the specified hierarchy.
	 * 
	 * @param hierarchy
	 * @param cls
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
	public static void setIntensityClassifications(final PathObjectHierarchy hierarchy, final Class<? extends PathObject> cls, final String measurementName, final double... thresholds) {
		Collection<PathObject> pathObjects = hierarchy.getObjects(null, cls);
		setIntensityClassifications(pathObjects, measurementName, thresholds);
		hierarchy.fireObjectClassificationsChangedEvent(QP.class, pathObjects);
	}
	
	/**
	 * Set the intensity classifications for objects of the specified class in the current hierarchy.
	 * 
	 * @param cls
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
	public static void setIntensityClassifications(final Class<? extends PathObject> cls, final String measurementName, final double... thresholds) {
		setIntensityClassifications(getCurrentHierarchy(), cls, measurementName, thresholds);
	}
	
	/**
	 * Set the intensity classifications for cells in the current hierarchy.
	 * 
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
	public static void setCellIntensityClassifications(final String measurementName, final double... thresholds) {
		setCellIntensityClassifications(getCurrentHierarchy(), measurementName, thresholds);
	}
	
	/**
	 * 
	 * @param hierarchy
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
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
			if (PathClassTools.isPositiveOrGradedIntensityClass(currentClass) || PathClassTools.isNegativeClass(currentClass))
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
		Collection<PathObject> pathObjects = hierarchy.getObjects(null, PathDetectionObject.class);
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
	
	
	/**
	 * Write an image region image to the specified path. The writer will be determined based on the file extension.
	 * @param server
	 * @param request
	 * @param path
	 * @throws IOException
	 */
	public static void writeImageRegion(ImageServer<BufferedImage> server, RegionRequest request, String path) throws IOException {
		ImageWriterTools.writeImageRegion(server, request, path);
	}
	
	/**
	 * Write a full image to the specified path. The writer will be determined based on the file extension.
	 * @param server
	 * @param path
	 * @throws IOException
	 */
	public static void writeImage(ImageServer<BufferedImage> server, String path) throws IOException {
		ImageWriterTools.writeImage(server, path);
	}
	
	/**
	 * Write an image to the specified path. The writer will be determined based on the file extension.
	 * @param img
	 * @param path
	 * @throws IOException
	 */
	public static void writeImage(BufferedImage img, String path) throws IOException {
		ImageWriterTools.writeImage(img, path);
	}
	
	
	/**
	 * Compute the distance for all detection object centroids to the closest annotation with each valid, not-ignored classification and add 
	 * the result to the detection measurement list.
	 * @param imageData
	 */
	public static void detectionToAnnotationDistances(ImageData<?> imageData) {
		DistanceTools.detectionToAnnotationDistances(imageData);
	}
	
	/**
	 * Compute the distance for all detection object centroids to the closest annotation with each valid, not-ignored classification and add 
	 * the result to the detection measurement list for the current ImageData.
	 */
	public static void detectionToAnnotationDistances() {
		DistanceTools.detectionToAnnotationDistances(getCurrentImageData());
	}

	/**
	 * Set the metadata for an ImageServer to have the required pixel sizes and z-spacing.
	 * <p>
	 * Returns true if changes were made, false otherwise.
	 * 
	 * @param imageData
	 * @param pixelWidthMicrons
	 * @param pixelHeightMicrons
	 * @param zSpacingMicrons
	 * @return true if the size was set, false otherwise
	 */
	public static boolean setPixelSizeMicrons(ImageData<?> imageData, Number pixelWidthMicrons, Number pixelHeightMicrons, Number zSpacingMicrons) {
		var server = imageData.getServer();
		if (isFinite(pixelWidthMicrons) && !isFinite(pixelHeightMicrons))
			pixelHeightMicrons = pixelWidthMicrons;
		else if (isFinite(pixelHeightMicrons) && !isFinite(pixelWidthMicrons))
			pixelWidthMicrons = pixelHeightMicrons;
		
		var metadataNew = new ImageServerMetadata.Builder(server.getMetadata())
			.pixelSizeMicrons(pixelWidthMicrons, pixelHeightMicrons)
			.zSpacingMicrons(zSpacingMicrons)
			.build();
		if (server.getMetadata().equals(metadataNew))
			return false;
		imageData.updateServerMetadata(metadataNew);
		return true;
	}
	
	/**
	 * Set the metadata for the current ImageData to have the required pixel sizes and z-spacing.
	 * <p>
	 * Returns true if changes were made, false otherwise.
	 * 
	 * @param pixelWidthMicrons
	 * @param pixelHeightMicrons
	 * @param zSpacingMicrons
	 * @return true if the size was set, false otherwise
	 */
	public static boolean setPixelSizeMicrons(Number pixelWidthMicrons, Number pixelHeightMicrons, Number zSpacingMicrons) {
		return setPixelSizeMicrons(getCurrentImageData(), pixelWidthMicrons, pixelHeightMicrons, zSpacingMicrons);
	}

	/**
	 * Set the metadata for the current ImageData to have the required pixel sizes.
	 * <p>
	 * Returns true if changes were made, false otherwise.
	 * 
	 * @param pixelWidthMicrons
	 * @param pixelHeightMicrons
	 * @return true if the size was set, false otherwise
	 */
	public static boolean setPixelSizeMicrons(Number pixelWidthMicrons, Number pixelHeightMicrons) {
		return setPixelSizeMicrons(pixelWidthMicrons, pixelHeightMicrons, null);
	}
	

	/**
	 * Apply a new classification to all objects in the current hierarchy with a specified classification.
	 * @param originalClassName name of the original classification
	 * @param newClassName name of the new classification
	 */
	public static void replaceClassification(String originalClassName, String newClassName) {
		replaceClassification(getCurrentHierarchy(), getPathClass(originalClassName), getPathClass(newClassName));
	}
	
	/**
	 * Apply a new classification to all objects in the current hierarchy with a specified original classification.
	 * @param originalClass the original classification
	 * @param newClass the new classification
	 */
	public static void replaceClassification(PathClass originalClass, PathClass newClass) {
		replaceClassification(getCurrentHierarchy(), originalClass, newClass);
	}

	/**
	 * Apply a new classification to all objects with a specified original classification in the provided hierarchy.
	 * @param hierarchy the hierarchy containing the objects
	 * @param originalClass the original classification
	 * @param newClass the new classification
	 */
	public static void replaceClassification(PathObjectHierarchy hierarchy, PathClass originalClass, PathClass newClass) {
		if (hierarchy == null)
			return;
		var pathObjects = hierarchy.getObjects(null, null);
		if (pathObjects.isEmpty())
			return;
		replaceClassification(pathObjects, originalClass, newClass);
		hierarchy.fireObjectClassificationsChangedEvent(QP.class, pathObjects);
	}
	
	/**
	 * Apply a new classification to all objects with a specified original classification in an object collection.
	 * @param pathObjects
	 * @param originalClass
	 * @param newClass
	 */
	public static void replaceClassification(Collection<PathObject> pathObjects, PathClass originalClass, PathClass newClass) {
		if (PathClassFactory.getPathClassUnclassified() == originalClass)
			originalClass = null;
		if (PathClassFactory.getPathClassUnclassified() == newClass)
			newClass = null;
		for (var pathObject : pathObjects) {
			if (pathObject.getPathClass() == originalClass)
				pathObject.setPathClass(newClass);
		}
	}
	
	
	
	
	
	
	
	private static boolean isFinite(Number val) {
		return val != null && Double.isFinite(val.doubleValue());
	}
	
}
