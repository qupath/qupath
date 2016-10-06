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
 * Panel for viewing ViewTracker controls, i.e. to start/stop recording and playback,
 * or view/export recorded data.
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

	public ViewTrackerControlPanel(final QuPathViewer viewer) {
		tracker = null;

		// TODO: Consider reinstating eye tracker support
//		Stage dialog = null;
//		try {
//			// The following is surely not a great way to do it... but attempting to show user feedback while trying the (potentially lengthy) eye tracker search
//			Class<?> cEyeTracker = getClass().getClassLoader().loadClass("qupath.lib.gui.viewer.recording.ViewEyeTracker");
//			final Constructor<?> cons = cEyeTracker.getConstructor(QuPathViewer.class);
//
//			// Show a message that we're trying to start the eye tracker
//			dialog = new Stage(StageStyle.UNDECORATED);
//			dialog.initOwner(viewer.getView().getScene().getWindow());
//			dialog.setTitle("Eye tracker initialization");
//			Label label = new Label("Searching for eye tracker...");
//			label.setPadding(new Insets(10, 10, 10, 10));
//			dialog.setScene(new Scene(label));
//			dialog.setAlwaysOnTop(true);
//			dialog.show();
//			final JDialog dialog2 = dialog;
//			SwingWorker<ViewTracker, Object> worker = new SwingWorker<ViewTracker, Object>() {
//
//				@Override
//				public ViewTracker doInBackground() {
//					try {
//						return (ViewTracker)cons.newInstance(viewer);
//					} catch (Exception e) {
//						return null;
//					}
//				}
//
//				@Override
//				protected void done() {
//					if (dialog2 != null && dialog2.isDisplayable()) {
//						dialog2.setVisible(false);
//						dialog2.dispose();
//					}
//				}
//			};
//			dialog.setModal(true);
//			worker.execute();
//			dialog.setVisible(true);
//			tracker = worker.get(1000, TimeUnit.MILLISECONDS);
//
//			//			tracker = (ViewTracker)cons.newInstance(viewer);
//		} catch (Throwable e) { // This may not be a very good way to do it...
//			logger.error("Unable to load eye tracker - will default to normal tracker: {}", e);
//		} finally {
//			if (dialog != null) {
//				dialog.setVisible(false);
//				dialog.dispose();
//			}
//		}
//
//		logger.debug("TRACKER: " + tracker);

		if (tracker == null)
			tracker = new DefaultViewTracker(viewer);

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

	
	public Node getNode() {
		return toolbar;
	}
	
	
	public ViewTracker getViewTracker() {
		return tracker;
	}
	


}