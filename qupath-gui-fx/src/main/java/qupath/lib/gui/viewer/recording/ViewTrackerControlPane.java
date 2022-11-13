/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.action.ActionUtils.ActionTextBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.control.TableRow;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
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
 * @author Melvin Gelbard
 */
public class ViewTrackerControlPane implements Runnable {
	
	private static final BooleanProperty cursorTrackingProperty = PathPrefs.createPersistentPreference("trackCursorPosition", true);
	private static final BooleanProperty activeToolTrackingProperty = PathPrefs.createPersistentPreference("trackActiveTool", true);
//	private static final BooleanProperty eyeTrackingProperty = PathPrefs.createPersistentPreference("trackEyePosition", false);
	private static final String[] columnNames = new String[] {"Name", "Duration"};	
	
	private static final Node iconRecord = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_RECORD);
	private static final Node iconRecordStop = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_STOP);
	private static final Node iconPlay = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.PLAYBACK_PLAY);
	private static final Node iconPlayStop = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_STOP);
	private static final Node iconRecording = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_RECORD);
	private static final Logger logger = LoggerFactory.getLogger(ViewTrackerControlPane.class);
	
	private static Stage dialog = null;
	
	private QuPathGUI qupath;
	private QuPathViewer viewer;	
	
	private final BooleanProperty recordingMode = new SimpleBooleanProperty(false);
