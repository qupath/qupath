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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.DefaultMode;
import qupath.lib.gui.QuPathGUI.Mode;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.opencv.CellCountsCV;
import qupath.opencv.DetectCytokeratinCV;
import qupath.opencv.WatershedNucleiCV;
import qupath.opencv.features.DelaunayClusteringPlugin;
import qupath.opencv.gui.classify.OpenCvClassifierCommand;
import qupath.opencv.tools.WandToolCV;

/**
 * QuPath extension to add commands dependent on OpenCV.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVExtension implements QuPathExtension {
	
	final private static Logger logger = LoggerFactory.getLogger(OpenCVExtension.class);
	
	static void addQuPathCommands(final QuPathGUI qupath) {
		
		logger.debug("Installing " + OpenCVExtension.class);

//		Menu menuTMA = qupath.getMenu("TMA", true);
//		QuPathGUI.addMenuItems(
//				menuTMA,
//				QuPathGUI.createCommandAction(new AlignCoreAnnotationsCV(qupath), "Align annotations within TMA core (TMA, experimental)")
//				);
		
		Menu menuFeatures = qupath.getMenu("Analyze>Calculate features", true);
		QuPathGUI.addMenuItems(
				menuFeatures,
				qupath.createPluginAction("Add Delaunay cluster features (experimental)", DelaunayClusteringPlugin.class, null)
				);

		Menu menuRegions = qupath.getMenu("Analyze>Region identification", true);
		QuPathGUI.addMenuItems(
				menuRegions,
//				QuPathGUI.createCommandAction(new TissueSegmentationCommand(qupath), "Tissue identification (OpenCV, experimental)"),
				qupath.createPluginAction("Create cytokeratin annotations (TMA, experimental)", DetectCytokeratinCV.class, null)
				);

		Menu menuCellAnalysis = qupath.getMenu("Analyze>Cell analysis", true);
		QuPathGUI.addMenuItems(
				menuCellAnalysis,
				new SeparatorMenuItem(),
				qupath.createPluginAction("Watershed nucleus detection (OpenCV, experimental)", WatershedNucleiCV.class, null),
				qupath.createPluginAction("Fast cell counts (brightfield)", CellCountsCV.class, null)
				);

		Menu menuClassify = qupath.getMenu("Classify>Object classification", true);
		QuPathGUI.addMenuItems(
				menuClassify,
				null,
				QuPathGUI.createCommandAction(new OpenCvClassifierCommand(qupath), "Create detection classifier", null, new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)));
		
		
//		// Add the Wand tool
//		logger.debug("Installing wand tool");
//		Platform.runLater(() -> {
//			WandToolCV wandTool = new WandToolCV(qupath);
//			qupath.putToolForMode(Modes.WAND, wandTool);
//		});
	}
	
	private static void ensureClassesLoaded() {
		logger.debug("Ensuring OpenCV classes are loaded");
		try (var scope = new org.bytedeco.javacpp.PointerScope(true)) {
			var mat = new org.bytedeco.opencv.opencv_core.Mat();
			var matvec = new org.bytedeco.opencv.opencv_core.MatVector();
			var rect = new org.bytedeco.opencv.opencv_core.Rect();
			org.bytedeco.opencv.opencv_core.Scalar.all(1.0);
			org.bytedeco.opencv.opencv_ml.RTrees.create();
			org.bytedeco.opencv.opencv_ml.ANN_MLP.create();
			org.bytedeco.opencv.opencv_ml.KNearest.create();
			org.bytedeco.opencv.opencv_ml.DTrees.create();
			mat.close();
			matvec.close();
			rect.close();
		}
	}


	@Override
	public void installExtension(QuPathGUI qupath) {
		// Can be annoying waiting a few seconds while classes are loaded later on
		var t = new Thread(() -> {
			// Add the Wand tool in a background thread (as it is rather slow to load)
			WandToolCV wandTool = new WandToolCV(qupath);
			logger.debug("Installing wand tool");
			Platform.runLater(() -> {
				qupath.putToolForMode(DefaultMode.WAND, wandTool);
			});
			logger.debug("Loading OpenCV classes");
			ensureClassesLoaded();
		});
		t.setDaemon(true);
		t.start();

		
		addQuPathCommands(qupath);
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
