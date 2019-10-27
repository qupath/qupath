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

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ChannelDisplayInfo.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.plots.HistogramPanelFX;
import qupath.lib.gui.plots.HistogramPanelFX.HistogramData;
import qupath.lib.gui.plots.HistogramPanelFX.ThresholdedChartWrapper;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;

/**
 * Command to show a Brightness/Contrast dialog to adjust the image display.
 * 
 * @author Pete Bankhead
 *
 */
public class BrightnessContrastCommand implements PathCommand, ImageDataChangeListener<BufferedImage>, PropertyChangeListener {
	
	private static Logger logger = LoggerFactory.getLogger(BrightnessContrastCommand.class);
	
	private static DecimalFormat df = new DecimalFormat("#.###");
		
	private QuPathGUI qupath;
	private QuPathViewer viewer;
	private ImageDisplay imageDisplay;
	
	private Slider sliderMin;
	private Slider sliderMax;
	private Stage dialog;
	
	private boolean slidersUpdating = false;
//	// The current slider min & max, in floating point precision
//	// Sliders themselves use integers - so there is likely to be a need to convert in many cases
//	private float minCurrent = 0;
//	private float maxCurrent = 0;
	
	private TableView<ChannelDisplayInfo> table = new TableView<>();
	
	private ColorPicker picker = new ColorPicker();
	
	private HistogramPanelFX histogramPanel = new HistogramPanelFX();
	private ThresholdedChartWrapper chartWrapper = new ThresholdedChartWrapper(histogramPanel.getChart());
	
	private Tooltip chartTooltip = new Tooltip(); // Basic stats go here now
	private ContextMenu popup;
	private BooleanProperty showGrayscale = new SimpleBooleanProperty();
	
	private BrightnessContrastKeyListener keyListener = new BrightnessContrastKeyListener();
	
	public BrightnessContrastCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.qupath.addImageDataChangeListener(this);
		
