/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer.recording;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.action.ActionUtils.ActionTextBehavior;

import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Panel for viewing ViewTracker controls.
 * <p>
 * This add buttons to start/stop recording and playback and view/export recorded data.
 * 
 * @author Pete Bankhead
 */
// TODO: Restrict the nu,ber of times we can call the controller to 1
public class ViewTrackerControlPane {
	
	private QuPathViewer viewer;
	
	private BooleanProperty recordingMode = new SimpleBooleanProperty(false);
	
	private final String[] columnNames = new String[] {"Name", "Duration"};
	
	private static final Node iconRecord = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.PLAYBACK_RECORD);
	private static final Node iconRecordStop = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.PLAYBACK_RECORD_STOP);
	private static final Node iconPlay = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.PLAYBACK_PLAY);
	private static final Node iconPlayStop = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.PLAYBACK_PLAY_STOP);
	private static final Node iconRecording = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.PLAYBACK_RECORD);
	
	private List<ViewTracker> trackersList;
	private ViewTracker tracker;
	
	private AnimationTimer recordingTime;
	
	private TableView<ViewTracker> table;
	
	private BorderPane mainPane;
	private TitledPane titledPane;
	private GridPane contentPane;
	private Label duration;
	
	private int nRecording = 0;
	
	ViewTrackerPlayback playback;

	/**
	 * Constructor.
	 * @param qupath instance of qupath
	 */
	public ViewTrackerControlPane(final QuPathGUI qupath) {
		this(qupath, ViewTrackers.createViewTracker(qupath));
	}

	/**
	 * Constructor.
	 * @param viewer the viewer to track
	 * @param viewTracker the tracker to use
	 */
	ViewTrackerControlPane(final QuPathGUI qupath, final ViewTracker viewTracker) {
		// TODO: add a listener to check if imageData (or something else that represents the image) has changed -> stop recording
		this.viewer = qupath.getViewer();
		
		trackersList = new ArrayList<>();
		prepareNewViewTracker(qupath);
		
		playback = new ViewTrackerPlayback(viewer);

		Action actionRecord = ActionTools.createSelectableAction(recordingMode, "Record", iconRecord, null);
		Action actionPlayback = ActionTools.createSelectableAction(playback.playingProperty(), "Play", iconPlay, null);
		
		// Can't select one while the other is selected
		actionRecord.disabledProperty().bind(actionPlayback.selectedProperty());
		
		
		// Ensure icons are correct
		recordingMode.addListener((v, o, n) -> {
			if (n) {
				actionRecord.setGraphic(iconRecordStop);
				actionRecord.setText("Stop recording");
				tracker.setRecording(true);
				startTrackingDuration();
			} else {
				actionRecord.setGraphic(iconRecord);
				actionRecord.setText("Start recording");
				tracker.setRecording(false);
				stopTrackingDuration();
				addViewTrackerEntry();
				prepareNewViewTracker(qupath);
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
		
		
		// Add GUI nodes
		mainPane = new BorderPane();
		mainPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE );

		titledPane = new TitledPane();
		titledPane.managedProperty().bind(titledPane.visibleProperty());
		titledPane.setAnimated(true);
		titledPane.expandedProperty().bind(recordingMode.not());
		// TODO: Fix bug when clicking on titledPane
		table = new TableView<ViewTracker>();
		table.setMaxHeight(200);
		contentPane = new GridPane();
		
		// Create buttons
		Button saveBtn = new Button("Save as..");
		saveBtn.setOnAction(e -> {
			ViewTrackers.handleExport(table.getSelectionModel().getSelectedItem());
			refreshListView();
		});
		Button deleteBtn = new Button("Delete");
		deleteBtn.setOnAction(e -> {
			trackersList.remove(table.getSelectionModel().getSelectedItem());
			refreshListView();
		});
		Button moreBtn = new Button("More...");
		moreBtn.setOnAction(e -> {
			new ViewTrackerAnalysisCommand(viewer, table.getSelectionModel().getSelectedItem()).run();
		});
		
		// Add all buttons to GridPane
		GridPane btnPane = new GridPane();
		btnPane.addRow(0, saveBtn, deleteBtn, moreBtn);
		
		// Disable all buttons if no recording is selected
		btnPane.getChildren().forEach(e -> e.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull()));
		
		// Disable playback button if no ViewTracker is selected
		actionPlayback.disabledProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
		
		
		int row = 0;
		PaneTools.addGridRow(contentPane,  row++,  0, null, table);
		PaneTools.addGridRow(contentPane,  row++,  0, null, btnPane);
		
		
		table.getSelectionModel().selectedItemProperty().addListener((v, o, tracker) -> {
			if (tracker != null)
				playback.setViewTracker(tracker);
		});
		
		
		// Remove arrow from TitledPane
		//Node arrow = mainPane.lookup(".arrow");
	    //arrow.setVisible(false);
	    

		
		for (int i = 0; i < columnNames.length; i++) {
			final int col = i;
			TableColumn<ViewTracker, Object> column = new TableColumn<>(columnNames[col]);
			column.setCellValueFactory(new Callback<CellDataFeatures<ViewTracker, Object>, ObservableValue<Object>>() {
			     @Override
				public ObservableValue<Object> call(CellDataFeatures<ViewTracker, Object> tracker) {
			         return new SimpleObjectProperty<>(getColumnValue(tracker.getValue(), col));
			     }
			  });
			table.getColumns().add(column);
		}
		
		refreshListView();
			
		
		// Separator and duration indicator on top of window
		duration = new Label("");
		duration.setVisible(false);
		Separator separator = new Separator();
		duration.visibleProperty().bind(recordingMode);
		separator.visibleProperty().bind(recordingMode);
		iconRecording.visibleProperty().bind(recordingMode);


		GridPane topButtonGrid = new GridPane();
		topButtonGrid.setHgap(10);
		topButtonGrid.addRow(0, ActionUtils.createToggleButton(actionRecord, ActionTextBehavior.HIDE),
				ActionUtils.createToggleButton(actionPlayback, ActionTextBehavior.HIDE),
				separator,
				iconRecording,
				duration
				);
		
		titledPane.setContent(contentPane);
		titledPane.setGraphic(topButtonGrid);
		titledPane.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		// TODO: Fix this line below
//		titledPane.setMaxHeight(Double.MAX_VALUE);
//		titledPane.expandedProperty().addListener((v, o, n) -> {
//			if (n)
//				mainPane.getScene().getWindow().setHeight(600);
//			else
//				mainPane.getScene().getWindow().setHeight(80);
////			logger.warn(mainPane.getHeight() + "");
//		});
		
		mainPane.setTop(titledPane);
		mainPane.setMaxSize(200, 300);
	}


	/**
	 * Add a ViewTracker entry to the TableView.
	 * This should be called every time a new recording has started.
	 */
	private void addViewTrackerEntry() {
		if (tracker.isEmpty())
			return;
		trackersList.add(tracker);
		refreshListView();
	}
	
	
	/**
	 * Prepare the new ViewTracker for the next recording.
	 * This should be called every time a (previous) recording has ended.
	 * 
	 * @param viewer
	 */
	private void prepareNewViewTracker(QuPathGUI qupath) {
		tracker = ViewTrackers.createViewTracker(qupath);
		recordingMode.unbind();
		tracker.recordingProperty().bind(recordingMode);
		tracker.setName("Recording " + nRecording++);
	}

	/**
	 * Get the Node containing the controls.
	 * @return
	 */
	public Node getNode() {
		return mainPane;
	}
	
	/**
	 * Get the value that should be displayed in the given column (e.g. recording name, timestamp)
	 * @param tracker
	 * @param col
	 * @return
	 */
	private static Object getColumnValue(final ViewTracker currentTracker, final int col) {
		switch (col) {
		case 0:
			File file = currentTracker.getFile();
			if (file != null)
				return file.getName();
			else
				return "*" + currentTracker.getName();
		case 1: 
			return ViewTrackers.getPrettyTimestamp(currentTracker.getStartTime(), currentTracker.getLastTime());
		}
		return null;
	}
	
	private void refreshListView() {
		if (trackersList != null)
			table.getItems().setAll(trackersList);
	}
	
	private void startTrackingDuration() {
		recordingTime = new AnimationTimer() {
            @Override
            public void handle(long now) {
                String timeElapsed = ViewTrackers.getPrettyTimestamp(tracker.getStartTime(), System.currentTimeMillis());
                duration.setText(timeElapsed);
            }
        };
		recordingTime.start();
	}
	

	private void stopTrackingDuration() {
		recordingTime.stop();
	}
	
	/**
	 * Property representing the recording mode of the controller.
	 * @return
	 */
	public BooleanProperty recordingMode() {
		return recordingMode;
	}
	
	public void forceStopRecording() {
		recordingMode.set(false);
	}
	
	
	public TitledPane getTitledPane() {
		return titledPane;
	}
	
	public List<ViewTracker> getViewTrackerList() {
		return trackersList;
	}

	
//	/**
//	 * Get the current ViewTracker associated with these controls.
//	 * @return
//	 */
//	public ViewTracker getViewTracker() {
//		return tracker;
//	}
	


}