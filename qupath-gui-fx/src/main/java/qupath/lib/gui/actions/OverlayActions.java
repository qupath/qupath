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

import qupath.lib.gui.SelectableItem;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionIcon;
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
	@ActionConfig("OverlayActions.showCountingGrid")
	public final Action SHOW_GRID;

	@ActionConfig("OverlayActions.setCountingGridSpacing")
	public final Action GRID_SPACING;
	
	@ActionIcon(PathIcons.PIXEL_CLASSIFICATION)
	@ActionAccelerator("c")
	@ActionConfig("OverlayActions.showPixelOverlay")
	public final Action SHOW_PIXEL_CLASSIFICATION;

	@ActionIcon(PathIcons.CELL_ONLY)
	@ActionConfig("OverlayActions.showCellBoundaries")
	public final Action SHOW_CELL_BOUNDARIES;

	@ActionIcon(PathIcons.NUCLEI_ONLY)
	@ActionConfig("OverlayActions.showCellNuclei")
	public final Action SHOW_CELL_NUCLEI;

	@ActionIcon(PathIcons.CELL_NUCLEI_BOTH)
	@ActionConfig("OverlayActions.showCellBoth")
	public final Action SHOW_CELL_BOUNDARIES_AND_NUCLEI;

	@ActionIcon(PathIcons.CENTROIDS_ONLY)
	@ActionConfig("OverlayActions.showCellCentroids")
	public final Action SHOW_CELL_CENTROIDS;

	@ActionIcon(PathIcons.ANNOTATIONS)
	@ActionAccelerator("a")
	@ActionConfig("OverlayActions.showAnnotations")
	public final Action SHOW_ANNOTATIONS;
	
	@ActionIcon(PathIcons.SHOW_NAMES)
	@ActionAccelerator("n")
	@ActionConfig("OverlayActions.showAnnotationNames")
	public final Action SHOW_NAMES;
	
	@ActionIcon(PathIcons.ANNOTATIONS_FILL)
	@ActionAccelerator("shift+f")
	@ActionConfig("OverlayActions.fillAnnotations")
	public final Action FILL_ANNOTATIONS;
	
	@ActionIcon(PathIcons.TMA_GRID)
	@ActionAccelerator("g")
	@ActionConfig("OverlayActions.showTMAGrid")
	public final Action SHOW_TMA_GRID;

	@ActionConfig("OverlayActions.showTMALabels")
	public final Action SHOW_TMA_GRID_LABELS;
	
	@ActionIcon(PathIcons.DETECTIONS)
	@ActionAccelerator("d")
	@ActionConfig("OverlayActions.showDetections")
	public final Action SHOW_DETECTIONS;
	
	@ActionIcon(PathIcons.DETECTIONS_FILL)
	@ActionAccelerator("f")
	@ActionConfig("OverlayActions.fillDetections")
	public final Action FILL_DETECTIONS;

	@ActionIcon(PathIcons.SHOW_CONNECTIONS)
	@ActionConfig("OverlayActions.showConnections")
	public final Action SHOW_CONNECTIONS;
	
	private OverlayOptions overlayOptions;
	
	public OverlayActions(OverlayOptions overlayOptions) {
		this.overlayOptions = overlayOptions;
		
		SHOW_GRID = ActionTools.createSelectableAction(overlayOptions.showGridProperty());
		GRID_SPACING = ActionTools.createAction(() -> Commands.promptToSetGridLineSpacing(overlayOptions));
		
		SHOW_PIXEL_CLASSIFICATION = ActionTools.createSelectableAction(overlayOptions.showPixelClassificationProperty());
		
		SHOW_ANNOTATIONS = ActionTools.createSelectableAction(overlayOptions.showAnnotationsProperty());
		SHOW_NAMES = ActionTools.createSelectableAction(overlayOptions.showNamesProperty());
		FILL_ANNOTATIONS = ActionTools.createSelectableAction(overlayOptions.fillAnnotationsProperty());
		
		SHOW_DETECTIONS = ActionTools.createSelectableAction(overlayOptions.showDetectionsProperty());
		FILL_DETECTIONS = ActionTools.createSelectableAction(overlayOptions.fillDetectionsProperty());

		SHOW_TMA_GRID = ActionTools.createSelectableAction(overlayOptions.showTMAGridProperty());
		SHOW_TMA_GRID_LABELS = ActionTools.createSelectableAction(overlayOptions.showTMACoreLabelsProperty());

		SHOW_CELL_BOUNDARIES = ActionTools.createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.BOUNDARIES_ONLY));
		SHOW_CELL_NUCLEI = ActionTools.createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.NUCLEI_ONLY));
		SHOW_CELL_BOUNDARIES_AND_NUCLEI = ActionTools.createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.NUCLEI_AND_BOUNDARIES));
		SHOW_CELL_CENTROIDS = ActionTools.createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.CENTROIDS));
		
		SHOW_CONNECTIONS = ActionTools.createSelectableAction(overlayOptions.showConnectionsProperty());
		
		ActionTools.getAnnotatedActions(this);
	}
	
	/**
	 * Get the overlay options controlled by these actions.
	 * @return
	 */
	public OverlayOptions getOverlayOptions() {
		return overlayOptions;
	}
	
}