		// Add 'pure' red, green & blue to the available colors
		picker.getCustomColors().addAll(
				ColorToolsFX.getCachedColor(255, 0, 0),
				ColorToolsFX.getCachedColor(0, 255, 0),
				ColorToolsFX.getCachedColor(0, 0, 255),
				ColorToolsFX.getCachedColor(255, 255, 0),
				ColorToolsFX.getCachedColor(0, 255, 255),
				ColorToolsFX.getCachedColor(255, 0, 255));
	}
	
	
	boolean isInitialized() {
		return sliderMin != null && sliderMax != null;
	}
	
	
	protected void initializeSliders() {
		sliderMin = new Slider(0, 255, 0);
		sliderMax = new Slider(0, 255, 255);
		sliderMin.valueProperty().addListener((v, o, n) -> handleSliderChange());
		sliderMax.valueProperty().addListener((v, o, n) -> handleSliderChange());
	}
	
	protected Stage createDialog() {
		if (!isInitialized())
			initializeSliders();
		
		initializePopup();
		
		imageDataChanged(null, null, qupath.getImageData());
		
		BorderPane pane = new BorderPane();
		
		GridPane box = new GridPane();
		String blank = "      ";
		Label labelMin = new Label("Min display");
//		labelMin.setTooltip(new Tooltip("Set minimum lookup table value - double-click to edit manually"));
		Label labelMinValue = new Label(blank);
		labelMinValue.setTooltip(new Tooltip("Set minimum lookup table value - double-click to edit manually"));
		labelMinValue.textProperty().bind(Bindings.createStringBinding(() -> {
//			if (table.getSelectionModel().getSelectedItem() == null)
//				return blank;
			return String.format("%.1f", sliderMin.getValue());
		}, table.getSelectionModel().selectedItemProperty(), sliderMin.valueProperty()));
		box.add(labelMin, 0, 0);
		box.add(sliderMin, 1, 0);
		box.add(labelMinValue, 2, 0);
		
		Label labelMax = new Label("Max display");
		labelMax.setTooltip(new Tooltip("Set maximum lookup table value - double-click to edit manually"));
		Label labelMaxValue = new Label(blank);
		labelMaxValue.setTooltip(new Tooltip("Set maximum lookup table value - double-click to edit manually"));
		labelMaxValue.textProperty().bind(Bindings.createStringBinding(() -> {
//				if (table.getSelectionModel().getSelectedItem() == null)
//					return blank;
				return String.format("%.1f", sliderMax.getValue());
			}, table.getSelectionModel().selectedItemProperty(), sliderMax.valueProperty()));
		box.add(labelMax, 0, 1);
		box.add(sliderMax, 1, 1);
		box.add(labelMaxValue, 2, 1);
		box.setVgap(5);
		GridPane.setFillWidth(sliderMin, Boolean.TRUE);
		GridPane.setFillWidth(sliderMax, Boolean.TRUE);
		box.prefWidthProperty().bind(pane.widthProperty());
		box.setPadding(new Insets(5, 0, 5, 0));
		GridPane.setHgrow(sliderMin, Priority.ALWAYS);
		GridPane.setHgrow(sliderMax, Priority.ALWAYS);
		
		// In the absence of a better way, make it possible to enter display range values 
		// manually by double-clicking on the corresponding label
		// TODO: Consider a better way to do this; 
		labelMinValue.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2) {
				ChannelDisplayInfo infoVisible = getCurrentInfo();
				if (infoVisible == null)
					return;

				Double value = DisplayHelpers.showInputDialog("Display range", "Set display range minimum", (double)infoVisible.getMinDisplay());
				if (value != null && !Double.isNaN(value)) {
					sliderMin.setValue(value);
					// Update display directly if out of slider range
					if (value < sliderMin.getMin() || value > sliderMin.getMax()) {
						imageDisplay.setMinMaxDisplay(infoVisible, (float)value.floatValue(), (float)infoVisible.getMaxDisplay());
//						infoVisible.setMinDisplay(value.floatValue());
//						viewer.updateThumbnail();
						viewer.repaintEntireImage();
					}
				}
			}
		});
		labelMaxValue.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2) {
				ChannelDisplayInfo infoVisible = getCurrentInfo();
				if (infoVisible == null)
					return;

				Double value = DisplayHelpers.showInputDialog("Display range", "Set display range maximum", (double)infoVisible.getMaxDisplay());
				if (value != null && !Double.isNaN(value)) {
					sliderMax.setValue(value);
					// Update display directly if out of slider range
					if (value < sliderMax.getMin() || value > sliderMax.getMax()) {
						imageDisplay.setMinMaxDisplay(infoVisible, (float)infoVisible.getMinDisplay(), (float)value.floatValue());
//						infoVisible.setMaxDisplay(value.floatValue());
//						viewer.updateThumbnail();
						viewer.repaintEntireImage();
					}
				}
			}
		});
		
		
		Button btnAuto = new Button("Auto");
		btnAuto.setOnAction(e -> {
			if (imageDisplay == null)
				return;
//				if (histogram == null)
//					return;
////				setSliders((float)histogram.getEdgeMin(), (float)histogram.getEdgeMax());
				ChannelDisplayInfo info = getCurrentInfo();
				imageDisplay.autoSetDisplayRange(info, PathPrefs.getAutoBrightnessContrastSaturationPercent()/100.0);
				for (ChannelDisplayInfo info2 : table.getSelectionModel().getSelectedItems()) {
					imageDisplay.autoSetDisplayRange(info2, PathPrefs.getAutoBrightnessContrastSaturationPercent()/100.0);
				}
				updateSliders();
				handleSliderChange();
		});
		
		Button btnReset = new Button("Reset");
		btnReset.setOnAction(e -> {
				for (ChannelDisplayInfo info : table.getSelectionModel().getSelectedItems()) {
					imageDisplay.setMinMaxDisplay(info, info.getMinAllowed(), info.getMaxAllowed());
				}
				sliderMin.setValue(sliderMin.getMin());
				sliderMax.setValue(sliderMax.getMax());
		});
		
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Brightness & contrast");
		
		// Create color/channel display table
		table = new TableView<>(imageDisplay == null ? FXCollections.observableArrayList() : imageDisplay.availableChannels());
		table.setPlaceholder(new Text("No channels available"));
		table.addEventHandler(KeyEvent.KEY_PRESSED, new CopyTableListener());
		
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		table.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
//			boolean alreadySelected = rowIndex == table.getSelectedRow();
//	        super.changeSelection(rowIndex, columnIndex, toggle, extend);
//	        ChannelDisplayInfo info = getCurrentInfo();
//	        if (alreadySelected)
//				toggleDisplay(info);
//	        else
//	        	updateDisplay(info, true);
	        updateHistogram();
			updateSliders();
		});
		// TODO: Consider reinstating code that tries to intercept selections & push them into a valid state
