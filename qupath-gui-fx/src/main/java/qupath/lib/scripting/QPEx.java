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
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.models.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.stores.ImageRegionStore;
import qupath.lib.io.PathAwtIO;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.PluginRunnerFX;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;

/**
 * Alternative to QP offering static methods of use for scripting, 
 * along with some extra methods that require access of GUI features.
 * 
 * @author Pete Bankhead
 *
 */
public class QPEx extends QP {

	final private static Logger logger = LoggerFactory.getLogger(QPEx.class);
	
	final public static String PROJECT_BASE_DIR = "{%PROJECT}";
	
	private static ImageRegionStore<?> sharedRegionStore;

	/**
	 * Load ImageData from a file.
	 * 
	 * @param path Path to the file containing ImageData.
	 * @param setBatchData If true, the <code>setBatchImageData(ImageData)</code> will be called if the loading is successful.
	 * @return
	 * 
	 * @see setBatchImageData
	 */
	public static ImageData<BufferedImage> loadImageData(final String path, final boolean setBatchData) {
		ImageData<BufferedImage> imageData = PathIO.readImageData(new File(resolvePath(path)), null, null, BufferedImage.class);
		if (setBatchData && imageData != null)
			setBatchImageData(imageData);
		return imageData;
	}
	
	
	
	public static void writeTMAData(final String path) {
		writeTMAData(path, true);
	}
	
	@Deprecated
	public static void writeTMAData(final String path, final boolean includeImages) {
		writeTMAData((ImageData<BufferedImage>)getCurrentImageData(), resolvePath(path), includeImages);
	}

	public static void exportTMAData(final String path, final double downsampleFactor) {
		exportTMAData((ImageData<BufferedImage>)getCurrentImageData(), resolvePath(path), downsampleFactor);
	}
	
	private static String resolvePath(final String path) {
		String base = getProjectBaseDirectory();
		if (base != null)
			return path.replace(PROJECT_BASE_DIR, base);
		else if (path.contains(PROJECT_BASE_DIR))
			throw new IllegalArgumentException("Cannot resolve path '" + path + "' - no project base directory available");
		return
			path;
	}
	
	public static void writeTMAData(final ImageData<BufferedImage> imageData, final String path) {
		writeTMAData(imageData, path, true);
	}
	
	@Deprecated
	public static void writeTMAData(final ImageData<BufferedImage> imageData, final String path, final boolean includeImages) {
		double downsample = includeImages ? Double.NaN : -1;
		exportTMAData(imageData, path, downsample);
	}
	
	public static void exportTMAData(final ImageData<BufferedImage> imageData, final String path, final double downsampleFactor) {
		if (imageData == null)
			return;
		PathAwtIO.writeTMAData(new File(resolvePath(path)), imageData, null, downsampleFactor);
	}
	
	
	public static QuPathGUI getQuPath() {
		return QuPathGUI.getInstance();
	}
	
	
	public static QuPathViewer getCurrentViewer() {
		QuPathGUI qupath = QuPathGUI.getInstance();
		return qupath == null ? null : qupath.getViewer();
	}
	
	
	public static String buildFilePath(String...path) {
		File file = new File(resolvePath(path[0]));
		for (int i = 1; i < path.length; i++)
			file = new File(file, path[i]);
		return file.getAbsolutePath();
	}
	
	
	public static boolean mkdirs(String path) {
		File file = new File(resolvePath(path));
		if (!file.exists())
			return file.mkdirs();
		return false;
	}
	
