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

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.DTrees;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.Modes;
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
	
	public static void addQuPathCommands(final QuPathGUI qupath) {

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

		Menu menuClassify = qupath.getMenu("Classify", true);
		QuPathGUI.addMenuItems(
				menuClassify,
				QuPathGUI.createCommandAction(new OpenCvClassifierCommand(qupath), "Create detection classifier", null, new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)));
		
		
		// Add the Wand tool
		WandToolCV wandTool = new WandToolCV(qupath);
		qupath.putToolForMode(Modes.WAND, wandTool);
	}
	
	private void ensureClassesLoaded() {
		try (var scope = new PointerScope(true)) {
			var mat = new Mat();
			Scalar.all(1.0);
			RTrees.create();
			ANN_MLP.create();
			KNearest.create();
			DTrees.create();
			mat.release();
		}
	}


	@Override
	public void installExtension(QuPathGUI qupath) {
		// Can be annoying waiting a few seconds while classes are loaded later on
		var t = new Thread(() -> ensureClassesLoaded());
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
