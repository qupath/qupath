/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.controlsfx.control.RangeSlider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.DateTimeStringConverter;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Command to export view tracking information.
 * 
 * @author Pete Bankhead
 *
 */
class ViewTrackerAnalysisCommand implements Runnable {
	private final static Logger logger = LoggerFactory.getLogger(ViewTrackerAnalysisCommand.class);
	
	private ViewTrackerControlPane owner;
	private QuPathViewer viewer;
	private ViewTracker tracker;
	private ImageServer<?> server;
	private ObjectProperty<ViewRecordingFrame> currentFrame = new SimpleObjectProperty<>();
	private BooleanProperty isOpened = new SimpleBooleanProperty(true);
	
	private Stage dialog;
	private SplitPane mainPane;
	private TableView<ViewRecordingFrame> table = new TableView<>();
	
	private ViewTrackerPlayback playback;
	
	private Slider zSlider;
	private Slider tSlider;
	private Slider timeSlider;
	
	private RadioButton timeNormalizedRadio;
	private RadioButton magnificationNormalizedRadio;
	private RangeSlider downsampleSlider;
	private RangeSlider timeDisplayedSlider;
	
	private static final Node iconPlay = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.PLAYBACK_PLAY);
	private static final Node iconPause = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_PAUSE);
	private static final Node iconStop = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_STOP);
	
	private ViewTrackerSlideOverview slideOverview;
	private ViewTrackerDataOverlay trackerDataOverlay;
	private Canvas canvas;
	
	private double initialWidth = -1;
	
	/**
	 * Create a view tracker analysis command.
	 * @param parent the parent pane that this dialog belongs to
	 * @param viewer the viewer being tracked
	 * @param tracker the tracker doing the tracking
	 */
	public ViewTrackerAnalysisCommand(ViewTrackerControlPane parent, final QuPathViewer viewer, final ViewTracker tracker) {
		this.owner = parent;
		this.viewer = viewer;
		this.tracker = tracker;
		this.server = viewer.getServer();
		this.canvas = new Canvas();
		this.slideOverview = new ViewTrackerSlideOverview(viewer, canvas);
		this.playback = new ViewTrackerPlayback(viewer);
		this.playback.setViewTracker(tracker);
	}

	@Override
	public void run() {
		if (dialog == null) {
			dialog = new Stage();
			dialog.sizeToScene();
			// TODO: Change owner? To block previous windows
			dialog.initOwner(owner.getNode().getScene().getWindow());
			dialog.setTitle("Recording analysis");
			dialog.setAlwaysOnTop(true);
			
			currentFrame.set(tracker.getFrame(0));
			
			int nCols = nCols(tracker);
			for (int i = 0; i < nCols; i++) {
				final int col = i;
				final String columnName = getColumnName(tracker, col);
				TableColumn<ViewRecordingFrame, Object> column = new TableColumn<>(columnName);
				column.setCellValueFactory(new Callback<CellDataFeatures<ViewRecordingFrame, Object>, ObservableValue<Object>>() {
				     @Override
					public ObservableValue<Object> call(CellDataFeatures<ViewRecordingFrame, Object> frame) {
				         return new SimpleObjectProperty<>(getColumnValue(frame.getValue(), columnName));
				     }
				  });
				table.getColumns().add(column);
			}
			
			
			table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
			table.getSelectionModel().selectedItemProperty().addListener((v, o, frame) -> {
				if (frame != null)
					currentFrame.set(frame);	
			});
			refreshTracker();
			
			playback.getCurrentFrame().addListener((v, o, frame) -> currentFrame.set(frame));
			
			currentFrame.addListener((v, o, frame) -> {
				if (frame == null)
					return;
				
				// Set viewer for frame
				ViewTrackerPlayback.setViewerForFrame(viewer, frame);
				
				// Set slide overview for frame
				slideOverview.setVisibleShape(frame);
				
				zSlider.setValue(frame.getZ());
				tSlider.setValue(frame.getT());
				timeSlider.setValue(frame.getTimestamp());
				
				
				slideOverview.paintCanvas();
			});

			mainPane = new SplitPane();
			mainPane.setDividerPositions(1.0);
			
			BorderPane tablePane = new BorderPane();
			tablePane.setCenter(table);
			
			
			//----------------------------------------------------------------------//
			//--------------------- SLIDE OVERVIEW (TOP RIGHT) ---------------------//
			//----------------------------------------------------------------------//
			
			GridPane analysisPane = new GridPane();
			analysisPane.setPadding(new Insets(5.0, 5.0, 5.0, 5.0));
			analysisPane.setHgap(10);
			analysisPane.setVgap(10);
			int z = server.getMetadata().getSizeZ();
			int t = server.getMetadata().getSizeT();
			
			tSlider = new Slider(0, t-1, 0);
			tSlider.setBlockIncrement(1);
//			tSlider.setMinorTickCount(0);
//			tSlider.setMajorTickUnit(1);
//			tSlider.setShowTickMarks(true);
			tSlider.valueProperty().addListener((v, o, n) -> {
		    	tSlider.setValue(n.intValue());
		    	viewer.setTPosition(n.intValue());
	    		slideOverview.paintCanvas();
			});
			zSlider = new Slider(0, z-1, 0);
			zSlider.setBlockIncrement(1);
			zSlider.setMinorTickCount(0);
			zSlider.setMajorTickUnit(1);
			zSlider.setShowTickMarks(true);
			zSlider.valueProperty().addListener((v, o, n) -> {
	    		zSlider.setValue(n.intValue());
	    		viewer.setZPosition(n.intValue());
	    		slideOverview.paintCanvas();
			});
			zSlider.setOrientation(Orientation.VERTICAL);
			
			var timeSliderLength = tracker.getLastTime() - tracker.getStartTime();
			timeSlider = new Slider(0L, timeSliderLength, 0L);
			
			if (timeSliderLength > 0) {
				timeSlider.setMajorTickUnit(timeSliderLength / 4);
				timeSlider.setMinorTickCount(0);
				timeSlider.setShowTickMarks(true);
			}
			timeSlider.valueProperty().addListener((v, o, n) -> {
				var frame = tracker.getFrameForTime(n.longValue());
				currentFrame.set(frame);
				
				if (table.getSelectionModel().getSelectedItem() != frame)
					table.getSelectionModel().select(frame);
			});
			
			long startTime = tracker.getStartTime();
			long endTime = tracker.getLastTime();

			Label timepointLabel = new Label();
			timepointLabel.textProperty().bind(
					Bindings.createStringBinding(() -> GeneralTools.formatNumber(tSlider.getValue(), 2), tSlider.valueProperty())
					);
			
			Label zSliceLabel = new Label();
			zSliceLabel.textProperty().bind(
					Bindings.createStringBinding(() -> GeneralTools.formatNumber(zSlider.getValue(), 2), zSlider.valueProperty())
					);
			
			Label timeLabelLeft = new Label();
			timeLabelLeft.textProperty().bind(
					Bindings.createStringBinding(() -> ViewTrackerTools.getPrettyTimestamp(startTime, (long)timeSlider.getValue() + startTime), timeSlider.valueProperty())
					);
			
			Label timeLabelRight = new Label();
			timeLabelRight.textProperty().bind(
					Bindings.createStringBinding(() -> "-" + ViewTrackerTools.getPrettyTimestamp((long)timeSlider.getValue() + startTime, endTime), timeSlider.valueProperty())
					);
			
			
			if (t == 1) {
				tSlider.setVisible(false);
				timepointLabel.setVisible(false);
			}
			
			if (z == 1) {
				zSlider.setVisible(false);
				zSliceLabel.setVisible(false);
			}

			Button btnPlay = new Button();
			btnPlay.setGraphic(iconPlay);
			btnPlay.setOnAction(e -> {
				if (!playback.isPlaying()) {
					// If it's not playing already, start playing
					playback.setFirstFrame(currentFrame.get());
					playback.setPlaying(true);
				} else {
					// If already playing, pause the playback where it currently is
					playback.doStopPlayback();
				}
				
			});
			
			Button btnStop = new Button();
			btnStop.setGraphic(iconStop);
			btnStop.setOnAction(e -> {
				playback.setPlaying(false);
				timeSlider.setValue(tracker.getFrame(0).getTimestamp());
			});
			
			// If we have a total of 0 or 1 frame in recording, disable playback
			btnPlay.disableProperty().bind(new SimpleBooleanProperty(tracker.nFrames() <= 1));
			btnStop.disableProperty().bind(new SimpleBooleanProperty(tracker.nFrames() <= 1));
			
			
			playback.playingProperty().addListener((v, o, n) -> {
				if (n) {
					btnPlay.setGraphic(iconPause);
					//btnPlay.setText("Pause");
					zSlider.setDisable(true);
					tSlider.setDisable(true);
				} else {
					btnPlay.setGraphic(iconPlay);
					//btnPlay.setText("Play");
					zSlider.setDisable(false);
					tSlider.setDisable(false);
				}
			});
			
			//--------------------------------------------------------------//
			//--------------------- DATA VISUALIZATION ---------------------//
			//--------------------------------------------------------------//
			
			CheckBox visualizationCheckBox = new CheckBox("Live data visualization");
			Label normalizedByLabel = new Label("Normalized by:");
			
			ToggleGroup toggleGroup = new ToggleGroup();
			timeNormalizedRadio = new RadioButton("Time");
			timeNormalizedRadio.setSelected(true);
			timeNormalizedRadio.setToggleGroup(toggleGroup);
			
			magnificationNormalizedRadio = new RadioButton("Magnification");
			magnificationNormalizedRadio.setToggleGroup(toggleGroup);
			
			//------------------ TIME DISPLAYED RANGESLIDER ------------------//
			Label timeDisplayedLeftLabel = new Label();
			Label timeDisplayedRightLabel = new Label();
			timeDisplayedSlider = new RangeSlider(0L, timeSliderLength, 0L, timeSliderLength);

			
			// Prompt input from user (min time)
			timeDisplayedLeftLabel.setOnMouseClicked(e -> {
				if (e.getButton().equals(MouseButton.PRIMARY) && e.getClickCount() == 2) {
					GridPane gp = new GridPane();
					TextField tf = new TextField();
					SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
					try {
						tf.setTextFormatter(new TextFormatter<>(new DateTimeStringConverter(format), format.parse("00:00:00")));
					} catch (ParseException ex) {
						logger.error("Error parsing the input time: ", ex.getLocalizedMessage());
					}
					gp.addRow(0, new Label("Enter time"), tf);
					var response = Dialogs.showConfirmDialog("Set min time", gp);

					if (response) {
						long time = TimeUnit.HOURS.toMillis(Integer.parseInt(tf.getText(0, 2))) +
								TimeUnit.MINUTES.toMillis(Integer.parseInt(tf.getText(3, 5))) +
								TimeUnit.SECONDS.toMillis(Integer.parseInt(tf.getText(6, 8)));
						timeDisplayedSlider.setLowValue(time);
					}
				}
			});
			// Prompt input from user (max time)
			timeDisplayedRightLabel.setOnMouseClicked(e -> {
				if (e.getButton().equals(MouseButton.PRIMARY) && e.getClickCount() == 2) {
					GridPane gp = new GridPane();
					TextField tf = new TextField();
					SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
					try {
						tf.setTextFormatter(new TextFormatter<>(new DateTimeStringConverter(format), format.parse("00:00:00")));
					} catch (ParseException ex) {
						logger.error("Error parsing the input time: ", ex.getLocalizedMessage());
					}
					gp.addRow(0, new Label("Enter time"), tf);
					var response = Dialogs.showConfirmDialog("Set max time", gp);
					if (response) {
						long time = TimeUnit.HOURS.toMillis(Integer.parseInt(tf.getText(0, 2))) +
								TimeUnit.MINUTES.toMillis(Integer.parseInt(tf.getText(3, 5))) +
								TimeUnit.SECONDS.toMillis(Integer.parseInt(tf.getText(6, 8)));
						timeDisplayedSlider.setHighValue(time);
					}
				}
			});
			
			timeDisplayedLeftLabel.textProperty().bind(
					Bindings.createStringBinding(() -> ViewTrackerTools.getPrettyTimestamp(startTime, (long)timeDisplayedSlider.getLowValue() + startTime), timeDisplayedSlider.lowValueProperty())
					);
			timeDisplayedRightLabel.textProperty().bind(
					Bindings.createStringBinding(() -> ViewTrackerTools.getPrettyTimestamp(startTime, (long)timeDisplayedSlider.getHighValue() + startTime), timeDisplayedSlider.highValueProperty())
					);
			
			
			//------------------ DOWNSAMPLE RANGESLIDER ------------------//
			List<Double> allFramesDownsamples = tracker.getAllFrames().stream()
					.map(e -> e.getDownFactor())
					.collect(Collectors.toList());
			
			var minDownsample = allFramesDownsamples.stream().min(Comparator.naturalOrder()).get();
			var maxDownsample = allFramesDownsamples.stream().max(Comparator.naturalOrder()).get();
			downsampleSlider = new RangeSlider(minDownsample, maxDownsample, minDownsample, maxDownsample);
			
			Label downsampleLeftLabel = new Label();
			Label downsampleRightLabel = new Label();
			
			// Prompt input from user (min downsample)
			downsampleLeftLabel.setOnMouseClicked(e -> {
				if (e.getButton().equals(MouseButton.PRIMARY) && e.getClickCount() == 2) {
					ParameterList params = new ParameterList();
					params.addDoubleParameter("downsampleFilterLow", "Enter downsample", downsampleSlider.getLowValue());
					
					if (!Dialogs.showParameterDialog("Set min downsample", params))
						return;
					
					double downFactor = params.getDoubleParameterValue("downsampleFilterLow");
					downsampleSlider.setLowValue(downFactor);
				}
			});
			// Prompt input from user (max downsample)
			downsampleRightLabel.setOnMouseClicked(e -> {
				if (e.getButton().equals(MouseButton.PRIMARY) && e.getClickCount() == 2) {
					ParameterList params = new ParameterList();
					params.addDoubleParameter("downsampleFilterHigh", "Enter downsample", downsampleSlider.getHighValue());
					
					if (!Dialogs.showParameterDialog("Set max downsample", params))
						return;
					
					double downFactor = params.getDoubleParameterValue("downsampleFilterHigh");
					downsampleSlider.setHighValue(downFactor);
				}
			});
			
			downsampleLeftLabel.textProperty().bind(
					Bindings.createStringBinding(() -> GeneralTools.formatNumber(downsampleSlider.getLowValue(), 2), downsampleSlider.lowValueProperty())
					);
			downsampleRightLabel.textProperty().bind(
					Bindings.createStringBinding(() -> GeneralTools.formatNumber(downsampleSlider.getHighValue(), 2), downsampleSlider.highValueProperty())
					);
			
			
			//------------------ BINDINGS/LISTENERS ------------------//
			normalizedByLabel.disableProperty().bind(visualizationCheckBox.selectedProperty().not());
			timeNormalizedRadio.disableProperty().bind(visualizationCheckBox.selectedProperty().not());
			magnificationNormalizedRadio.disableProperty().bind(visualizationCheckBox.selectedProperty().not());
			timeDisplayedLeftLabel.disableProperty().bind(visualizationCheckBox.selectedProperty().not());
			timeDisplayedRightLabel.disableProperty().bind(visualizationCheckBox.selectedProperty().not());
			downsampleSlider.disableProperty().bind(visualizationCheckBox.selectedProperty().not().or(new SimpleBooleanProperty(minDownsample == maxDownsample)));
			downsampleLeftLabel.disableProperty().bind(visualizationCheckBox.selectedProperty().not());
			downsampleRightLabel.disableProperty().bind(visualizationCheckBox.selectedProperty().not());
			timeDisplayedSlider.disableProperty().bind(visualizationCheckBox.selectedProperty().not());
			
			trackerDataOverlay = new ViewTrackerDataOverlay(server, viewer, tracker);
			timeDisplayedSlider.lowValueProperty().addListener((v, o, n) -> updateOverlays());
			timeDisplayedSlider.highValueProperty().addListener((v, o, n) -> updateOverlays());
			downsampleSlider.lowValueProperty().addListener((v, o, n) -> updateOverlays());
			downsampleSlider.highValueProperty().addListener((v, o, n) -> updateOverlays());


			visualizationCheckBox.selectedProperty().addListener((v, o, n) -> {
				if (n) {
					updateOverlays();
				} else {
					viewer.getCustomOverlayLayers().clear();
					slideOverview.setOverlay(null);
				}
			});
			
			// Listener for timeNormalizedRadio, no need for magnificationNormalizedRadio one as it's either one or the other
			timeNormalizedRadio.selectedProperty().addListener((v, o, n) -> updateOverlays());
			
			GridPane canvasPane = new GridPane();
			GridPane timepointPane = new GridPane();
			GridPane topPane = new GridPane();
			GridPane playbackPane = new GridPane();
			GridPane normalizedByPane = new GridPane();
			GridPane dataVisualizationPane = new GridPane();
			
			Separator sep = new Separator();
			
			dataVisualizationPane.setHgap(10.0);
			dataVisualizationPane.setVgap(5.0);
			playbackPane.setHgap(10.0);
			normalizedByPane.setHgap(10.0);
			
			canvasPane.addRow(0, zSliceLabel, zSlider, canvas);
			timepointPane.addRow(0, timepointLabel, tSlider);
			normalizedByPane.addRow(0, timeNormalizedRadio, magnificationNormalizedRadio);
			playbackPane.addRow(0, btnPlay, btnStop);
			playbackPane.setAlignment(Pos.CENTER);
//			PaneTools.addGridRow(dataVisualizationPane, 0, 0, "Enable live data visualization", visualizationCheckBox, visualizationCheckBox, visualizationCheckBox);
//			PaneTools.addGridRow(dataVisualizationPane, 1, 0, "Normalization type", normalizedByLabel, timeNormalizedRadio, magnificationNormalizedRadio);
//			PaneTools.addGridRow(dataVisualizationPane, 2, 0, "Choose a time range", timeDisplayedLeftLabel, timeDisplayedSlider, timeDisplayedRightLabel);
//			PaneTools.addGridRow(dataVisualizationPane, 3, 0, "Choose a magnification range", downsampleLeftLabel, downsampleSlider, downsampleRightLabel);


			int topPaneRow = 0;
			PaneTools.addGridRow(topPane, topPaneRow++, 0, "Slide to change timepoint", timepointPane);
			PaneTools.addGridRow(topPane, topPaneRow++, 0, "Slide to change z-slice", canvasPane);
			
			
			int row = 0;
			PaneTools.addGridRow(analysisPane, row++, 0, null, new HBox(), topPane);
			PaneTools.addGridRow(analysisPane, row++, 0, "Slide to change the grid resolution",  timeLabelLeft, timeSlider, timeLabelRight);
			PaneTools.addGridRow(analysisPane, row++, 0, "Playback options", new HBox(), playbackPane);
			PaneTools.addGridRow(analysisPane, row++, 0, null, sep, sep, sep);
			PaneTools.addGridRow(analysisPane, row++, 0, "Enable live data visualization", visualizationCheckBox, visualizationCheckBox, visualizationCheckBox);
			PaneTools.addGridRow(analysisPane, row++, 0, "Normalization type", normalizedByLabel, normalizedByPane);
			PaneTools.addGridRow(analysisPane, row++, 0, "Choose a time range", timeDisplayedLeftLabel, timeDisplayedSlider, timeDisplayedRightLabel);
			PaneTools.addGridRow(analysisPane, row++, 0, "Choose a magnification range", downsampleLeftLabel, downsampleSlider, downsampleRightLabel);
			
		    ColumnConstraints col1 = new ColumnConstraints();
		    ColumnConstraints col2 = new ColumnConstraints();
		    col1.setHgrow(Priority.ALWAYS);
		    col2.setHgrow(Priority.ALWAYS);
		    
		    analysisPane.getColumnConstraints().addAll(new ColumnConstraints(80), col1, new ColumnConstraints(80), col2);
			
//			GridPane.setHgrow(normalizedByLabel, Priority.ALWAYS);
//			GridPane.setHgrow(timeNormalizedRadio, Priority.ALWAYS);
//			GridPane.setHgrow(normalizedByLabel, Priority.ALWAYS);
//			normalizedByLabel.setPrefWidth(200.0);
//			timeNormalizedRadio.setPrefWidth(200.0);
//			magnificationNormalizedRadio.setPrefWidth(200.0);
//			normalizedByLabel.setMaxWidth(Double.MAX_VALUE);
			
			
			//--------------------- BOTTOM BUTTON PANE---------------------//
			Button btnExpand = new Button("Expand");
			Button btnOpen = new Button("Open directory");
			btnOpen.setDisable(tracker.getFile() == null);
			List<Button> buttons = new ArrayList<>();
			
			btnOpen.setOnAction(e -> GuiTools.browseDirectory(tracker.getFile()));
			
			btnExpand.setOnAction(e -> {
				if (btnExpand.getText().equals("Expand")) {
					initialWidth = dialog.getWidth();
					dialog.setMinWidth(initialWidth + 250);
					dialog.setMaxWidth(Double.MAX_VALUE);
					mainPane.getItems().add(tablePane);
					analysisPane.setMaxWidth(dialog.getScene().getWidth());
					btnExpand.setText("Collapse");
				} else {
					mainPane.getItems().remove(tablePane);
					btnExpand.setText("Expand");
					dialog.setMinWidth(initialWidth);
					dialog.setMaxWidth(initialWidth);
				}
			});
			buttons.add(btnOpen);
			buttons.add(btnExpand);
			
			GridPane panelButtons = PaneTools.createColumnGridControls(buttons.toArray(new ButtonBase[0]));
			PaneTools.addGridRow(analysisPane, row++, 0, "Button", panelButtons, panelButtons, panelButtons);
			

			mainPane.getItems().add(analysisPane);
			dialog.setScene(new Scene(mainPane));
		}
		
		dialog.setOnHiding(e -> {
			viewer.getCustomOverlayLayers().clear();
			isOpened.set(false);
		});
		
		dialog.show();
		dialog.setWidth(dialog.getWidth());
		dialog.setMinWidth(dialog.getWidth());
		dialog.setMaxWidth(dialog.getWidth());
		//dialog.toFront();
	}

	static Object getColumnValue(final ViewRecordingFrame frame, final String columnName) {
		switch (columnName) {
		case "Timestamp (ms)": return frame.getTimestamp();
		case "X": return frame.getImageBounds().x;
		case "Y": return frame.getImageBounds().y;
		case "Width": return frame.getImageBounds().width;
		case "Height": return frame.getImageBounds().height;
		case "Canvas width": return frame.getSize().width;
		case "Canvas height": return frame.getSize().height;
		case "Downsample factor": return frame.getDownFactor();
		case "Rotation": return frame.getRotation();
		case "Cursor X": return frame.getCursorPosition() == null ? "" : frame.getCursorPosition().getX();
		case "Cursor Y": return frame.getCursorPosition() == null ? "" : frame.getCursorPosition().getY();
		case "Active Tool": return frame.getActiveTool().getName();
		case "Eye X": return frame.getEyePosition().getX();
		case "Eye Y": return frame.getEyePosition().getY();
		case "Fixated": return frame.isEyeFixated();
		case "Z": return frame.getZ();
		case "T": return frame.getT();
		}
		return null;
	}
	
	static String getColumnName(ViewTracker tracker, int col) {
		switch (col) {
		case 0: return "Timestamp (ms)";
		case 1: return "X";
		case 2: return "Y";
		case 3: return "Width";
		case 4: return "Height";
		case 5: return "Canvas width";
		case 6: return "Canvas height";
		case 7: return "Downsample factor";
		case 8: return "Rotation";
		case 9: 
			if (tracker.hasCursorTrackingData()) return "Cursor X";
			else if (tracker.hasActiveToolTrackingData()) return "Active Tool";
			else if (tracker.hasEyeTrackingData()) return "Eye X";
			else return "Z";
		case 10: 
			if (tracker.hasCursorTrackingData()) return "Cursor Y";
			else if (tracker.hasEyeTrackingData()) return "Eye Y";
			else return "T";
		case 11: 
			if (tracker.hasActiveToolTrackingData()) return "Active Tool";
			else if (tracker.hasEyeTrackingData()) return "Eye X";
		case 12: 
			if (tracker.hasEyeTrackingData()) {
				if (tracker.hasActiveToolTrackingData()) return "Eye X";
				else return "Eye Y";
			} else
				return "Z";
		case 13: return tracker.hasEyeTrackingData() ? "Eye Y" : "T";
		case 14: return "Fixated";
		case 15: return "Z";
		case 16: return "T";
		}
		return null;
	}
	
	static int nCols(final ViewTracker tracker) {
		if (tracker == null)
			return 0;
		
		int nCol = 9;
		nCol += tracker.hasCursorTrackingData() ? 2 : 0;
		nCol += tracker.hasActiveToolTrackingData() ? 1 : 0;
		nCol += tracker.hasEyeTrackingData() ? 3 : 0;
		nCol += tracker.hasZAndT() ? 2 : 0;
		return nCol;
	}
	
	
	void refreshTracker() {
		List<ViewRecordingFrame> frames = new ArrayList<>();
		if (tracker == null)
			return;
		for (int i = 0; i < tracker.nFrames(); i++) {
			frames.add(tracker.getFrame(i));
		}
		table.getItems().setAll(frames);
	}
	
	
	private void updateOverlays() {
		trackerDataOverlay.updateDataImage(
				timeDisplayedSlider.lowValueProperty().longValue(),
				timeDisplayedSlider.highValueProperty().longValue(),
				downsampleSlider.lowValueProperty().doubleValue(),
				downsampleSlider.highValueProperty().doubleValue(),
				timeNormalizedRadio.selectedProperty().get()
				);
		
		// Update the viewer's custom overlay layer
		viewer.getCustomOverlayLayers().setAll(trackerDataOverlay.getOverlay());
		// Update the slideOverview
		slideOverview.setOverlay(trackerDataOverlay.getOverlay());
	}
	
	BooleanProperty getIsOpenedProperty() {
		return isOpened;
	}
}