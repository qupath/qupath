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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.DateTimeStringConverter;
import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.ColorMapCanvas;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractOverlay.LocationStringFunction;
import qupath.lib.gui.viewer.recording.ViewTrackerDataMaps.Feature;
import qupath.lib.gui.viewer.overlays.BufferedImageOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Command to export view tracking information.
 * 
 * @author Pete Bankhead
 * @author Melvin Gelbard
 */
final class ViewTrackerAnalysisCommand implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(ViewTrackerAnalysisCommand.class);
	
	private final QuPathGUI qupath;
	private final QuPathViewer viewer;
	private final ViewTracker tracker;
	private final ImageServer<?> server;
	private ObjectProperty<ViewRecordingFrame> currentFrame = new SimpleObjectProperty<>();
	private BooleanProperty isOpenedProperty = new SimpleBooleanProperty(true);
	
	private Stage dialog;
	private SplitPane mainPane;
	private TableView<ViewRecordingFrame> table = new TableView<>();
	
	private ViewTrackerPlayback playback;
	
	private Slider zSlider;
	private Slider tSlider;
	private Slider timeSlider;

	private ProgressIndicator progressIndicator;
	
	private ObservableList<ColorMap> colorMaps;
	private ColorMapCanvas colorMapCanvas;
	
	private CheckBox visualizationCheckBox;