	public static boolean fileExists(String path) {
		return new File(resolvePath(path)).exists();
	}

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
		Project<?> project = getProject();
		return project == null ? null : project.getBaseDirectory().getAbsolutePath();
	}
	
	/**
	 * Get the current project, or null if no project is open.
	 * 
	 * @return
	 */
	public static Project<BufferedImage> getProject() {
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath != null)
			return qupath.getProject();
		else
			return null;
	}
	
	
	/**
	 * Get the project entry for the currently-open image within the current project, 
	 * or null if no project/image is open.
	 * 
	 * @return
	 */
	public static ProjectImageEntry<BufferedImage> getProjectEntry() {
		Project<BufferedImage> project = getProject();
		String path = getCurrentServerPath();
		if (project == null || path == null)
			return null;
		return project.getImageEntry(path);
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
	
	
	static void setSharedRegionStore(final ImageRegionStore<?> regionStore) {
		sharedRegionStore = regionStore;
	}

	static ImageRegionStore<?> getSharedRegionStore() {
		return sharedRegionStore;
	}

	
	
	@SuppressWarnings("unchecked")
	public static boolean runPlugin(final String className, final ImageData<?> imageData, final String args) throws InterruptedException {
		if (imageData == null)
			return false;
		
		boolean completed = false;
		String pluginName = className;
		boolean cancelled = false;
		try {
			Class<?> cPlugin = QP.class.getClassLoader().loadClass(className);
			Constructor<?> cons = cPlugin.getConstructor();
			final PathPlugin plugin = (PathPlugin)cons.newInstance();
			pluginName = plugin.getName();
			PluginRunner runner;
			// TODO: Give potential of passing a plugin runner
			if (isBatchMode() || imageData != getQuPath().getImageData()) {
				runner = new CommandLinePluginRunner(getSharedRegionStore(), imageData, true);
			}
			else {
				runner = new PluginRunnerFX(getQuPath(), true);
			}
			completed = plugin.runPlugin(runner, args);
			cancelled = runner.isCancelled();
		} catch (Exception e) {
			logger.error("Error running plugin {}", className);
			logger.error("", e);
		}
		// Notify caller that this failed
		if (cancelled)
			throw new InterruptedException(pluginName + " cancelled!");
//		if (!completed) {
//			throw new InterruptedException(pluginName + " cancelled!");
//		}
		return completed;
	}
	
	
	public static boolean runPlugin(String className, String args) throws InterruptedException {
		ImageData<?> imageData = getCurrentImageData();
		if (imageData == null)
			return false;
		return runPlugin(className, imageData, args);
	}
	
	
	static boolean isBatchMode() {
		return getQuPath() == null || !getQuPath().getStage().isShowing();
	}
	
	
	
	public static File promptForFile(String[] extensions) {
		String filterDescription = extensions == null || extensions.length == 0 ? null : "Valid files";
		if (extensions != null && extensions.length == 0)
			extensions = null;
		return QuPathGUI.getSharedDialogHelper().promptForFile(null, null, filterDescription, extensions);
	}
	

	public static PathObject mergeAnnotations(final Collection<PathObject> annotations) {
		return mergeAnnotations(getCurrentHierarchy(), annotations);
	}
	
	public static PathObject mergeSelectedAnnotations() {
		return mergeSelectedAnnotations(getCurrentHierarchy());
	}
	
	public static PathObject mergeAnnotations(final PathObjectHierarchy hierarchy, final Collection<PathObject> annotations) {
		if (hierarchy == null)
			return null;
		
		// Get all the selected annotations with area
		PathShape shapeNew = null;
		List<PathObject> children = new ArrayList<>();
		Set<PathClass> pathClasses = new HashSet<>();
		for (PathObject child : annotations) {
			if (child instanceof PathAnnotationObject && child.getROI() instanceof PathArea) {
				if (shapeNew == null)
					shapeNew = (PathShape)child.getROI();//.duplicate();
				else
					shapeNew = PathROIToolsAwt.combineROIs(shapeNew, (PathArea)child.getROI(), PathROIToolsAwt.CombineOp.ADD);
				if (child.getPathClass() != null)
					pathClasses.add(child.getPathClass());
				children.add(child);
			}
		}
		// Check if we actually merged anything
		if (children.isEmpty())
			return null;
		if (children.size() == 1)
			return children.get(0);
	
		// Create and add the new object, removing the old ones
		PathObject pathObjectNew = new PathAnnotationObject(shapeNew);
		if (pathClasses.size() == 1)
			pathObjectNew.setPathClass(pathClasses.iterator().next());
		else
			logger.warn("Cannot assign class unambiguously - " + pathClasses.size() + " classes represented in selection");
		hierarchy.removeObjects(children, true);
		hierarchy.addPathObject(pathObjectNew, false);
		//				pathObject.removePathObjects(children);
		//				pathObject.addPathObject(pathObjectNew);
		//				hierarchy.fireHierarchyChangedEvent(pathObject);
		return pathObjectNew;
	}


	public static PathObject mergeSelectedAnnotations(final PathObjectHierarchy hierarchy) {
		return hierarchy == null ? null : mergeAnnotations(hierarchy, hierarchy.getSelectionModel().getSelectedObjects());
	}
	
	public static PathObject makeInverseAnnotation(final PathObject pathObject) {
		return makeInverseAnnotation(getCurrentImageData(), pathObject);
	}
	
	public static PathObject makeInverseAnnotation(final ImageData<?> imageData, final PathObject pathObject) {
		if (imageData == null)
			return null;
		
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		// Get the currently-selected area
		PathArea shapeSelected = null;
		if (pathObject instanceof PathAnnotationObject) {
			shapeSelected = getAreaROI(pathObject);
		}
		if (shapeSelected == null) {
			logger.error("Cannot create inverse annotation from " + pathObject);
			return null;
		}

		// Get the parent area to use
		PathObject parent = pathObject.getParent();
		PathArea shape = getAreaROI(parent);
		if (shape == null)
			shape = new RectangleROI(0, 0, imageData.getServer().getWidth(), imageData.getServer().getHeight(), shapeSelected.getC(), shapeSelected.getZ(), shapeSelected.getT());

		// Create the new ROI
		PathShape shapeNew = PathROIToolsAwt.combineROIs(shape, shapeSelected, PathROIToolsAwt.CombineOp.SUBTRACT);
		PathObject pathObjectNew = new PathAnnotationObject(shapeNew);

		// Reassign all other children to the new parent
		List<PathObject> children = new ArrayList<>(parent.getChildObjects());
		children.remove(pathObject);
		pathObjectNew.addPathObjects(children);

		parent.addPathObject(pathObjectNew);
		hierarchy.fireHierarchyChangedEvent(parent);		
		return pathObjectNew;
	}
	
	
	private static PathArea getAreaROI(PathObject pathObject) {
		if (pathObject == null)
			return null;
		ROI pathROI = pathObject.getROI();
		if (!(pathROI instanceof PathArea))
			return null;
		return (PathArea)pathObject.getROI();
	}
	
	
	
	public static void saveAnnotationMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathAnnotationObject.class, path, includeColumns);
	}
	
	public static void saveTMAMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), TMACoreObject.class, path, includeColumns);
	}
	
	public static void saveDetectionMeasurements(final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), PathDetectionObject.class, path, includeColumns);
	}
	
	
	public static void saveAnnotationMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, PathAnnotationObject.class, path, includeColumns);
	}
	
	public static void saveTMAMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, TMACoreObject.class, path, includeColumns);
	}
	
	public static void saveDetectionMeasurements(final ImageData<?> imageData, final String path, final String... includeColumns) {
		saveMeasurements(imageData, PathDetectionObject.class, path, includeColumns);
	}

	public static void saveMeasurements(final Class<? extends PathObject> type, final String path, final String... includeColumns) {
		saveMeasurements(getCurrentImageData(), type, path, includeColumns);
	}
	
	public static void saveMeasurements(final ImageData<?> imageData, final Class<? extends PathObject> type, final String path, final String... includeColumns) {
		File fileOutput = new File(resolvePath(path));
		if (fileOutput.isDirectory()) {
			String ext = ",".equals(PathPrefs.getTableDelimiter()) ? ".csv" : ".txt";
			fileOutput = new File(fileOutput, imageData.getServer().getShortServerName() + " " + PathObjectTools.getSuitableName(type, true) + ext);
		}
		ObservableMeasurementTableData model = new ObservableMeasurementTableData();
		model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));
		try {
			PrintWriter writer = new PrintWriter(fileOutput);
			Collection<String> excludeColumns;
			if (includeColumns.length == 0) {
				excludeColumns = Collections.emptyList();
			} else {
				excludeColumns = new LinkedHashSet<>(model.getAllNames());
				excludeColumns.removeAll(Arrays.asList(includeColumns));
			}
			writer.println(SummaryMeasurementTableCommand.getTableModelString(model, PathPrefs.getTableDelimiter(), excludeColumns));
			writer.close();
		} catch (FileNotFoundException e) {
			logger.error("File {} not found!", fileOutput);
		}
	}
	
	
	
}