//		{
//			
//			private static final long serialVersionUID = -162279729611434398L;
//
//			public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
////		        ChannelDisplayInfo infoPrevious = tableModel.getChannelForRow(rowIndex);
//				boolean alreadySelected = rowIndex == table.getSelectedRow();
//		        super.changeSelection(rowIndex, columnIndex, toggle, extend);
//		        ChannelDisplayInfo info = getCurrentInfo();
////				infoVisible = info;
////				if (info != null && infoPrevious == info && info.isAdditive()) {
//		        if (alreadySelected)
//					toggleDisplay(info);
//		        else
//		        	updateDisplay(info, true);
////				else
////					updateDisplay(info, alreadySelected);
//		        updateHistogram();
//				updateSliders();
//		    }
//			
//		};
		TableColumn<ChannelDisplayInfo, ChannelDisplayInfo> col1 = new TableColumn<>("Color");
		col1.setCellValueFactory(new Callback<CellDataFeatures<ChannelDisplayInfo, ChannelDisplayInfo>, ObservableValue<ChannelDisplayInfo>>() {
		     @Override
			public ObservableValue<ChannelDisplayInfo> call(CellDataFeatures<ChannelDisplayInfo, ChannelDisplayInfo> p) {
		         return new SimpleObjectProperty<ChannelDisplayInfo>(p.getValue());
		     }
		  });
		col1.setCellFactory(column -> {
		    return new TableCell<ChannelDisplayInfo, ChannelDisplayInfo>() {
		    	@Override
		        protected void updateItem(ChannelDisplayInfo item, boolean empty) {
		    		super.updateItem(item, empty);
		            if (item == null || empty) {
		                setText(null);
		                setGraphic(null);
		                return;
		            }
		            setText(item.getName());
					Rectangle square = new Rectangle(0, 0, 10, 10);
					Integer rgb = item.getColor();
					if (rgb == null)
						square.setFill(Color.TRANSPARENT);
					else
						square.setFill(ColorToolsFX.getCachedColor(rgb));
					setGraphic(square);
		    	}
		    };
		   });
		
		col1.setSortable(false);
		TableColumn<ChannelDisplayInfo, Boolean> col2 = new TableColumn<>("Selected");
		col2.setCellValueFactory(new Callback<CellDataFeatures<ChannelDisplayInfo, Boolean>, ObservableValue<Boolean>>() {
		     @Override
			public ObservableValue<Boolean> call(CellDataFeatures<ChannelDisplayInfo, Boolean> item) {
		    	 SimpleBooleanProperty property = new SimpleBooleanProperty(imageDisplay.selectedChannels().contains(item.getValue()));
		    	 // Remove repaint code here - now handled by table selection changes
		    	 property.addListener((v, o, n) -> {
	    			 imageDisplay.setChannelSelected(item.getValue(), n);
	    			 table.refresh();
		    		 Platform.runLater(() -> viewer.repaintEntireImage());
		    	 });
		    	 return property;
		     }
		  });
		col2.setCellFactory(column -> {
			CheckBoxTableCell<ChannelDisplayInfo, Boolean> cell = new CheckBoxTableCell<>();
			// Select cells when clicked - means a click anywhere within the row forces selection.
			// Previously, clicking within the checkbox didn't select the row.
			cell.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
				if (e.isPopupTrigger())
					return;
				int ind = cell.getIndex();
				var tableView = cell.getTableView();
				if (ind < tableView.getItems().size()) {
					if (e.isShiftDown())
						tableView.getSelectionModel().select(ind);
					else
						tableView.getSelectionModel().clearAndSelect(ind);
					var channel = cell.getTableRow().getItem();
					// Handle clicks within the cell but outside the checkbox
					if (e.getTarget() == cell && channel != null && imageDisplay != null) {
						updateDisplay(channel, !imageDisplay.selectedChannels().contains(channel));
					}
					e.consume();
				}
			});
			return cell;
		});
		col2.setSortable(false);
		col2.setEditable(true);
		col2.setResizable(false);
		
		
		// Handle color change requests when an appropriate row is double-clicked
		table.setRowFactory(tableView -> {
			TableRow<ChannelDisplayInfo> row = new TableRow<>();
			row.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2) {
					ChannelDisplayInfo info = row.getItem();
					if (info instanceof ChannelDisplayInfo.DirectServerChannelInfo) {
						ChannelDisplayInfo.DirectServerChannelInfo multiInfo = (ChannelDisplayInfo.DirectServerChannelInfo)info;
						int c = multiInfo.getChannel();
						
						Color color = ColorToolsFX.getCachedColor(multiInfo.getColor());
						picker.setValue(color);
						
						
						Dialog<ButtonType> colorDialog = new Dialog<>();
						colorDialog.setTitle("Channel color");
						colorDialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
						colorDialog.getDialogPane().setHeaderText("Select color for " + info.getName());
						StackPane colorPane = new StackPane(picker);
						colorDialog.getDialogPane().setContent(colorPane);
						Optional<ButtonType> result = colorDialog.showAndWait();
						if (result.orElseGet(() -> ButtonType.CANCEL) == ButtonType.APPLY) {
//							if (!DisplayHelpers.showMessageDialog("Choose channel color", picker))
//								return;
							Color color2 = picker.getValue();
							if (color == color2)
								return;
							
							// Update the server metadata
							var imageData = viewer.getImageData();
							int colorUpdated = ColorToolsFX.getRGB(color2);
							if (imageData != null) {
								var server = imageData.getServer();
								var metadata = server.getMetadata();
								var channels = new ArrayList<>(metadata.getChannels());
								var channel = channels.get(c);
								channels.set(c, ImageChannel.getInstance(channel.getName(), colorUpdated));
								var metadata2 = new ImageServerMetadata.Builder(metadata)
										.channels(channels).build();
								imageData.updateServerMetadata(metadata2);
							}
							
							// Update the display
							multiInfo.setLUTColor(
									(int)(color2.getRed() * 255),
									(int)(color2.getGreen() * 255),
									(int)(color2.getBlue() * 255)
									);
							
							// Add color property
							imageDisplay.saveChannelColorProperties();
							viewer.repaintEntireImage();
							updateHistogram();
							table.refresh();
						}
					}
				}
			});			
			return row;
		});
		
		
		table.getColumns().add(col1);
		table.getColumns().add(col2);
		table.setEditable(true);
		table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
		col1.prefWidthProperty().bind(table.widthProperty().subtract(col2.widthProperty()).subtract(25)); // Hack... space for a scrollbar
		
