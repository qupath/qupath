package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.algorithms.IntensityFeaturesPlugin;
import qupath.lib.algorithms.TilerPlugin;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.DefaultActions;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.plugins.objects.SmoothFeaturesPlugin;

public class AnalyzeMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private DefaultActions defaultActions;
	
	private Actions actions;
	
	AnalyzeMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
		this.defaultActions = qupath.getDefaultActions();
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
		
		@ActionDescription("Estimate stain vectors for color deconvolution in brightfield images. " + 
				"This can be used when there are precisely 2 stains (e.g. hematoxylin and eosin, hematoxylin and DAB) " +
				"to improve stain separation.")
		@ActionMenu("Preprocessing>Estimate stain vectors")
		public final Action COLOR_DECONVOLUTION_REFINE = qupath.createImageDataAction(imageData -> Commands.promptToEstimateStainVectors(imageData));
		
		@ActionDescription("Create tiles. These can be useful as part of a larger workflow, for example " + 
				"by adding intensity measurements to the tiles, training a classifier and then merging classified tiles to identify larger regions.")
		@ActionMenu("Tiles & superpixels>Create tiles")
		public final Action CREATE_TILES = qupath.createPluginAction("Create tiles", TilerPlugin.class, null);

		@ActionMenu("Cell detection>")
		public final Action SEP_0 = ActionTools.createSeparator();

		@ActionDescription("Supplement the measurements for detection objects by calculating a weighted sum of the corresponding measurements from neighboring objects.")
		@ActionMenu("Calculate features>Add smoothed features")
		public final Action SMOOTHED_FEATURES = qupath.createPluginAction("Add Smoothed features", SmoothFeaturesPlugin.class, null);
		@ActionDescription("Add new intensity-based features to objects.")
		@ActionMenu("Calculate features>Add intensity features")
		public final Action INTENSITY_FEATURES = qupath.createPluginAction("Add intensity features", IntensityFeaturesPlugin.class, null);
		@ActionDescription("Add new shape-based features to objects.")
		@ActionMenu("Calculate features>Add shape features")
		public final Action SHAPE_FEATURES = qupath.createImageDataAction(imageData -> Commands.promptToAddShapeFeatures(qupath));

		@ActionDescription("Calculate distances between detection centroids and the closest annotation for each classification, using zero if the centroid is inside the annotation. " +
				"For example, this may be used to identify the distance of every cell from 'bigger' region that has been annotated (e.g. an area of tumor, a blood vessel).")
		@ActionMenu("Spatial analysis>Distance to annotations 2D")
		public final Action DISTANCE_TO_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData, false));
		
		@ActionDescription("Calculate distances between detection centroids and the closest annotation for each classification, using the negative distance to the boundary if the centroid is inside the annotation. " +
				"For example, this may be used to identify the distance of every cell from 'bigger' region that has been annotated (e.g. an area of tumor, a blood vessel).")
		@ActionMenu("Spatial analysis>Signed distance to annotations 2D")
		public final Action SIGNED_DISTANCE_TO_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData, true));
		
		@ActionDescription("Calculate distances between detection centroids for each classification. " +
				"For example, this may be used to identify the closest cell of a specified type.")
		@ActionMenu("Spatial analysis>Detect centroid distances 2D")
		public final Action DISTANCE_CENTROIDS = qupath.createImageDataAction(imageData -> Commands.detectionCentroidDistances2D(imageData));

	}
	
}
