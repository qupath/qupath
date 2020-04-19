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

package qupath.opencv.gui;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.openblas.global.openblas;
import org.bytedeco.opencv.global.opencv_core;
import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionMenu;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.icons.IconFactory;
import qupath.lib.gui.icons.IconFactory.PathIcons;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.opencv.CellCountsCV;
import qupath.opencv.features.DelaunayClusteringPlugin;

/**
 * QuPath extension to add commands dependent on OpenCV.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVExtension implements QuPathExtension {
	
	final private static Logger logger = LoggerFactory.getLogger(OpenCVExtension.class);
	
	/**
	 * Commands based on OpenCV.
	 */
	@SuppressWarnings("javadoc")
	public static class Commands {
		
		@ActionMenu("Analyze>Spatial analysis>")
		@ActionDescription("Apply a Delaunay triangulation to detection objects based on their centroid locations. "
				+ "This helps identify clusters of objects neighboring one another."
				+ "\n\nNote this command is likely to be replaced in a future version.")
		public final Action actionDelaunay;

//		@ActionMenu("Analyze>Region identification>")
//		@ActionDescription("")
//		@Deprecated
//		public final Action actionCytokeratin;

		@ActionMenu("Analyze>Cell detection>")
		@ActionDescription("Fast cell counting for hematoxylin and DAB images.")
		@Deprecated
		public final Action actionFastCellCounts;

		@ActionMenu("Classify>Object classification>Older classifiers>Create detection classifier")
		@ActionDescription("QuPath's original detection classifier. "
				+ "\n\nThis is being replaced by a new and more flexible approach to object classification.")
		@Deprecated
		public final Action actionObjectClassifier;
		
		private Commands(QuPathGUI qupath) {
			actionDelaunay = qupath.createPluginAction("Delaunay cluster features 2D", DelaunayClusteringPlugin.class, null);
//			actionCytokeratin = qupath.createPluginAction("Create cytokeratin annotations (experimental)", DetectCytokeratinCV.class, null);
			actionFastCellCounts = qupath.createPluginAction("Fast cell counts (brightfield)", CellCountsCV.class, null);
			var classifierCommand = new OpenCvClassifierCommand(qupath);
			actionObjectClassifier = qupath.createImageDataAction(imageData -> classifierCommand.run());
		}

	}
	
	@Override
	public void installExtension(QuPathGUI qupath) {

		logger.debug("Installing " + OpenCVExtension.class);
		
		var commands = new Commands(qupath);
		qupath.installActions(ActionTools.getAnnotatedActions(commands));
		
		var t = new Thread(() -> {
	    	// TODO: Check if openblas multithreading continues to have trouble with Mac/Linux
	    	if (!GeneralTools.isWindows()) {
	    		openblas.blas_set_num_threads(1);
	    	}
	    	
			// Install the Wand tool
			Loader.load(opencv_core.class);
			var wandTool = PathTools.createTool(new WandToolCV(qupath), "Wand tool",
					IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, PathIcons.WAND_TOOL));
			logger.debug("Installing wand tool");
			Platform.runLater(() -> {
				qupath.installTool(wandTool, new KeyCodeCombination(KeyCode.W));
				qupath.getToolAction(wandTool).setLongText(
						"Click and drag to draw with a wand tool. "
						+ "Adjust brightness/contrast or wand preferences to customize the sensitivity and behavior."
						);
			});
			
			logger.debug("Loading OpenCV classes");
		});
		t.start();
	}
	
	@Override
	public String getName() {
		return "OpenCV extensions";
	}


	@Override
	public String getDescription() {
		return "QuPath commands that depend on the OpenCv computer vision library - http://opencv.org";
	}

}
