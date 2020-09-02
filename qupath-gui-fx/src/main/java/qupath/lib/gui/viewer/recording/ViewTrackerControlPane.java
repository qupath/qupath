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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.action.ActionUtils.ActionTextBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableRow;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;

/**
 * Panel for viewing ViewTracker controls.
 * <p>
 * This add buttons to start/stop recording and playback and view/export recorded data.
 * 
 * @author Pete Bankhead
 */
// TODO: Restrict the number of times we can call the controller to 1
// TODO: Prevent recordings with 0 frame from being saved
public class ViewTrackerControlPane {
	
	private final static Logger logger = (Logger) LoggerFactory.getLogger(ViewTrackerControlPane.class);
	
	private QuPathGUI qupath;
	private QuPathViewer viewer;
	
	private BooleanProperty recordingMode = new SimpleBooleanProperty(false);
	private BooleanProperty supportsEyeTracking = new SimpleBooleanProperty(false);
	
	private static BooleanProperty cursorTrackingProperty = PathPrefs.createPersistentPreference("trackCursorPosition", true);
	private static BooleanProperty activeToolTrackingProperty = PathPrefs.createPersistentPreference("trackActiveTool", true);
	private static BooleanProperty eyeTrackingProperty = PathPrefs.createPersistentPreference("trackEyePosition", false);
	
	private final String[] columnNames = new String[] {"Name", "Duration"};
	