//		col2.setResizable(false);
//		col2.setMaxWidth(100);
//		col2.setPrefWidth(50);
//		col2.setResizable(false);

//		col1.prefWidthProperty().bind(table.widthProperty().multiply(0.7));
//		col2.prefWidthProperty().bind(table.widthProperty().multiply(0.2));
		
//		table.getColumnModel().getColumn(0).setCellRenderer(new ChannelCellRenderer());
//		table.getColumnModel().getColumn(1).setMaxWidth(table.getColumnModel().getColumn(1).getPreferredWidth());
		
		BorderPane panelColor = new BorderPane();
//		panelColor.setBorder(BorderFactory.createTitledBorder("Color display"));
		panelColor.setCenter(table);
		
		CheckBox cbShowGrayscale = new CheckBox("Show grayscale");
		cbShowGrayscale.selectedProperty().bindBidirectional(showGrayscale);
		cbShowGrayscale.setTooltip(new Tooltip("Show single channel with grayscale lookup table"));
		if (imageDisplay != null)
			cbShowGrayscale.setSelected(!imageDisplay.useColorLUTs());
		showGrayscale.addListener(o -> {
			if (imageDisplay == null)
				return;
			Platform.runLater(() -> viewer.repaintEntireImage());
			table.refresh();
		});
		CheckBox cbKeepDisplaySettings = new CheckBox("Keep settings");
		cbKeepDisplaySettings.selectedProperty().bindBidirectional(PathPrefs.keepDisplaySettingsProperty());
		cbKeepDisplaySettings.setTooltip(new Tooltip("Retain same display settings where possible when opening similar images"));
		
		FlowPane paneCheck = new FlowPane();
		paneCheck.getChildren().add(cbShowGrayscale);
		paneCheck.getChildren().add(cbKeepDisplaySettings);
		paneCheck.setHgap(10);
		paneCheck.setPadding(new Insets(5, 0, 0, 0));
		panelColor.setBottom(paneCheck);		
		pane.setCenter(panelColor);
		
		// Create brightness/contrast panel
		BorderPane panelSliders = new BorderPane();
		panelSliders.setTop(box);
		GridPane panelButtons = PaneToolsFX.createColumnGridControls(
				btnAuto,
				btnReset
				);
		panelSliders.setBottom(panelButtons);
		panelSliders.setPadding(new Insets(5, 0, 5, 0));
		
		BorderPane panelMinMax = new BorderPane();
//		panelMinMax.setBorder(BorderFactory.createTitledBorder("Brightness/Contrast"));
		panelMinMax.setTop(panelSliders);
		
		histogramPanel.setDrawAxes(false);
		histogramPanel.getChart().setAnimated(false);
//		histogramPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
//		panelMinMax.setCenter(histogramPanel.getChart());
		panelMinMax.setCenter(chartWrapper.getPane());
		chartWrapper.getPane().setPrefSize(200, 200);
