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

import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.ActionTools.ActionAccelerator;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionIcon;
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
	@ActionDescription("KEY:ViewerActions.description.zoomToFit")
	public final Action ZOOM_TO_FIT;

	@ActionIcon(PathIcons.OVERVIEW)
	@ActionDescription("KEY:ViewerActions.description.overview")
	public final Action SHOW_OVERVIEW;

	@ActionIcon(PathIcons.LOCATION)
	@ActionDescription("KEY:ViewerActions.description.location")
	public final Action SHOW_LOCATION;

	@ActionIcon(PathIcons.SHOW_SCALEBAR)
	@ActionDescription("KEY:ViewerActions.description.scalebar")
	public final Action SHOW_SCALEBAR;
	
	@ActionAccelerator("shortcut+alt+s")
	@ActionDescription("KEY:ViewerActions.description.synchronize")
	public final Action TOGGLE_SYNCHRONIZE_VIEWERS;
	
	@ActionDescription("KEY:ViewerActions.description.matchResolutions")
	public final Action MATCH_VIEWER_RESOLUTIONS;
	
	private ViewerManager viewerManager;
	
	public ViewerActions(ViewerManager viewerManager) {
		this.viewerManager = viewerManager;
		
		SHOW_OVERVIEW = ActionTools.createSelectableAction(viewerManager.showOverviewProperty(), getName("overview"));
		SHOW_LOCATION = ActionTools.createSelectableAction(viewerManager.showLocationProperty(), getName("location"));
		SHOW_SCALEBAR = ActionTools.createSelectableAction(viewerManager.showScalebarProperty(), getName("scalebar"));
		TOGGLE_SYNCHRONIZE_VIEWERS = ActionTools.createSelectableAction(viewerManager.synchronizeViewersProperty(), getName("synchronize"));
		MATCH_VIEWER_RESOLUTIONS = new Action(getName("matchResolutions"), e -> viewerManager.matchResolutions());
		ZOOM_TO_FIT = ActionTools.createSelectableAction(viewerManager.zoomToFitProperty(), getName("zoomToFit"));
		
		ActionTools.getAnnotatedActions(this);
	}
	
	public ViewerManager getViewerManager() {
		return viewerManager;
	}
	
	private static String getName(String key) {
		return QuPathResources.getString("ViewerActions.name." + key);
	}
	
}