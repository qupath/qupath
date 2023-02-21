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
public class ProjectActions {
	
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
	
	@ActionAccelerator("shortcut+alt+s")
	@ActionConfig("ViewerActions.synchronize")
	public final Action TOGGLE_SYNCHRONIZE_VIEWERS;
	
	@ActionConfig("ViewerActions.matchResolutions")
	public final Action MATCH_VIEWER_RESOLUTIONS;
	
	private ViewerManager viewerManager;
	
	public ProjectActions(ViewerManager viewerManager) {
		this.viewerManager = viewerManager;
		
		SHOW_OVERVIEW = ActionTools.createSelectableAction(viewerManager.showOverviewProperty());
		SHOW_LOCATION = ActionTools.createSelectableAction(viewerManager.showLocationProperty());
		SHOW_SCALEBAR = ActionTools.createSelectableAction(viewerManager.showScalebarProperty());
		TOGGLE_SYNCHRONIZE_VIEWERS = ActionTools.createSelectableAction(viewerManager.synchronizeViewersProperty());
		MATCH_VIEWER_RESOLUTIONS = new Action(e -> viewerManager.matchResolutions());
		ZOOM_TO_FIT = ActionTools.createSelectableAction(viewerManager.zoomToFitProperty());
		
		ActionTools.getAnnotatedActions(this);
	}
	
	public ViewerManager getViewerManager() {
		return viewerManager;
	}
	
}