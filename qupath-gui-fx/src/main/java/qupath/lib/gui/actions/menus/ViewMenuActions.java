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

import static qupath.lib.gui.actions.ActionTools.createAction;

import java.util.List;

import org.controlsfx.control.action.Action;

import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.CommonActions;
import qupath.lib.gui.actions.OverlayActions;
import qupath.lib.gui.actions.ViewerActions;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionIcon;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.panes.SlideLabelView;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.gui.tools.IconFactory.PathIcons;

public class ViewMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private CommonActions commonActions;
	private ViewerActions viewerActions;
	private OverlayActions overlayActions;
	
	private Actions actions;
	
	ViewMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			this.commonActions = qupath.getCommonActions();
			this.viewerActions = qupath.getViewerActions();
			this.overlayActions = qupath.getOverlayActions();
			this.actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}
	
	@Override
	public String getName() {
		return QuPathResources.getString("Menu.View");
	}

	
	@ActionMenu("Menu.View")
	public class Actions {
		
		public final Action SHOW_ANALYSIS_PANEL = commonActions.SHOW_ANALYSIS_PANE;
		
		@ActionConfig("Action.View.commandList")
		@ActionAccelerator("shortcut+l")
		public final Action COMMAND_LIST = Commands.createSingleStageAction(() -> CommandFinderTools.createCommandFinderDialog(qupath));

		@ActionConfig("Action.View.recentCommands")
		@ActionAccelerator("shortcut+p")
		public final Action RECENT_COMMAND_LIST = Commands.createSingleStageAction(() -> CommandFinderTools.createRecentCommandsDialog(qupath));

		public final Action SEP_0 = ActionTools.createSeparator();
		
		public final Action BRIGHTNESS_CONTRAST = commonActions.BRIGHTNESS_CONTRAST;
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionMenu("Menu.View.Multiview")		
		public final MultiviewActions multiviewActions = new MultiviewActions();
		
		@ActionConfig("Action.View.channelViewer")
		public final Action CHANNEL_VIEWER = qupath.createViewerAction(viewer -> Commands.showChannelViewer(viewer));

		@ActionConfig("Action.View.miniViewer")
		public final Action MINI_VIEWER = qupath.createViewerAction(viewer -> Commands.showMiniViewer(viewer));
		
		public final Action SEP_2 = ActionTools.createSeparator();
		
		@ActionMenu("Menu.View.Zoom")
		public final Object zoomActions = new ZoomActions();
				
		@ActionConfig("Action.View.rotate")
		public final Action ROTATE_IMAGE = qupath.createImageDataAction(imageData -> Commands.createRotateImageDialog(qupath));

		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionMenu("Menu.View.CellDisplay")
		public final CellDisplayActions cellDisplayActions = new CellDisplayActions();
		
		public final Action SHOW_ANNOTATIONS = overlayActions.SHOW_ANNOTATIONS;
		public final Action FILL_ANNOTATIONS = overlayActions.FILL_ANNOTATIONS;
		public final Action SHOW_NAMES = overlayActions.SHOW_NAMES;
		public final Action SHOW_TMA_GRID = overlayActions.SHOW_TMA_GRID;
		public final Action SHOW_TMA_GRID_LABELS = overlayActions.SHOW_TMA_GRID_LABELS;
		public final Action SHOW_DETECTIONS = overlayActions.SHOW_DETECTIONS;
		public final Action FILL_DETECTIONS = overlayActions.FILL_DETECTIONS;

		public final Action SHOW_CONNECTIONS = overlayActions.SHOW_CONNECTIONS;

		public final Action SHOW_PIXEL_CLASSIFICATION = overlayActions.SHOW_PIXEL_CLASSIFICATION;
		
		public final Action SEP_5 = ActionTools.createSeparator();
		
		public final Action SHOW_OVERVIEW = viewerActions.SHOW_OVERVIEW;
		public final Action SHOW_LOCATION = viewerActions.SHOW_LOCATION;
		public final Action SHOW_SCALEBAR = viewerActions.SHOW_SCALEBAR;
		public final Action SHOW_Z_PROJECT = viewerActions.SHOW_Z_PROJECT;
		public final Action SHOW_GRID = overlayActions.SHOW_GRID;
		public final Action GRID_SPACING = overlayActions.GRID_SPACING;
		
		public final Action SEP_6 = ActionTools.createSeparator();
		
		@ActionConfig("Action.View.viewTracker")
		public final Action VIEW_TRACKER = qupath.createImageDataAction(imageData -> Commands.showViewTracker(qupath));

		@ActionConfig("Action.View.slideLabel")
		public final Action SLIDE_LABEL = ActionTools.createSelectableCommandAction(new SlideLabelView(qupath).showingProperty());

		public final Action SEP_7 = ActionTools.createSeparator();
		
		@ActionConfig("Action.View.inputDisplay")
		public final Action INPUT_DISPLAY = ActionTools.createSelectableCommandAction(qupath.showInputDisplayProperty());

		@ActionConfig("Action.View.memoryMonitor")
		public final Action MEMORY_MONITORY = Commands.createSingleStageAction(() -> Commands.createMemoryMonitorDialog(qupath));
		
		public final Action SHOW_LOG = commonActions.SHOW_LOG;
		
		
		public final Action SEP_8 = ActionTools.createSeparator();

		@ActionMenu("Menu.View.Multitouch")
		public final MultitouchActions multitouchActions = new MultitouchActions();

	}

	public class MultiviewGridActions {

		public final Action MULTIVIEW_GRID_1x1 = viewerActions.VIEWER_GRID_1x1;

		public final Action MULTIVIEW_GRID_1x2 = viewerActions.VIEWER_GRID_1x2;

		public final Action MULTIVIEW_GRID_2x1 = viewerActions.VIEWER_GRID_2x1;

		public final Action MULTIVIEW_GRID_2x2 = viewerActions.VIEWER_GRID_2x2;

		public final Action MULTIVIEW_GRID_3x3 = viewerActions.VIEWER_GRID_3x3;

		public final Action SEP_00 = ActionTools.createSeparator();

		@ActionConfig("Action.View.Multiview.addRow")
		public final Action MULTIVIEW_ADD_ROW = qupath.createViewerAction(viewer -> qupath.getViewerManager().addRow(viewer));

		@ActionConfig("Action.View.Multiview.addColumn")
		public final Action MULTIVIEW_ADD_COLUMN = qupath.createViewerAction(viewer -> qupath.getViewerManager().addColumn(viewer));

		public final Action SEP_01 = ActionTools.createSeparator();

		@ActionConfig("Action.View.Multiview.removeRow")
		public final Action MULTIVIEW_REMOVE_ROW = qupath.createViewerAction(viewer -> qupath.getViewerManager().removeRow(viewer));

		@ActionConfig("Action.View.Multiview.removeColumn")
		public final Action MULTIVIEW_REMOVE_COLUMN = qupath.createViewerAction(viewer -> qupath.getViewerManager().removeColumn(viewer));

		public final Action SEP_02 = ActionTools.createSeparator();

		@ActionConfig("Action.View.Multiview.resetGridSize")
		public final Action MULTIVIEW_RESET_GRID = qupath.createViewerAction(viewer -> qupath.getViewerManager().resetGridSize());

	}
	
	public class MultiviewActions {

		@ActionMenu("Action.View.Multiview.gridMenu")
		public final MultiviewGridActions MULTIVIEW_GRID_ACTIONS = new MultiviewGridActions();

		public final Action SEP_00 = ActionTools.createSeparator();

		public final Action MULTIVIEW_SYNCHRONIZE_VIEWERS = viewerActions.TOGGLE_SYNCHRONIZE_VIEWERS;
		
		public final Action MULTIVIEW_MATCH_RESOLUTIONS = viewerActions.MATCH_VIEWER_RESOLUTIONS;

		public final Action SEP_01 = ActionTools.createSeparator();

		@ActionConfig("Action.View.Multiview.closeViewer")
		public final Action MULTIVIEW_CLOSE_VIEWER = qupath.createViewerAction(viewer -> qupath.closeViewer(viewer));

		public final Action SEP_02 = ActionTools.createSeparator();

		// Refined here to take the active viewer from QuPath itself
		@ActionConfig("ViewerActions.detachViewer")
		public final Action DETACH_VIEWER = qupath.createViewerAction(viewer -> qupath.getViewerManager().detachViewerFromGrid(viewer));

		@ActionConfig("ViewerActions.attachViewer")
		public final Action ATTACH_VIEWER = qupath.createViewerAction(viewer -> qupath.getViewerManager().attachViewerToGrid(viewer));

	}
	
	
	
	public class ZoomActions {
		
		@ActionConfig("Action.View.Zoom.400")
		public final Action ZOOM_400 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 0.25));
		@ActionConfig("Action.View.Zoom.100")
		public final Action ZOOM_100 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 1));
		@ActionConfig("Action.View.Zoom.10")
		public final Action ZOOM_10 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 10));
		@ActionConfig("Action.View.Zoom.1")
		public final Action ZOOM_1 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 100));
		
		public final Action SEP_3 = ActionTools.createSeparator();

		@ActionConfig("Action.View.Zoom.zoomIn")
		@ActionIcon(PathIcons.ZOOM_IN)