//	private RadioButton timeNormalizedRadio;
//	private RadioButton downsampleNormalizedRadio;
	private RangeSlider downsampleSlider;
	private RangeSlider timeDisplayedSlider;
	
	private static final Node iconPlay = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.PLAYBACK_PLAY);
	private static final Node iconStop = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_STOP);
	private static final Node iconRewind = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TRACKING_REWIND);
	
	private ViewTrackerSlideOverview slideOverview;
	private ViewTrackerDataMaps dataMaps;
	
	private double initialWidth = -1;
	private double initialHeight = -1;
	
	// Regenerating the data overlay will be done in the background to prevent freezing the GUI
	private final ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("data-overlay", true));
	
	/**
	 * Create a view tracker analysis command.
	 * @param qupath 
	 * @param tracker
	 */
	ViewTrackerAnalysisCommand(final QuPathGUI qupath, final ViewTracker tracker) {
		this.qupath = qupath;
		this.tracker = tracker;
		this.viewer = qupath.getViewer();
		this.server = viewer.getServer();
		this.slideOverview = new ViewTrackerSlideOverview(viewer);
		this.playback = new ViewTrackerPlayback(viewer);
		this.playback.setViewTracker(tracker);
	}

	@Override
	public void run() {
		if (dialog == null) {
			dialog = new Stage();
			dialog.sizeToScene();
			dialog.initOwner(qupath.getStage());
			dialog.setTitle("Recording analysis");
			
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
				
				slideOverview.paintCanvas(o.getZ() != frame.getZ() || o.getT() != frame.getT(), false);
			});

			mainPane = new SplitPane();
			mainPane.setDividerPositions(1.0);
			
			BorderPane tablePane = new BorderPane();
			tablePane.setCenter(table);
			
			
			//----------------------------------------------------------------------//
			//----------------- SLIDE OVERVIEW (TOP LEFT)------------------//
			//----------------------------------------------------------------------//
			
			int z = server.getMetadata().getSizeZ();
			int t = server.getMetadata().getSizeT();
			
			tSlider = new Slider(0, t-1, 0);
			tSlider.setBlockIncrement(1);
			tSlider.setValue(viewer.getTPosition());
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
			zSlider.setValue(viewer.getZPosition());
			zSlider.valueProperty().addListener((v, o, n) -> {
	    		zSlider.setValue(n.intValue());
	    		viewer.setZPosition(n.intValue());
	    		slideOverview.paintCanvas();
			});
			zSlider.setOrientation(Orientation.VERTICAL);
			
			var timeSliderLength = tracker.getLastTime() - tracker.getStartTime();
			timeSlider = new Slider(0L, timeSliderLength, 0L);
			timeSlider.setMinWidth(250.0);
			
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
			timeSlider.setOnMouseClicked(e -> {
				playback.doStopPlayback();
			});
			
			long startTime = tracker.getStartTime();
			long endTime = tracker.getLastTime();

			Label timepointLabel = new Label();
			timepointLabel.textProperty().bind(
					Bindings.createStringBinding(() ->  "t=" + GeneralTools.formatNumber(tSlider.getValue(), 2), tSlider.valueProperty())
					);
			
			Label zSliceLabel = new Label();
			zSliceLabel.textProperty().bind(
					Bindings.createStringBinding(() -> "z=" + GeneralTools.formatNumber(zSlider.getValue(), 2), zSlider.valueProperty())
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
					
					// Set the right slide overview image plane and visible shape for current frame
					slideOverview.paintCanvas();
					
					playback.doStartPlayback();
				} else {
					// If already playing, pause the playback where it currently is
					playback.doStopPlayback();
				}
			});
			
			Button btnRewind = new Button();
			btnRewind.setGraphic(iconRewind);
			btnRewind.setOnAction(e -> {
				boolean isPlaying = playback.isPlaying();
				playback.doStopPlayback();
				timeSlider.setValue(tracker.getFrame(0).getTimestamp());
				if (isPlaying)
					playback.doStartPlayback();
			});
			
			// If we have a total of 0 or 1 frame in recording, disable playback
			btnPlay.disableProperty().bind(new SimpleBooleanProperty(tracker.nFrames() <= 1));
			btnRewind.disableProperty().bind(new SimpleBooleanProperty(tracker.nFrames() <= 1));
			
			
			playback.playingProperty().addListener((v, o, n) -> {
				if (n) {
					btnPlay.setGraphic(iconStop);
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
			//----------- DATA VISUALIZATION PANE ---------------//
			//--------------------------------------------------------------//
			
			visualizationCheckBox = new CheckBox("Enable data overlay");
//			Label normalizedByLabel = new Label("Normalized by");
			progressIndicator = new ProgressIndicator(); // Bound below in the Binding section
			progressIndicator.setPrefSize(15, 15);
			
//			ToggleGroup toggleGroup = new ToggleGroup();
//			timeNormalizedRadio = new RadioButton("Time");
//			timeNormalizedRadio.setSelected(true);
//			timeNormalizedRadio.setToggleGroup(toggleGroup);
//			
//			downsampleNormalizedRadio = new RadioButton("Downsample");
//			downsampleNormalizedRadio.setToggleGroup(toggleGroup);

			// Create a color mapper ComboBox
			colorMaps = FXCollections.observableArrayList(ColorMaps.getColorMaps().values());
			ComboBox<ColorMap> colorMapCombo = new ComboBox<>(colorMaps);
			colorMapCombo.setMinWidth(350);
			colorMapCanvas = new ColorMapCanvas(10, colorMaps.get(0));
			colorMapCombo.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> colorMapCanvas.setColorMap(n));
			
			if (colorMapCombo.getSelectionModel().isEmpty() && !colorMapCombo.getItems().isEmpty())
				colorMapCombo.getSelectionModel().selectFirst();
			colorMapCombo.setTooltip(new Tooltip("Select color map"));
			
			//------------------ TIME DISPLAYED RANGESLIDER ------------------//
			Label timeDisplayedLeftLabel = new Label();
			Label timeDisplayedRightLabel = new Label();
			timeDisplayedSlider = new RangeSlider(0, timeSliderLength, 0, timeSliderLength);
			timeDisplayedSlider.setMinWidth(250.0);

			
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
						updateOverlays();
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
						tf.setTextFormatter(new TextFormatter<>(new DateTimeStringConverter(format), format.parse(ViewTrackerTools.getPrettyTimestamp((long) timeDisplayedSlider.getHighValue()))));
					} catch (ParseException ex) {
						logger.error("Error parsing the input time: ", ex.getLocalizedMessage());
					}
					gp.addRow(0, new Label("Enter time"), tf);
					var response = Dialogs.showConfirmDialog("Set max time", gp);
					if (response) {
						long time = ViewTrackerTools.getTimestampFromPrettyString(tf.getText());
						timeDisplayedSlider.setHighValue(time);
						updateOverlays();
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
					.map(e -> e.getDownsampleFactor())
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
					updateOverlays();
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
					updateOverlays();
				}
			});
			
			downsampleLeftLabel.textProperty().bind(
					Bindings.createStringBinding(() -> GeneralTools.formatNumber(downsampleSlider.getLowValue(), 2), downsampleSlider.lowValueProperty())
					);
			downsampleRightLabel.textProperty().bind(
					Bindings.createStringBinding(() -> GeneralTools.formatNumber(downsampleSlider.getHighValue(), 2), downsampleSlider.highValueProperty())
					);
			
			// TODO: if using keys to change sliders, nothing will trigger the overlay update
			dataMaps = new ViewTrackerDataMaps(server, tracker);
			timeDisplayedSlider.setOnMouseReleased(v -> updateOverlays());
			downsampleSlider.setOnMouseReleased(v -> updateOverlays());
			colorMapCanvas.colorMapProperty().addListener((v, o, n) -> updateOverlays());
			progressIndicator.visibleProperty().bind(dataMaps.generatingOverlayProperty());

			visualizationCheckBox.selectedProperty().addListener((v, o, n) -> {
				if (n)
					updateOverlays();
				else {
					viewer.getCustomOverlayLayers().clear();
					slideOverview.setOverlay(null);
				}
			});

