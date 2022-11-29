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

import java.awt.geom.AffineTransform;
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
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ObjectArrays;

import qupath.bioimageio.spec.BioimageIoSpec;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.DelaunayTools;
import qupath.lib.analysis.DistanceTools;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.analysis.features.ObjectMeasurements.ShapeFeatures;
import qupath.lib.analysis.heatmaps.ColorModels;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.analysis.images.ContourTracing;
import qupath.lib.awt.common.AffineTransforms;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.LogTools;
import qupath.lib.common.Timeit;
import qupath.lib.common.Version;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.LabeledImageServer;
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
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
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
import qupath.opencv.dnn.DnnModelParams;
import qupath.opencv.dnn.DnnModels;
import qupath.opencv.dnn.DnnTools;
import qupath.opencv.io.OpenCVTypeAdapters;
import qupath.opencv.ml.BioimageIoTools;
import qupath.opencv.ml.objects.OpenCVMLClassifier;
import qupath.opencv.ml.objects.features.FeatureExtractors;
import qupath.opencv.ml.pixel.PixelClassifierTools;
import qupath.opencv.ml.pixel.PixelClassifierTools.CreateObjectOptions;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.GroovyCV;
import qupath.opencv.tools.NumpyTools;
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
@SuppressWarnings("deprecation")
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
	 * The current QuPath version, parsed according to semantic versioning.
	 * May be null if the version is not known.
	 */
	public static final Version VERSION = GeneralTools.getSemanticVersion();
	
	
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
			
			QP.class,
			
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
			AffineTransforms.class,
			PathObjectTools.class,
			RoiTools.class,
			GsonTools.class,
			BufferedImageTools.class,
			ColorTools.class,
			GeneralTools.class,
			
			Timeit.class,
			ScriptAttributes.class,
			
			Version.class,
			
			DistanceTools.class,
