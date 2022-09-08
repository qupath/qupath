/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.scripting;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ObjectArrays;

import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.DelaunayTools;
import qupath.lib.analysis.DistanceTools;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.analysis.features.ObjectMeasurements.ShapeFeatures;
import qupath.lib.analysis.heatmaps.ColorModels;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.analysis.images.ContourTracing;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.images.writers.TileExporter;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.io.PathIO.GeoJsonExportOptions;
import qupath.lib.io.PointIO;
import qupath.lib.io.UriResource;
import qupath.lib.io.UriUpdater;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectPredicates;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.CellTools;
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
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.dnn.DnnTools;
import qupath.opencv.io.OpenCVTypeAdapters;
import qupath.opencv.ml.objects.OpenCVMLClassifier;
import qupath.opencv.ml.objects.features.FeatureExtractors;
import qupath.opencv.ml.pixel.PixelClassifierTools;
import qupath.opencv.ml.pixel.PixelClassifierTools.CreateObjectOptions;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.GroovyCV;
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
	
	private static final Logger logger = LoggerFactory.getLogger(QP.class);
	
	/**
	 * Brightfield image type with hematoxylin and DAB staining
	 */
	public static final ImageData.ImageType BRIGHTFIELD_H_DAB = ImageData.ImageType.BRIGHTFIELD_H_DAB;
	
	/**
	 * Brightfield image type with hematoxylin and eosin staining
	 */
	public static final ImageData.ImageType BRIGHTFIELD_H_E = ImageData.ImageType.BRIGHTFIELD_H_E;
	
	/**
	 * Brightfield image type
	 */
	public static final ImageData.ImageType BRIGHTFIELD_OTHER = ImageData.ImageType.BRIGHTFIELD_OTHER;
	
	/**
	 * Fluorescence image type
	 */
	public static final ImageData.ImageType FLUORESCENCE = ImageData.ImageType.FLUORESCENCE;
	
	/**
	 * Any other image type (neither brightfield nor fluorescence)
	 */
	public static final ImageData.ImageType OTHER = ImageData.ImageType.OTHER;
	
	/**
	 * Store ImageData accessible to the script thread
	 */
	private static Map<Thread, ImageData<BufferedImage>> batchImageData = new WeakHashMap<>();

	/**
	 * Store Project accessible to the script thread
	 */
	private static Map<Thread, Project<BufferedImage>> batchProject = new WeakHashMap<>();
	
	/**
	 * Placeholder for the path to the current project.
	 * May be used as follows:
	 * <pre>
	 *   var path = buildFilePath(PROJECT_BASE_DIR, 'subdir', 'name.txt')
	 * </pre>
	 */
	public static final String PROJECT_BASE_DIR = "{%PROJECT}";
	
	
	/**
	 * TODO: Figure out where this should go...
	 * Its purpose is to prompt essential type adapters to be registered so that they function 
	 * within scripts. See https://github.com/qupath/qupath/issues/514
	 */
	static {
		logger.info("Initializing type adapters");
		ObjectClassifiers.ObjectClassifierTypeAdapterFactory.registerSubtype(OpenCVMLClassifier.class);
		
		GsonTools.getDefaultBuilder()
			.registerTypeAdapterFactory(PixelClassifiers.getTypeAdapterFactory())
			.registerTypeAdapterFactory(FeatureExtractors.getTypeAdapterFactory())
			.registerTypeAdapterFactory(ObjectClassifiers.getTypeAdapterFactory())
			.registerTypeAdapterFactory(OpenCVTypeAdapters.getOpenCVTypeAdaptorFactory())
			.registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter());
		
		// Currently, the type adapters are registered within the class... so we need to initialize the class
		@SuppressWarnings("unused")
		var init = new ImageOps();
		@SuppressWarnings("unused")
		var servers = new ImageServers();
		@SuppressWarnings("unused")
		var predicates = new PathObjectPredicates();
		@SuppressWarnings("unused")
		var colorModels = new ColorModels();
		@SuppressWarnings("unused")
		var dnnTools = new DnnTools();
		
	}

	
	private static final Set<Class<?>> CORE_CLASSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			// Core datastructures
			ImageData.class,
			ImageServer.class,
			PathObject.class,
			PathObjectHierarchy.class,
			PathClass.class,
			
			ImageRegion.class,
			RegionRequest.class,
			ImagePlane.class,
			Padding.class,
			
			PixelType.class,

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
			ColorTools.class,
			GeneralTools.class,
			DistanceTools.class,
