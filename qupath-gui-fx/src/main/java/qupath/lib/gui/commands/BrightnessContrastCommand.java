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
import java.util.Optional;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
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
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.gui.plots.HistogramPanelFX;
import qupath.lib.gui.plots.HistogramPanelFX.ThresholdedChartWrapper;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;

/**
 * Command to show a Brightness/Contrast dialog to adjust the image display.
 * 
 * @author Pete Bankhead
 *
 */
public class BrightnessContrastCommand implements PathCommand, ImageDataChangeListener<BufferedImage>, PropertyChangeListener {
	
	private static DecimalFormat df = new DecimalFormat("#.###");
	
	/**
	 * Controls proportion of saturated pixels to apply when automatically setting brightness/contrast.
	 */
	private float autoBrightnessContrastSaturation = 0.01f;
	
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
		
		imageDataChanged(null, null, qupath.getImageData());
		
		BorderPane pane = new BorderPane();
		
		GridPane box = new GridPane();
		Label labelMin = new Label("Min display");
		labelMin.setTooltip(new Tooltip("Set minimum lookup table value - double-click to edit manually"));
		box.add(labelMin, 0, 0);
		box.add(sliderMin, 1, 0);
		Label labelMax = new Label("Max display");
		labelMax.setTooltip(new Tooltip("Set maximum lookup table value - double-click to edit manually"));
		box.add(labelMax, 0, 1);
		box.add(sliderMax, 1, 1);
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
		labelMin.setOnMouseClicked(e -> {
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
						viewer.updateThumbnail();
						viewer.repaintEntireImage();
					}
				}
			}
		});
		labelMax.setOnMouseClicked(e -> {
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
						viewer.updateThumbnail();
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
				imageDisplay.autoSetDisplayRange(info, autoBrightnessContrastSaturation);
				updateSliders();
				handleSliderChange();
		});
		
		Button btnReset = new Button("Reset");
		btnReset.setOnAction(e -> {
				sliderMin.setValue(sliderMin.getMin());
				sliderMax.setValue(sliderMax.getMax());
		});
		
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Brightness & contrast");
		
		// Create color/channel display table
		table = new TableView<>();
		table.setPlaceholder(new Text("No channels available"));
		
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
		            if (item instanceof ChannelDisplayInfo.MultiChannelInfo && imageDisplay != null && imageDisplay.getImageData() != null) {
		            	ChannelDisplayInfo.MultiChannelInfo multiInfo = (ChannelDisplayInfo.MultiChannelInfo)item;
		            	setText(multiInfo.getName(imageDisplay.getImageData()));
		            } else
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
		    	 SimpleBooleanProperty property = new SimpleBooleanProperty(imageDisplay.getSelectedChannels().contains(item.getValue()));
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
			cell.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
				int ind = cell.getIndex();
				if (ind < column.getTableView().getItems().size())
					column.getTableView().getSelectionModel().select(ind);
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
					if (info instanceof ChannelDisplayInfo.MultiChannelInfo) {
						ChannelDisplayInfo.MultiChannelInfo multiInfo = (ChannelDisplayInfo.MultiChannelInfo)info;
						Color color = ColorToolsFX.getCachedColor(multiInfo.getColor());
						picker.setValue(color);
						
						
						Dialog<ButtonType> colorDialog = new Dialog<>();
						colorDialog.setTitle("Channel color");
						colorDialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
						colorDialog.getDialogPane().setHeaderText("Select color for " + info.getName());
						StackPane colorPane = new StackPane(picker);
						colorDialog.getDialogPane().setContent(colorPane);
						Optional<ButtonType> result = colorDialog.showAndWait();
						if (result.isPresent() && result.get() == ButtonType.APPLY) {
//							if (!DisplayHelpers.showMessageDialog("Choose channel color", picker))
//								return;
							Color color2 = picker.getValue();
							if (color == color2)
								return;
							Integer channelRGB = ColorToolsFX.getRGB(color2);
							multiInfo.setLUTColor(channelRGB);
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
		if (imageDisplay != null)
			cbShowGrayscale.setSelected(!imageDisplay.useColorLUTs());
		cbShowGrayscale.setOnAction(e -> {
			if (imageDisplay == null)
				return;
				imageDisplay.setUseColorLUTs(!imageDisplay.useColorLUTs());
				viewer.updateThumbnail();
				viewer.repaintEntireImage();
				table.refresh();
		});
		FlowPane paneCheck = new FlowPane();
		paneCheck.getChildren().add(cbShowGrayscale);
		paneCheck.setPadding(new Insets(5, 0, 0, 0));
		panelColor.setBottom(paneCheck);		
		pane.setCenter(panelColor);
		
		// Create brightness/contrast panel
		BorderPane panelSliders = new BorderPane();
		panelSliders.setTop(box);
		GridPane panelButtons = PanelToolsFX.createColumnGridControls(
				btnAuto,
				btnReset
				);
		panelSliders.setBottom(panelButtons);
		panelSliders.setPadding(new Insets(5, 0, 5, 0));
		
		BorderPane panelMinMax = new BorderPane();
//		panelMinMax.setBorder(BorderFactory.createTitledBorder("Brightness/Contrast"));
		panelMinMax.setTop(panelSliders);
		
		histogramPanel.setDrawAxes(false);
//		histogramPanel.getChart().setAnimated(false);
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
			} else
				histogramPanel.getHistogramData().setAll(HistogramPanelFX.createHistogramData(histogram, true, infoSelected.getColor()));
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
		if (channel == null)
			imageDisplay.setChannelSelected(null, selected);
		else
			imageDisplay.setChannelSelected(channel, selected);
		
		// If the table isn't null, we are displaying something
		if (table != null) {
			updateHistogram();
	
			// Update current min & max
			ChannelDisplayInfo info = getCurrentInfo();
			if (info != null) {
				Histogram histogram = imageDisplay.getHistogram(info);
				if (histogram != null) {
					float minCurrent = (float)Math.min(info.getMinAllowed(), histogram.getEdgeMin());
					float maxCurrent = (float)Math.max(info.getMaxAllowed(), histogram.getEdgeMax());
					info.setMinMaxAllowed(minCurrent, maxCurrent);
				}
			}
			table.refresh();
		}
//		viewer.updateThumbnail();
		viewer.repaintEntireImage();
	}
	
	
	void toggleDisplay(ChannelDisplayInfo channel) {
		if (channel == null)
			updateDisplay(null, true);
		else
			updateDisplay(channel, !imageDisplay.getSelectedChannels().contains(channel));
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
		
		viewer.updateThumbnail();
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
	

	
	
	
	
	public void updateTable() {
		if (!isInitialized())
			return;
		// Reset any buffers for images currently open (used to cache floating point values)
		for (ChannelDisplayInfo info :table.getItems())
			info.resetBuffers();
		// Clear the table
		if (imageDisplay == null) {
			table.getItems().clear();
		}
		else if (table.getItems().equals(imageDisplay.getAvailableChannels()))
			table.refresh();
		else
			table.getItems().setAll(imageDisplay.getAvailableChannels());
	}


	@Override
	public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		// TODO: Consider different viewers but same ImageData
		if (imageDataOld == imageDataNew)
			return;
		
//		updateTable();
		
		QuPathViewer viewerNew = qupath.getViewer();
		if (viewer != viewerNew) {
			if (viewer != null)
				viewer.getView().removeEventHandler(KeyEvent.KEY_TYPED, keyListener);
			if (viewerNew != null)
				viewerNew.getView().addEventHandler(KeyEvent.KEY_TYPED, keyListener);
			viewer = viewerNew;
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
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> propertyChange(evt));
			return;
		}
		if (!((evt.getSource() instanceof ImageData<?>) && evt.getPropertyName().equals("stains")))
			imageDisplay.updateChannelOptions(false);
		
		updateTable();

		updateSliders();
		
		if (viewer != null) {
			viewer.updateThumbnail();
			viewer.repaintEntireImage();
		}
	}
	
	
	
	class BrightnessContrastKeyListener implements EventHandler<KeyEvent> {

		@Override
		public void handle(KeyEvent event) {
			String character = event.getCharacter();
			if (character != null && character.length() > 0) {
				int c = (int)event.getCharacter().charAt(0) - '0';
				if (c >= 1 && c <= Math.min(9, imageDisplay.getAvailableChannels().size())) {
					if (table != null)
						table.getSelectionModel().select(c-1);
					toggleDisplay(imageDisplay.getAvailableChannels().get(c-1));
					event.consume();
				}
			}
		}
		
	}
	

}