//		@ActionAccelerator("ignore shift+plus")
		public final Action ZOOM_IN = Commands.createZoomCommand(qupath, 10);

		@ActionConfig("Action.View.Zoom.zoomOut")
		@ActionIcon(PathIcons.ZOOM_OUT)
		@ActionAccelerator("-")
		public final Action ZOOM_OUT = Commands.createZoomCommand(qupath, -10);
		
		@ActionConfig("Action.View.Zoom.zoomToFit")
		public final Action ZOOM_TO_FIT = viewerActions.ZOOM_TO_FIT;
		
		
		private ZoomActions() {
			// This accelerator should work even if the character requires a shift key to be pressed
			// Oddly, on an English keyboard Shift+= must be pressed at first (since this indicates the + key), 
			// but afterwards = alone will suffice (i.e. Shift is truly ignored again).
			ZOOM_IN.setAccelerator(new KeyCharacterCombination("+", KeyCombination.SHIFT_ANY));
			// Match on whatever would type +
			var combo = new KeyCombination(KeyCombination.SHIFT_ANY, KeyCombination.SHORTCUT_ANY) {
				@Override
				public boolean match(final KeyEvent event) {
					return 
						event.getCode() != KeyCode.UNDEFINED &&
						"+".equals(event.getText()) &&
						super.match(event);
				}
			};
			ZOOM_IN.setAccelerator(combo);
		}
		
	}
	
	
	public class CellDisplayActions {
		
		public final Action SHOW_CELL_BOUNDARIES = overlayActions.SHOW_CELL_BOUNDARIES;

		public final Action SHOW_CELL_NUCLEI = overlayActions.SHOW_CELL_NUCLEI;

		public final Action SHOW_CELL_BOUNDARIES_AND_NUCLEI = overlayActions.SHOW_CELL_BOUNDARIES_AND_NUCLEI;

		public final Action SHOW_CELL_CENTROIDS = overlayActions.SHOW_CELL_CENTROIDS;
		
	}
	
	
	public class MultitouchActions {
		
		@ActionConfig("Action.View.Multitouch.allOn")
		public final Action GESTURES_ALL = createAction(() -> {
			PathPrefs.useScrollGesturesProperty().set(true);
			PathPrefs.useZoomGesturesProperty().set(true);
			PathPrefs.useRotateGesturesProperty().set(true);
		});
		
		@ActionConfig("Action.View.Multitouch.allOff")
		public final Action GESTURES_NONE = createAction(() -> {
			PathPrefs.useScrollGesturesProperty().set(false);
			PathPrefs.useZoomGesturesProperty().set(false);
			PathPrefs.useRotateGesturesProperty().set(false);
		});
		
		public final Action SEP_9 = ActionTools.createSeparator();
		
		@ActionConfig("Action.View.Multitouch.scroll")
		public final Action GESTURES_SCROLL = ActionTools.createSelectableCommandAction(PathPrefs.useScrollGesturesProperty());
		
		@ActionConfig("Action.View.Multitouch.zoom")
		public final Action GESTURES_ZOOM = ActionTools.createSelectableCommandAction(PathPrefs.useZoomGesturesProperty());

		@ActionConfig("Action.View.Multitouch.rotate")
		public final Action GESTURES_ROTATE = ActionTools.createSelectableCommandAction(PathPrefs.useRotateGesturesProperty());
		
	}
	

}