//			timeNormalizedRadio.selectedProperty().addListener((v, o, n) -> updateOverlays());
			
			//--------------------------------------------------------------//
			//---------- PUTTING EVERYTHING TOGETHER ---------//
			//--------------------------------------------------------------//
			
			GridPane mainLeftPane = new GridPane();
			GridPane slideOverviewPane = new GridPane();
			GridPane dataVisualizationPane = new GridPane();
			
			mainLeftPane.setPadding(new Insets(5.0, 5.0, 5.0, 15.0));
			slideOverviewPane.setPadding(new Insets(5.0, 5.0, 5.0, 5.0));
			dataVisualizationPane.setPadding(new Insets(5.0, 5.0, 15.0, 5.0));
			
			mainLeftPane.addRow(0, slideOverviewPane);
			mainLeftPane.addRow(1, dataVisualizationPane);
			
			GridPane canvasPane = new GridPane();
			GridPane timelinePane = new GridPane();
			GridPane playbackPane = new GridPane();
//			GridPane normalizedByPane = new GridPane();
			GridPane rangeSlidersPane = new GridPane();
			GridPane downsamplePane = new GridPane();
			Separator sep = new Separator();
			
			slideOverviewPane.setHgap(10);
			slideOverviewPane.setVgap(10);
			dataVisualizationPane.setHgap(10);
			dataVisualizationPane.setVgap(10);
			playbackPane.setHgap(10.0);
//			normalizedByPane.setHgap(10.0);
			rangeSlidersPane.setHgap(10.0);
			rangeSlidersPane.setVgap(10.0);
			downsamplePane.setHgap(10.0);
			
			// Some 'invisible' elements might shift the canvas to the right, so make sure this doesn't happen
			if (zSlider.isVisible()) {
				canvasPane.addRow(0, new HBox(), timepointLabel, tSlider);
				canvasPane.addRow(1, zSliceLabel, zSlider, slideOverview.getCanvas());
				zSlider.setTooltip(new Tooltip("Slide to change the visible z-slice"));
			} else {
				canvasPane.addRow(0, timepointLabel, tSlider, new HBox());
				canvasPane.addRow(1, new HBox(), slideOverview.getCanvas());
			}
			
			if (tSlider.isVisible())
				tSlider.setTooltip(new Tooltip("Slide to change the visible timepoint"));
				