//		histogramPanel.getChart().setPrefSize(200, 200);
//		histogramPanel.setPreferredSize(new Dimension(200, 120));
		

		pane.setBottom(panelMinMax);
		pane.setPadding(new Insets(10, 10, 10, 10));
		
		Scene scene = new Scene(pane, 350, 500);
		dialog.setScene(scene);
		dialog.setMinWidth(300);
		dialog.setMinHeight(400);
		dialog.setMaxWidth(600);
		dialog.setMaxHeight(800);
		
		updateTable();
		
		if (!table.getItems().isEmpty())
			table.getSelectionModel().select(0);
		updateDisplay(getCurrentInfo(), true);
		updateHistogram();
		updateSliders();

		return dialog;
	}
	
	
	private void updateHistogram() {
		if (table == null || !isInitialized())
			return;
		ChannelDisplayInfo infoSelected = getCurrentInfo();
		Histogram histogram = (imageDisplay == null || infoSelected == null) ? null : imageDisplay.getHistogram(infoSelected);
//		histogram = histogramMap.get(infoSelected);
		if (histogram == null) {
			histogramPanel.getHistogramData().clear();
		}
		else {
			// Any animation is slightly nicer if we can modify the current data, rather than creating a new one
			if (histogramPanel.getHistogramData().size() == 1) {
				Color color = infoSelected.getColor() == null ? ColorToolsFX.TRANSLUCENT_BLACK_FX : ColorToolsFX.getCachedColor(infoSelected.getColor());
				histogramPanel.getHistogramData().get(0).setHistogram(histogram, color);
			} else {
				HistogramData histogramData = HistogramPanelFX.createHistogramData(histogram, true, infoSelected.getColor());
				histogramData.setDoNormalizeCounts(true);
				histogramPanel.getHistogramData().setAll(histogramData);
			}
		}
		
		
		NumberAxis xAxis = (NumberAxis)histogramPanel.getChart().getXAxis();
		if (infoSelected != null && infoSelected.getMaxAllowed() == 255 && infoSelected.getMinAllowed() == 0) {
			xAxis.setAutoRanging(false);
			xAxis.setLowerBound(0);
			xAxis.setUpperBound(255);
		} else if (infoSelected != null) {
			xAxis.setAutoRanging(false);
			xAxis.setLowerBound(infoSelected.getMinAllowed());
			xAxis.setUpperBound(infoSelected.getMaxAllowed());
//			xAxis.setAutoRanging(true);
		}
		if (infoSelected != null)
			xAxis.setTickUnit(infoSelected.getMaxAllowed() - infoSelected.getMinAllowed());
		
		// Don't use the first of last count if it's an outlier
		NumberAxis yAxis = (NumberAxis)histogramPanel.getChart().getYAxis();
		if (infoSelected != null && histogram != null) {
			long maxCount = 0L;
			for (int i = 1; i < histogram.nBins()-1; i++)
				maxCount = Math.max(maxCount, histogram.getCountsForBin(i));
			if (maxCount == 0)
				maxCount = histogram.getMaxCount();
			yAxis.setAutoRanging(false);
			yAxis.setLowerBound(0);
			yAxis.setUpperBound((double)maxCount / histogram.getCountSum());
		}
		
		
		histogramPanel.getChart().getXAxis().setTickLabelsVisible(true);
		histogramPanel.getChart().getXAxis().setLabel("Pixel value");
		histogramPanel.getChart().getYAxis().setTickLabelsVisible(true);
//		histogramPanel.getChart().getYAxis().setLabel("Frequency");
		
		GridPane pane = new GridPane();
		pane.setHgap(4);
		pane.setVgap(2);
		int r = 0;
		// TODO: Show min & max somewhere - but beware of the need to stay updated!
//		if (infoSelected != null) {
//			pane.add(new Label("Min display"), 0, r);
//			pane.add(new Label(df.format(infoSelected.getMinDisplay())), 1, r);
//			r++;
//			pane.add(new Label("Max display"), 0, r);
//			pane.add(new Label(df.format(infoSelected.getMaxDisplay())), 1, r);
//			r++;
//		}
		if (histogram != null) {
			pane.add(new Label("Min"), 0, r);
			pane.add(new Label(df.format(histogram.getMinValue())), 1, r);
			r++;
			pane.add(new Label("Max"), 0, r);
			pane.add(new Label(df.format(histogram.getMaxValue())), 1, r);
			r++;
			pane.add(new Label("Mean"), 0, r);
			pane.add(new Label(df.format(histogram.getMeanValue())), 1, r);
			r++;
			pane.add(new Label("Std.dev"), 0, r);
			pane.add(new Label(df.format(histogram.getStdDev())), 1, r);
			r++;
		}
		chartTooltip.setGraphic(pane);
		
		if (r == 0)
			Tooltip.uninstall(histogramPanel.getChart(), chartTooltip);
		else
			Tooltip.install(histogramPanel.getChart(), chartTooltip);
		
////	case 0: return columnIndex == 0 ? "Min display" : df.format(channel.getMinDisplay());
////	case 1: return columnIndex == 0 ? "Max display" : df.format(channel.getMaxDisplay());
////	case 2: return columnIndex == 0 ? "Min" : df.format(histogram.getMinValue());
////	case 3: return columnIndex == 0 ? "Max" : df.format(histogram.getMaxValue());
////	case 4: return columnIndex == 0 ? "Mean" : df.format(histogram.getMeanValue());
////	case 5: return columnIndex == 0 ? "Std.dev" : df.format(histogram.getStdDev());
//		
//		histogramTableModel.setHistogram(infoSelected, histogram);
	}
	
	
	void updateDisplay(ChannelDisplayInfo channel, boolean selected) {
		if (imageDisplay == null)
			return;
		if (channel == null) {
//			imageDisplay.setChannelSelected(null, selected);
		} else
			imageDisplay.setChannelSelected(channel, selected);
		
		// If the table isn't null, we are displaying something
		if (table != null) {
			updateHistogram();
	
//			// Update current min & max
//			ChannelDisplayInfo info = getCurrentInfo();
//			if (info != null) {
//				Histogram histogram = imageDisplay.getHistogram(info);
//				if (histogram != null) {
//					float minCurrent = (float)Math.min(info.getMinAllowed(), histogram.getEdgeMin());
//					float maxCurrent = (float)Math.max(info.getMaxAllowed(), histogram.getEdgeMax());
//					info.setMinMaxAllowed(minCurrent, maxCurrent);
//				}
//			}
			table.refresh();
		}
//		viewer.updateThumbnail();
		viewer.repaintEntireImage();
	}
	
	
	void toggleDisplay(ChannelDisplayInfo channel) {
		if (channel == null)
			updateDisplay(null, true);
		else
			updateDisplay(channel, !imageDisplay.selectedChannels().contains(channel));
	}
	
	
	ChannelDisplayInfo getCurrentInfo() {
		ChannelDisplayInfo info = table.getSelectionModel().getSelectedItem();
		// Default to first, if we don't have a selection
		if (info == null && !table.getItems().isEmpty())
			info = table.getItems().get(0);
		return info;
	}
	
		
	@Override
	public void run() {
		if (dialog == null)
			dialog = createDialog();
		dialog.show();
	}

	
	void handleSliderChange() {
		if (slidersUpdating)
			return;
		ChannelDisplayInfo infoVisible = getCurrentInfo();
		if (infoVisible == null)
			return;
		double minValue = sliderMin.getValue();
		double maxValue = sliderMax.getValue();
				
		imageDisplay.setMinMaxDisplay(infoVisible, (float)minValue, (float)maxValue);
		
		// Avoid displaying -0... which looks weird
		if (Math.abs(Math.round(minValue * 100)) == 0)
			minValue = 0;
		if (Math.abs(Math.round(maxValue * 100)) == 0)
			maxValue = 0;
		
//		viewer.updateThumbnail();
		viewer.repaintEntireImage();
		
//		histogramPanel.setVerticalLines(new double[]{infoVisible.getMinDisplay(), infoVisible.getMaxDisplay()}, ColorToolsFX.TRANSLUCENT_BLACK_FX);
	}
	
	
	private void updateSliders() {
		if (!isInitialized())
			return;
		ChannelDisplayInfo infoVisible = getCurrentInfo();
		if (infoVisible == null) {
			sliderMin.setDisable(true);
			sliderMax.setDisable(true);
			return;
		}
		float range = infoVisible.getMaxAllowed() - infoVisible.getMinAllowed();
		int n = (int)range;
		boolean is8Bit = range == 255 && infoVisible.getMinAllowed() == 0 && infoVisible.getMaxAllowed() == 255;
		if (is8Bit)
			n = 256;
		else if (n <= 20)
			n = (int)(range / .001);
		else if (n <= 200)
			n = (int)(range / .01);
		slidersUpdating = true;
		
		sliderMin.setMin(infoVisible.getMinAllowed());
		sliderMin.setMax(infoVisible.getMaxAllowed());
		sliderMin.setValue(infoVisible.getMinDisplay());
		sliderMax.setMin(infoVisible.getMinAllowed());
		sliderMax.setMax(infoVisible.getMaxAllowed());
		sliderMax.setValue(infoVisible.getMaxDisplay());
		
		if (is8Bit) {
			sliderMin.setMajorTickUnit(1);
			sliderMax.setMajorTickUnit(1);
			sliderMin.setMinorTickCount(n);
			sliderMax.setMinorTickCount(n);
		} else {
			sliderMin.setMajorTickUnit(1);
			sliderMax.setMajorTickUnit(1);
			sliderMin.setMinorTickCount(n);
			sliderMax.setMinorTickCount(n);
		}
		slidersUpdating = false;
		sliderMin.setDisable(false);
		sliderMax.setDisable(false);
		
		chartWrapper.getThresholds().clear();
		Color color = Color.rgb(0, 0, 0, 0.2);
		chartWrapper.addThreshold(sliderMin.valueProperty(), color);
		chartWrapper.addThreshold(sliderMax.valueProperty(), color);
		chartWrapper.setIsInteractive(true);
//		chartWrapper.getThresholds().setAll(sliderMin.valueProperty(), sliderMax.valueProperty());
		
//		histogramPanel.setVerticalLines(new double[]{infoVisible.getMinDisplay(), infoVisible.getMaxDisplay()}, ColorToolsFX.TRANSLUCENT_BLACK_FX);
	}
	

	/**
	 * Popup menu to toggle additive channels on/off.
	 */
	private void initializePopup() {
		popup = new ContextMenu();
		
		MenuItem miTurnOn = new MenuItem("Show channels");
		miTurnOn.setOnAction(e -> setTableSelectedChannels(true));
		miTurnOn.disableProperty().bind(showGrayscale);
		MenuItem miTurnOff = new MenuItem("Hide channels");
		miTurnOff.setOnAction(e -> setTableSelectedChannels(false));
		miTurnOff.disableProperty().bind(showGrayscale);
		MenuItem miToggle = new MenuItem("Toggle channels");
		miToggle.setOnAction(e -> toggleTableSelectedChannels());
		miToggle.disableProperty().bind(showGrayscale);
		
		popup.getItems().addAll(
				miTurnOn,
				miTurnOff,
				miToggle
				);
	}
	
	/**
	 * Request that channels currently selected (highlighted) in the table have their 
	 * selected status changed accordingly.  This allows multiple channels to be turned on/off 
	 * in one step.
	 * @param showChannels
	 * 
	 * @see #toggleTableSelectedChannels()
	 */
	private void setTableSelectedChannels(boolean showChannels) {
		if (!isInitialized())
			return;
		var selected = table.getSelectionModel().getSelectedItems();
		for (ChannelDisplayInfo info : table.getSelectionModel().getSelectedItems()) {
			imageDisplay.setChannelSelected(info, showChannels);
		}
		table.refresh();
		if (viewer != null) {
//			viewer.updateThumbnail();
			viewer.repaintEntireImage();
		}
	}

	/**
	 * Request that channels currently selected (highlighted) in the table have their 
	 * selected status inverted.  This allows multiple channels to be turned on/off 
	 * in one step.
	 * 
	 * @see #setTableSelectedChannels(boolean)
	 */
	private void toggleTableSelectedChannels() {
		if (!isInitialized())
			return;
		Set<ChannelDisplayInfo> selected = new HashSet<>(imageDisplay.selectedChannels());
		for (ChannelDisplayInfo info : table.getSelectionModel().getSelectedItems()) {
			imageDisplay.setChannelSelected(info, !selected.contains(info));
		}
		table.refresh();
		if (viewer != null) {
//			viewer.updateThumbnail();
			viewer.repaintEntireImage();
		}
	}
	
	
	
	public void updateTable() {
		if (!isInitialized())
			return;

		// Update table appearance (maybe colors changed etc.)
		if (imageDisplay == null) {
			table.setItems(FXCollections.emptyObservableList());
		} else {
			table.setItems(imageDisplay.availableChannels());
			showGrayscale.bindBidirectional(imageDisplay.useGrayscaleLutProperty());
		}
		table.refresh();
		
		// If all entries are additive, allow bulk toggling by right-click
		int n = table.getItems().size();
		if (n > 0 || n == table.getItems().stream().filter(c -> c.isAdditive()).count()) {
			table.setContextMenu(popup);
		} else {
			table.setContextMenu(null);
		}
	}


	@Override
	public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		// TODO: Consider different viewers but same ImageData
		if (imageDataOld == imageDataNew)
			return;
				
		QuPathViewer viewerNew = qupath.getViewer();
		if (viewer != viewerNew) {
			if (viewer != null)
				viewer.getView().removeEventHandler(KeyEvent.KEY_TYPED, keyListener);
			if (viewerNew != null)
				viewerNew.getView().addEventHandler(KeyEvent.KEY_TYPED, keyListener);
			viewer = viewerNew;
		}
		
		if (imageDisplay != null) {
			showGrayscale.unbindBidirectional(imageDisplay.useGrayscaleLutProperty());
			imageDisplay.useGrayscaleLutProperty().unbindBidirectional(showGrayscale);
		}
		
		imageDisplay = viewer == null ? null : viewer.getImageDisplay();
		
		if (imageDataOld != null)
			imageDataOld.removePropertyChangeListener(this);
		if (imageDataNew != null)
			imageDataNew.addPropertyChangeListener(this);
		
		updateTable();
		