	private static final Node iconRecord = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_RECORD);
	private static final Node iconRecordStop = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_STOP);
	private static final Node iconPlay = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.PLAYBACK_PLAY);
	private static final Node iconPlayStop = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_STOP);
	private static final Node iconRecording = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_RECORD);
	
	private List<ViewTracker> trackersList;
	private ViewTracker tracker;
	
	private BooleanProperty isAnalysisActive = new SimpleBooleanProperty(false);
	
	private CheckMenuItem miTrackCursor = new CheckMenuItem("Track cursor");
	private CheckMenuItem miTrackActiveTool = new CheckMenuItem("Track active tool");
	private CheckMenuItem miTrackEye = new CheckMenuItem("Track eye position");
	
	private ChangeListener<ImageData<?>> imageDataListener;
	private ChangeListener<QuPathViewer> viewerListener;
	
	private AnimationTimer recordingTime;
	
	private TableView<ViewTracker> table;
	
	private BorderPane mainPane;
	private TitledPane titledPane;
	private GridPane contentPane;
	private Label duration;
	
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
	 * @throws IOException 
	 */
	ViewTrackerControlPane(final QuPathGUI qupath, final ViewTracker viewTracker) {
		this.qupath = qupath;
		this.viewer = qupath.getViewer();
		
		trackersList = new ArrayList<>();
		populateTrackersList(qupath);
		
		// Create listener that will be triggered for every imageData change
		imageDataListener = new ChangeListener<ImageData<?>>() {
			@Override
			public void changed(ObservableValue<? extends ImageData<?>> v, ImageData<?> o, ImageData<?> n) {
				// Stop recording (if recording)
				if (recordingMode.get())
					recordingMode.set(false);
				
				// Empty TableView
				trackersList.clear();
				
				// If the new ImageData is not null (e.g. 'close viewer'), get existing recordings
				if (n != null)
					populateTrackersList(qupath);
				
				// Make sure titledPane is not clickable if no ImageData
				titledPane.disableProperty().bind(Bindings.or(viewer.imageDataProperty().isNull(), isAnalysisActive));
				refreshListView();
			}
		};
		viewer.imageDataProperty().addListener(imageDataListener);
		
		// Create listener to stop recording if user changes the active viewer (e.g. multi-viewer)
		viewerListener = new ChangeListener<QuPathViewer>() {
			@Override
			public void changed(ObservableValue<? extends QuPathViewer> v, QuPathViewer o, QuPathViewer n) {
				// Stop recording (if recording)
				if (recordingMode.get())
					recordingMode.set(false);
					
				trackersList.clear();
				
				// Add listener to the new viewer's imageData property (and remove previous one)
				viewer.imageDataProperty().removeListener(imageDataListener);
				viewer = n;
				viewer.imageDataProperty().addListener(imageDataListener);
				
				// If the new ImageData is not null (e.g. 'close viewer'), get existing recordings
				if (n.getImageData() != null)
					populateTrackersList(qupath);
				
				// Make sure titledPane is not clickable if no ImageData
				titledPane.disableProperty().bind(Bindings.or(viewer.imageDataProperty().isNull(), isAnalysisActive));
				refreshListView();
			}
		};
		qupath.viewerProperty().addListener(viewerListener);
		
		prepareNewViewTracker(qupath);
		playback = new ViewTrackerPlayback(viewer);

		Action actionRecord = ActionTools.createSelectableAction(recordingMode, "Record", iconRecord, null);
		Action actionPlayback = ActionTools.createSelectableAction(playback.playingProperty(), "Play", iconPlay, null);
		
		// Can't select one while the other is selected
		actionRecord.disabledProperty().bind(actionPlayback.selectedProperty());
		
		
		// Ensure icons are correct
		recordingMode.addListener((v, o, n) -> {
			// Cannot bind, because user could click titledPane and make it crash
			titledPane.setExpanded(!n);
			
			if (n) {
				actionRecord.setGraphic(iconRecordStop);
				actionRecord.setText("Stop recording");
				setTrackerName(tracker);
				tracker.setRecording(true);
				startTrackingDuration();
			} else {
				actionRecord.setGraphic(iconRecord);
				actionRecord.setText("Start recording");
				tracker.setRecording(false);
				stopTrackingDuration();
				addViewTrackerEntry();
				table.getSelectionModel().clearSelection();
				table.scrollTo(table.getItems().size()-1);
				table.getSelectionModel().selectLast();
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
		table = new TableView<ViewTracker>();
		table.setMaxHeight(200);
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		contentPane = new GridPane();
		
		// Create buttons
		Button exportBtn = new Button("Export");
		exportBtn.setOnAction(e -> {
			ViewTrackers.handleExport(table.getSelectionModel().getSelectedItem());
			refreshListView();
		});
		Button deleteBtn = new Button("Delete");
		deleteBtn.setOnAction(e -> {
			var trackersToDelete = table.getSelectionModel().getSelectedItems();
			var response = Dialogs.showConfirmDialog("Delete recording(s)", "Delete recording(s)? The data will be lost.");
			if (!response)
				return;
			
			for (ViewTracker tracker: trackersToDelete) {
				try {
					Files.delete(tracker.getFile().toPath());
				} catch (IOException ex) {
					Dialogs.showErrorNotification("Could not delete recording", ex);
				}
			}
			// Remove all trackers to delete from ListView (even if exception when deleting file)
			trackersList.removeAll(trackersToDelete);
			refreshListView();
		});
		Button moreBtn = new Button("More...");
		moreBtn.setOnAction(e -> openViewTrackingAnalysisCommand());
		
		// Add all buttons to GridPane
		GridPane btnPane = PaneTools.createColumnGrid(3);
		exportBtn.setMaxWidth(Double.MAX_VALUE);
		deleteBtn.setMaxWidth(Double.MAX_VALUE);
		moreBtn.setMaxWidth(Double.MAX_VALUE);
		btnPane.addRow(0, exportBtn, deleteBtn, moreBtn);
		
		
		// Disable all buttons if no recording is selected, disable 'Export' and 'More' if multiple selection
		exportBtn.disableProperty().bind(Bindings.or(Bindings.equal(Bindings.size(table.getSelectionModel().getSelectedItems()), 1).not(), isAnalysisActive));
		deleteBtn.disableProperty().bind(Bindings.or(table.getSelectionModel().selectedItemProperty().isNull(), isAnalysisActive));
		moreBtn.disableProperty().bind(Bindings.or(Bindings.equal(Bindings.size(table.getSelectionModel().getSelectedItems()), 1).not(), isAnalysisActive));
//		btnPane.getChildren().forEach(e -> e.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull()));
		
		// Disable playback button if no ViewTracker is selected
		actionPlayback.disabledProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
		
		
		int row = 0;
		PaneTools.addGridRow(contentPane,  row++,  0, null, table);
		PaneTools.addGridRow(contentPane,  row++,  0, null, btnPane);
		
		
		table.getSelectionModel().selectedItemProperty().addListener((v, o, tracker) -> {
			if (tracker != null)
				playback.setViewTracker(tracker);
		});
	    
		// Support drag & drop for files
		table.setOnDragOver(e -> {
			e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
		table.setOnDragDropped(e -> {
			Dragboard dragboard = e.getDragboard();
			if (dragboard.hasFiles()) {
		        logger.trace("Files dragged onto view tracking control dialog");
				try {
					List<File> files = dragboard.getFiles()
							.stream()
							.filter(f -> f.isFile() && !f.isHidden())
							.collect(Collectors.toList());
					files.removeIf(t -> {
						for (var element: table.getItems()) {
							if (element.getFile().getAbsolutePath().equals(t.getAbsolutePath()))
								return true;
						}
						return false;
					});
					for (File file: files) {
						if (!GeneralTools.getExtension(file).get().equals(".tsv"))
							continue;
						var tracker = ViewTrackers.handleImport(file.toPath());
						trackersList.add(tracker);
					}
				} catch (Exception ex) {
					Dialogs.showErrorMessage("Drag & Drop", ex);
				}
			}
			refreshListView();
			e.setDropCompleted(true);
			e.consume();
        });
		
		// Handle the double-click
		table.setRowFactory(e -> {
		    TableRow<ViewTracker> recordingRow = new TableRow<>();
		    recordingRow.setOnMouseClicked(event -> {
		        if (event.getClickCount() == 2 && (!recordingRow.isEmpty()))
		        	openViewTrackingAnalysisCommand();
		    });
		    return recordingRow;
		});
		
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
			
		
		// Option Context Menu
		miTrackCursor = new CheckMenuItem("Track cursor");
		miTrackActiveTool = new CheckMenuItem("Track active tool");
		miTrackEye = new CheckMenuItem("Track eye position");
		
		miTrackCursor.selectedProperty().addListener((v, o, n) -> cursorTrackingProperty.set(n));
		miTrackActiveTool.selectedProperty().addListener((v, o, n) -> activeToolTrackingProperty.set(n));
		miTrackEye.selectedProperty().addListener((v, o, n) -> eyeTrackingProperty.set(n));
		
		miTrackCursor.setSelected(cursorTrackingProperty.get());
		miTrackActiveTool.setSelected(activeToolTrackingProperty.get());
		miTrackEye.setSelected(eyeTrackingProperty.get());
		
		miTrackEye.disableProperty().bind(supportsEyeTracking.not());
		
		ContextMenu menu = new ContextMenu(miTrackCursor, miTrackActiveTool, miTrackEye);
		Button optionBtn = GuiTools.createMoreButton(menu, Side.RIGHT);
		
		// Toggle buttons to play/record and stop
		ToggleButton toggleRecord = ActionUtils.createToggleButton(actionRecord, ActionTextBehavior.HIDE);
		ToggleButton togglePlayback = ActionUtils.createToggleButton(actionPlayback, ActionTextBehavior.HIDE);
		
		// Separator and duration indicator on top of window
		duration = new Label("");
		duration.setVisible(false);
		
		Separator separator = new Separator();
		duration.visibleProperty().bind(recordingMode);
		separator.visibleProperty().bind(recordingMode);
		iconRecording.visibleProperty().bind(recordingMode);
		
		optionBtn.disableProperty().bind(Bindings.or(recordingMode, isAnalysisActive));
		toggleRecord.disableProperty().bind(isAnalysisActive);
		togglePlayback.disableProperty().bind(isAnalysisActive);
		table.disableProperty().bind(isAnalysisActive);

		GridPane topButtonGrid = new GridPane();
		topButtonGrid.setHgap(10);
		topButtonGrid.addRow(0,
				toggleRecord,
				togglePlayback,
				separator,
				iconRecording,
				duration,
				optionBtn
				);
		
		titledPane.setContent(contentPane);
		titledPane.setGraphic(topButtonGrid);
		titledPane.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		titledPane.setOnMouseClicked(mouseEvent -> titledPane.setExpanded(!recordingMode.getValue()));
		
		mainPane.setTop(titledPane);
		mainPane.setMaxSize(200, 300);
	}


	private void openViewTrackingAnalysisCommand() {
		ViewTrackerAnalysisCommand activeTracker = new ViewTrackerAnalysisCommand(this, viewer, table.getSelectionModel().getSelectedItem());
		isAnalysisActive.bind(activeTracker.getActiveProperty());
		activeTracker.run();
	}

	private void populateTrackersList(QuPathGUI qupath) {
		var entry = qupath.getProject().getEntry(viewer.getImageData());
		if (entry == null) 
			return;
		File entryDirectory = entry.getEntryPath().toFile();
		if (entryDirectory != null && entryDirectory.exists())
			trackersList.addAll(getExistingRecordings(entryDirectory));
	}

	private void setTrackerName(ViewTracker tracker) {
		// Check if there were imported ViewTrackers with clashing name
		List<Integer> temp = new ArrayList<>();
		try {
			temp = trackersList.stream()
					.filter(e -> e.getFile().getName().matches("Recording (\\d*).tsv"))
					.map(e -> {
						String name = e.getFile().getName();
						return GeneralTools.getNameWithoutExtension(name.substring(name.lastIndexOf(" ")+1));
					})
					.mapToInt(e -> Integer.parseInt(e))
					.boxed().collect(Collectors.toList());
		} catch (Exception e) {
			logger.warn("Could not fetch previous recordings", e.getLocalizedMessage());
		}
		
		// Get ID that doesn't clash with existing recordings
		int nRecording = 0;
		while (temp.contains(nRecording))
			nRecording++;
		tracker.setName("Recording " + nRecording++);
		
	}

	private Collection<ViewTracker> getExistingRecordings(File entryDirectory) {
		File recordingDirectory = new File(entryDirectory, "recordings");
		List<ViewTracker> trackers = new ArrayList<>();
		if (recordingDirectory.exists()) {
			try (Stream<Path> walk = Files.walk(recordingDirectory.toPath())) {
				trackers.addAll(walk.filter(Files::isRegularFile)
												.filter(path -> GeneralTools.getExtension(path.toFile()).get().equals(".tsv"))
												.map(path -> ViewTrackers.handleImport(path))
												.collect(Collectors.toList()));
			} catch (IOException e) {
				logger.error("Could not fetch existing recordings: ", e.getLocalizedMessage());
			}
		}
		return trackers;
	}

	/**
	 * Add a ViewTracker entry to the TableView.
	 * This should be called every time a new recording is finished.
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
		// Good practice to unbind property of previous trackers
		if (tracker != null) {
			tracker.cursorTrackingProperty().unbind();
			tracker.activeToolProperty().unbind();
			tracker.eyeTrackingProperty().unbind();			
		}
		
		// Create new tracker
		tracker = ViewTrackers.createViewTracker(qupath);
		
		// Bind properties
		tracker.cursorTrackingProperty().bind(cursorTrackingProperty);
		tracker.activeToolProperty().bind(activeToolTrackingProperty);
		tracker.eyeTrackingProperty().bind(eyeTrackingProperty);
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
		if (currentTracker == null)
			return null;
		
		switch (col) {
		case 0:
			File file = currentTracker.getFile();
			if (file != null)
				return GeneralTools.getNameWithoutExtension(file);
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
	
	public void removeListeners() {
		viewer.imageDataProperty().removeListener(imageDataListener);
		qupath.viewerProperty().removeListener(viewerListener);
	}
}