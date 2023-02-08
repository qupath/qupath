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


package qupath.lib.gui.actions;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.SelectableItem;
import qupath.lib.gui.actions.ActionTools.ActionAccelerator;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionIcon;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.OverlayOptions.DetectionDisplayMode;


/**
 * Actions that interact with {@link OverlayOptions}.
 * These can be used as a basis for creating UI controls that operate on the same options.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class OverlayActions {
	
	@ActionIcon(PathIcons.GRID)
	@ActionAccelerator("shift+g")
	@ActionDescription("KEY:OverlayActions.description.showCountingGrid")
	public final Action SHOW_GRID;

	@ActionDescription("Set the spacing for the counting grid)")
	public final Action GRID_SPACING;
	
	
	@ActionIcon(PathIcons.PIXEL_CLASSIFICATION)
	@ActionAccelerator("c")
	@ActionDescription("KEY:OverlayActions.description.setCountingGridSpacing")
	public final Action SHOW_PIXEL_CLASSIFICATION;

	@ActionIcon(PathIcons.CELL_ONLY)
	@ActionDescription("KEY:OverlayActions.description.showCellBoundaries")
	public final Action SHOW_CELL_BOUNDARIES;

	@ActionIcon(PathIcons.NUCLEI_ONLY)
	@ActionDescription("KEY:OverlayActions.description.showCellNuclei")
	public final Action SHOW_CELL_NUCLEI;

	@ActionIcon(PathIcons.CELL_NUCLEI_BOTH)
	@ActionDescription("KEY:OverlayActions.description.showCellBoth")
	public final Action SHOW_CELL_BOUNDARIES_AND_NUCLEI;

	@ActionIcon(PathIcons.CENTROIDS_ONLY)
	@ActionDescription("KEY:OverlayActions.description.showCellCentroids")
	public final Action SHOW_CELL_CENTROIDS;

	@ActionIcon(PathIcons.ANNOTATIONS)
	@ActionAccelerator("a")
	@ActionDescription("KEY:OverlayActions.description.showAnnotations")
	public final Action SHOW_ANNOTATIONS;
	
	@ActionIcon(PathIcons.SHOW_NAMES)
	@ActionAccelerator("n")
	@ActionDescription("KEY:OverlayActions.description.showAnnotationNames")
	public final Action SHOW_NAMES;
	
	@ActionIcon(PathIcons.ANNOTATIONS_FILL)
	@ActionAccelerator("shift+f")
	@ActionDescription("KEY:OverlayActions.description.fillAnnotations")
	public final Action FILL_ANNOTATIONS;
	
	@ActionIcon(PathIcons.TMA_GRID)
	@ActionAccelerator("g")
	@ActionDescription("KEY:OverlayActions.description.showTMAGrid")
	public final Action SHOW_TMA_GRID;

	@ActionDescription("KEY:OverlayActions.description.showTMALabels")
	public final Action SHOW_TMA_GRID_LABELS;
	
	@ActionIcon(PathIcons.DETECTIONS)
	@ActionAccelerator("d")
	@ActionDescription("KEY:OverlayActions.description.showDetections")
	public final Action SHOW_DETECTIONS;
	
	@ActionIcon(PathIcons.DETECTIONS_FILL)
	@ActionAccelerator("f")
	@ActionDescription("KEY:OverlayActions.description.fillDetections")
	public final Action FILL_DETECTIONS;
	
	@ActionDescription("KEY:OverlayActions.description.showConnections")
	public final Action SHOW_CONNECTIONS;
	
	private OverlayOptions overlayOptions;
	
	public OverlayActions(OverlayOptions overlayOptions) {
		this.overlayOptions = overlayOptions;
		
		SHOW_GRID = ActionTools.createSelectableAction(overlayOptions.showGridProperty(), getName("showCountingGrid"));
		GRID_SPACING = ActionTools.createAction(() -> Commands.promptToSetGridLineSpacing(overlayOptions), getName("setCountingGridSpacing"));
		
		SHOW_PIXEL_CLASSIFICATION = ActionTools.createSelectableAction(overlayOptions.showPixelClassificationProperty(), getName("showPixelOverlay"));
		
		SHOW_ANNOTATIONS = ActionTools.createSelectableAction(overlayOptions.showAnnotationsProperty(), getName("showAnnotations"));
		SHOW_NAMES = ActionTools.createSelectableAction(overlayOptions.showNamesProperty(), getName("showAnnotationNames"));
		FILL_ANNOTATIONS = ActionTools.createSelectableAction(overlayOptions.fillAnnotationsProperty(), getName("fillAnnotations"));
		
		SHOW_DETECTIONS = ActionTools.createSelectableAction(overlayOptions.showDetectionsProperty(), getName("showDetections"));
		FILL_DETECTIONS = ActionTools.createSelectableAction(overlayOptions.fillDetectionsProperty(), getName("fillDetections"));

		SHOW_TMA_GRID = ActionTools.createSelectableAction(overlayOptions.showTMAGridProperty(), getName("showTMAGrid"));
		SHOW_TMA_GRID_LABELS = ActionTools.createSelectableAction(overlayOptions.showTMACoreLabelsProperty(), getName("showTMALabels"));

		SHOW_CELL_BOUNDARIES = ActionTools.createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.BOUNDARIES_ONLY), getName("showCellBoundaries"));
		SHOW_CELL_NUCLEI = ActionTools.createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.NUCLEI_ONLY), getName("showCellNuclei"));
		SHOW_CELL_BOUNDARIES_AND_NUCLEI = ActionTools.createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.NUCLEI_AND_BOUNDARIES), getName("showCellBoth"));
		SHOW_CELL_CENTROIDS = ActionTools.createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.CENTROIDS), getName("showCellCentroids"));
		
		SHOW_CONNECTIONS = ActionTools.createSelectableAction(overlayOptions.showConnectionsProperty(), getName("showConnections"));
		
		ActionTools.getAnnotatedActions(this);
	}
	
	/**
	 * Get the overlay options controlled by these actions.
	 * @return
	 */
	public OverlayOptions getOverlayOptions() {
		return overlayOptions;
	}
	
	
	private static String getName(String key) {
		return QuPathResources.getString("OverlayActions.name." + key);
	}
	
}