//	private final BooleanProperty supportsEyeTracking = new SimpleBooleanProperty(false);
	
	
	private ObservableList<ViewTracker> trackersList;
	
	/**
	 * Either the currently recording tracker or the next one to start recording
	 */
	private ViewTracker tracker;
	
	/**
	 * Property that communicates whether the analysis pane is opened (for any tracker)
	 */
	private final BooleanProperty isAnalysisOpened = new SimpleBooleanProperty(false);
	
	private final ChangeListener<ImageData<?>> imageDataListener;
	private final ChangeListener<QuPathViewer> viewerListener;
	
	/**
	 * Timer that tracks the current recording's duration (and update the corresponding Label)
	 */
	private AnimationTimer recordingTime;
	
	private TableView<ViewTracker> table;
	
	private BorderPane mainPane;
	private TitledPane titledPane;
	private GridPane contentPane;
	private Label duration;
	
	private ViewTrackerPlayback playback;

	/**
	 * Create a ViewTrackerControlPane.
	 * @param qupath 
	 */
	public ViewTrackerControlPane(final QuPathGUI qupath) {
		this.qupath = qupath;
		viewer = qupath.getViewer();
		trackersList = FXCollections.observableArrayList(getExistingRecordings(qupath, viewer.getImageData()));
		
		// Create listener that will be triggered for every imageData change
		imageDataListener = (v, o, n) -> {
			// Stop recording (if recording)
			if (recordingMode.get())
				recordingMode.set(false);
			
			// Empty TableView
			trackersList.clear();
			
			// If the new ImageData is not null (e.g. 'close viewer'), get existing recordings
			if (n != null)
				trackersList.setAll(getExistingRecordings(qupath, viewer.getImageData()));
			
			table.setItems(trackersList);
			
			// Make sure titledPane is not clickable if no ImageData
			titledPane.disableProperty().bind(viewer.imageDataProperty().isNull().or(isAnalysisOpened));
		};

		// Create listener to stop recording if user changes the active viewer (e.g. multi-viewer)
		viewerListener = (v, o, n) -> {
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
				trackersList.setAll(getExistingRecordings(qupath, viewer.getImageData()));
			
			table.setItems(trackersList);
			
			// Make sure titledPane is not clickable if no ImageData
			titledPane.disableProperty().bind(Bindings.or(viewer.imageDataProperty().isNull(), isAnalysisOpened));
		};
		
		// Add listeners
		viewer.imageDataProperty().addListener(imageDataListener);
		qupath.viewerProperty().addListener(viewerListener);
		
		prepareNewViewTracker(qupath);
		playback = new ViewTrackerPlayback(viewer);

		Action actionRecord = ActionTools.createSelectableAction(recordingMode, "Start a new recording of the viewer", iconRecord, null);
		Action actionPlayback = ActionTools.createSelectableAction(playback.playingProperty(), "Play the selected recording", iconPlay, null);
		
		// Ensure icons are correct
		recordingMode.addListener((v, o, n) -> {
			// Cannot bind, because user could click titledPane and make it crash
			titledPane.setExpanded(!n);
			
			if (n) {
				// Set text and icon to recording
				actionRecord.setGraphic(iconRecordStop);
				actionRecord.setText("Stop recording");
				
				// Set non-clashing name to recording
				var newName = GeneralTools.generateDistinctName("Recording", trackersList.stream()
						.map(t -> t.getName())
						.collect(Collectors.toList()));
				tracker.setName(newName);
				
				// Start recording and timer
				tracker.setRecording(true);
				startRecordingTimer();
			} else {
				// Set text and icon to not recording
				actionRecord.setGraphic(iconRecord);
				actionRecord.setText("Start recording the viewer");
				
				// Stop recording and timer
				tracker.setRecording(false);
				stopRecordingTimer();
				
				// Add this new tracker to the list of existing trackers
				if (!tracker.isEmpty())
					trackersList.add(tracker);
				table.getSelectionModel().clearSelection();
				table.scrollTo(table.getItems().size()-1);
				table.getSelectionModel().selectLast();
				
				// Prepare the next tracker
				prepareNewViewTracker(qupath);
			}
		});
		
		BooleanProperty playing = playback.playingProperty();
		iconPlay.setStyle("-fx-text-fill: -fx-text-background-color;");
		actionPlayback.graphicProperty().bind(Bindings.createObjectBinding(() -> {
					return playing.get() ? iconPlayStop : iconPlay;
				},
				playing
				));
		actionPlayback.textProperty().bind(Bindings.createStringBinding(() -> {
			return playing.get() ? "Stop the playback" : "Play the selected recording";
		},
		playing
		));
		
		
		// Add GUI nodes
		mainPane = new BorderPane();
		mainPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE );

		titledPane = new TitledPane();
		titledPane.managedProperty().bind(titledPane.visibleProperty());
		titledPane.setAnimated(true);
		table = new TableView<>();
		table.setMaxHeight(200);
		table.setItems(trackersList);
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		contentPane = new GridPane();
		contentPane.setVgap(5.0);
		
		// Create buttons
		Button exportBtn = new Button("Export");
		exportBtn.setOnAction(ev -> ViewTrackerTools.handleExport(table.getSelectionModel().getSelectedItem()));
		exportBtn.setTooltip(new Tooltip("Export the selected recording as a tab-separated file (TSV)"));
		Button deleteBtn = new Button("Delete");
		deleteBtn.setTooltip(new Tooltip("Delete the selected recording from the project directory"));
		deleteBtn.setOnAction(e -> {
			var trackersToDelete = table.getSelectionModel().getSelectedItems();
			String deleteRecording = "Delete recording" + (trackersToDelete.size() > 1 ? "s" : "");
			var response = Dialogs.showConfirmDialog(deleteRecording, deleteRecording + "? The data will be lost.");
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
		});
		Button btnMore = new Button("More");
		btnMore.setOnAction(e -> openViewTrackingAnalysisCommand());
		btnMore.setTooltip(new Tooltip("Open the recording analysis window for the selected recording"));
		
		
		// Add all buttons to GridPane
		GridPane btnPane = PaneTools.createColumnGrid(3);
		exportBtn.setMaxWidth(Double.MAX_VALUE);
		deleteBtn.setMaxWidth(Double.MAX_VALUE);
		btnMore.setMaxWidth(Double.MAX_VALUE);
		btnPane.addRow(0, exportBtn, deleteBtn, btnMore);
		
		// Disable all buttons if no recording is selected, disable 'Export' and 'More' if multiple selection
		exportBtn.disableProperty().bind(Bindings.or(Bindings.equal(Bindings.size(table.getSelectionModel().getSelectedItems()), 1).not(), isAnalysisOpened));
		deleteBtn.disableProperty().bind(Bindings.or(table.getSelectionModel().selectedItemProperty().isNull(), isAnalysisOpened));
		btnMore.disableProperty().bind(Bindings.or(Bindings.equal(Bindings.size(table.getSelectionModel().getSelectedItems()), 1).not(), isAnalysisOpened));
		
		// Disable playback button if no ViewTracker or multiple ViewTrackers are selected
		actionPlayback.disabledProperty().bind(Bindings.size(table.getSelectionModel().getSelectedItems()).isNotEqualTo(1).or(recordingMode).or(isAnalysisOpened));
		
		// Disable recording button if playback is playing
		actionRecord.disabledProperty().bind(playing.or(isAnalysisOpened));
		
		
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
						var tracker = ViewTrackerTools.handleImport(file.toPath());
						if (tracker == null) {
							logger.warn("Unable to read view tracker: {}", file);
						} else
							trackersList.add(tracker);
					}
				} catch (Exception ex) {
					Dialogs.showErrorMessage("Drag & Drop", ex);
				}
			}
			e.setDropCompleted(true);
			e.consume();
        });
		
		table.setRowFactory(e -> {
		    final TableRow<ViewTracker> recordingRow = new TableRow<>();
		    final ContextMenu menu = new ContextMenu();
		    final MenuItem renameItem = new MenuItem("Rename");
		    final MenuItem openDirectoryItem = new MenuItem("Open directory");
		    menu.getItems().addAll(renameItem, openDirectoryItem);
		    renameItem.setOnAction(ev -> {
		    	var newName = Dialogs.showInputDialog("Rename", "New name", recordingRow.getItem().getFile() == null ? "" : recordingRow.getItem().getFile().getName());
		    	newName = GeneralTools.generateDistinctName(newName, trackersList.stream().map(tracker -> tracker.getName()).collect(Collectors.toList()));
		    	if (newName == null || newName.isEmpty() || newName.equals(recordingRow.getItem().getFile().getName()))
		    		return;
		    	recordingRow.getItem().setName(newName);
		    });
		    
		    // Open directory where the recording file is saved
		    openDirectoryItem.setOnAction(ev ->	{
		    	final File file = recordingRow.getItem().getFile();
		    	if (file != null)
		    		GuiTools.browseDirectory(file);
		    	else
		    		Dialogs.showErrorMessage("Cannot open directory", "Recording was not locally saved!");
		    });
            
            // Only display context menu for non-empty rows
		    recordingRow.contextMenuProperty().bind(
		    		Bindings.when(recordingRow.emptyProperty())
		    		.then((ContextMenu) null)
		    		.otherwise(menu)
		    		);
              
		    // Handle the double-click
		    recordingRow.setOnMouseClicked(event -> {
		        if (event.getClickCount() == 2 && (!recordingRow.isEmpty()))
		        	openViewTrackingAnalysisCommand();
		    });

		    return recordingRow;
		});
		
		// Setting columns and their values
		for (String columnName: columnNames) {
			// Create new column
			TableColumn<ViewTracker, String> column = new TableColumn<>(columnName);
			
			// Set the width of the columns so they take all available space
			column.prefWidthProperty().bind(table.widthProperty().subtract(17).divide(columnNames.length));
			
			// Set whatever will be written in each column
			column.setCellValueFactory(item -> {
				final ViewTracker currentTracker = item.getValue();
				if (currentTracker == null)
					return null;
				if (columnName.equals("Name")) {
					File file = currentTracker.getFile();
					if (file != null)
						return currentTracker.nameProperty();
					return new SimpleObjectProperty<>("*" + currentTracker.nameProperty());
				} else if (columnName.equals("Duration"))
					return new SimpleObjectProperty<>(ViewTrackerTools.getPrettyTimestamp(currentTracker.getStartTime(), currentTracker.getLastTime()));
				return null;
			});
			table.getColumns().add(column);
		}
			
		// Option Context Menu
		CheckMenuItem miTrackCursor = new CheckMenuItem("Track cursor");
		CheckMenuItem miTrackActiveTool = new CheckMenuItem("Track active tool");