//			ImageWriter.class,
			ImageWriterTools.class,
			PathClassTools.class,
			GeometryTools.class,
			IJTools.class,
			OpenCVTools.class,
			DnnTools.class,
			TileExporter.class,
			ServerTools.class,
			PixelClassifierTools.class,
			
			DensityMaps.class,
			ColorTransforms.class,
			
			ImageOps.class,
			DelaunayTools.class,
			CellTools.class,
						
			ContourTracing.class,
			
			GroovyCV.class,
			
			// Predicates & filters
			PathObjectFilter.class,
			PathObjectPredicates.class,
			
			// IO classes
			PathIO.class,
			PointIO.class,
			ProjectIO.class,
			UriUpdater.class,
			
			// External classes
			BufferedImage.class
			)));
	
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
		if (Modifier.isStatic(m.getModifiers()))
			sb.append("static ");
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
	public static Collection<Class<?>> getCoreClasses() {
		return CORE_CLASSES;
	}
	
	
	/**
	 * Set the {@link Project} and {@link ImageData} to use for batch processing for the current thread.
	 * @param project
	 * @param imageData
	 */
	public static void setBatchProjectAndImage(final Project<BufferedImage> project, final ImageData<BufferedImage> imageData) {
		setBatchProject(project);
		setBatchImageData(imageData);
	}
	
	/**
	 * Reset the {@link Project} and {@link ImageData} used for batch processing for the current thread.
	 */
	public static void resetBatchProjectAndImage() {
		setBatchProject(null);
		setBatchImageData(null);
	}
	
	/**
	 * Set the ImageData to use for batch processing.  This will be local for the current thread.
	 * @param imageData
	 * @return
	 */
	static ImageData<BufferedImage> setBatchImageData(final ImageData<BufferedImage> imageData) {
		Thread thread = Thread.currentThread();
		logger.trace("Setting image data for {} to {}", thread, imageData);
		if (imageData == null)
			return batchImageData.remove(thread);
		return batchImageData.put(thread, imageData);
	}
	
	
	/**
	 * Set the ImageData to use for batch processing.  This will be local for the current thread.
	 * <p>
	 * @return The ImageData set with setBatchImageData, or null if no ImageData has been set for the current thread.
	 */
	static ImageData<BufferedImage> getBatchImageData() {
		return batchImageData.get(Thread.currentThread());
	}
	
	/**
	 * Set the Project to use for batch processing.  This will be local for the current thread.
	 * @param project
	 * @return
	 */
	static Project<BufferedImage> setBatchProject(final Project<BufferedImage> project) {
		Thread thread = Thread.currentThread();
		logger.trace("Setting project for {} to {}", thread, project);
		if (project == null)
			return batchProject.remove(thread);
		return batchProject.put(thread, project);
	}
	
	
	/**
	 * Set the ImageData to use for batch processing.  This will be local for the current thread.
	 * <p>
	 * @return The ImageData set with setBatchImageData, or null if no ImageData has been set for the current thread.
	 */
	static Project<BufferedImage> getBatchProject() {
		return batchProject.get(Thread.currentThread());
	}
	
	
	
	/**
	 * Load ImageData from a file.
	 * 
	 * @param path path to the file containing ImageData.
	 * @param setBatchData if true, the <code>setBatchImageData(ImageData)</code> will be called if the loading is successful.
	 * @return
	 * @throws IOException 
	 * 
	 * @see #setBatchImageData
	 */
	@Deprecated
	public static ImageData<BufferedImage> loadImageData(final String path, final boolean setBatchData) throws IOException {
		ImageData<BufferedImage> imageData = PathIO.readImageData(new File(resolvePath(path)), null, null, BufferedImage.class);
		if (setBatchData && imageData != null)
			setBatchImageData(imageData);
		return imageData;
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
	 * @deprecated Use instead {@link #makeRGB(int, int, int)} or {@link #makeARGB(int, int, int, int)}
	 */
	@Deprecated
	public static Integer getColorRGB(final int... v) {
		if (v.length == 1)
			return ColorTools.packRGB(v[0], v[0], v[0]);
		if (v.length == 3)
			return ColorTools.packRGB(v[0], v[1], v[2]);
		if (v.length == 4)
			return ColorTools.packARGB(v[3], v[0], v[1], v[2]);
		throw new IllegalArgumentException("Input to getColorRGB must be either 1, 3 or 4 integer values, between 0 and 255!");
	}
	
	/**
	 * Make a packed int representation of an RGB color.
	 * Alpha defaults to 255.
	 * Red, green and blue values should be in the range 0-255; if they are not, they will be clipped.
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 */
	public static Integer makeRGB(int r, int g, int b) {
		return ColorTools.packClippedRGB(r, g, b);
	}

	/**
	 * Make a packed int representation of an ARGB color.
	 * Alpha, red, green and blue values should be in the range 0-255; if they are not, they will be clipped.
	 * @param a
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 */
	public static Integer makeARGB(int a, int r, int g, int b) {
		return ColorTools.packClippedARGB(a, r, g, b);
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
	 * In this implementation, it is the same as calling {@link #getBatchImageData()}.
	 * 
	 * @return
	 * 
	 * @see #getBatchImageData()
	 */
	public static ImageData<BufferedImage> getCurrentImageData() {
		return getBatchImageData();
	}
	
	
	/**
	 * Get the current project.
	 * <p>
	 * In this implementation, it is the same as calling {@link #getBatchProject()}.
	 * 
	 * @return
	 * 
	 * @see #getBatchProject()
	 */
	public static Project<BufferedImage> getProject() {
		return getBatchProject();
	}
	
	/**
	 * Resolve a path, replacing any placeholders. Currently, this means only {@link #PROJECT_BASE_DIR}.
	 * @param path
	 * @return
	 */
	public static String resolvePath(final String path) {
		String base = getProjectBaseDirectory();
		if (base != null)
			return path.replace(PROJECT_BASE_DIR, base);
		else if (path.contains(PROJECT_BASE_DIR))
			throw new IllegalArgumentException("Cannot resolve path '" + path + "' - no project base directory available");
		return
			path;
	}
	
	/**
	 * Build a file path from multiple components.
	 * A common use of this is
	 * <pre>
	 *   String path = buildFilePath(PROJECT_BASE_DIR, "export")
	 * </pre>
	 * @param path
	 * @return
	 */
	public static String buildFilePath(String...path) {
		File file = new File(resolvePath(path[0]));
		for (int i = 1; i < path.length; i++)
			file = new File(file, path[i]);
		return file.getAbsolutePath();
	}
	
	/**
	 * Ensure directories exist for the specified path, calling {@code file.mkdirs()} if not.
	 * @param path the directory path
	 * @return true if a directory was created, false otherwise
	 */
	public static boolean mkdirs(String path) {
		File file = new File(resolvePath(path));
		if (!file.exists())
			return file.mkdirs();
		return false;
	}
	
	/**
	 * Query if a file exists.
	 * @param path full file path
	 * @return true if the file exists, false otherwise
	 */
	public static boolean fileExists(String path) {
		return new File(resolvePath(path)).exists();
	}

	/**
	 * Query if a file path corresponds to a directory.
	 * @param path full file path
	 * @return true if the file exists and is a directory, false otherwise
	 */
	public static boolean isDirectory(String path) {
		return new File(resolvePath(path)).isDirectory();
	}

	
	/**
	 * Get the base directory for the currently-open project, or null if no project is open.
	 * 
	 * This can be useful for setting e.g. save directories relative to the current project.
	 * 
	 * @return
	 */
	private static String getProjectBaseDirectory() {
		File dir = Projects.getBaseDirectory(getProject());
		return dir == null ? null : dir.getAbsolutePath();
	}
	
	/**
	 * Get the project entry for the currently-open image within the current project, 
	 * or null if no project/image is open.
	 * 
	 * @return
	 */
	public static ProjectImageEntry<BufferedImage> getProjectEntry() {
		Project<BufferedImage> project = getProject();
		var imageData = getCurrentImageData();
		if (project == null || imageData == null)
			return null;
		return project.getEntry(imageData);
	}
	
	
	/**
	 * Get the metadata value from the current project entry for the specified key, 
	 * or null if no such metadata value exists (or no project entry is open).
	 * 
	 * @param key
	 * @return
	 */
	public static String getProjectEntryMetadataValue(final String key) {
		ProjectImageEntry<BufferedImage> entry = getProjectEntry();
		if (entry == null)
			return null;
		return entry.getMetadataValue(key);
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
	 * Get the name of the current image.
	 * This first checks the name associated with {@link #getProjectEntry()}, if available.
	 * If no name is found (e.g. because no project is in use, then the name is extracted 
	 * from the metadata of {@link #getCurrentServer()}.
	 * If this is also missing, then {@code null} is returned.
	 * @return
	 */
	public static String getCurrentImageName() {
		var entry = getProjectEntry();
		if (entry != null && !entry.getImageName().isBlank())
			return entry.getImageName();
		var server = getCurrentServer();
		if (server != null)
			return server.getMetadata().getName();
		return null;
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
		var selected = hierarchy.getSelectionModel().getSelectedObject();
		if (selected == null && !hierarchy.getSelectionModel().noSelection())
			logger.debug("getSelectedObject() is null because there is no primary selected object, "
					+ "you might want getSelectedObjects() instead");
		return selected;
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
	 * Add the specified shape measurements to the current selected objects of the current image.
	 * If no features are specified, all will be added.
	 * @param features
	 */
	public static void addShapeMeasurements(String... features) {
		var imageData = getCurrentImageData();
		Collection<PathObject> selected = imageData == null ? Collections.emptyList() : imageData.getHierarchy().getSelectionModel().getSelectedObjects();
		if (selected.isEmpty()) {
			logger.debug("Cannot add shape measurements (no objects selected)");
			return;
		}
		if (features.length == 0)
			addShapeMeasurements(imageData, new ArrayList<>(selected));
		else if (features.length == 1)
			addShapeMeasurements(imageData, new ArrayList<>(selected), features[0]);
		else
			addShapeMeasurements(imageData, new ArrayList<>(selected), features[0], Arrays.copyOfRange(features, 1, features.length));
	}

	/**
	 * Add shape measurements to the specified objects.
	 * <p>
	 * Note {@link #addShapeMeasurements(ImageData, Collection, ShapeFeatures...)} can be used without specifying any features.
	 * This method requires at least one feature so as to have a distinct method signature.
	 * 
	 * @param imageData the image to which the objects belong. This is used to determine pixel calibration and to fire an update event. May be null.
	 * @param pathObjects the objects that should be measured
	 * @param feature first feature to add
	 * @param additionalFeatures optional array of Strings specifying the features to add
	 */
	public static void addShapeMeasurements(ImageData<?> imageData, Collection<? extends PathObject> pathObjects, String feature, String... additionalFeatures) {
		addShapeMeasurements(imageData, pathObjects, parseEnumOptions(ShapeFeatures.class, feature, additionalFeatures));
	}
	
	/**
	 * Add shape measurements to the specified objects.
	 * @param imageData the image to which the objects belong. This is used to determine pixel calibration and to fire an update event. May be null.
	 * @param pathObjects the objects that should be measured
	 * @param features the specific features to add. If none are specified, all available features will be added.
	 */
	public static void addShapeMeasurements(ImageData<?> imageData, Collection<? extends PathObject> pathObjects, ShapeFeatures... features) {
		if (pathObjects.isEmpty())
			return;
		if (imageData == null) {
			ObjectMeasurements.addShapeMeasurements(pathObjects, null, features);
		} else {
			var hierarchy = imageData.getHierarchy();
			ObjectMeasurements.addShapeMeasurements(pathObjects, imageData.getServer().getPixelCalibration(), features);
			hierarchy.fireObjectMeasurementsChangedEvent(hierarchy, pathObjects);			
		}
	}
	
	/**
	 * Parse an array of strings into a compatible enum.
	 * @param <T>
	 * @param classEnum
	 * @param option
	 * @param additionalOptions
	 * @return
	 */
	static <T extends Enum<T>> T[] parseEnumOptions(Class<T> classEnum, String option, String... additionalOptions) {
		if (option == null && additionalOptions.length == 0)
			return (T[])java.lang.reflect.Array.newInstance(classEnum, 0);
		var objectOptions = new LinkedHashSet<T>();
		var allOptions = option == null ? additionalOptions : ObjectArrays.concat(option, additionalOptions);
		for (var optionName : allOptions) {
			if (optionName == null)
				continue;
			try {
				var temp = Enum.valueOf(classEnum, optionName);
				objectOptions.add(temp);
			} catch (Exception e) {
				logger.warn("Could not parse option {}", optionName);
			}
		}
		var array = (T[])java.lang.reflect.Array.newInstance(classEnum, objectOptions.size());
		return objectOptions.toArray(array);
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
	 * @param imageData
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
	 * @param imageData 
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
	 * @param className the full Java class name for the plugin
	 * @param args any arguments required by the plugin (usually a JSON-encoded map)
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
	 * @param className the full Java class name for the plugin
	 * @param imageData the ImageData to which the plugin should be applied
	 * @param args any arguments required by the plugin (usually a JSON-encoded map)
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
	 * Run the specified plugin on the current {@code ImageData}, using a map for arguments.
	 * 
	 * @param className the full Java class name for the plugin
	 * @param args the arguments
	 * @return
	 * @throws InterruptedException
	 * 
	 * @see #getCurrentImageData
	 * 
	 * @since v0.4.0
	 * @implNote this is currently a convenience method that converts the arguments to a JSON-encoded string and calls 
	 *           {@link #runPlugin(String, String)}
	 */
	public static boolean runPlugin(final String className, final Map<String, ?> args) throws InterruptedException {
		var json = args == null ? "" : GsonTools.getInstance().toJson(args);
		return runPlugin(className, json);
	}
	
	/**
	 * Run the specified plugin on the specified {@code ImageData}, using a map for arguments.
	 * 
	 * @param className the full Java class name for the plugin
	 * @param imageData the ImageData to which the plugin should be applied
	 * @param args the arguments
	 * @return
	 * @throws InterruptedException
	 * 
	 * @since v0.4.0
	 * @implNote this is currently a convenience method that converts the arguments to a JSON-encoded string and calls 
	 *           {@link #runPlugin(String, ImageData, String)}
	 */
	public static boolean runPlugin(final String className, final ImageData<?> imageData, final Map<String, ?> args) throws InterruptedException {
		var json = args == null ? "" : GsonTools.getInstance().toJson(args);
		return runPlugin(className, imageData, json);
	}
	
	/**
	 * Run the specified plugin on the current {@code ImageData}, with Groovy keyword argument support.
	 * <p>
	 * This reason is that this Groovy supports keyword arguments, but only if a {@link Map} is the first argument to a method.
	 * This therefore makes it possible to change only non-default arguments with a call like this:
	 * <pre><code>
	 * runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', cellExpansionMicrons: 3, detectionImage: "DAPI", threshold: 1.0)
	 * </code></pre>
	 * It is not even essential to provide the required {@code className} in the first position.
	 * 
	 * @param className the full Java class name for the plugin
	 * @param args the arguments
	 * @return
	 * @throws InterruptedException
	 * 
	 * @since v0.4.0
	 * @implNote this calls {@link #runPlugin(String, Map)}
	 */
	public static boolean runPlugin(final Map<String, ?> args, final String className) throws InterruptedException {
		return runPlugin(className, args);
	}

	/**
	 * Run the specified plugin on the specified {@code ImageData}, with Groovy keyword argument support.
	 * <p>
	 * This reason is that this Groovy supports keyword arguments, but only if a {@link Map} is the first argument to a method.
	 * This therefore makes it possible to change only non-default arguments with a call like this:
	 * <pre><code>
	 * runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', imageData, cellExpansionMicrons: 3, detectionImage: "DAPI", threshold: 1.0)
	 * </code></pre>
	 * It is not even essential to provide the required {@code className} in the first position.
	 * 
	 * @param className the full Java class name for the plugin
	 * @param args the arguments
	 * @param imageData 
	 * @return
	 * @throws InterruptedException
	 * 
	 * @since v0.4.0
	 * @implNote this calls {@link #runPlugin(String, ImageData, Map)}
	 */
	public static boolean runPlugin(final Map<String, ?> args, final String className, final ImageData<?> imageData) throws InterruptedException {
		return runPlugin(className, imageData, args);
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
	 * @param includeRootObject
	 * @return
	 * @see #getCurrentHierarchy
	 */
	public static PathObject[] getAllObjects(boolean includeRootObject) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return new PathObject[0];
		var objList = hierarchy.getFlattenedObjectList(null);
		if (includeRootObject)
			return objList.toArray(new PathObject[0]);
		return objList.parallelStream().filter(e -> !e.isRootObject()).toArray(PathObject[]::new);
	}

	/**
	 * Get an array of all objects in the current hierarchy. 
	 * Note that this includes the root object.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy
	 */
	public static PathObject[] getAllObjects() {
		return getAllObjects(true);
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
		createSelectAllObject(setSelected, 0, 0);
	}

	/**
	 * Build an {@link ImageServer} with a specified class.
	 * 
	 * @param path image path (usually a file path or URI)
	 * @param args optional arguments
	 * @param cls generic type for the server (usually BufferedImage)
	 * @return an {@link ImageServer}, if one could be build from the supplied arguments
	 * 
	 * @throws IOException if unable to build the server
	 * @deprecated In the usual case where {@link BufferedImage} is the class, use {@link #buildServer(String, String...)} instead 
	 *             because it handles default args.
	 * @see ImageServers#buildServer(URI, String...)
	 */
	@Deprecated
	public static <T> ImageServer<T> buildServer(String path, Class<T> cls, String... args) throws IOException {
		return ImageServerProvider.buildServer(path, cls, args);
	}
	
	/**
	 * Build an {@link ImageServer} with the class {@link BufferedImage}.
	 * 
	 * @param path image path (usually a file path or URI)
	 * @param args optional arguments
	 * @return an {@link ImageServer}, if one could be build from the supplied arguments
	 * 
	 * @throws IOException if unable to build the server
	 * @apiNote In v0.3 the behavior of this method changed to support more default arguments.
	 * @see ImageServers#buildServer(URI, String...)
	 */
	public static ImageServer<BufferedImage> buildServer(String path, String... args) throws IOException {
		return ImageServers.buildServer(path, args);
	}
	
	/**
	 * Build an {@link ImageServer} with the class {@link BufferedImage}.
	 * 
	 * @param uri image URI
	 * @param args optional arguments
	 * @return an {@link ImageServer}, if one could be build from the supplied arguments
	 * 
	 * @throws IOException if unable to build the server
	 * @since v0.3
	 * @see ImageServers#buildServer(URI, String...)
	 */
	public static ImageServer<BufferedImage> buildServer(URI uri, String... args) throws IOException {
		return ImageServers.buildServer(uri, args);
	}
	
	
	/**
	 * Create an annotation for the entire width and height of the current image data, on the default plane (z-slice, time point).
	 * 
	 * @param setSelected if true, select the object that was created after it is added to the hierarchy
	 * @param z z-slice index for the annotation
	 * @param t timepoint index for the annotation
	 */
	public static void createSelectAllObject(final boolean setSelected, int z, int t) {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return;
		ImageServer<?> server = imageData.getServer();
		PathObject pathObject = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getPlane(z, t))
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
	 * Delete the selected objects from the current hierarchy, optionally keeping their child (descendant) objects.
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
	 * Set selected objects to contain all objects.
	 * 
	 * @param hierarchy 
	 * @param includeRootObject 
	 */
	public static void selectAllObjects(PathObjectHierarchy hierarchy, boolean includeRootObject) {
		var allObjs = hierarchy.getFlattenedObjectList(null);
		if (!includeRootObject)
			allObjs = allObjs.stream().filter(e -> !e.isRootObject()).collect(Collectors.toList());
		if (hierarchy != null)
			hierarchy.getSelectionModel().setSelectedObjects(allObjs, null);
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
	 * Set one or more objects to be selected within the specified hierarchy.
	 * @param hierarchy
	 * @param pathObjects
	 */
	public static void selectObjects(final PathObjectHierarchy hierarchy, final PathObject... pathObjects) {
		if (pathObjects.length == 0)
			return;
		if (pathObjects.length == 1)
			hierarchy.getSelectionModel().setSelectedObject(pathObjects[0]);
		else
			hierarchy.getSelectionModel().setSelectedObjects(Arrays.asList(pathObjects), null);
	}
	
	/**
	 * Set one or more objects to be selected within the current hierarchy.
	 * @param pathObjects
	 */
	public static void selectObjects(final PathObject... pathObjects) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			selectObjects(hierarchy, pathObjects);
	}

	/**
	 * Get a list of all objects in the specified hierarchy according to a specified predicate.
	 * @param hierarchy 
	 * @param predicate
	 * @return
	 */
	public static List<PathObject> getObjects(final PathObjectHierarchy hierarchy, final Predicate<PathObject> predicate) {
		return hierarchy.getFlattenedObjectList(null).stream().filter(predicate).collect(Collectors.toList());
	}

	/**
	 * Set selected objects to contain (only) all objects in the specified hierarchy according to a specified predicate.
	 * @param hierarchy 
	 * @param predicate
	 */
	public static void selectObjects(final PathObjectHierarchy hierarchy, final Predicate<PathObject> predicate) {
		hierarchy.getSelectionModel().setSelectedObjects(getObjects(hierarchy, predicate), null);
	}
	
	/**
	 * Set objects that are a subclass of a specified class.
	 * Not to be confused with {@link #selectObjectsByPathClass(PathClass...)} and {@link #selectObjectsByClassification(String...)}.
	 * 
	 * @param cls
	 */
	public static void selectObjectsByClass(final Class<? extends PathObject> cls) {
		selectObjects(p -> cls.isInstance(p));
	}
	
	/**
	 * Set objects that are a subclass of a specified class.
	 * Not to be confused with {@link #selectObjectsByPathClass(PathObjectHierarchy, PathClass...)} and {@link #selectObjectsByClassification(PathObjectHierarchy, String...)}.
	 * 
	 * @param hierarchy 
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
	 * @param includeMissing 
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
	 * @param includeMissing 
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
	
	/**
	 * Select all tile objects in the specified hierarchy.
	 * @param hierarchy
	 */
	public static void selectTiles(final PathObjectHierarchy hierarchy) {
		selectObjectsByClass(hierarchy, PathTileObject.class);
	}

	/**
	 * Select all tile objects in the current hierarchy.
	 */
	public static void selectTiles() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			selectTiles(hierarchy);
	}
	
	/**
	 * Select objects for the current hierarchy that have one of the specified classifications.
	 * @param pathClassNames one or more classification names, which may be converted to a {@link PathClass} with {@link #getPathClass(String)}
	 */
	public static void selectObjectsByClassification(final String... pathClassNames) {
		var hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			selectObjectsByClassification(hierarchy, pathClassNames);
	}
	
	/**
	 * Select objects for the current hierarchy that have one of the specified {@link PathClass} classifications assigned.
	 * @param pathClasses one or more classifications
	 * 
	 * @see #selectObjectsByPathClass(PathClass...)
	 */
	public static void selectObjectsByPathClass(final PathClass... pathClasses) {
		var hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			selectObjectsByPathClass(hierarchy, pathClasses);
	}

	/**
	 * Select objects for the specified hierarchy that have one of the specified classifications.
	 * @param hierarchy the hierarchy containing objects that may be selected
	 * @param pathClassNames one or more classification names, which may be converted to a {@link PathClass} with {@link #getPathClass(String)}
	 * 
	 * @see #selectObjectsByPathClass(PathObjectHierarchy, PathClass...)
	 */
	public static void selectObjectsByClassification(final PathObjectHierarchy hierarchy, final String... pathClassNames) {
		PathClass[] pathClasses;
		if (pathClassNames == null)
			pathClasses = new PathClass[1];
		else
			pathClasses = Arrays.stream(pathClassNames).map(s -> getPathClass(s)).toArray(PathClass[]::new);
		selectObjectsByPathClass(hierarchy, pathClasses);
	}
	
	/**
	 * Select objects for the specified hierarchy that have one of the specified {@link PathClass} classifications assigned.
	 * @param hierarchy the hierarchy containing objects that may be selected
	 * @param pathClasses one or more classifications
	 */
	public static void selectObjectsByPathClass(final PathObjectHierarchy hierarchy, final PathClass... pathClasses) {
		Set<PathClass> pathClassSet;
		if (pathClasses == null)
			pathClassSet = Set.of((PathClass)null);
		else
			pathClassSet = Arrays.stream(pathClasses).map(p -> p == PathClassFactory.getPathClassUnclassified() ? null : p).collect(Collectors.toCollection(HashSet::new));
		selectObjects(hierarchy, p -> pathClassSet.contains(p.getPathClass()));
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
	 * Export all objects (excluding root object) to an output file as GeoJSON.
	 * 
	 * @param path 
	 * @param option 
	 * @param additionalOptions
	 * @throws IOException
	 */
	public static void exportAllObjectsToGeoJson(String path, String option, String... additionalOptions) throws IOException {
		exportAllObjectsToGeoJson(path, parseEnumOptions(GeoJsonExportOptions.class, option, additionalOptions));
	}
	
	/**
	 * Export all objects (excluding root object) to an output file as GeoJSON.
	 * 
	 * @param path 
	 * @param options
	 * @throws IOException
	 */
	public static void exportAllObjectsToGeoJson(String path, GeoJsonExportOptions... options) throws IOException {
		exportObjectsToGeoJson(Arrays.asList(getAllObjects(false)), path, options);
	}
	
	/**
	 * Export the selected objects to an output file as GeoJSON.
	 * 
	 * @param path 
	 * @param option 
	 * @param additionalOptions
	 * @throws IOException
	 */
	public static void exportSelectedObjectsToGeoJson(String path, String option, String... additionalOptions) throws IOException {
		exportSelectedObjectsToGeoJson(path, parseEnumOptions(GeoJsonExportOptions.class, option, additionalOptions));
	}
	
	/**
	 * Export the selected objects to an output file as GeoJSON.
	 * 
	 * @param path 
	 * @param options
	 * @throws IOException
	 */
	public static void exportSelectedObjectsToGeoJson(String path, GeoJsonExportOptions... options) throws IOException {
		exportObjectsToGeoJson(getSelectedObjects(), path, options);
	}
	
	/**
	 * Export specified objects to an output file as GeoJSON.
	 * 
	 * @param pathObjects 
	 * @param path 
	 * @param option
	 * @param additionalOptions
	 * @throws IOException
	 */
	public static void exportObjectsToGeoJson(Collection<? extends PathObject> pathObjects, String path, String option, String... additionalOptions) throws IOException {
		exportObjectsToGeoJson(pathObjects, path, parseEnumOptions(GeoJsonExportOptions.class, option, additionalOptions));
	}
	
	/**
	 * Export specified objects to an output file as GeoJSON.
	 * 
	 * @param pathObjects 
	 * @param path 
	 * @param options
	 * @throws IOException
	 */
	public static void exportObjectsToGeoJson(Collection<? extends PathObject> pathObjects, String path, GeoJsonExportOptions... options) throws IOException {
		PathIO.exportObjectsAsGeoJSON(new File(path), pathObjects, options);
	}

	/**
	 * Import all {@link PathObject}s from the given file. <p>
	 * {@code IllegalArgumentException} is thrown if the file is not compatible. <br>
	 * {@code FileNotFoundException} is thrown if the file is not found. <br>
	 * {@code IOException} is thrown if an error occurs while reading the file. <br>
	 * {@code ClassNotFoundException} should never occur naturally (except through a change in the code).
	 * 
	 * 
	 * @param path
	 * @return success
	 * @throws FileNotFoundException
	 * @throws IllegalArgumentException
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public static boolean importObjectsFromFile(String path) throws FileNotFoundException, IllegalArgumentException, IOException, ClassNotFoundException {
		var objs = PathIO.readObjects(new File(path));
		return getCurrentHierarchy().addPathObjects(objs);
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
			try (var ml = pathObject.getMeasurementList()) {
				ml.removeMeasurements(measurementNames);
			}
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
	 * Clear the measurement lists for all detections in a hierarchy (including sub-classes of detections).
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
	 * Clear the measurement lists for all TMA core objects in a hierarchy.
	 * @param hierarchy
	 */
	public static void clearTMACoreMeasurements(PathObjectHierarchy hierarchy) {
		if (hierarchy != null)
			clearMeasurements(hierarchy, TMACoreObject.class);
	}
	
	/**
	 * Clear the measurement lists for all TMA core objects in the current hierarchy.
	 */
	public static void clearTMACoreMeasurements() {
		clearTMACoreMeasurements(getCurrentHierarchy());
	}
	
	/**
	 * Clear the measurement lists for objects of a specific class in a hierarchy (subclasses are not included!).
	 * @param hierarchy
	 * @param cls 
	 */
	public static void clearMeasurements(PathObjectHierarchy hierarchy, Class<? extends PathObject> cls) {
		if (hierarchy != null)
			clearMeasurements(hierarchy, hierarchy.getObjects(null, null).stream().filter(p -> p.getClass().equals(cls)).toArray(PathObject[]::new));
	}
	
	/**
	 * Clear the measurement lists for objects of a specific class in the current hierarchy (subclasses are not included!).
	 * @param cls 
	 */
	public static void clearMeasurements(Class<? extends PathObject> cls) {
		clearMeasurements(getCurrentHierarchy(), cls);
	}
	
	/**
	 * Clear the measurement lists for all detections in the current hierarchy.
	 */
	public static void clearMeasurements() {
		clearDetectionMeasurements(getCurrentHierarchy());
	}
	
	/**
	 * Clear the measurement lists for all cells in a hierarchy.
	 * @param hierarchy
	 */
	public static void clearCellMeasurements(PathObjectHierarchy hierarchy) {
		if (hierarchy != null)
			clearMeasurements(hierarchy, hierarchy.getCellObjects());
	}
	
	/**
	 * Clear the measurement lists for all cells in the current hierarchy.
	 */
	public static void clearCellMeasurements() {
		clearCellMeasurements(getCurrentHierarchy());
	}
	
	/**
	 * Clear the measurement lists for all tiles in a hierarchy.
	 * @param hierarchy
	 */
	public static void clearTileMeasurements(PathObjectHierarchy hierarchy) {
		if (hierarchy != null)
			clearMeasurements(hierarchy, hierarchy.getTileObjects());
	}
	
	/**
	 * Clear the measurement lists for all tiles in the current hierarchy.
	 */
	public static void clearTileMeasurements() {
		clearTileMeasurements(getCurrentHierarchy());
	}
	
	/**
	 * Clear the measurement lists for the root object.
	 * @param hierarchy
	 */
	public static void clearRootMeasurements(PathObjectHierarchy hierarchy) {
		if (hierarchy != null)
			clearMeasurements(hierarchy, hierarchy.getRootObject());
	}
	
	/**
	 * Clear the measurement lists for the root object.
	 */
	public static void clearRootMeasurements() {
		clearRootMeasurements(getCurrentHierarchy());
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
	public static void setIntensityClassifications(final Collection<? extends PathObject> pathObjects, final String measurementName, final double... thresholds) {
		PathObjectTools.setIntensityClassifications(pathObjects, measurementName, thresholds);
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
	 * Set the intensity classifications for detections in the specified hierarchy.
	 * @param hierarchy 
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
	public static void setDetectionIntensityClassifications(final PathObjectHierarchy hierarchy, final String measurementName, final double... thresholds) {
		setIntensityClassifications(hierarchy, PathDetectionObject.class, measurementName, thresholds);
	}
	
	/**
	 * Set the intensity classifications for detections in the current hierarchy.
	 * 
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
	public static void setDetectionIntensityClassifications(final String measurementName, final double... thresholds) {
		setDetectionIntensityClassifications(getCurrentHierarchy(), measurementName, thresholds);
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
	 * Write the output of applying a pixel classifier to an image. The writer will be determined based on the file extension.
	 * @param imageData image to which the classifier should be applied
	 * @param classifier pixel classifier
	 * @param path output file path
	 * @throws IOException
	 */
	public static void writePredictionImage(ImageData<BufferedImage> imageData, PixelClassifier classifier, String path) throws IOException {
		if (imageData == null)
			imageData = getCurrentImageData();
		var server = PixelClassifierTools.createPixelClassificationServer(imageData, classifier);
		ImageWriterTools.writeImage(server, path);
	}
	
	/**
	 * Write the output of applying a pixel classifier to the current image image.
	 * @param classifierName name of the classifier, see {@link #loadPixelClassifier(String)}
	 * @param path output file path
	 * @throws IOException
	 */
	public static void writePredictionImage(String classifierName, String path) throws IOException {
		writePredictionImage(getCurrentImageData(), loadPixelClassifier(classifierName), path);
	}
	
	/**
	 * Write the output of applying a density map to an image. The writer will be determined based on the file extension.
	 * @param imageData image to which the classifier should be applied
	 * @param densityMap the density map
	 * @param path output file path
	 * @throws IOException
	 */
	public static void writeDensityMapImage(ImageData<BufferedImage> imageData, DensityMapBuilder densityMap, String path) throws IOException {
		if (imageData == null)
			imageData = getCurrentImageData();
		var server = densityMap.buildServer(imageData);
		ImageWriterTools.writeImage(server, path);
	}
	
	/**
	 * Write the output of applying a density map to the current image image.
	 * @param densityMapName name of the density map, see {@link #loadDensityMap(String)}
	 * @param path output file path
	 * @throws IOException
	 */
	public static void writeDensityMapImage(String densityMapName, String path) throws IOException {
		writeDensityMapImage(getCurrentImageData(), loadDensityMap(densityMapName), path);
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
	 * Compute the distance for all detection object centroids to the closest detection with each valid, not-ignored classification and add 
	 * the result to the detection measurement list.
	 * @param imageData
	 * @param splitClassNames 
	 * @see DistanceTools#detectionCentroidDistances(ImageData, boolean)
	 */
	public static void detectionCentroidDistances(ImageData<?> imageData, boolean splitClassNames) {
		DistanceTools.detectionCentroidDistances(imageData, splitClassNames);
	}
	
	/**
	 * Compute the distance for all detection object centroids to the closest detection with each valid, not-ignored classification and add 
	 * the result to the detection measurement list for the current ImageData - without splitting class names.
	 * 
	 * @deprecated retained only for compatibility of v0.2.0 milestone releases; use instead #detectionCentroidDistances(boolean)
	 * 
	 * @see DistanceTools#detectionCentroidDistances(ImageData, boolean)
	 */
	@Deprecated
	public static void detectionCentroidDistances() {
		detectionCentroidDistances(false);
	}
	
	/**
	 * Compute the distance for all detection object centroids to the closest detection with each valid, not-ignored classification and add 
	 * the result to the detection measurement list for the current ImageData.
	 * @param splitClassNames 
	 * @see DistanceTools#detectionCentroidDistances(ImageData, boolean)
	 */
	public static void detectionCentroidDistances(boolean splitClassNames) {
		detectionCentroidDistances(getCurrentImageData(), splitClassNames);
	}
	
	/**
	 * Compute the distance for all detection object centroids to the closest annotation with each valid, not-ignored classification and add 
	 * the result to the detection measurement list.
	 * If the centroid falls inside an annotation, the distance is zero.
	 * @param imageData
	 * @param splitClassNames 
	 * @see DistanceTools#detectionToAnnotationDistances(ImageData, boolean)
	 * @see QP#detectionToAnnotationDistancesSigned(ImageData, boolean)
	 */
	public static void detectionToAnnotationDistances(ImageData<?> imageData, boolean splitClassNames) {
		DistanceTools.detectionToAnnotationDistances(imageData, splitClassNames);
	}
	
	/**
	 * Compute the distance for all detection object centroids to the closest annotation with each valid, not-ignored classification and add 
	 * the result to the detection measurement list for the current ImageData - without splitting class names.
	 * 
	 * @deprecated retained only for compatibility of v0.2.0 milestone releases; use instead #detectionToAnnotationDistances(boolean)
	 * 
	 */
	@Deprecated
	public static void detectionToAnnotationDistances() {
		detectionToAnnotationDistances(false);
	}
	
	/**
	 * Compute the distance for all detection object centroids to the closest annotation with each valid, not-ignored classification and add 
	 * the result to the detection measurement list for the current ImageData.
	 * If the centroid falls inside an annotation, the distance is zero.
	 * @param splitClassNames 
	 * @see DistanceTools#detectionToAnnotationDistances(ImageData, boolean)
	 * @see QP#detectionToAnnotationDistancesSigned(boolean)
	 */
	public static void detectionToAnnotationDistances(boolean splitClassNames) {
		detectionToAnnotationDistances(getCurrentImageData(), splitClassNames);
	}
	
	/**
	 * Compute the signed distance for all detection object centroids to the closest annotation with each valid, not-ignored classification and add 
	 * the result to the detection measurement list.
	 * If the centroid falls inside an annotation, the negative distance to the annotation boundary is used.
	 * @param imageData
	 * @param splitClassNames 
	 * @see DistanceTools#detectionToAnnotationDistancesSigned(ImageData, boolean)
	 * @see QP#detectionToAnnotationDistances(ImageData, boolean)
	 * @since v0.4.0
	 */
	public static void detectionToAnnotationDistancesSigned(ImageData<?> imageData, boolean splitClassNames) {
		DistanceTools.detectionToAnnotationDistancesSigned(imageData, splitClassNames);
	}
	
	/**
	 * Compute the signed distance for all detection object centroids to the closest annotation with each valid, not-ignored classification and add 
	 * the result to the detection measurement list for the current ImageData.
	 * If the centroid falls inside an annotation, the negative distance to the annotation boundary is used.
	 * @param splitClassNames 
	 * @see DistanceTools#detectionToAnnotationDistancesSigned(ImageData, boolean)
	 * @see QP#detectionToAnnotationDistances(boolean)
	 * @since v0.4.0
	 */
	public static void detectionToAnnotationDistancesSigned(boolean splitClassNames) {
		detectionToAnnotationDistancesSigned(getCurrentImageData(), splitClassNames);
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
	
	
	/**
	 * Resolve the location of annotations in the current hierarchy by setting parent/child relationships.
	 */
	public static void resolveHierarchy() {
		resolveHierarchy(getCurrentHierarchy());
	}
	
	/**
	 * Resolve the location of annotations in the specified hierarchy by setting parent/child relationships.
	 * 
	 * @param hierarchy
	 */
	public static void resolveHierarchy(PathObjectHierarchy hierarchy) {
		if (hierarchy == null)
			return;
		hierarchy.resolveHierarchy();
	}
	
	/**
	 * Insert objects into the hierarchy, resolving their location and setting parent/child relationships.
	 * 
	 * @param pathObjects
	 */
	public static void insertObjects(Collection<? extends PathObject> pathObjects) {
		var hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			hierarchy.insertPathObjects(pathObjects);
	}	
	
	/**
	 * Insert object into the hierarchy, resolving its location and setting parent/child relationships.
	 * 
	 * @param pathObject
	 */
	public static void insertObjects(PathObject pathObject) {
		var hierarchy = getCurrentHierarchy();
		if (hierarchy != null)
			hierarchy.insertPathObject(pathObject, true);
	}	
	
	private static boolean isFinite(Number val) {
		return val != null && Double.isFinite(val.doubleValue());
	}
	
	/**
	 * Merge point annotations sharing the same {@link PathClass} and {@link ImagePlane} as the selected annotations
	 * of the current hierarchy, creating multi-point annotations for all matching points and removing the (previously-separated) annotations.
	 * 
	 * @return true if changes are made to the hierarchy, false otherwise
	 */
	public static boolean mergePointsForAllClasses() {
		return PathObjectTools.mergePointsForAllClasses(getCurrentHierarchy());
	}
	
	/**
	 * Merge point annotations sharing the same {@link PathClass} and {@link ImagePlane} for the current hierarchy, 
	 * creating multi-point annotations for all matching points and removing the (previously-separated) annotations.
	 * 
	 * @return true if changes are made to the hierarchy, false otherwise
	 */
	public static boolean mergePointsForSelectedObjectClasses() {
		return PathObjectTools.mergePointsForSelectedObjectClasses(getCurrentHierarchy());
	}
	
	/**
	 * Duplicate the selected annotations in the current hierarchy.
	 * 
	 * @return true if changes are made to the hierarchy, false otherwise
	 */
	public static boolean duplicateSelectedAnnotations() {
		return PathObjectTools.duplicateSelectedAnnotations(getCurrentHierarchy());
	}
	
	/**
	 * Merge annotations for the current hierarchy.
	 * @param annotations the annotations to merge
	 * @return true if changes were made the hierarchy, false otherwise
	 */
	public static boolean mergeAnnotations(final Collection<PathObject> annotations) {
		return mergeAnnotations(getCurrentHierarchy(), annotations);
	}
	
	/**
	 * Merge the currently-selected annotations of the current hierarchy to create a new annotation containing the union of their ROIs.
	 * <p>
	 * Note:
	 * <ul>
	 * <li>The existing annotations will be removed from the hierarchy if possible, therefore should be duplicated first 
	 * if this is not desired.</li>
	 * <li>The new object will be set to be the selected object in the hierarchy (which can be used to retrieve it if needed).</li>
	 * </ul>
	 * 
	 * @return true if changes are made to the hierarchy, false otherwise
	 */
	public static boolean mergeSelectedAnnotations() {
		return mergeSelectedAnnotations(getCurrentHierarchy());
	}
	
	/**
	 * Merge the specified annotations to create a new annotation containing the union of their ROIs.
	 * <p>
	 * Note:
	 * <ul>
	 * <li>The existing annotations will be removed from the hierarchy if possible, therefore should be duplicated first 
	 * if this is not desired.</li>
	 * <li>The new object will be set to be the selected object in the hierarchy (which can be used to retrieve it if needed).</li>
	 * </ul>
	 * 
	 * @param hierarchy
	 * @param annotations
	 * @return true if changes are made to the hierarchy, false otherwise
	 */
	public static boolean mergeAnnotations(final PathObjectHierarchy hierarchy, final Collection<PathObject> annotations) {
		if (hierarchy == null)
			return false;
		
		// Get all the selected annotations with area
		ROI shapeNew = null;
		List<PathObject> merged = new ArrayList<>();
		Set<PathClass> pathClasses = new HashSet<>();
		for (PathObject annotation : annotations) {
			if (annotation.isAnnotation() && annotation.hasROI() && (annotation.getROI().isArea() || annotation.getROI().isPoint())) {
				if (shapeNew == null)
					shapeNew = annotation.getROI();//.duplicate();
				else if (shapeNew.getImagePlane().equals(annotation.getROI().getImagePlane()))
					shapeNew = RoiTools.combineROIs(shapeNew, annotation.getROI(), RoiTools.CombineOp.ADD);
				else {
					logger.warn("Cannot merge ROIs across different image planes!");
					return false;
				}
				if (annotation.getPathClass() != null)
					pathClasses.add(annotation.getPathClass());
				merged.add(annotation);
			}
		}
		// Check if we actually merged anything
		if (merged.isEmpty() || merged.size() == 1)
			return false;
	
		// Create and add the new object, removing the old ones
		PathObject pathObjectNew = PathObjects.createAnnotationObject(shapeNew);
		if (pathClasses.size() == 1)
			pathObjectNew.setPathClass(pathClasses.iterator().next());
		else
			logger.warn("Cannot assign class unambiguously - " + pathClasses.size() + " classes represented in selection");
		hierarchy.removeObjects(merged, true);
		hierarchy.addPathObject(pathObjectNew);
		hierarchy.getSelectionModel().setSelectedObject(pathObjectNew);
		//				pathObject.removePathObjects(children);
		//				pathObject.addPathObject(pathObjectNew);
		//				hierarchy.fireHierarchyChangedEvent(pathObject);
		return true;
	}

	/**
	 * Merge the currently-selected annotations to create a new annotation containing the union of their ROIs.
	 * <p>
	 * Note:
	 * <ul>
	 * <li>The existing annotations will be removed from the hierarchy if possible, therefore should be duplicated first 
	 * if this is not desired.</li>
	 * <li>The new object will be set to be the selected object in the hierarchy (which can be used to retrieve it if needed).</li>
	 * </ul>
	 * 
	 * @param hierarchy
	 * @return
	 */
	public static boolean mergeSelectedAnnotations(final PathObjectHierarchy hierarchy) {
		return hierarchy == null ? false : mergeAnnotations(hierarchy, hierarchy.getSelectionModel().getSelectedObjects());
	}
	
	/**
	 * Make an annotation for the current {@link ImageData}, for which the ROI is obtained by subtracting 
	 * the existing ROI from the ROI of its parent object (or entire image if no suitable parent object is available).
	 * 
	 * @param pathObject the existing object defining the ROI to invert
	 * @return true if an inverted annotation is added to the hierarchy, false otherwise
	 */
	public static boolean makeInverseAnnotation(final PathObject pathObject) {
		return makeInverseAnnotation(getCurrentImageData(), pathObject);
	}
	

	/**
	 * Make an annotation for the specified {@link ImageData}, for which the ROI is obtained by subtracting 
	 * the existing ROI from the ROI of its parent object (or entire image if no suitable parent object is available).
	 * 
	 * @param imageData the imageData for which an inverted annotation should be created
	 * @param pathObject the existing object defining the ROI to invert
	 * @return true if an inverted annotation is added to the hierarchy, false otherwise
	 */
	public static boolean makeInverseAnnotation(final ImageData<?> imageData, final PathObject pathObject) {
		if (imageData == null)
			return false;
		return makeInverseAnnotation(imageData, Collections.singletonList(pathObject));
	}
	
	/**
	 * Make an inverse annotation using the current {@link ImageData} and its current selected objects.
	 * @return true if an inverted annotation is added to the hierarchy, false otherwise
	 */
	public static boolean makeInverseAnnotation() {
		return makeInverseAnnotation(getCurrentImageData());
	}

	/**
	 * Make an inverse annotation using the specified {@link ImageData} and current selected objects.
	 * @param imageData the imageData for which an inverted annotation should be created
	 * @return true if an inverted annotation is added to the hierarchy, false otherwise
	 */
	public static boolean makeInverseAnnotation(final ImageData<?> imageData) {
		return makeInverseAnnotation(imageData, imageData.getHierarchy().getSelectionModel().getSelectedObjects());
	}
	
	/**
	 * Make an annotation, for which the ROI is obtained by subtracting the ROIs of the specified objects from the closest 
	 * common ancestor ROI (or entire image if the closest ancestor is the root).
	 * <p>
	 * In an inverted annotation can be created, it is added to the hierarchy and set as selected.
	 * 
	 * @param imageData the image containing the annotation
	 * @param pathObjects the annotation to invert
	 * @return true if an inverted annotation is added to the hierarchy, false otherwise.
	 */
	public static boolean makeInverseAnnotation(final ImageData<?> imageData, Collection<PathObject> pathObjects) {
		if (imageData == null)
			return false;
		
		var map = pathObjects.stream().filter(p -> p.hasROI() && p.getROI().isArea())
				.collect(Collectors.groupingBy(p -> p.getROI().getImagePlane()));
		if (map.isEmpty()) {
			logger.warn("No area annotations available - cannot created inverse ROI!");
			return false;
		}
		if (map.size() > 1) {
			logger.error("Cannot merge annotations from different image planes!");
			return false;
		}
		ImagePlane plane = map.keySet().iterator().next();
		List<PathObject> pathObjectList = map.get(plane);
				
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Try to get the best candidate parent
		Collection<PathObject> parentSet = pathObjectList.stream().map(p -> p.getParent()).collect(Collectors.toCollection(HashSet::new));
		PathObject parent;
		if (parentSet.size() > 1) {
			parentSet.clear();
			boolean firstTime = true;
			for (PathObject temp : pathObjectList) {
				if (firstTime)
					parentSet.addAll(PathObjectTools.getAncestorList(temp));
				else
					parentSet.retainAll(PathObjectTools.getAncestorList(temp));
				firstTime = false;
			}
			List<PathObject> parents = new ArrayList<>(parentSet);
			Collections.sort(parents, Comparator.comparingInt(PathObject::getLevel).reversed()
					.thenComparingDouble(p -> p.hasROI() ? p.getROI().getArea() : Double.MAX_VALUE));
			parent = parents.get(0);
		} else
			parent = parentSet.iterator().next();			
		
		// Get the parent area
		Geometry geometryParent;
		if (parent == null || parent.isRootObject() || !parent.hasROI())
			geometryParent = GeometryTools.createRectangle(0, 0, imageData.getServer().getWidth(), imageData.getServer().getHeight());
		else
			geometryParent = parent.getROI().getGeometry();

		// Get the parent area to use
		var union = GeometryTools.union(pathObjectList.stream().map(p -> p.getROI().getGeometry()).collect(Collectors.toList()));
		var geometry = geometryParent.difference(union);

		// Create the new ROI
		ROI shapeNew = GeometryTools.geometryToROI(geometry, plane);
		PathObject pathObjectNew = PathObjects.createAnnotationObject(shapeNew);
		parent.addPathObject(pathObjectNew);
		hierarchy.fireHierarchyChangedEvent(parent);	
		hierarchy.getSelectionModel().setSelectedObject(pathObjectNew);
		return true;
	}
	
	
	
	
	/**
	 * Apply an object classifier to the current {@link ImageData}.
	 * This method throws an {@link IllegalArgumentException} if the classifier cannot be found.
	 * @param names the name of the classifier within the current project, or file path to a classifier to load from disk.
	 * 				If more than one name is provided, a composite classifier is created.
	 * @throws IllegalArgumentException if the classifier cannot be found
	 */
	public static void runObjectClassifier(String... names) throws IllegalArgumentException {
		runObjectClassifier(getCurrentImageData(), names);
	}
	
	/**
	 * Apply an object classifier to the specified {@link ImageData}.
	 * This method throws an {@link IllegalArgumentException} if the classifier cannot be found.
	 * @param imageData 
	 * @param names the name of the classifier within the current project, or file path to a classifier to load from disk.
	 * 				If more than one name is provided, a composite classifier is created.
	 * @throws IllegalArgumentException if the classifier cannot be found
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void runObjectClassifier(ImageData imageData, String... names) throws IllegalArgumentException {
		if (names.length == 0) {
			logger.warn("Cannot run object classifier - no names provided!");
			return;			
		}
		if (imageData == null) {
			logger.warn("Cannot run object classifier - no ImageData available!");
			return;
		}
		ObjectClassifier classifier = loadObjectClassifier(names);
		
		var pathObjects = classifier.getCompatibleObjects(imageData);
		if (classifier.classifyObjects(imageData, pathObjects, true) > 0)
			imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, pathObjects);
	}
	
	/**
	 * Load an object classifier for a project or file path.
	 * 
	 * @param names the names of the classifier within the current project, or file paths to a classifier to load from disk.
	 * 				If more than one name is provided, a composite classifier is created (applying each classifier in sequence).
	 * @return the requested {@link ObjectClassifier}
	 * @throws IllegalArgumentException if the classifier cannot be found
	 */
	public static ObjectClassifier<BufferedImage> loadObjectClassifier(String... names) throws IllegalArgumentException {
		var project = getProject();
		List<ObjectClassifier<BufferedImage>> classifiers = new ArrayList<>();
		for (String name : names) {
			ObjectClassifier<BufferedImage> classifier = null;
			Exception exception = null;
			if (project != null) {
				try {
					var objectClassifiers = project.getObjectClassifiers();
					if (objectClassifiers.contains(name))
						classifier = objectClassifiers.get(name);
				} catch (Exception e) {
					exception = e;
					logger.debug("Object classifier '{}' not found in project", name);
				}
			}
			if (classifier == null) {
				try {
					var path = Paths.get(name);
					if (Files.exists(path))
						classifier = ObjectClassifiers.readClassifier(path);
				} catch (Exception e) {
					exception = e;
					logger.debug("Object classifier '{}' cannot be read from file", name);
				}
			}
			if (classifier == null) {
				throw new IllegalArgumentException("Unable to find object classifier " + name, exception);
			} 
			// Try to fix URIs, if we can
			if (classifier instanceof UriResource) {
				UriUpdater.fixUris((UriResource)classifier, project);
			}
			if (names.length == 1)
				return classifier;
			else
				classifiers.add(classifier);
		}
		return ObjectClassifiers.createCompositeClassifier(classifiers);
	}
	
	
	/**
	 * Load a density map for a project or file path.
	 * 
	 * @param name the name of the density map within the current project, or file path to a density map to load from disk.
	 * @return the requested {@link DensityMapBuilder}
	 * @throws IllegalArgumentException if the density map cannot be found
	 */
	public static DensityMapBuilder loadDensityMap(String name) throws IllegalArgumentException {
		var project = getProject();
		Exception exception = null;
		if (project != null) {
			try {
				var densityMaps = project.getResources(DensityMaps.PROJECT_LOCATION, DensityMapBuilder.class, "json");
				if (densityMaps.contains(name))
					return densityMaps.get(name);
			} catch (Exception e) {
				exception = e;
				logger.debug("Density map '{}' not found in project", name);
			}
		}
		try {
			var path = Paths.get(name);
			if (Files.exists(path))
				return DensityMaps.loadDensityMap(path);
		} catch (Exception e) {
			exception = e;
			logger.debug("Density map '{}' cannot be read from file", name);
		}
		throw new IllegalArgumentException("Unable to find density map " + name, exception);
	}
	
	
	/**
	 * Locate a specified file based upon its name or path, with a search depth of 4.
	 * This first checks if the provided path is to a file that already exists.
	 * If it is not, then it searches recursively within the current project (if available) 
	 * up to a fixed search depth for a file with the same name.
	 * 
	 * @param nameOrPath the original name or path
	 * @return the identified file path, or the original file path if no update was found or required
	 * @throws IOException
	 * @see UriUpdater#locateFile(String, int, Path...)
	 */
	public static String locateFile(String nameOrPath) throws IOException {
		return locateFile(nameOrPath, 4);
	}
	
	/**
	 * Locate a specified file based upon its name or path.
	 * This first checks if the provided path is to a file that already exists.
	 * If it is not, then it searches recursively within the current project (if available) 
	 * up to a specified search depth for a file with the same name.
	 * 
	 * @param nameOrPath the original name or path
	 * @param searchDepth how deep to search subdirectories recursively
	 * @return the identified file path, or the original file path if no update was found or required
	 * @throws IOException
	 * @see UriUpdater#locateFile(String, int, Path...)
	 */
	public static String locateFile(String nameOrPath, int searchDepth) throws IOException {
		var project = getProject();
		var path = project == null ? null : project.getPath();
		if (path != null) {
			return UriUpdater.locateFile(nameOrPath, searchDepth, path);
		}
		return nameOrPath;
	}
	
	
	/**
	 * Find hotspots in a density map for the current image.
	 * 
	 * @param densityMapName name of the density map builder, see {@link #loadDensityMap(String)}
	 * @param channel channel number (usually 0)
	 * @param numHotspots the maximum number of hotspots to generate within each selected object
	 * @param minCounts the minimum value in the 'counts' channel; this is used to avoid generating hotspots in areas with few objects
	 * @param deleteExisting if true, similar annotations will be deleted from the image
	 * @param peaksOnly if true, hotspots will only be generated at intensity peaks in the density map
	 * @throws IOException
	 */
	public static void findDensityMapHotspots(String densityMapName, int channel, int numHotspots, double minCounts, boolean deleteExisting, boolean peaksOnly) throws IOException {
		findDensityMapHotspots(getCurrentImageData(), loadDensityMap(densityMapName), channel, numHotspots, minCounts, deleteExisting, peaksOnly);
	}
	
	/**
	 * Find hotspots in a density map.
	 * 
	 * @param imageData the image data
	 * @param densityMapName name of the density map builder, see {@link #loadDensityMap(String)}
	 * @param channel channel number (usually 0)
	 * @param numHotspots the maximum number of hotspots to generate within each selected object
	 * @param minCounts the minimum value in the 'counts' channel; this is used to avoid generating hotspots in areas with few objects
	 * @param deleteExisting if true, similar annotations will be deleted from the image
	 * @param peaksOnly if true, hotspots will only be generated at intensity peaks in the density map
	 * @throws IOException
	 */
	public static void findDensityMapHotspots(ImageData<BufferedImage> imageData, String densityMapName, int channel, int numHotspots, double minCounts, boolean deleteExisting, boolean peaksOnly) throws IOException {
		findDensityMapHotspots(imageData, loadDensityMap(densityMapName), channel, numHotspots, minCounts, deleteExisting, peaksOnly);
	}
	
	/**
	 * Find hotspots in a density map.
	 * 
	 * @param imageData the image data
	 * @param densityMap builder to generate a density map
	 * @param channel channel number (usually 0)
	 * @param numHotspots the maximum number of hotspots to generate within each selected object
	 * @param minCounts the minimum value in the 'counts' channel; this is used to avoid generating hotspots in areas with few objects
	 * @param deleteExisting if true, similar annotations will be deleted from the image
	 * @param peaksOnly if true, hotspots will only be generated at intensity peaks in the density map
	 * @throws IOException
	 */
	public static void findDensityMapHotspots(ImageData<BufferedImage> imageData, DensityMapBuilder densityMap, int channel, int numHotspots, double minCounts, boolean deleteExisting, boolean peaksOnly) throws IOException {
		var densityServer = densityMap.buildServer(imageData);
		double radius = densityMap.buildParameters().getRadius();
		var pathClass = PathClassFactory.getPathClass(densityServer.getChannel(channel).getName());
		DensityMaps.findHotspots(imageData.getHierarchy(), densityServer, channel, numHotspots, radius, minCounts, pathClass, deleteExisting, peaksOnly);
	}

	
	
	/**
	 * Create annotations from a density map for the current image.
	 * 
	 * @param densityMapName the name of the density map within the current project, or file path to a density map to load from disk
	 * @param thresholds map between channels to threshold (zero-based index) and thresholds to apply
	 * @param pathClassName name of the classification for the annotations that will be created
	 * @param options additional options when creating the annotations
	 * @throws IOException 
	 * @see #loadDensityMap(String)
	 * @see CreateObjectOptions
	 */
	public static void createAnnotationsFromDensityMap(String densityMapName, Map<Integer, ? extends Number> thresholds, String pathClassName, String... options) throws IOException {
		createAnnotationsFromDensityMap(getCurrentImageData(), densityMapName, thresholds, pathClassName, options);
	}
	
	/**
	 * Create annotations from a density map for the specified image.
	 * 
	 * @param imageData image for which the density map should be generated
	 * @param densityMapName the name of the density map within the current project, or file path to a density map to load from disk
	 * @param thresholds map between channels to threshold (zero-based index) and thresholds to apply
	 * @param pathClassName name of the classification for the annotations that will be created
	 * @param options additional options when creating the annotations
	 * @throws IOException 
	 * @see #loadDensityMap(String)
	 * @see CreateObjectOptions
	 */
	public static void createAnnotationsFromDensityMap(ImageData<BufferedImage> imageData, String densityMapName, Map<Integer, ? extends Number> thresholds, String pathClassName, String... options) throws IOException {
		var densityMap = loadDensityMap(densityMapName);
		createAnnotationsFromDensityMap(imageData, densityMap, thresholds, pathClassName, parseEnumOptions(CreateObjectOptions.class, null, options));
	}
	
	/**
	 * Create annotations from a density map for the specified image.
	 * 
	 * @param imageData image to which the density map corresponds
	 * @param densityMap the density map to use
	 * @param thresholds map between channels to threshold (zero-based index) and thresholds to apply
	 * @param pathClassName name of the classification for the annotations that will be created
	 * @param options additional options when creating the annotations
	 * @throws IOException 
	 * @see #loadDensityMap(String)
	 * @see CreateObjectOptions
	 */
	public static void createAnnotationsFromDensityMap(ImageData<BufferedImage> imageData, DensityMapBuilder densityMap, Map<Integer, ? extends Number> thresholds, String pathClassName, CreateObjectOptions... options) throws IOException {
		var densityServer = densityMap.buildServer(imageData);
		DensityMaps.threshold(imageData.getHierarchy(), densityServer, thresholds, pathClassName, options);
	}
	
	
	
	/**
	 * Load a pixel classifier for a project or file path.
	 * 
	 * @param name the name of the classifier within the current project, or file path to a classifier to load from disk.
	 * @return the requested {@link PixelClassifier}
	 * @throws IllegalArgumentException if the classifier cannot be found
	 */
	public static PixelClassifier loadPixelClassifier(String name) throws IllegalArgumentException {
		var project = getProject();
		Exception exception = null;
		PixelClassifier pixelClassifier = null;
		if (project != null) {
			try {
				var pixelClassifiers = project.getPixelClassifiers();
				if (pixelClassifiers.contains(name))
					pixelClassifier = pixelClassifiers.get(name);
			} catch (Exception e) {
				exception = e;
				logger.debug("Pixel classifier '{}' not found in project", name);
			}
		}
		try {
			var path = Paths.get(name);
			if (Files.exists(path))
				pixelClassifier = PixelClassifiers.readClassifier(path);
		} catch (Exception e) {
			exception = e;
			logger.debug("Pixel classifier '{}' cannot be read from file", name);
		}
		if (pixelClassifier == null)
			throw new IllegalArgumentException("Unable to find pixel classifier " + name, exception);
		// Fix URIs if we need to
		if (pixelClassifier instanceof UriResource) {
			UriUpdater.fixUris((UriResource)pixelClassifier, project);
		}
		return pixelClassifier;
	}
	
	
	
	/**
	 * Add measurements from pixel classification to the selected objects.
	 * @param classifierName the pixel classifier name
	 * @param measurementID
	 * @see #loadPixelClassifier(String)
	 */
	public static void addPixelClassifierMeasurements(String classifierName, String measurementID) {
		addPixelClassifierMeasurements(loadPixelClassifier(classifierName), measurementID);
	}
	
	/**
	 * Add measurements from pixel classification to the selected objects.
	 * @param classifier the pixel classifier
	 * @param measurementID
	 */
	public static void addPixelClassifierMeasurements(PixelClassifier classifier, String measurementID) {
		var imageData = (ImageData<BufferedImage>)getCurrentImageData();
		PixelClassifierTools.addMeasurementsToSelectedObjects(imageData, classifier, measurementID);
	}
	
	/**
	 * Create detection objects based upon the output of a pixel classifier, applied to selected objects.
	 * If no objects are selected, objects are created across the entire image.
	 * 
	 * @param classifierName the name of the pixel classifier
	 * @param minArea the minimum area of connected regions to retain
	 * @param minHoleArea the minimum area of connected 'hole' regions to retain
     * @param options additional options to control how objects are created
	 * @throws IOException 
	 * @throws IllegalArgumentException 
	 * @see #loadPixelClassifier(String)
	 */
	public static void createDetectionsFromPixelClassifier(
			String classifierName, double minArea, double minHoleArea, String... options) throws IllegalArgumentException, IOException {
		createDetectionsFromPixelClassifier(loadPixelClassifier(classifierName), minArea, minHoleArea, options);
	}

	/**
	 * Create detection objects based upon the output of a pixel classifier, applied to selected objects.
	 * If no objects are selected, objects are created across the entire image.
	 * 
	 * @param classifier the pixel classifier
	 * @param minArea the minimum area of connected regions to retain
	 * @param minHoleArea the minimum area of connected 'hole' regions to retain
     * @param options additional options to control how objects are created
	 * @throws IOException 
	 */
	public static void createDetectionsFromPixelClassifier(
			PixelClassifier classifier, double minArea, double minHoleArea, String... options) throws IOException {
		var imageData = (ImageData<BufferedImage>)getCurrentImageData();
		PixelClassifierTools.createDetectionsFromPixelClassifier(imageData, classifier, minArea, minHoleArea, parseEnumOptions(CreateObjectOptions.class, null, options));
	}
	 
	
	/**
	 * Create annotation objects based upon the output of a pixel classifier, applied to selected objects.
	 * If no objects are selected, objects are created across the entire image.
	 * 
	 * @param classifierName the name of the pixel classifier
	 * @param minArea the minimum area of connected regions to retain
	 * @param minHoleArea the minimum area of connected 'hole' regions to retain
     * @param options additional options to control how objects are created
	 * @throws IOException 
	 * @throws IllegalArgumentException 
	 * @see #loadPixelClassifier(String)
	 */
	public static void createAnnotationsFromPixelClassifier(
			String classifierName, double minArea, double minHoleArea, String... options) throws IllegalArgumentException, IOException {
		createAnnotationsFromPixelClassifier(loadPixelClassifier(classifierName), minArea, minHoleArea, options);
	}

	/**
	 * Create annotation objects based upon the output of a pixel classifier, applied to selected objects.
	 * If no objects are selected, objects are created across the entire image.
	 * 
	 * @param classifier the pixel classifier
	 * @param minArea the minimum area of connected regions to retain
	 * @param minHoleArea the minimum area of connected 'hole' regions to retain
     * @param options additional options to control how objects are created
	 * @throws IOException 
	 */
	public static void createAnnotationsFromPixelClassifier(
			PixelClassifier classifier, double minArea, double minHoleArea, String... options) throws IOException {
		var imageData = (ImageData<BufferedImage>)getCurrentImageData();
		PixelClassifierTools.createAnnotationsFromPixelClassifier(imageData, classifier, minArea, minHoleArea, parseEnumOptions(CreateObjectOptions.class, null, options));
	}
	
	
	/**
	 * Classify detections according to the prediction of the pixel corresponding to the detection centroid using a {@link PixelClassifier}.
	 * If the detections are cells, the nucleus ROI is used where possible.
	 * 
	 * @param classifier the pixel classifier
	 */
	public static void classifyDetectionsByCentroid(PixelClassifier classifier) {
		var imageData = (ImageData<BufferedImage>)getCurrentImageData();
		PixelClassifierTools.classifyDetectionsByCentroid(imageData, classifier);
	}
	
	/**
	 * Classify detections according to the prediction of the pixel corresponding to the detection centroid using a {@link PixelClassifier}.
	 * If the detections are cells, the nucleus ROI is used where possible.
	 * 
	 * @param classifierName name of the pixel classifier
	 */
	public static void classifyDetectionsByCentroid(String classifierName) {
		classifyDetectionsByCentroid(loadPixelClassifier(classifierName));
	}
	
}