//			ImageWriter.class,
			ImageWriterTools.class,
			PathClassTools.class,
			GeometryTools.class,
			IJTools.class,
			OpenCVTools.class,
			NumpyTools.class,
			DnnTools.class,
			DnnModels.class,
			DnnModelParams.class,
			TileExporter.class,
			LabeledImageServer.class,
			ServerTools.class,
			PixelClassifierTools.class,
			
			BioimageIoSpec.class,
			BioimageIoTools.class,
			
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
	
	
	private static Project<BufferedImage> defaultProject;

	private static ImageData<BufferedImage> defaultImageData;

	/**
	 * Set the default project, which will be returned by {@link #getProject()} if it would otherwise return null 
	 * (i.e. there has been no project set for the calling thread via {@link #setBatchProjectAndImage(Project, ImageData)}).
	 * <p>
	 * The intended use is for QuPath to set this to be the current project in the user interface, when running interactively.
	 * 
	 * @param project
	 */
	public static void setDefaultProject(final Project<BufferedImage> project) {
		defaultProject = project;
		logger.debug("Default project set to {}", project);
	}
	
	/**
	 * Set the default image data, which will be returned by {@link #getCurrentImageData()} if it would otherwise return null 
	 * (i.e. there has been no project set for the calling thread via {@link #setBatchProjectAndImage(Project, ImageData)}).
	 * <p>
	 * The intended use is for QuPath to set this to be the current image data in the user interface, when running interactively.
	 * This is not necessarily always the image that is 'current' when running scripts, e.g. when batch processing.
	 * 
	 * @param imageData
	 */
	public static void setDefaultImageData(final ImageData<BufferedImage> imageData) {
		defaultImageData = imageData;
		logger.debug("Default image data set to {}", defaultImageData);
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
	 * This returns {@link #getBatchImageData()} if it is not null; otherwise, it returns 
	 * the default image data last set through {@link #setDefaultImageData(ImageData)}.
	 * @return
	 * 
	 * @see #getBatchImageData()
	 */
	public static ImageData<BufferedImage> getCurrentImageData() {
		var defaultTemp = defaultImageData;
		var imageData = getBatchImageData();
		if (imageData != null || defaultTemp == null)
			return imageData;
		// If we don't have any other possible image data, return with debug logging
		var batchImages = batchImageData.values();
		if (batchImages.isEmpty() || (batchImages.size() == 1 && batchImages.contains(defaultTemp))) {
			logger.debug("Returning the default ImageData: {}", defaultTemp);
			return defaultTemp;
		}
		// If we have other options, return with a warning
		logger.warn("No batch image data for the current thread, returning the default image data instead: {}", defaultTemp);
		return defaultTemp;
	}
	
	
	/**
	 * Get the current project.
	 * <p>
	 * This returns {@link #getBatchProject()} if it is not null; otherwise, it returns 
	 * the default project last set through {@link #setDefaultProject(Project)}.
	 * @return
	 * 
	 * @see #getBatchProject()
	 */
	public static Project<BufferedImage> getProject() {
		var defaultTemp = defaultProject;
		// Return batch project or null if that's all we can do
		var project = getBatchProject();
		if (project != null || defaultTemp == null)
			return project;
		// If we don't have any other possible project, return with debug logging
		var batchProjects = batchProject.values();
		if (batchProjects.isEmpty() || (batchProjects.size() == 1 && batchProjects.contains(defaultTemp))) {
			logger.debug("Returning the default project: {}", defaultTemp);
			return defaultTemp;
		}
		// If we have other options, return with a warning
		logger.warn("No batch project for the current thread, returning the default project instead {}", defaultTemp);
		return defaultTemp;
	}
	
	/**
	 * Resolve a path, replacing any placeholders. Currently, this means only {@link #PROJECT_BASE_DIR}.
	 * @param path
	 * @return
	 * @throws IllegalArgumentException if {@link #PROJECT_BASE_DIR} is used but no project is available
	 */
	public static String resolvePath(final String path) throws IllegalArgumentException {
		String base = getProjectBaseDirectory();
		if (base != null)
			return path.replace(PROJECT_BASE_DIR, base);
		else if (path.contains(PROJECT_BASE_DIR))
			throw new IllegalArgumentException("No project base directory available - '" + path + "' cannot be resolved");
		return
			path;
	}
	
	/**
	 * Build a file path from multiple components.
	 * A common use of this is
	 * <pre>{@code
	 *   String path = buildFilePath(PROJECT_BASE_DIR, "export");
	 * }</pre>
	 * although that can now be replaced by {@link #buildPathInProject(String...)}
	 * @param first the first component of the file path
	 * @param more additional path components to append
	 * @return
	 * @see #buildPathInProject(String...)
	 * @see #makePathInProject(String...)
	 * @see #makeFileInProject(String...)
	 * @throws IllegalArgumentException if {@link #PROJECT_BASE_DIR} is used but no project is available
	 */
	public static String buildFilePath(String first, String... more) throws IllegalArgumentException {
		File file = new File(resolvePath(first));
		for (int i = 0; i < more.length; i++) {
			var part = more[i];
			if (part == null)
				throw new IllegalArgumentException("Part of the file path given to buildFilePath() is null!");
			else if (PROJECT_BASE_DIR.equals(part))
				throw new IllegalArgumentException("PROJECT_BASE_DIR must be the first element given to buildFilePath()");
			file = new File(file, part);
		}
		var path = file.getAbsolutePath();
		// TODO: Consider checking for questionable characters
		return path;
	}
	
	/**
	 * Build a file or directory path relative to the current project, but do not make 
	 * any changes on the file system.
	 * This is equivalent to calling
	 * <pre>{@code
	 *   String path = buildFilePath(PROJECT_BASE_DIR, more);
	 * }</pre>
	 * <p>
	 * If you want to additionally create the directory, see {@link #makePathInProject(String...)}
	 * 
	 * @param more additional path components to append
	 * @return
	 * @throws IllegalArgumentException if no project path is available
	 * @since v0.4.0
	 * @see #makePathInProject(String...)
	 * @see #makeFileInProject(String...)
	 */
	public static String buildPathInProject(String... more) throws IllegalArgumentException {
		return buildFilePath(PROJECT_BASE_DIR, more);
	}
	
	/**
	 * Build a file or directory path relative to the current project, and ensure that it exists.
	 * If it does not, an attempt will be made to create a directory with the specified name, 
	 * and all necessary parent directories.
	 * <p>
	 * This is equivalent to calling
	 * <pre>{@code
	 *   String path = buildPathInProject(PROJECT_BASE_DIR, more);
	 *   mkdirs(path);
	 * }</pre>
	 * <p>
	 * Note that if you need a file and not a directory, see {@link #makeFileInProject(String...)}.
	 *  
	 * @param more additional path components to append
	 * @return
	 * @throws IllegalArgumentException if no project path is available
	 * @since v0.4.0
	 * @see #buildPathInProject(String...)
	 * @see #makeFileInProject(String...)
	 */
	public static String makePathInProject(String... more) throws IllegalArgumentException {
		String path = buildPathInProject(more);
		mkdirs(path);
		return path;
	}
	
	/**
	 * Build a file path relative to the current project, and create a {@link File} object.
	 * An attempt will be made to create any required directories needed to create the file. 
	 * <p>
	 * The purpose is to reduce the lines of code needed to build a usable file in a QuPath 
	 * script. 
	 * A Groovy script showing this method in action:
	 * <pre>
	 *   File file = makeFileInProject("export", "file.txt")
	 *   file.text = "Some text here"
	 * </pre>
	 * <p>
	 * Note that, if the file does not already exist, it will not be created by this method - 
	 * only the directories leading to it.
	 * Additionally, if the file refers to an existing directory then the directory will be 
	 * returned - and will not be writable as a file.
	 *  
	 * @param more additional path components to append
	 * @return the file object, which may or may not refer to a file or directory that exists
	 * @throws IllegalArgumentException if no project path is available
	 * @since v0.4.0
	 * @see #makePathInProject(String...)
	 * @see #buildPathInProject(String...)
	 */
	public static File makeFileInProject(String... more) throws IllegalArgumentException {
		if (more.length == 0)
			return new File(makePathInProject());
		String basePath = makePathInProject(Arrays.copyOfRange(more, 0, more.length-1));
		Path path = Paths.get(basePath, more[more.length-1]);
		return path.toFile();
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
	 * <p>
	 * This first checks the name associated with {@link #getProjectEntry()}, if available.
	 * If no name is found (e.g. because no project is in use, then the name is extracted 
	 * from the metadata of {@link #getCurrentServer()}.
	 * If this is also missing, then {@code null} is returned.
	 * @return
	 * @since v0.4.0
	 * @see #getCurrentImageNameWithoutExtension()
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
	 * Get the name of the current image, removing any file extension.
	 * Equivalent to
	 * <pre>{@code 
	 * var name = GeneralTools.getNameWithoutExtension(getCurrentName());
	 * }</pre>
	 * @return
	 * @since v0.4.0
	 * @see #getCurrentImageName()
	 */
	public static String getCurrentImageNameWithoutExtension() {
		var name = getCurrentImageName();
		return name == null ? null : GeneralTools.getNameWithoutExtension(name);
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
		hierarchy.addObject(pathObject);
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
		hierarchy.addObjects(pathObjects);
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
	public static void removeObjects(Collection<? extends PathObject> pathObjects, boolean keepChildren) {
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
		return hierarchy.getAnnotationObjects();
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
		return hierarchy.getDetectionObjects();
	}
	
	/**
	 * Get a list of the current tile objects.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy
	 * @since v0.4.0
	 */
	public static Collection<PathObject> getTileObjects() {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		return hierarchy.getTileObjects();
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
		return hierarchy.getCellObjects();
	}

	/**
	 * Get all objects in the current hierarchy.
	 * 
	 * @param includeRootObject
	 * @return
	 * @see #getCurrentHierarchy
	 */
	public static Collection<PathObject> getAllObjects(boolean includeRootObject) {
		PathObjectHierarchy hierarchy = getCurrentHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		var objList = hierarchy.getFlattenedObjectList(null);
		if (includeRootObject)
			return objList;
		return objList.stream().filter(e -> !e.isRootObject()).collect(Collectors.toList());
	}

	/**
	 * Get all objects in the current hierarchy, including the root object.
	 * 
	 * @return
	 * 
	 * @see #getCurrentHierarchy
	 * @see #getAllObjects(boolean)
	 */
	public static Collection<PathObject> getAllObjects() {
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
	 * @deprecated v0.4.0 use {@link #createFullImageAnnotation(boolean)} instead
	 */
	@Deprecated
	public static void createSelectAllObject(final boolean setSelected) {
		LogTools.warnOnce(logger, "createSelectAllObject(boolean) is deprecated, use createFullImageAnnotation(boolean) instead");
		createSelectAllObject(setSelected, 0, 0);
	}
	
	/**
	 * Create an annotation for the entire width and height of the current image data, on the default plane (z-slice, time point).
	 * 
	 * @param setSelected if true, select the object that was created after it is added to the hierarchy
	 * @param z z-slice index for the annotation
	 * @param t timepoint index for the annotation
	 * @deprecated v0.4.0 use {@link #createFullImageAnnotation(boolean, int, int)} instead
	 */
	@Deprecated
	public static void createSelectAllObject(final boolean setSelected, int z, int t) {
		LogTools.warnOnce(logger, "createSelectAllObject(boolean, int, int) is deprecated, use createFullImageAnnotation(boolean, int, int) instead");
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return;
		ImageServer<?> server = imageData.getServer();
		PathObject pathObject = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getPlane(z, t))
				);
		imageData.getHierarchy().addObject(pathObject);
		if (setSelected)
			imageData.getHierarchy().getSelectionModel().setSelectedObject(pathObject);
	}
	
	/**
	 * Create annotation around the full image for the current image, on all z-slices and timepoints.
	 * @param setSelected if true, set the annotations to be selected when they are created
	 * @return the annotations that were created, or an empty list if no image data was available
	 * @since v0.4.0
	 * @see #createAllFullImageAnnotations(ImageData, boolean)
	 */
	public static List<PathObject> createAllFullImageAnnotations(boolean setSelected) {
		return createAllFullImageAnnotations(getCurrentImageData(), setSelected);
	}
	
	
	/**
	 * Create annotation around the full image for the specified image, on all z-slices and timepoints.
	 * @param imageData the image data
	 * @param setSelected if true, set the annotations to be selected when they are created
	 * @return the annotations that were created, or an empty list if no image data was available
	 * @since v0.4.0
	 */
	public static List<PathObject> createAllFullImageAnnotations(ImageData<?> imageData, boolean setSelected) {
		if (imageData == null)
			return Collections.emptyList();
		ImageServer<?> server = imageData.getServer();
		List<PathObject> annotations = new ArrayList<>();
		for (int t = 0; t < server.nTimepoints(); t++) {
			for (int z = 0; z < server.nZSlices(); z++) {
				PathObject pathObject = PathObjects.createAnnotationObject(
						ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getPlane(z, t))
						);
				annotations.add(pathObject);
			}			
		}
		imageData.getHierarchy().addObjects(annotations);
		if (setSelected)
			imageData.getHierarchy().getSelectionModel().setSelectedObjects(annotations, annotations.get(0));
		return annotations;
	}
	
	
	/**
	 * Create an annotation around the full image for the current image, on the default (first) z-slice and timepoint.
	 * @param setSelected if true, set the annotation to be selected when it is created
	 * @return the annotation that was created, or null if no image data was available
	 * @since v0.4.0
	 * @see #createFullImageAnnotation(boolean, int, int)
	 * @see #createFullImageAnnotation(ImageData, boolean)
	 */
	public static PathObject createFullImageAnnotation(boolean setSelected) {
		return createFullImageAnnotation(getCurrentImageData(), setSelected);
	}
	
	/**
	 * Create an annotation around the full image for the current image, on the specified z-slice and timepoint.
	 * @param setSelected if true, set the annotation to be selected when it is created
	 * @param z z-slice (0-based index)
	 * @param t timepoint (0-based index)
	 * @return the annotation that was created, or null if no image data was available
	 * @since v0.4.0
	 * @see #createFullImageAnnotation(ImageData, boolean, int, int)
	 */
	public static PathObject createFullImageAnnotation(boolean setSelected, int z, int t) {
		return createFullImageAnnotation(getCurrentImageData(), setSelected, z, t);		
	}
	
	/**
	 * Create an annotation around the full image for the specified image, on the default (first) z-slice and timepoint.
	 * @param imageData the image data for which the annotation should be added
	 * @param setSelected if true, set the annotation to be selected when it is created
	 * @return the annotation that was created, or null if no image data was available
	 * @since v0.4.0
	 */
	public static PathObject createFullImageAnnotation(ImageData<?> imageData, boolean setSelected) {
		return createFullImageAnnotation(imageData, setSelected, 0, 0);
	}
	
	/**
	 * Create an annotation around the full image for the specified image, on the specified z-slice and timepoint.
	 * @param imageData the image data for which the annotation should be added
	 * @param setSelected if true, set the annotation to be selected when it is created
	 * @param z z-slice (0-based index)
	 * @param t timepoint (0-based index)
	 * @return the annotation that was created, or null if no image data was available
	 * @since v0.4.0
	 */
	public static PathObject createFullImageAnnotation(ImageData<?> imageData, boolean setSelected, int z, int t) {
		if (imageData == null)
			return null;
		ImageServer<?> server = imageData.getServer();
		PathObject pathObject = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getPlane(z, t))
				);
		imageData.getHierarchy().addObject(pathObject);
		if (setSelected)
			imageData.getHierarchy().getSelectionModel().setSelectedObject(pathObject);
		return pathObject;
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

	
//	public static boolean removeObjectsOutsideImage() {
//		return removeObjectsOutsideImage(getCurrentServer(), getCurrentHierarchy());
//	}
//
//	public static boolean removeObjectsOutsideImage(ImageServer<?> server, PathObjectHierarchy hierarchy) {
//		hierarchy.getAllObjects()
//	}

	/**
	 * Apply an affine transform to all objects in the current hierarchy, retaining parent-child relationships between objects.
	 * @param transform
	 * @implNote Currently, existing objects will be removed, and replaced with new ones that have had their ROIs transformed 
	 *           and the same IDs retained. However it is best not to rely upon this behavior; it is possible that in the future 
	 *           the ROIs will be updated in-place to improve efficiency.
	 * @since v0.4.0
	 */
	public static void transformSelectedObjects(AffineTransform transform) {
		transformSelectedObjects(getCurrentHierarchy(), transform);
	}

	/**
	 * Apply an affine transform to all objects in the specified hierarchy, retaining parent-child relationships between objects.
	 * @param hierarchy
	 * @param transform
	 * @implNote Currently, existing objects will be removed, and replaced with new ones that have had their ROIs transformed 
	 *           and the same IDs retained. However it is best not to rely upon this behavior; it is possible that in the future 
	 *           the ROIs will be updated in-place to improve efficiency.
	 * @since v0.4.0
	 */
	public static void transformSelectedObjects(PathObjectHierarchy hierarchy, AffineTransform transform) {
		Objects.requireNonNull(hierarchy, "Can't transform selected objects - hierarchy is null!");
		var selected = hierarchy.getSelectionModel().getSelectedObjects();
		if (selected.isEmpty()) {
			logger.warn("Cannot transform selected objects - no objects are selected");
			return;
		}
		var primary = hierarchy.getSelectionModel().getSelectedObject();
		List<PathObject> transformed = new ArrayList<>();
		for (var pathObject : selected.toArray(PathObject[]::new))
			transformed.add(PathObjectTools.transformObject(pathObject, transform, true, false));
		
		hierarchy.removeObjects(selected, true);
		hierarchy.addObjects(transformed);
		
		// Set selected objects
		var newPrimary = primary == null ? null : transformed.stream().filter(p -> p.getID().equals(primary.getID())).findFirst().orElse(null);
		hierarchy.getSelectionModel().setSelectedObjects(transformed, newPrimary);
	}
	
	/**
	 * Resize the ROIs of all objects in the current object hierarchy.
	 * @param scaleFactor scale factor
	 * @since v0.4.0
	 * @see #transformAllObjects(AffineTransform)
	 */
	public static void scaleAllObjects(double scaleFactor) {
		scaleAllObjects(getCurrentHierarchy(), scaleFactor);
	}
	
	/**
	 * Resize the ROIs of all objects in the specified object hierarchy.
	 * @param hierarchy the object hierarchy
	 * @param scaleFactor scale factor
	 * @since v0.4.0
	 * @see #transformAllObjects(PathObjectHierarchy, AffineTransform)
	 */
	public static void scaleAllObjects(PathObjectHierarchy hierarchy, double scaleFactor) {
		transformAllObjects(hierarchy, AffineTransform.getScaleInstance(scaleFactor, scaleFactor));
	}
	
	/**
	 * Translate (move) the ROIs of all objects in the current object hierarchy.
	 * @param dx amount to translate horizontally (in pixels)
	 * @param dy amount to translate vertically (in pixels)
	 * @since v0.4.0
	 * @see #transformAllObjects(AffineTransform)
	 */
	public static void translateAllObjects(double dx, double dy) {
		translateAllObjects(getCurrentHierarchy(), dx, dy);
	}
	
	/**
	 * Translate (move) the ROIs of all objects in the specified object hierarchy.
	 * @param hierarchy the object hierarchy
	 * @param dx amount to translate horizontally (in pixels)
	 * @param dy amount to translate vertically (in pixels)
	 * @since v0.4.0
	 * @see #transformAllObjects(PathObjectHierarchy, AffineTransform)
	 */
	public static void translateAllObjects(PathObjectHierarchy hierarchy, double dx, double dy) {
		transformAllObjects(hierarchy, AffineTransform.getTranslateInstance(dx, dy));
	}
	
	/**
	 * Apply an affine transform to all selected objects in the current hierarchy.
	 * The selected objects will be replaced by new ones, but parent-child relationships will be lost;
	 * if these are needed, consider calling {@link #resolveHierarchy()} afterwards.
	 * @param transform
	 * @implNote Currently, existing objects will be removed, and replaced with new ones that have had their ROIs transformed 
	 *           and the same IDs retained. However it is best not to rely upon this behavior; it is possible that in the future 
	 *           the ROIs will be updated in-place to improve efficiency.
	 * @since v0.4.0
	 */
	public static void transformAllObjects(AffineTransform transform) {
		transformAllObjects(getCurrentHierarchy(), transform);
	}
	
	/**
	 * Apply an affine transform to all selected objects in the specified hierarchy.
	 * The selected objects will be replaced by new ones, but parent-child relationships will be lost;
	 * if these are needed, consider calling {@link #resolveHierarchy(PathObjectHierarchy)} afterwards.
	 * @param hierarchy 
	 * @param transform
	 * @implNote Currently, existing objects will be removed, and replaced with new ones that have had their ROIs transformed 
	 *           and the same IDs retained. However it is best not to rely upon this behavior; it is possible that in the future 
	 *           the ROIs will be updated in-place to improve efficiency.
	 * @since v0.4.0
	 */
	public static void transformAllObjects(PathObjectHierarchy hierarchy, AffineTransform transform) {
		Objects.requireNonNull(hierarchy, "Can't transform all objects - hierarchy is null!");
		var primary = hierarchy.getSelectionModel().getSelectedObject();
		Set<UUID> selectedIDs = hierarchy.getSelectionModel().getSelectedObjects()
				.stream()
				.map(p -> p.getID())
				.collect(Collectors.toSet());
		var transformed = PathObjectTools.transformObjectRecursive(hierarchy.getRootObject(), transform, true, false);
		
		var newObjects = new ArrayList<>(transformed.getChildObjects());
		
		// Handle TMA grid... which is rather more awkward
		var tmaGrid = hierarchy.getTMAGrid();
		TMAGrid newTmaGrid = null;
		if (tmaGrid != null && !tmaGrid.getTMACoreList().isEmpty()) {
			var originalCores = tmaGrid.getTMACoreList();
			var newCores = PathObjectTools.getFlattenedObjectList(transformed, null, true)
					.stream()
					.filter(p -> p.isTMACore())
					.collect(Collectors.toList());
			
			var matches = PathObjectTools.matchByID(originalCores, newCores);
			var newCoresOrdered = new ArrayList<TMACoreObject>();
			for (var oldCore : originalCores) {
				var newCore = matches.getOrDefault(oldCore, null);
				if (newCore != null)
					newCoresOrdered.add((TMACoreObject)newCore);
			}
			if (newCoresOrdered.size() == originalCores.size()) {
				newTmaGrid = DefaultTMAGrid.create(newCoresOrdered, tmaGrid.getGridWidth());
				newObjects.removeAll(newCoresOrdered);
			} else
				logger.warn("Unable to match old and new TMA cores!");
		}
		
		hierarchy.clearAll();
		if (newTmaGrid != null)
			hierarchy.setTMAGrid(newTmaGrid);
		hierarchy.addObjects(newObjects);
		
		// Restore the selection, now with the transformed objects
		if (!selectedIDs.isEmpty()) {
			var toSelect = PathObjectTools.findByUUID(selectedIDs, hierarchy.getFlattenedObjectList(null)).values();
			var newPrimary = primary == null ? null : toSelect.stream().filter(p -> p.getID().equals(primary.getID())).findFirst().orElse(null);
			hierarchy.getSelectionModel().setSelectedObjects(toSelect, newPrimary);
		}
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
	 * @param rowFirst true if the horizontal label should be added before the vertical label, false otherwise
	 * @return true if there were sufficient horizontal and vertical labels to label the entire grid, false otherwise.
	 */
	public static boolean relabelTMAGrid(final PathObjectHierarchy hierarchy, final String labelsHorizontal, final String labelsVertical, final boolean rowFirst) {
		if (hierarchy == null || hierarchy.getTMAGrid() == null) {
			logger.error("Cannot relabel TMA grid - no grid found!");
			return false;
		}
		TMAGrid grid = hierarchy.getTMAGrid();
		return PathObjectTools.relabelTMAGrid(grid, labelsHorizontal, labelsVertical, rowFirst);
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
	 * Create a new regular {@link TMAGrid} and set it as active on the hierarchy for an image.
	 * <p>
	 * For the label string format, see see {@link PathObjectTools#parseTMALabelString(String)}.
	 * 
	 * @param imageData the image to which the TMA grid should be added. This is used to determine 
	 *                  dimensions and pixel calibration. If there is a ROI selected, it will be used 
	 *                  to define the bounding box for the grid.
	 * @param hLabels a String representing horizontal labels
	 * @param vLabels a String representing vertical labels
	 * @param rowFirst true if the horizontal label should be added before the vertical label, false otherwise
	 * @param diameterCalibrated the diameter of each core, in calibrated units
	 * @see PathObjectTools#addTMAGrid(ImageData, String, String, boolean, double)
	 */
	public static void createTMAGrid(ImageData<?> imageData, String hLabels, String vLabels, boolean rowFirst, double diameterCalibrated) {
		PathObjectTools.addTMAGrid(imageData, hLabels, vLabels, rowFirst, diameterCalibrated);
	}
	
	/**
	 * Create a new regular {@link TMAGrid} and set it as active on the hierarchy for the current image.
	 * <p>
	 * For the label string format, see see {@link PathObjectTools#parseTMALabelString(String)}.
	 * 
	 * @param hLabels a String representing horizontal labels
	 * @param vLabels a String representing vertical labels
	 * @param rowFirst true if the horizontal label should be added before the vertical label, false otherwise
	 * @param diameterCalibrated the diameter of each core, in calibrated units
	 * @see PathObjectTools#addTMAGrid(ImageData, String, String, boolean, double)
	 */
	public static void createTMAGrid(String hLabels, String vLabels, boolean rowFirst, double diameterCalibrated) {
		createTMAGrid(getCurrentImageData(), hLabels, vLabels, rowFirst, diameterCalibrated);
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
	 * Refresh all object IDs for the current hierarchy.
	 * @since v0.4.0
	 */
	public static void refreshIDs() {
		refreshIDs(getCurrentHierarchy(), false);
	}

	/**
	 * Refresh all object IDs for the current hierarchy to ensure there are no duplicates,
	 * retaining the original IDs where possible.
	 * @return true if object IDs were changed, false otherwise
	 * @since v0.4.0
	 */
	public static boolean refreshDuplicateIDs() {
		return refreshIDs(getCurrentHierarchy(), true);
	}
	
	/**
	 * Refresh all object IDs for the current hierarchy.
	 * @param hierarchy the object hierarchy
	 * @since v0.4.0
	 */
	public static void refreshIDs(PathObjectHierarchy hierarchy) {
		refreshIDs(hierarchy, false);
	}

	/**
	 * Refresh all object IDs for the current hierarchy to ensure there are no duplicates,
	 * retaining the original IDs where possible.
	 * @param hierarchy the object hierarchy
	 * @return true if object IDs were changed, false otherwise
	 * @since v0.4.0
	 */
	public static boolean refreshDuplicateIDs(PathObjectHierarchy hierarchy) {
		return refreshIDs(hierarchy, true);
	}
	
	/**
	 * Refresh all object IDs for the specified hierarchy, optionally restricted to duplicates.
	 * @param hierarchy the object hierarchy
	 * @param duplicatesOnly if true, only update enough object IDs to avoid duplicates
	 * @return true if object IDs were changed, false otherwise
	 * @since v0.4.0
	 */
	private static boolean refreshIDs(PathObjectHierarchy hierarchy, boolean duplicatesOnly) {
		if (hierarchy == null)
			return false;
		var pathObjects = hierarchy.getAllObjects(true);
		if (duplicatesOnly) {
			var set = new HashSet<UUID>();
			var changed = new ArrayList<PathObject>();
			for (var p : pathObjects) {
				while (!set.add(p.getID())) {
					p.refreshID();
					changed.add(p);
				}
			}
			if (changed.isEmpty())
				return false;
			assert set.size() == pathObjects.size();
			hierarchy.fireObjectsChangedEvent(hierarchy, changed);
			return true;
		} else {
			pathObjects.stream().forEach(p -> p.refreshID());
			hierarchy.fireObjectsChangedEvent(hierarchy, pathObjects);
			return true;
		}
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
				pathObject.resetPathClass();
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
		return pathObject != null && pathObject.getMeasurementList().containsKey(measurementName);
	}

	/**
	 * Extract the specified measurement from a PathObject.
	 * 
	 * @param pathObject
	 * @param measurementName
	 * @return
	 */
	public static double measurement(final PathObject pathObject, final String measurementName) {
		return pathObject == null ? Double.NaN : pathObject.getMeasurementList().get(measurementName);
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
	 * Select all objects in the specified hierarchy, excluding the root object.
	 * @param hierarchy 
	 * @since v0.4.0
	 */
	public static void selectAllObjects(PathObjectHierarchy hierarchy) {
		selectAllObjects(hierarchy, false);
	}

	
	/**
	 * Select all objects in the current hierarchy, excluding the root object.
	 * @since v0.4.0
	 */
	public static void selectAllObjects() {
		selectAllObjects(getCurrentHierarchy());
	}
	

	/**
	 * Selected objects in the current hierarchy occurring on the specified z-slice and timepoint.
	 * @param z z-slice (0-based index)
	 * @param t timepoint (0-based index)
	 * @since v0.4.0
	 * @see #selectObjectsByPlane(PathObjectHierarchy, ImagePlane)
	 */
	public static void selectObjectsByPlane(int z, int t) {
		selectObjectsByPlane(ImagePlane.getPlane(z, t));
	}

	/**
	 * Selected objects in the current hierarchy occurring on the specified plane (z-slice and timepoint).
	 * @param plane
	 * @since v0.4.0
	 * @see #selectObjectsByPlane(PathObjectHierarchy, ImagePlane)
	 */
	public static void selectObjectsByPlane(ImagePlane plane) {
		selectObjectsByPlane(getCurrentHierarchy(), plane);
	}
	
	/**
	 * Selected objects in the specified hierarchy occurring on the specified plane (z-slice and timepoint).
	 * @param hierarchy
	 * @param plane
	 * @since v0.4.0
	 */
	public static void selectObjectsByPlane(PathObjectHierarchy hierarchy, ImagePlane plane) {
		selectObjects(p -> p.hasROI() && p.getROI().getZ() == plane.getZ() && p.getROI().getT() == plane.getT());
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
	public static void selectObjects(final Collection<? extends PathObject> pathObjects) {
		selectObjects(pathObjects, null);
	}
	
	/**
	 * Set all objects in a collection to be selected, including a specified main selected object.
	 * @param pathObjects
	 * @param mainSelection
	 */
	public static void selectObjects(final Collection<? extends PathObject> pathObjects, PathObject mainSelection) {
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
			pathClassSet = Arrays.stream(pathClasses).map(p -> p == PathClass.NULL_CLASS ? null : p).collect(Collectors.toCollection(HashSet::new));
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
					double v = p.getMeasurementList().get(measurement);
					return !Double.isNaN(v) && mapComparison.get(comparison).test(Double.compare(p.getMeasurementList().get(measurement), value));
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
		PathClass pathClass = PathClass.fromString(pathClassName);
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
		exportObjectsToGeoJson(getAllObjects(false), path, options);
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
		return getCurrentHierarchy().addObjects(objs);
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
		return PathClass.fromString(name);
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
		return PathClass.fromString(name, rgb);
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
		return PathClass.getInstance(baseClass, name, rgb);
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
		if (PathClass.NULL_CLASS == originalClass)
			originalClass = null;
		if (PathClass.NULL_CLASS == newClass)
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
		return duplicateSelectedAnnotations(getCurrentHierarchy());
	}
	
	/**
	 * Duplicate the selected annotations in the specified hierarchy.
	 * @param hierarchy 
	 * 
	 * @return true if changes are made to the hierarchy, false otherwise
	 * @since v0.4.0
	 */
	public static boolean duplicateSelectedAnnotations(PathObjectHierarchy hierarchy) {
		return PathObjectTools.duplicateSelectedAnnotations(hierarchy);
	}
	
	
	/**
	 * Copy the selected objects in the current hierarchy to the specified z-slice and timepoint.
	 * This copies only the objects themselves, discarding any parent/child relationships.
	 * The copied objects will become the new selection.
	 * @param z z-slice (0-based index)
	 * @param t timepoint (0-based index)
	 * @return true if changes are made to the hierarchy, false otherwise
	 * @since v0.4.0
	 * @see #copySelectedObjectsToPlane(PathObjectHierarchy, ImagePlane)
	 */
	public static boolean copySelectedObjectsToPlane(int z, int t) {
		return copySelectedObjectsToPlane(getCurrentHierarchy(), ImagePlane.getPlane(z, t));
	}
	
	/**
	 * Copy the selected objects in the current hierarchy to the specified image plane.
	 * This copies only the objects themselves, discarding any parent/child relationships.
	 * The copied objects will become the new selection.
	 * @param plane 
	 * @return true if changes are made to the hierarchy, false otherwise
	 * @since v0.4.0
	 * @see #copySelectedObjectsToPlane(PathObjectHierarchy, ImagePlane)
	 */
	public static boolean copySelectedObjectsToPlane(ImagePlane plane) {
		return copySelectedObjectsToPlane(getCurrentHierarchy(), plane);
	}
	
	/**
	 * Copy the selected objects in the specified hierarchy to the specified image plane.
	 * This copies only the objects themselves, discarding any parent/child relationships.
	 * The copied objects will become the new selection.
	 * @param hierarchy 
	 * @param plane 
	 * 
	 * @return true if changes are made to the hierarchy, false otherwise
	 * @since v0.4.0
	 */
	public static boolean copySelectedObjectsToPlane(PathObjectHierarchy hierarchy, ImagePlane plane) {
		return copySelectedObjectsToPlane(hierarchy, plane, p -> p.hasROI());
	}
	
	/**
	 * Copy the selected annotations in the current hierarchy to the specified z-slice and timepoint.
	 * This copies only the objects themselves, discarding any parent/child relationships.
	 * The copied objects will become the new selection.
	 * @param z z-slice (0-based index)
	 * @param t timepoint (0-based index)
	 * @return true if changes are made to the hierarchy, false otherwise
	 * @since v0.4.0
	 * @see #copySelectedObjectsToPlane(PathObjectHierarchy, ImagePlane)
	 */
	public static boolean copySelectedAnnotationsToPlane(int z, int t) {
		return copySelectedAnnotationsToPlane(getCurrentHierarchy(), ImagePlane.getPlane(z, t));
	}
	
	/**
	 * Copy the selected annotations in the current hierarchy to the specified image plane.
	 * This copies only the objects themselves, discarding any parent/child relationships.
	 * The copied objects will become the new selection.
	 * @param plane 
	 * @return true if changes are made to the hierarchy, false otherwise
	 * @since v0.4.0
	 * @see #copySelectedObjectsToPlane(PathObjectHierarchy, ImagePlane)
	 */
	public static boolean copySelectedAnnotationsToPlane(ImagePlane plane) {
		return copySelectedAnnotationsToPlane(getCurrentHierarchy(), plane);
	}
	
	/**
	 * Copy the selected annotations in the specified hierarchy to the specified image plane.
	 * This copies only the objects themselves, discarding any parent/child relationships.
	 * The copied objects will become the new selection.
	 * @param hierarchy 
	 * @param plane 
	 * 
	 * @return true if changes are made to the hierarchy, false otherwise
	 * @since v0.4.0
	 */
	public static boolean copySelectedAnnotationsToPlane(PathObjectHierarchy hierarchy, ImagePlane plane) {
		return copySelectedObjectsToPlane(hierarchy, plane, p -> p.hasROI() && p.isAnnotation());
	}

		
	private static boolean copySelectedObjectsToPlane(PathObjectHierarchy hierarchy, ImagePlane plane, Predicate<PathObject> filter) {
		if (hierarchy == null)
			return false;
		
		Collection<PathObject> selected = hierarchy.getSelectionModel().getSelectedObjects();
		if (selected.isEmpty()) {
			return false;
		}
		
		// Try to retain the primary selection, if known
		var primary = hierarchy.getSelectionModel().getSelectedObject();
		if (primary != null) {
			selected = new ArrayList<>(selected);
			selected.remove(primary);
			((List<PathObject>)selected).add(0, primary);
		}
		
		var transformed = selected.stream()
				.filter(filter)
				.map(p -> PathObjectTools.updatePlane(p, plane, false, true))
				.collect(Collectors.toList());
		if (transformed.isEmpty())
			return false;
		hierarchy.addObjects(transformed);
		// Consider making this optional
		hierarchy.getSelectionModel().setSelectedObjects(transformed, primary == null ? null : transformed.get(0));
		return true;
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
		hierarchy.addObject(pathObjectNew);
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
		parent.addChildObject(pathObjectNew);
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
		var pathClass = PathClass.fromString(densityServer.getChannel(channel).getName());
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
	 * Get the logger associated with this class.
	 * @return
	 */
	public static Logger getLogger() {
		return logger;
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
	
	/**
	 * Check whether the current QuPath version is &geq; the specified version.
	 * This can be added at the beginning of a script to prevent the script running if it is known to be incompatible.
	 * <p>
	 * It throws an exception if the test is failed so that it can be added in a single line, with the script stopping 
	 * if the criterion is not met.
	 * <p>
	 * Using this successfully depends upon {@link #VERSION} being available.
	 * To avoid an exception if it is not, use
	 * <code>
	 * <pre>{@code 
	 * if (VERSION != null)
	 *   checkMinVersion("0.4.0");
	 * }
	 * </pre>
	 * @param version last known compatible version (inclusive)
	 * @throws UnsupportedOperationException if the version test is not passed, of version information is unavailable
	 * @see #checkVersionRange(String, String)
	 * @since v0.4.0
	 */
	public static void checkMinVersion(String version) throws UnsupportedOperationException {
		if (VERSION == null)
			throw new UnsupportedOperationException("Can't check version - QuPath version is unknown!");
		var versionToCompare = Version.parse(version);
		if (versionToCompare.compareTo(VERSION) > 0)
			throw new UnsupportedOperationException("Mininum version " + versionToCompare + " exceeds current QuPath version " + VERSION);
	}
	
	/**
	 * Check whether the current QuPath version is &geq; the specified minimum version, and &lt; the specified maximum.
	 * This can be added at the beginning of a script to prevent the script running if it is known to be incompatible.
	 * <p>
	 * The minimum is inclusive and maximum is exclusive so that the maximum can be given as the first version known to 
	 * introduce a breaking change.
	 * <p>
	 * Using this successfully depends upon {@link #VERSION} being available.
	 * To avoid an exception if it is not, use
	 * <code>
	 * <pre>{@code 
	 * if (VERSION != null)
	 *   checkVersionRange("0.4.0", "0.5.0");
	 * }
	 * </pre>
	 * @param minVersion last known compatible version (inclusive)
	 * @param maxVersion next known incompatible version
	 * @throws UnsupportedOperationException if the version test is not passed, of version information is unavailable
	 * @see #checkMinVersion(String)
	 * @since v0.4.0
	 */	public static void checkVersionRange(String minVersion, String maxVersion) throws UnsupportedOperationException {
		checkMinVersion(minVersion);
		var versionMax = Version.parse(maxVersion);
		if (versionMax.compareTo(VERSION) <= 0)
			throw new UnsupportedOperationException("Current QuPath version " + VERSION + " is >= the specified (non-inclusive) maximum " + versionMax);			
	}
	
	 
	 /**
	  * Remove objects that are entirely outside the current image.
	  * @return true if objects were removed, false otherwise
	  * @since v0.4.0
	  * @see #removeObjectsOutsideImage(ImageData, boolean)
	  */
	 public static boolean removeObjectsOutsideImage() {
		 return removeObjectsOutsideImage(getCurrentImageData());
	 }
	 
	 /**
	  * Remove objects that are entirely or partially outside the current image.
	  * @param ignoreIntersecting if true, ignore objects that are intersecting the image bounds; if false, remove these intersecting objects too
	  * @return true if objects were removed, false otherwise
	  * @since v0.4.0
	  * @see #removeObjectsOutsideImage(ImageData, boolean)
	  */
	 public static boolean removeObjectsOutsideImage(boolean ignoreIntersecting) {
		 return removeObjectsOutsideImage(getCurrentImageData(), ignoreIntersecting);
	 }

	 /**
	  * Remove objects that are entirely or outside the specified image.
	  * @param imageData the image data, including a hierarchy and server to use
	  * @return true if objects were removed, false otherwise
	  * @since v0.4.0
	  * @see #removeObjectsOutsideImage(ImageData, boolean)
	  */
	 public static boolean removeObjectsOutsideImage(ImageData<?> imageData) {
		 return removeObjectsOutsideImage(imageData, true);		 
	 }

	 /**
	  * Remove objects that are entirely or partially outside the specified image.
	  * @param imageData the image data, including a hierarchy and server to use
	  * @param ignoreIntersecting if true, ignore objects that are intersecting the image bounds; if false, remove these intersecting objects too
	  * @return true if objects were removed, false otherwise
	  * @since v0.4.0
	  * @see #removeObjectsOutsideImage(ImageData, boolean)
	  * @see #removeOrClipObjectsOutsideImage(ImageData)
	  * @implNote TMA cores outside the image can't be removed, because doing so would potentially mess up the TMA grid.
	  */
	 public static boolean removeObjectsOutsideImage(ImageData<?> imageData, boolean ignoreIntersecting) {
		 Objects.requireNonNull(imageData, "Hierarchy must not be null!");
		 var hierarchy = imageData.getHierarchy();
		 var server = imageData.getServer();
		 // Remove objects outside the image - unless they are TMA cores, which would mess up the grid
		 var toRemoveOriginal = PathObjectTools.findObjectsOutsideImage(hierarchy.getAllObjects(false), server, ignoreIntersecting);
		 var toRemove = toRemoveOriginal
				 .stream()
				 .filter(p -> !p.isTMACore())
				 .collect(Collectors.toList());
		 if (toRemove.size() < toRemoveOriginal.size())
			 logger.warn("TMA cores outside the image can't be removed");
		 if (toRemove.isEmpty())
			 return false;
		 hierarchy.removeObjects(toRemove, true);
		 hierarchy.getSelectionModel().deselectObjects(toRemove);
		 return true;
	 }
	 
	 /**
	  * Remove objects occurring outside the current image bounds, clipping annotations where possible to retain 
	  * the part that is inside the image.
	  * @return true if changes were made, false otherwise
	  * @since v0.4.0
	  * @see #removeOrClipObjectsOutsideImage(ImageData)
	  * @see #removeObjectsOutsideImage(ImageData, boolean)
	  */
	 public static boolean removeOrClipObjectsOutsideImage() {
		 return removeOrClipObjectsOutsideImage(getCurrentImageData());
	 }
	 

	 /**
	  * Remove objects occurring outside the specified image bounds, clipping annotations where possible to retain 
	  * the part that is inside the image.
	  * @param imageData 
	  * @return true if changes were made, false otherwise
	  * @since v0.4.0
	  * @see #removeObjectsOutsideImage(ImageData, boolean)
	  */
	 public static boolean removeOrClipObjectsOutsideImage(ImageData<?> imageData) {
		 var server = imageData.getServer();
		 var hierarchy = getCurrentHierarchy();
		 
		 // Remove all the objects that are completely outside the image
		 boolean changes = removeObjectsOutsideImage(imageData, true);
		 
		 // Find remaining objects that overlap the bounds
		 var overlapping = PathObjectTools.findObjectsOutsideImage(hierarchy.getAllObjects(false), server, false)
				 .stream()
				 .filter(p -> !p.isTMACore())
				 .collect(Collectors.toList());
		 if (overlapping.isEmpty())
			 return changes;
		 
		 // Remove the detections entirely
		 var overlappingDetections = overlapping.stream().filter(p -> p.isDetection()).collect(Collectors.toList());
		 if (!overlappingDetections.isEmpty()) {
			 hierarchy.removeObjects(overlappingDetections, true);
			 changes = true;
			 // Check if that's everything
			 if (overlapping.size() == overlappingDetections.size())
				 return changes;
		 }
		 
		 // Clip any remaining annotations
		 var clipBounds = GeometryTools.createRectangle(0, 0, server.getWidth(), server.getHeight());
		 for (var pathObject : overlapping) {
			 if (pathObject.isAnnotation()) {
				 var roi = pathObject.getROI();
				 var geom = roi.getGeometry();
				 var geom2 = clipBounds.intersection(geom);
				 var roi2 = GeometryTools.geometryToROI(geom2, roi.getImagePlane());
				 ((PathAnnotationObject)pathObject).setROI(roi2);
				 changes = true;
			 }
		 }
		 hierarchy.fireHierarchyChangedEvent(QP.class);
		 return changes;
	 }

	 
	 /*
	  * If Groovy finds a getXXX() it's liable to make xXX look like a variable...
	  * then if the user tries to *create* (or set) a variable with that name, 
	  * it will attempt to call setXXX(something).
	  * That could then fail quietly and be confusing.
	  * So these methods exist to make it fail noisily, and with a proposed solution.
	  */
	 
	 
	 private static void setTMACoreList(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("tMACoreList");
	 }

	 private static void setCellObjects(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("cellObjects");
	 }

	 private static void setTileObjects(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("tileObjects");
	 }

	 private static void setDetectionObjects(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("detectionObjects");
	 }

	 private static void setAnnotationObjects(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("annotationObjects");
	 }

	 private static void setAllObjects(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("allObjects");
	 }

	 private static void setCurrentHierarchy(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("currentHierarchy");
	 }

	 private static void setCurrentImageData(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("currentImageData");
	 }

	 private static void setCurrentServer(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("currentServer");
	 }

	 private static void setCurrentServerPath(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("currentServerPath");
	 }

	 private static void setProject(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("project");
	 }

	 private static void setProjectEntry(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("projectEntry");
	 }

	 private static void setCoreClasses(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("coreClasses");
	 }

	 private static void setCurrentImageName(Object o) throws UnsupportedOperationException {
		 warnAboutSetter("currentImageName");
	 }


	 /**
	  * Warn about attempt to set a property in Groovy, which really there is just a getter that isn't meant to be set.
	  * @param name
	  * @throws UnsupportedOperationException
	  */
	 private static void warnAboutSetter(String name) throws UnsupportedOperationException {
		 logger.warn("Unsupported attempt to set {}. This can happen in a Groovy script if you use "
				 + "'{}' as a global variable name - please use a different name instead, "
				 + "or default a local variable with 'def {}'", name, name, name);
		 throw new UnsupportedOperationException(name + " cannot be set!");
	 }
	
}