//		CheckMenuItem miTrackEye = new CheckMenuItem("Track eye position");
		
		miTrackCursor.selectedProperty().addListener((v, o, n) -> cursorTrackingProperty.set(n));
		miTrackActiveTool.selectedProperty().addListener((v, o, n) -> activeToolTrackingProperty.set(n));
//		miTrackEye.selectedProperty().addListener((v, o, n) -> eyeTrackingProperty.set(n));
		
		miTrackCursor.setSelected(cursorTrackingProperty.get());
		miTrackActiveTool.setSelected(activeToolTrackingProperty.get());
//		miTrackEye.setSelected(eyeTrackingProperty.get());
		
//		miTrackEye.disableProperty().bind(supportsEyeTracking.not());
		
//		ContextMenu menu = new ContextMenu(miTrackCursor, miTrackActiveTool, miTrackEye);
		ContextMenu menu = new ContextMenu(miTrackCursor, miTrackActiveTool);
		Button optionBtn = GuiTools.createMoreButton(menu, Side.RIGHT);
		
		// Toggle buttons to play/record and stop
		ToggleButton toggleRecord = ActionUtils.createToggleButton(actionRecord, ActionTextBehavior.HIDE);
		ToggleButton togglePlayback = ActionUtils.createToggleButton(actionPlayback, ActionTextBehavior.HIDE);
		
		// Separator and duration indicator on top of window
		duration = new Label("");
		duration.setVisible(false);
		duration.setMinWidth(50.0);
		
		Separator separator = new Separator(Orientation.VERTICAL);
		duration.visibleProperty().bind(recordingMode);
		separator.visibleProperty().bind(recordingMode);
		iconRecording.visibleProperty().bind(recordingMode);
		Tooltip.install(iconRecording, new Tooltip("Recording"));
		
		// Make the recording icon pulse when active
		var fadeOut = new FadeTransition(Duration.seconds(1.0), iconRecording);
		fadeOut.setFromValue(1.0);
		fadeOut.setToValue(0.5);
		var fadeIn = new FadeTransition(Duration.seconds(1.0), iconRecording);
		fadeIn.setFromValue(0.5);
		fadeIn.setToValue(1.0);
		
		var pulse = new SequentialTransition(iconRecording, fadeOut, fadeIn);
		pulse.setCycleCount(FadeTransition.INDEFINITE);
		recordingMode.addListener((v, o, n) -> {
			if (n)
				pulse.playFromStart();
			else
				pulse.stop();
		});
		
		optionBtn.disableProperty().bind(Bindings.or(recordingMode, isAnalysisOpened));
		table.disableProperty().bind(isAnalysisOpened);

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
		titledPane.setPrefWidth(250);
		
		mainPane.setTop(titledPane);
		mainPane.setMaxSize(250, 300);
	}
	
	@Override
	public void run() {
		if (dialog != null)
			dialog.requestFocus();
		else {
			dialog = new Stage();
			dialog.sizeToScene();
			dialog.initOwner(qupath.getStage());
			dialog.setTitle("Tracking");
			
			StackPane pane = new StackPane(mainPane);
			dialog.setScene(new Scene(pane));

			// Necessary for window resizing when expanding the TitledPane
			titledPane.heightProperty().addListener((v, o, n) -> dialog.sizeToScene());
			
			dialog.setResizable(false);
//			dialog.setAlwaysOnTop(true);
			
			// When user requests closing, make sure no data is lost
			dialog.setOnCloseRequest(e -> {
				// If currently recording
				if (recordingMode.get()) {
					var response = Dialogs.showYesNoDialog("Shut down recording", "The current recording will be stopped." + System.lineSeparator() + "Continue?");
					if (!response) {
						e.consume();
						return;
					}
				}
				
				// If some recordings are not saved
				List<ViewTracker> unsaved = trackersList.stream().filter(tracker -> tracker.getFile() == null).collect(Collectors.toList());
				if (!unsaved.isEmpty()) {
					var response = Dialogs.showYesNoDialog("Save recordings", "You will lose your unsaved recordings." + System.lineSeparator() + "Continue?");
					if (!response) {
						e.consume();
						return;
					}
				}
				
				// Reset dialog variable so another call to run() will create a new pane
				dialog = null;
			});
			
			// Stop recording and remove listeners
			dialog.setOnHidden(e -> {
				forceStopRecording();
				removeListeners();
			});
			
			dialog.show();
		}
		
		// Remove the arrow in the TitledPane
		titledPane.lookup(".arrow").setVisible(false);
	}
	
	/**
	 * Prepare the new {@link ViewTracker} for the next recording.
	 * This should be called every time a (previous) recording has ended.
	 * @param qupath 
	 */
	private void prepareNewViewTracker(QuPathGUI qupath) {
		// Good practice to unbind property of previous trackers
		if (tracker != null) {
			tracker.cursorTrackingProperty().unbind();
			tracker.activeToolProperty().unbind();
//			tracker.eyeTrackingProperty().unbind();			
		}
		
		// Create new tracker
		tracker = new ViewTracker(qupath);
		
		// Bind properties
		tracker.cursorTrackingProperty().bind(cursorTrackingProperty);
		tracker.activeToolProperty().bind(activeToolTrackingProperty);
//		tracker.eyeTrackingProperty().bind(eyeTrackingProperty);
	}
	
	private void startRecordingTimer() {
		recordingTime = new AnimationTimer() {
			@Override
			public void handle(long now) {
				String timeElapsed = ViewTrackerTools.getPrettyTimestamp(tracker.getStartTime(), System.currentTimeMillis());
				duration.setText(timeElapsed);
			}
		};
		recordingTime.start();
	}
	
	private void stopRecordingTimer() {
		recordingTime.stop();
	}

	private void openViewTrackingAnalysisCommand() {
		ViewTrackerAnalysisCommand activeTracker = new ViewTrackerAnalysisCommand(qupath, table.getSelectionModel().getSelectedItem());
		isAnalysisOpened.bind(activeTracker.isOpenedProperty());
		activeTracker.run();
	}

	private static List<ViewTracker> getExistingRecordings(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		List<ViewTracker> out = new ArrayList<>();
		var entry = qupath.getProject().getEntry(imageData);
		if (entry == null)
			return out;
		File entryDirectory = entry.getEntryPath().toFile();
		if (entryDirectory != null && entryDirectory.exists())
			return getRecordingsFromDirectory(entryDirectory);
		return out;
	}

	private static List<ViewTracker> getRecordingsFromDirectory(File entryDirectory) {
		File recordingDirectory = new File(entryDirectory, "recordings");
		List<ViewTracker> trackers = new ArrayList<>();
		if (recordingDirectory.exists()) {
			try (Stream<Path> walk = Files.walk(recordingDirectory.toPath())) {
				trackers.addAll(walk.filter(Files::isRegularFile)
												.filter(path -> GeneralTools.getExtension(path.toFile()).orElse("").equals(".tsv"))
												.map(path -> ViewTrackerTools.handleImport(path))
												.filter(t -> t != null)
												.collect(Collectors.toList()));
			} catch (IOException ex) {
				logger.error("Could not fetch existing recordings: " + ex.getLocalizedMessage(), ex);
			}
		}
		return trackers;
	}
	
	/**
	 * Forces the current recording to stop.
	 */
	void forceStopRecording() {
		recordingMode.set(false);
	}
	
	/**
	 * Get the node containing the controls.
	 * @return main pane
	 */
	Node getNode() {
		return mainPane;
	}
	
	/**
	 * Remove the {@link ViewTrackerControlPane} listener on the current viewer and this QuPath instance.
	 */
	void removeListeners() {
		viewer.imageDataProperty().removeListener(imageDataListener);
		qupath.viewerProperty().removeListener(viewerListener);
	}
}