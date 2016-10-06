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
import java.lang.reflect.Constructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.stores.ImageRegionStore;
import qupath.lib.io.PathAwtIO;
import qupath.lib.io.PathIO;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.PluginRunnerFX;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

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
	
	
	private static QuPathGUI getGUI() {
		return QuPathGUI.getInstance();
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
			if (isBatchMode() || imageData != getGUI().getImageData()) {
				runner = new CommandLinePluginRunner(getSharedRegionStore(), imageData, true);
			}
			else {
				runner = new PluginRunnerFX(getGUI(), true);
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
		return getGUI() == null || !getGUI().getStage().isShowing();
	}
	
	
	
	public static File promptForFile(String[] extensions) {
		String filterDescription = extensions == null || extensions.length == 0 ? null : "Valid files";
		if (extensions != null && extensions.length == 0)
			extensions = null;
		return QuPathGUI.getSharedDialogHelper().promptForFile(null, null, filterDescription, extensions);
	}
	
}