//		updateHistogramMap();
		// Update if we aren't currently initializing
		updateHistogram();
		updateSliders();
	}
	
	
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals("qupath.lib.display.ImageDisplay"))
			return;
		
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> propertyChange(evt));
			return;
		}
		// Update display - we might have changed stain vectors or server metadata in some major way
		if (evt.getPropertyName().equals("serverMetadata") || 
				!((evt.getSource() instanceof ImageData<?>) && evt.getPropertyName().equals("stains")))
			imageDisplay.updateChannelOptions(false);
		
		updateTable();

		updateSliders();
		
		if (viewer != null) {
//			viewer.updateThumbnail();
			viewer.repaintEntireImage();
		}
	}
	
	
	
	class BrightnessContrastKeyListener implements EventHandler<KeyEvent> {

		@Override
		public void handle(KeyEvent event) {
			String character = event.getCharacter();
			if (character != null && character.length() > 0) {
				int c = (int)event.getCharacter().charAt(0) - '0';
				if (c >= 1 && c <= Math.min(9, imageDisplay.availableChannels().size())) {
					if (table != null)
						table.getSelectionModel().select(c-1);
					toggleDisplay(imageDisplay.availableChannels().get(c-1));
					event.consume();
				}
			}
		}
		
	}
	
	
	class CopyTableListener implements EventHandler<KeyEvent> {
		
		private KeyCombination copyCombo = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
		private KeyCombination pasteCombo = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);

		private KeyCombination spaceCombo = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.SHORTCUT_ANY);
		private KeyCombination enterCombo = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_ANY);

		@Override
		public void handle(KeyEvent event) {
			if (copyCombo.match(event)) {
				doCopy(event);
				event.consume();
			} else if (pasteCombo.match(event)) {
				doPaste(event);
				event.consume();
			} else if (spaceCombo.match(event) || enterCombo.match(event)) {
				var channel = table.getSelectionModel().getSelectedItem();
				if (imageDisplay != null && channel != null) {
					updateDisplay(channel, !imageDisplay.selectedChannels().contains(channel));
				}
				event.consume();
			}
		}
		
		/**
		 * Copy the channel names to the clipboard
		 * @param event
		 */
		void doCopy(KeyEvent event) {
			var names = table.getSelectionModel().getSelectedItems().stream().map(c -> c.getName()).collect(Collectors.toList());
			var clipboard = Clipboard.getSystemClipboard();
			var content = new ClipboardContent();
			content.putString(String.join(System.lineSeparator(), names));
			clipboard.setContent(content);
		}
		
		void doPaste(KeyEvent event) {
			ImageData<BufferedImage> imageData = viewer.getImageData();
			if (imageData == null)
				return;
			ImageServer<BufferedImage> server = imageData.getServer();
			
			var clipboard = Clipboard.getSystemClipboard();
			var string = clipboard.getString();
			if (string == null)
				return;
			var selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
			if (selected.isEmpty())
				return;
			
			if (server.isRGB()) {
				logger.warn("Cannot set channel names for RGB images");
			}
			var names = string.lines().collect(Collectors.toList());
			if (selected.size() != names.size()) {
				DisplayHelpers.showErrorNotification("Paste channel names", "The number of lines on the clipboard doesn't match the number of channel names to replace!");
				return;
			}
			if (names.size() != new HashSet<>(names).size()) {
				DisplayHelpers.showErrorNotification("Paste channel names", "Channel names should be unique!");
				return;
			}
			var metadata = server.getMetadata();
			var channels = new ArrayList<>(metadata.getChannels());
			List<String> changes = new ArrayList<>();
			for (int i = 0; i < selected.size(); i++) {
				if (!(selected.get(i) instanceof DirectServerChannelInfo))
					continue;
				var info = (DirectServerChannelInfo)selected.get(i);
				if (info.getName().equals(names.get(i)))
					continue;
				int c = info.getChannel();
				var oldChannel = channels.get(c);
				var newChannel = ImageChannel.getInstance(names.get(i), channels.get(c).getColor());
				changes.add(oldChannel.getName() + " -> " + newChannel.getName());
				channels.set(c, newChannel);
			}
			List<String> allNewNames = channels.stream().map(c -> c.getName()).collect(Collectors.toList());
			Set<String> allNewNamesSet = new LinkedHashSet<>(allNewNames);
			if (allNewNames.size() != allNewNamesSet.size()) {
				DisplayHelpers.showErrorMessage("Channel", "Cannot paste channels - names would not be unique \n(check log for details)");
				for (String n : allNewNamesSet)
					allNewNames.remove(n);
				logger.warn("Requested channel names would result in duplicates: " + String.join(", ", allNewNames));
				return;
			}
			if (changes.isEmpty()) {
				logger.debug("Channel names pasted, but no changes to make");
			}
			else {
				var dialog = new Dialog<ButtonType>();
				dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
				dialog.setTitle("Channels");
				dialog.setHeaderText("Confirm new channel names?");
				dialog.getDialogPane().setContent(new TextArea(String.join("\n", changes)));
				if (dialog.showAndWait().orElseGet(() -> ButtonType.CANCEL) == ButtonType.APPLY) {
					var newMetadata = new ImageServerMetadata.Builder(metadata)
							.channels(channels).build();
					imageData.updateServerMetadata(newMetadata);
//					table.refresh();
				}
			}
		}
		
	}
	

}