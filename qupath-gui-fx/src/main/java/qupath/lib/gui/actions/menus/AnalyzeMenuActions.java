package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.algorithms.IntensityFeaturesPlugin;
import qupath.lib.algorithms.TilerPlugin;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;
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
		return QuPathResources.getString("KEY:Menu.Analyze.name");
	}
	

	@ActionMenu("KEY:Menu.Analyze.name")
	public class Actions {
		
		@ActionDescription("KEY:Menu.Analyze.Preprocessing.description.estimateStainVectors")
		@ActionMenu("KEY:Menu.Analyze.Preprocessing.name.estimateStainVectors")
		public final Action COLOR_DECONVOLUTION_REFINE = qupath.createImageDataAction(imageData -> Commands.promptToEstimateStainVectors(imageData));
		
		@ActionDescription("KEY:Menu.Analyze.Tiles.description.createTiles")
		@ActionMenu("KEY:Menu.Analyze.Tiles.name.createTiles")
		public final Action CREATE_TILES = qupath.createPluginAction("Create tiles", TilerPlugin.class, null);

		@ActionMenu("KEY:Menu.Analyze.CellDetection.name")
		public final Action SEP_0 = ActionTools.createSeparator();

		@ActionMenu("KEY:Menu.Analyze.Features.name.smoothedFeatures")
		@ActionDescription("KEY:Menu.Analyze.Features.description.smoothedFeatures")
		public final Action SMOOTHED_FEATURES = qupath.createPluginAction("Add Smoothed features", SmoothFeaturesPlugin.class, null);
		
		@ActionMenu("KEY:Menu.Analyze.Features.name.intensityFeatures")
		@ActionDescription("KEY:Menu.Analyze.Features.description.intensityFeatures")
		public final Action INTENSITY_FEATURES = qupath.createPluginAction("Add intensity features", IntensityFeaturesPlugin.class, null);
		
		@ActionMenu("KEY:Menu.Analyze.Features.name.shapeFeatures")
		@ActionDescription("KEY:Menu.Analyze.Features.description.shapeFeatures")
		public final Action SHAPE_FEATURES = qupath.createImageDataAction(imageData -> Commands.promptToAddShapeFeatures(qupath));

		@ActionMenu("KEY:Menu.Analyze.Spatial.name.distanceToAnnotations2D")
		@ActionDescription("KEY:Menu.Analyze.Spatial.description.distanceToAnnotations2D")
		public final Action DISTANCE_TO_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData, false));
		
		@ActionMenu("KEY:Menu.Analyze.Spatial.name.signedDistanceToAnnotations2D")
		@ActionDescription("KEY:Menu.Analyze.Spatial.description.signedDistanceToAnnotations2D")
		public final Action SIGNED_DISTANCE_TO_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData, true));
		
		@ActionMenu("KEY:Menu.Analyze.Spatial.name.detectionCentroidDistances2D")
		@ActionDescription("KEY:Menu.Analyze.Spatial.description.detectionCentroidDistances2D")
		public final Action DISTANCE_CENTROIDS = qupath.createImageDataAction(imageData -> Commands.detectionCentroidDistances2D(imageData));

	}
	
}
