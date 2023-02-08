package qupath.lib.gui.actions.menus;

import static qupath.lib.gui.actions.ActionTools.createAction;

import java.util.List;

import org.controlsfx.control.action.Action;

import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.DefaultActions;
import qupath.lib.gui.actions.OverlayActions;
import qupath.lib.gui.actions.ViewerActions;
import qupath.lib.gui.actions.ActionTools.ActionAccelerator;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionIcon;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.panes.SlideLabelView;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.gui.tools.IconFactory.PathIcons;

public class ViewMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private DefaultActions defaultActions;
	private ViewerActions viewerActions;
	private OverlayActions overlayActions;
	
	private Actions actions;
	
	ViewMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			this.defaultActions = qupath.getDefaultActions();
			this.viewerActions = qupath.getViewerActions();
			this.overlayActions = qupath.getOverlayActions();
			this.actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}
	
	@Override
	public String getName() {
		return QuPathResources.getString("KEY:Menu.View.name");
	}

	
	@ActionMenu("KEY:Menu.View.name")
	public class Actions {
		
		public final Action SHOW_ANALYSIS_PANEL = defaultActions.SHOW_ANALYSIS_PANE;
		
		@ActionDescription("Show the command list (much easier than navigating menus...).")
		@ActionMenu("Show command list")
		@ActionAccelerator("shortcut+l")
		public final Action COMMAND_LIST = Commands.createSingleStageAction(() -> CommandFinderTools.createCommandFinderDialog(qupath));

		@ActionDescription("Show a list containing recently-used commands.")
		@ActionMenu("Show recent commands")
		@ActionAccelerator("shortcut+p")
		public final Action RECENT_COMMAND_LIST = Commands.createSingleStageAction(() -> CommandFinderTools.createRecentCommandsDialog(qupath));

		public final Action SEP_0 = ActionTools.createSeparator();
		
		public final Action BRIGHTNESS_CONTRAST = defaultActions.BRIGHTNESS_CONTRAST;
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionMenu("Multi-view...>Synchronize viewers")
		public final Action MULTIVIEW_SYNCHRONIZE_VIEWERS = viewerActions.TOGGLE_SYNCHRONIZE_VIEWERS;
		
		@ActionMenu("Multi-view...>Match viewer resolutions")
		public final Action MULTIVIEW_MATCH_RESOLUTIONS = viewerActions.MATCH_VIEWER_RESOLUTIONS;

		@ActionMenu("Multi-view...>")
		public final Action SEP_00 = ActionTools.createSeparator();

		@ActionMenu("Multi-view...>Add row")
		@ActionDescription("Add a new row of viewers to the multi-view grid. "
				+ "This makes it possible to view two or more images side-by-side (vertically).")
		public final Action MULTIVIEW_ADD_ROW = qupath.createViewerAction(viewer -> qupath.getViewerManager().addRow(viewer));

		@ActionMenu("Multi-view...>Add column")
		@ActionDescription("Add a new column of viewers to the multi-view grid. "
					+ "This makes it possible to view two or more images side-by-side (horizontally).")
		public final Action MULTIVIEW_ADD_COLUMN = qupath.createViewerAction(viewer -> qupath.getViewerManager().addColumn(viewer));

		@ActionMenu("Multi-view...>")
		public final Action SEP_01 = ActionTools.createSeparator();
		
		@ActionMenu("Multi-view...>Remove row")
		@ActionDescription("Remove the row containing the current viewer from the multi-view grid, if possible. The last row cannot be removed.")
		public final Action MULTIVIEW_REMOVE_ROW = qupath.createViewerAction(viewer -> qupath.getViewerManager().removeRow(viewer));

		@ActionMenu("Multi-view...>Remove column")
		@ActionDescription("Remove the column containing the current viewer from the multi-view grid, if possible. The last column cannot be removed.")
		public final Action MULTIVIEW_REMOVE_COLUMN = qupath.createViewerAction(viewer -> qupath.getViewerManager().removeColumn(viewer));

		@ActionMenu("Multi-view...>")
		public final Action SEP_02 = ActionTools.createSeparator();

		@ActionMenu("Multi-view...>Reset grid size")
		@ActionDescription("Reset the multi-view grid so that all viewers have the same size")
		public final Action MULTIVIEW_RESET_GRID = qupath.createViewerAction(viewer -> qupath.getViewerManager().resetGridSize());		
		
		@ActionMenu("Multi-view...>")
		public final Action SEP_03 = ActionTools.createSeparator();
		
		@ActionMenu("Multi-view...>Close viewer")
		@ActionDescription("Close the image in the current viewer. This is needed before it's possible to remove a viewer from the multi-view grid.")
		public final Action MULTIVIEW_CLOSE_VIEWER = qupath.createViewerAction(viewer -> qupath.closeViewer(viewer));
		
		@ActionDescription("Open a viewer window that shows individual channels of an image size by side. "
				+ "This is useful when working with multiplexed/multichannel fluorescence images.")
		@ActionMenu("Show channel viewer")
		public final Action CHANNEL_VIEWER = qupath.createViewerAction(viewer -> Commands.showChannelViewer(viewer));

		@ActionDescription("Open a viewer window that shows a view of the pixel under the cursor. "
				+ "This is useful for viewing the image booth zoomed in and zoomed out at the same time.")
		@ActionMenu("Show mini viewer")
		public final Action MINI_VIEWER = qupath.createViewerAction(viewer -> Commands.showMiniViewer(viewer));
		
		public final Action SEP_2 = ActionTools.createSeparator();
		
		@ActionDescription("Set the zoom factor to 400% (downsample = 0.25).")
		@ActionMenu("Zoom...>400%")
		public final Action ZOOM_400 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 0.25));
		@ActionDescription("Set the zoom factor to 100% (downsample = 1).")
		@ActionMenu("Zoom...>100%")
		public final Action ZOOM_100 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 1));
		@ActionDescription("Set the zoom factor to 10% (downsample = 10).")
		@ActionMenu("Zoom...>10%")
		public final Action ZOOM_10 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 10));
		@ActionDescription("Set the zoom factor to 1% (downsample = 100).")
		@ActionMenu("Zoom...>1%")
		public final Action ZOOM_1 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 100));
		
		public final Action SEP_3 = ActionTools.createSeparator();
		@ActionDescription("Zoom in for the current viewer.")
		@ActionMenu("Zoom...>Zoom in")
		@ActionIcon(PathIcons.ZOOM_IN)
