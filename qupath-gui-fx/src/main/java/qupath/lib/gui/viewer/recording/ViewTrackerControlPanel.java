/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.viewer.recording;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.action.ActionUtils.ActionTextBehavior;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Panel for viewing ViewTracker controls.
 * <p>
 * This add buttons to start/stop recording and playback and view/export recorded data.
 * 
 * @author Pete Bankhead
 */
public class ViewTrackerControlPanel {

//	private final static Logger logger = LoggerFactory.getLogger(ViewTrackerPanel.class);
	
	private static final Node iconRecord = PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.PLAYBACK_RECORD);
	private static final Node iconRecordStop = PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.PLAYBACK_RECORD_STOP);
	private static final Node iconPlay = PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.PLAYBACK_PLAY);
	private static final Node iconPlayStop = PathIconFactory.createNode(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.PLAYBACK_PLAY_STOP);
	
	private ViewTracker tracker;
	
	private ToolBar toolbar = new ToolBar();

	/**
	 * Constructor.
	 * @param viewer the viewer to track
	 */
	public ViewTrackerControlPanel(final QuPathViewer viewer) {
		this(viewer, ViewTrackers.createViewTracker(viewer));
	}

	/**
	 * Constructor.
	 * @param viewer the viewer to track
	 * @param viewTracker the tracker to use
	 */
	ViewTrackerControlPanel(final QuPathViewer viewer, final ViewTracker viewTracker) {
		this.tracker = viewTracker;

//		ToolBar toolbar = new ToolBar();
		ViewTrackerPlayback playback = new ViewTrackerPlayback(tracker, viewer);
		
		Action actionRecord = QuPathGUI.createSelectableCommandAction(tracker.recordingProperty(), "Record", iconRecord, null);
		Action actionPlayback = QuPathGUI.createSelectableCommandAction(playback.playingProperty(), "Play", iconPlay, null);
		actionPlayback.setDisabled(tracker.isEmpty());
		
		// Can't select one while the other is selected
		actionRecord.disabledProperty().bind(actionPlayback.selectedProperty());
//		actionPlayback.disabledProperty().bind(actionRecord.selectedProperty());
		
		// Ensure icons are correct
		tracker.recordingProperty().addListener((v, o, n) -> {
			if (n) {
				actionRecord.setGraphic(iconRecordStop);
				actionRecord.setText("Stop recording");
				actionPlayback.setDisabled(true);
			} else {
				actionRecord.setGraphic(iconRecord);
				actionRecord.setText("Start recording");				
				actionPlayback.setDisabled(tracker.isEmpty());
			}
		});
		
		BooleanProperty playing = playback.playingProperty();
		actionPlayback.graphicProperty().bind(Bindings.createObjectBinding(() -> {
					return playing.get() ? iconPlayStop : iconPlay;
				},
				playing
				));
		actionPlayback.textProperty().bind(Bindings.createStringBinding(() -> {
			return playing.get() ? "Stop" : "Play";
		},
		playing
		));
		
		// Add to pane
		toolbar.getItems().addAll(
				ActionUtils.createToggleButton(actionRecord, ActionTextBehavior.HIDE),
				ActionUtils.createToggleButton(actionPlayback, ActionTextBehavior.HIDE),
				new Separator(),
				ActionUtils.createButton(QuPathGUI.createCommandAction(new ViewTrackerExportCommand(viewer, tracker), "More..."))
				);
		
//		pane.getChildren().addAll(toolbar);
	}

	/**
	 * Get the Node containing the controls.
	 * @return
	 */
	public Node getNode() {
		return toolbar;
	}
	
	/**
	 * Get the current ViewTracker associated with these controls.
	 * @return
	 */
	public ViewTracker getViewTracker() {
		return tracker;
	}
	


}