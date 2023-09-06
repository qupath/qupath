/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.algorithms.IntensityFeaturesPlugin;
import qupath.lib.algorithms.TilerPlugin;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.plugins.objects.SmoothFeaturesPlugin;

public class AnalyzeMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	
	private Actions actions;
	
	AnalyzeMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}
	
	@Override
	public String getName() {
		return QuPathResources.getString("Menu.Analyze");
	}
	

	@ActionMenu("Menu.Analyze")
	public class Actions {
		
		@ActionConfig("Action.Analyze.Preprocessing.estimateStainVectors")
		public final Action COLOR_DECONVOLUTION_REFINE = qupath.createImageDataAction(imageData -> Commands.promptToEstimateStainVectors(imageData));
		
		@ActionMenu("Menu.Analyze.Tiles")
		@ActionConfig("Action.Analyze.Tiles.createTiles")
		public final Action CREATE_TILES = qupath.createPluginAction("Create tiles", TilerPlugin.class, null);

		@ActionMenu("Menu.Analyze.CellDetection")
		public final Action SEP_0 = ActionTools.createSeparator();

		@ActionMenu("Menu.Analyze.Features")
		@ActionConfig("Action.Analyze.Features.smoothedFeatures")
		public final Action SMOOTHED_FEATURES = qupath.createPluginAction("Add Smoothed features", SmoothFeaturesPlugin.class, null);
		
		@ActionMenu("Menu.Analyze.Features")
		@ActionConfig("Action.Analyze.Features.intensityFeatures")
		public final Action INTENSITY_FEATURES = qupath.createPluginAction("Add intensity features", IntensityFeaturesPlugin.class, null);
		
		@ActionMenu("Menu.Analyze.Features")
		@ActionConfig("Action.Analyze.Features.shapeFeatures")
		public final Action SHAPE_FEATURES = qupath.createImageDataAction(imageData -> Commands.promptToAddShapeFeatures(qupath));

		@ActionMenu("Menu.Analyze.Spatial")
		@ActionConfig("Action.Analyze.Spatial.distanceToAnnotations2D")
		public final Action DISTANCE_TO_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData, false));
		
		@ActionMenu("Menu.Analyze.Spatial")
		@ActionConfig("Action.Analyze.Spatial.signedDistanceToAnnotations2D")
		public final Action SIGNED_DISTANCE_TO_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData, true));
		
		@ActionMenu("Menu.Analyze.Spatial")
		@ActionConfig("Action.Analyze.Spatial.detectionCentroidDistances2D")
		public final Action DISTANCE_CENTROIDS = qupath.createImageDataAction(imageData -> Commands.detectionCentroidDistances2D(imageData));

	}
	
}
