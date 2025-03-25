/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023, 2025 QuPath developers, The University of Edinburgh
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

import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionIcon;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.ViewerManager;

/**
 * Actions that interact with one or more viewers.
 * These can be used as a basis for creating UI controls that operate on the same options.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class ViewerActions {
	
	@ActionIcon(PathIcons.ZOOM_TO_FIT)
	@ActionConfig("ViewerActions.zoomToFit")
	public final Action ZOOM_TO_FIT;

	@ActionIcon(PathIcons.OVERVIEW)
	@ActionConfig("ViewerActions.overview")
	public final Action SHOW_OVERVIEW;

	@ActionIcon(PathIcons.LOCATION)
	@ActionConfig("ViewerActions.location")
	public final Action SHOW_LOCATION;

	@ActionIcon(PathIcons.SHOW_SCALEBAR)
	@ActionConfig("ViewerActions.scalebar")
	public final Action SHOW_SCALEBAR;

	@ActionConfig("ViewerActions.zProject")
	public final Action SHOW_Z_PROJECT;
	
	@ActionAccelerator("shortcut+alt+s")
	@ActionConfig("ViewerActions.synchronize")
	public final Action TOGGLE_SYNCHRONIZE_VIEWERS;

	@ActionConfig("ViewerActions.grid1x1")
	@ActionIcon(PathIcons.VIEWER_GRID_1x1)
	public final Action VIEWER_GRID_1x1;

	@ActionConfig("ViewerActions.grid1x2")
	@ActionIcon(PathIcons.VIEWER_GRID_1x2)
	public final Action VIEWER_GRID_1x2;

	@ActionConfig("ViewerActions.grid2x1")
	@ActionIcon(PathIcons.VIEWER_GRID_2x1)
	public final Action VIEWER_GRID_2x1;

	@ActionConfig("ViewerActions.grid2x2")
	@ActionIcon(PathIcons.VIEWER_GRID_2x2)
	public final Action VIEWER_GRID_2x2;

	@ActionConfig("ViewerActions.grid3x3")
	@ActionIcon(PathIcons.VIEWER_GRID_3x3)
	public final Action VIEWER_GRID_3x3;

	@ActionConfig("ViewerActions.matchResolutions")
	public final Action MATCH_VIEWER_RESOLUTIONS;

	@ActionConfig("ViewerActions.detachViewer")
	public final Action DETACH_VIEWER;

	@ActionConfig("ViewerActions.attachViewer")
	public final Action ATTACH_VIEWER;

	private ViewerManager viewerManager;
	
	public ViewerActions(ViewerManager viewerManager) {
		this.viewerManager = viewerManager;
		
		SHOW_OVERVIEW = ActionTools.createSelectableAction(viewerManager.showOverviewProperty());
		SHOW_LOCATION = ActionTools.createSelectableAction(viewerManager.showLocationProperty());
		SHOW_SCALEBAR = ActionTools.createSelectableAction(viewerManager.showScalebarProperty());
		SHOW_Z_PROJECT = ActionTools.createSelectableAction(viewerManager.showZProjectControlsProperty());
		TOGGLE_SYNCHRONIZE_VIEWERS = ActionTools.createSelectableAction(viewerManager.synchronizeViewersProperty());
		MATCH_VIEWER_RESOLUTIONS = new Action(e -> viewerManager.matchResolutions());

		ZOOM_TO_FIT = ActionTools.createAction(() -> viewerManager.getActiveViewer().zoomToFit());
		ZOOM_TO_FIT.disabledProperty().bind(viewerManager.activeViewerProperty().isNull());

		VIEWER_GRID_1x1 = ActionTools.createAction(() -> viewerManager.setGridSize(1, 1));
		VIEWER_GRID_2x1 = ActionTools.createAction(() -> viewerManager.setGridSize(2, 1));
		VIEWER_GRID_1x2 = ActionTools.createAction(() -> viewerManager.setGridSize(1, 2));
		VIEWER_GRID_2x2 = ActionTools.createAction(() -> viewerManager.setGridSize(2, 2));
		VIEWER_GRID_3x3 = ActionTools.createAction(() -> viewerManager.setGridSize(3, 3));

		DETACH_VIEWER = ActionTools.createAction(() -> viewerManager.detachActiveViewerFromGrid());
		ATTACH_VIEWER = ActionTools.createAction(() -> viewerManager.attachActiveViewerToGrid());

		ActionTools.getAnnotatedActions(this);
	}
	
	public ViewerManager getViewerManager() {
		return viewerManager;
	}
	
}