//			normalizedByPane.addRow(0, timeNormalizedRadio, downsampleNormalizedRadio, progressIndicator);
//			normalizedByPane.addRow(0, progressIndicator);
			timelinePane.addRow(0, timeLabelLeft, timeSlider, timeLabelRight);
			playbackPane.addRow(0, btnPlay, btnRewind);
			timelinePane.setAlignment(Pos.CENTER);
			playbackPane.setAlignment(Pos.CENTER);
			canvasPane.setAlignment(Pos.CENTER);
			
			btnPlay.setTooltip(new Tooltip("Play the recording"));
			btnRewind.setTooltip(new Tooltip("Rewind the recording"));

			int row = 0;
			PaneTools.addGridRow(slideOverviewPane, row++, 0, null, new HBox(), canvasPane);
			PaneTools.addGridRow(slideOverviewPane, row++, 0, null, timeLabelLeft, timeSlider, timeLabelRight);
			PaneTools.addGridRow(slideOverviewPane, row++, 0, "Playback options", new HBox(), playbackPane);
			PaneTools.addGridRow(slideOverviewPane, row++, 0, null, sep, sep, sep, sep);
			
			row = 0;
			PaneTools.addGridRow(dataVisualizationPane, row++, 0, "Show the amount of time spent in each region of the image", visualizationCheckBox, progressIndicator);
			PaneTools.addGridRow(dataVisualizationPane, row++, 0, "The data will only take into account the values recorded in-between this range", new Label("Time range"), timeDisplayedLeftLabel, timeDisplayedSlider, timeDisplayedRightLabel);
			PaneTools.addGridRow(dataVisualizationPane, row++, 0, "The data will only take into account the values recorded in-between this range", new Label("Downsample range"), downsampleLeftLabel, downsampleSlider, downsampleRightLabel);
			PaneTools.addGridRow(dataVisualizationPane, row++, 0, "Color map", new Label("Color map"), colorMapCombo, colorMapCombo, colorMapCombo);
			PaneTools.addGridRow(dataVisualizationPane, row++, 0, null, colorMapCanvas, colorMapCanvas, colorMapCanvas, colorMapCanvas);
			
			for (Node child: dataVisualizationPane.getChildren()) {
				if (child == visualizationCheckBox)
					continue;
				
				if (child == downsampleSlider)
					child.disableProperty().bind(visualizationCheckBox.selectedProperty().not().or(new SimpleBooleanProperty(minDownsample == maxDownsample)));
//				else if (child == downsampleNormalizedRadio)
//					child.disableProperty().bind(visualizationCheckBox.selectedProperty().not().or(new SimpleBooleanProperty(minDownsample == maxDownsample)));
				else
					child.disableProperty().bind(visualizationCheckBox.selectedProperty().not());
			}
			
		    ColumnConstraints col1 = new ColumnConstraints();
		    ColumnConstraints col2 = new ColumnConstraints();
		    ColumnConstraints col3 = new ColumnConstraints();
		    ColumnConstraints col4 = new ColumnConstraints();
		    col2.setHgrow(Priority.ALWAYS);
		    slideOverviewPane.getColumnConstraints().addAll(col1, col2, col3, col4);
			
			//--------------------- BOTTOM BUTTON PANE---------------------//
			Button btnExpand = new Button("Show frames");
			Button btnOpen = new Button("Open directory");
			btnOpen.setDisable(tracker.getFile() == null);
			List<Button> buttons = new ArrayList<>();
			
			btnOpen.setOnAction(e -> GuiTools.browseDirectory(tracker.getFile()));
			
			btnExpand.setOnAction(e -> {
				if (btnExpand.getText().equals("Show frames")) {
					dialog.setWidth(initialWidth + 700);
					dialog.setResizable(true);
					dialog.setMinWidth(initialWidth + 50);
					dialog.setMinHeight(initialHeight);
					mainLeftPane.setMinWidth(initialWidth);
					mainLeftPane.setMaxWidth(initialWidth);
					mainPane.getItems().add(tablePane);
					mainPane.setDividerPositions(0.0);
					btnExpand.setText("Hide frames");
				} else {
					mainPane.getItems().remove(tablePane);
					btnExpand.setText("Show frames");
					dialog.setWidth(initialWidth + 30);
					dialog.setHeight(initialHeight);
					dialog.setResizable(false);
				}
			});
			buttons.add(btnOpen);
			buttons.add(btnExpand);
			
			GridPane panelButtons = PaneTools.createColumnGridControls(buttons.toArray(new ButtonBase[0]));
			PaneTools.addGridRow(mainLeftPane, row++, 0, null, panelButtons, panelButtons, panelButtons);

			mainPane.getItems().add(mainLeftPane);
			dialog.setScene(new Scene(mainPane));
		}
		
		viewer.imageDataProperty().addListener((v, o, n) -> dialog.hide());
		
		dialog.setOnHiding(e -> {
			viewer.getCustomOverlayLayers().clear();
			playback.doStopPlayback();
			isOpenedProperty.set(false);
		});

		dialog.setResizable(false);
		dialog.show();
		
		initialWidth = dialog.getWidth();
		initialHeight = dialog.getHeight();
	}

	private static Object getColumnValue(final ViewRecordingFrame frame, final String columnName) {
		switch (columnName) {
			case "Timestamp (ms)": return frame.getTimestamp();
			case "X": return frame.getImageBounds().x;
			case "Y": return frame.getImageBounds().y;
			case "Width": return frame.getImageBounds().width;
			case "Height": return frame.getImageBounds().height;
			case "Canvas width": return frame.getSize().width;
			case "Canvas height": return frame.getSize().height;
			case "Downsample factor": return frame.getDownsampleFactor();
			case "Rotation": return frame.getRotation();
			case "Cursor X": return frame.getCursorPosition() == null ? "" : frame.getCursorPosition().getX();
			case "Cursor Y": return frame.getCursorPosition() == null ? "" : frame.getCursorPosition().getY();
			case "Active Tool": return frame.getActiveTool() == null ? "Other" : frame.getActiveTool().getName();
			case "Eye X": return frame.getEyePosition().getX();
			case "Eye Y": return frame.getEyePosition().getY();
			case "Fixated": return frame.isEyeFixated();
			case "Z": return frame.getZ();
			case "T": return frame.getT();
		}
		return null;
	}
	
	private static String getColumnName(ViewTracker tracker, int col) {
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
					return "Eye Y";
				}
				return "Z";
			case 13: return tracker.hasEyeTrackingData() ? "Eye Y" : "T";
			case 14: return "Fixated";
			case 15: return "Z";
			case 16: return "T";
		}
		return null;
	}
	
	private static int nCols(final ViewTracker tracker) {
		if (tracker == null)
			return 0;
		
		int nCol = 9;
		nCol += tracker.hasCursorTrackingData() ? 2 : 0;
		nCol += tracker.hasActiveToolTrackingData() ? 1 : 0;
		nCol += tracker.hasEyeTrackingData() ? 3 : 0;
		nCol += tracker.hasZAndT() ? 2 : 0;
		return nCol;
	}
	
	private void refreshTracker() {
		List<ViewRecordingFrame> frames = new ArrayList<>();
		if (tracker == null)
			return;
		for (int i = 0; i < tracker.nFrames(); i++) {
			frames.add(tracker.getFrame(i));
		}
		table.getItems().setAll(frames);
	}
	
	private void updateOverlays() {
		executor.execute(() -> {
			dataMaps.generatingOverlayProperty().set(true);
			dataMaps.updateDataMaps(
					timeDisplayedSlider.lowValueProperty().longValue(),
					timeDisplayedSlider.highValueProperty().longValue(),
					downsampleSlider.lowValueProperty().doubleValue(),
					downsampleSlider.highValueProperty().doubleValue(),
					Feature.TIMESTAMP,
//					timeNormalizedRadio.selectedProperty().get() ? Feature.TIMESTAMP : Feature.DOWNSAMPLE,
					colorMapCanvas.getColorMap()
					);
			viewer.repaint();
			
			Number maxValue = dataMaps.getMaxValue(zSlider.getValue(), tSlider.getValue());
			Function<Double, String> fun;
			fun = d -> "View time: " + ViewTracker.df.format(Double.valueOf(d/255 * maxValue.longValue()/1000)) + "s";
//			if (timeNormalizedRadio.isSelected())
//				fun = d -> "View time: " + ViewTracker.df.format(Double.valueOf(d/255 * maxValue.longValue()/1000)) + "s";
//			else
//				fun = d -> "Downsample: " + ViewTracker.df.format(d/255 * maxValue.doubleValue());
				
			colorMapCanvas.setTooltipFunction(fun);
			
			// Make sure the live visualisation is still requested when map generation is done
			if (isOpenedProperty.get() && visualizationCheckBox.isSelected()) {
				var overlay = new BufferedImageOverlay(viewer, dataMaps.getRegionMaps());
				
				var locationString = new DataMapsLocationString(dataMaps);
				overlay.setLocationStringFunction(locationString);

				// Update the viewer's custom overlay layer
				viewer.getCustomOverlayLayers().setAll(overlay);
				
				// Update the slideOverview
				slideOverview.setOverlay(overlay);
				slideOverview.setLocationStringFunction(locationString);
			}
			
			// Make sure the progress indicator doesn't show 'loading' anymore
			dataMaps.generatingOverlayProperty().set(false);
		});
	}
	
	BooleanProperty isOpenedProperty() {
		return isOpenedProperty;
	}
	
	public class DataMapsLocationString implements LocationStringFunction {
		
		ViewTrackerDataMaps data;
		
		DataMapsLocationString(ViewTrackerDataMaps data) {
			this.data = data;
		}

		@Override
		public String getLocationString(ImageData<BufferedImage> imageData, double x, double y, int z, int t) {
			Number value = data.getValueFromOriginalLocation((int)x, (int)y, z, t);
			if (value == null)
				return "";

			return "View time: " + ViewTracker.df.format(Double.valueOf(value.longValue()/1000.0)) + " s";
//			return (value.doubleValue() == 0 ? "" : "Downsample: " + ViewTracker.df.format(value.doubleValue()));
		}
	}
}