//		@ActionAccelerator("ignore shift+plus")
		public final Action ZOOM_IN = Commands.createZoomCommand(qupath, 10);
		@ActionDescription("Zoom out for the current viewer.")
		@ActionMenu("Zoom...>Zoom out")
		@ActionIcon(PathIcons.ZOOM_OUT)
		@ActionAccelerator("-")
		public final Action ZOOM_OUT = Commands.createZoomCommand(qupath, -10);
		
		@ActionMenu("Zoom...>Zoom to fit")
		public final Action ZOOM_TO_FIT = viewerActions.ZOOM_TO_FIT;
				
		@ActionDescription("Rotate the image visually (this is only for display - the coordinate system remains unchanged).")
		@ActionMenu("Rotate image")
		public final Action ROTATE_IMAGE = qupath.createImageDataAction(imageData -> Commands.createRotateImageDialog(qupath));

		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionMenu("Cell display>")
		public final Action SHOW_CELL_BOUNDARIES = overlayActions.SHOW_CELL_BOUNDARIES;
		@ActionMenu("Cell display>")
		public final Action SHOW_CELL_NUCLEI = overlayActions.SHOW_CELL_NUCLEI;
		@ActionMenu("Cell display>")
		public final Action SHOW_CELL_BOUNDARIES_AND_NUCLEI = overlayActions.SHOW_CELL_BOUNDARIES_AND_NUCLEI;
		@ActionMenu("Cell display>")
		public final Action SHOW_CELL_CENTROIDS = overlayActions.SHOW_CELL_CENTROIDS;
		
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
		public final Action SHOW_GRID = overlayActions.SHOW_GRID;
		public final Action GRID_SPACING = overlayActions.GRID_SPACING;
		
		public final Action SEP_6 = ActionTools.createSeparator();
		
		@ActionDescription("Record zoom and panning movements within a viewer for later playback and analysis.")
		@ActionMenu("Show view tracker")
		public final Action VIEW_TRACKER = qupath.createImageDataAction(imageData -> Commands.showViewTracker(qupath));

		@ActionDescription("Show the slide label associated with the image in the active viewer (if available).")
		@ActionMenu("Show slide label")
		public final Action SLIDE_LABEL = ActionTools.createSelectableCommandAction(new SlideLabelView(qupath).showingProperty());

		public final Action SEP_7 = ActionTools.createSeparator();
		
		@ActionDescription("Show mouse clicks and keypresses on screen. "
				+ "This is particularly useful for demos and tutorials.")
		@ActionMenu("Show input display")
		public final Action INPUT_DISPLAY = ActionTools.createSelectableCommandAction(qupath.showInputDisplayProperty());

		@ActionDescription("Show a dialog to track memory usage within QuPath, and clear the cache if required.")
		@ActionMenu("Show memory monitor")
		public final Action MEMORY_MONITORY = Commands.createSingleStageAction(() -> Commands.createMemoryMonitorDialog(qupath));
		
		public final Action SHOW_LOG = defaultActions.SHOW_LOG;
		
		
		public final Action SEP_8 = ActionTools.createSeparator();

		@ActionDescription("Turn on all multi-touch gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Turn on all gestures")
		public final Action GESTURES_ALL = createAction(() -> {
			PathPrefs.useScrollGesturesProperty().set(true);
			PathPrefs.useZoomGesturesProperty().set(true);
			PathPrefs.useRotateGesturesProperty().set(true);
		});
		
		@ActionDescription("Turn off all multi-touch gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Turn off all gestures")
		public final Action GESTURES_NONE = createAction(() -> {
			PathPrefs.useScrollGesturesProperty().set(false);
			PathPrefs.useZoomGesturesProperty().set(false);
			PathPrefs.useRotateGesturesProperty().set(false);
		});
		
		@ActionMenu("Multi-touch gestures>")
		public final Action SEP_9 = ActionTools.createSeparator();
		
		@ActionDescription("Toggle scroll gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Use scroll gestures")
		public final Action GESTURES_SCROLL = ActionTools.createSelectableCommandAction(PathPrefs.useScrollGesturesProperty());
		
		@ActionDescription("Toggle zoom gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Use zoom gestures")
		public final Action GESTURES_ZOOM = ActionTools.createSelectableCommandAction(PathPrefs.useZoomGesturesProperty());

		@ActionDescription("Toggle rotate gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Use rotate gestures")
		public final Action GESTURES_ROTATE = ActionTools.createSelectableCommandAction(PathPrefs.useRotateGesturesProperty());

		private Actions() {
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

}
