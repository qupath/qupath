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

package qupath.lib.gui.panels;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import qupath.lib.classifiers.PathClassificationLabellingHelper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.MeasurementMapper;
import qupath.lib.gui.helpers.MeasurementMapper.ColorMapper;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;


/**
 * Component used for displaying measurement maps, whereby detection objects are recolored 
 * according to measurement values.
 * 
 * @author Pete Bankhead
 *
 */
// TODO: Revise MeasurementMapPanel whenever multiple viewers are present
public class MeasurementMapPanel {
	
	private final static Logger logger = LoggerFactory.getLogger(MeasurementMapPanel.class);
	
	private QuPathGUI qupath;
	
	private Map<String, MeasurementMapper> mapperMap = new HashMap<>();

	private BorderPane pane = new BorderPane();
	
	private ObservableList<String> baseList = FXCollections.observableArrayList();
	private FilteredList<String> filteredList = new FilteredList<>(baseList);
	private ListView<String> listMeasurements = new ListView<>(filteredList);
	
	private int sliderRange = 200;
	private Slider sliderMin = new Slider(0, sliderRange, 0);
	private Slider sliderMax = new Slider(0, sliderRange, sliderRange);
	
	// For not painting values outside the mapper range
	private CheckBox cbExcludeOutside = new CheckBox("Exclude outside range");
	
	private Canvas colorMapperKey;
	private Image colorMapperKeyImage;
	
	private Label labelMin = new Label("");
	private Label labelMax = new Label("");
	
	private MeasurementMapper mapper = null;
		
	public MeasurementMapPanel(final QuPathGUI qupath) {
		this.qupath = qupath;
		
		updateMeasurements();
		
		cbExcludeOutside.setSelected(false);
		
		final ToggleButton toggleShowMap = new ToggleButton("Show map");
		toggleShowMap.setTooltip(new Tooltip("Show/hide the map"));
		toggleShowMap.setSelected(true);
		toggleShowMap.setOnAction(e -> {
			if (toggleShowMap.isSelected())
				showMap();
			else
				hideMap();
		});
		
		listMeasurements.getSelectionModel().selectedItemProperty().addListener((e, f, g) -> {
			if (toggleShowMap.isSelected())
					showMap();
			}			
		);
		listMeasurements.setTooltip(new Tooltip("List of available measurements"));
		
		pane.setCenter(listMeasurements);
		
		cbExcludeOutside.selectedProperty().addListener((e, f, g) -> updateDisplay());
		
		sliderMin.valueProperty().addListener((e, f, g) -> updateDisplay());
		sliderMax.valueProperty().addListener((e, f, g) -> updateDisplay());
		
		sliderMin.setTooltip(new Tooltip("Min display value"));
		sliderMax.setTooltip(new Tooltip("Max display value"));
		
		BorderPane panelLabels = new BorderPane();
		labelMin.setTextAlignment(TextAlignment.RIGHT);
		labelMin.setTextAlignment(TextAlignment.LEFT);
//		labelMin.setOnMouseClicked(e -> {
//			if (e.getClickCount() == 2) {
//				String input = DisplayHelpers.showInputDialog("Set minimum value", "Enter the minimum value for the measurement map", Double.toString(sliderMin.getValue()));
//				if (input == null || input.trim().isEmpty())
//					return;
//				try {
//					double val = Double.parseDouble(input);
//					sliderMin.setValue(val);
//				} catch (NumberFormatException ex) {
//					logger.error("Unable to parse number from {}", input);
//				}
//			}
//		});
		panelLabels.setLeft(labelMin);
		panelLabels.setRight(labelMax);
		
		Button btnRefresh = new Button("Update map");
		btnRefresh.setTooltip(new Tooltip("Update map data & recompute the min/max settings used to display colors"));
		btnRefresh.setOnAction(e -> {
			updateMeasurements();
			mapperMap.clear();
			if (toggleShowMap.isSelected())
				showMap();
		}
				);

		double canvasHeight = 10;
		colorMapperKey = new Canvas() {
			@Override
			public double minHeight(double width) {
			    return canvasHeight;
			}

			@Override
			public double maxHeight(double width) {
			    return canvasHeight;
			}

			@Override
			public double prefHeight(double width) {
			    return canvasHeight;
			}
			
			@Override
			public double minWidth(double width) {
				return 0;
			}
			
			@Override
			public double maxWidth(double width) {
				return Double.MAX_VALUE;
			}
			
			@Override
			public boolean isResizable() {
				return true;
			}
			
			@Override
			public void resize(double width, double height)	{
			    super.setWidth(width);
			    super.setHeight(height);
			    updateColorMapperKey();
			}
		};
		Tooltip.install(colorMapperKey, new Tooltip("Measurement map key"));
		
		// Filter to reduce visible measurements
		TextField tfFilter = new TextField();
		tfFilter.setTooltip(new Tooltip("Enter text to filter measurement list"));
		tfFilter.textProperty().addListener((v, o, n) -> {
			String val = n.trim().toLowerCase();			
			filteredList.setPredicate(s -> {
				if (val.isEmpty())
					return true;
				return s.toLowerCase().contains(val);
			});
		});
		BorderPane paneFilter = new BorderPane();
		paneFilter.setPadding(new Insets(5, 0, 10, 0));
		paneFilter.setCenter(tfFilter);

		VBox vbButtons = new VBox(
				paneFilter,
				sliderMin,
				sliderMax,
				colorMapperKey,
				panelLabels,
				btnRefresh,
				toggleShowMap
				);
		
		vbButtons.setSpacing(2);
		
		sliderMin.setMaxWidth(Double.MAX_VALUE);
		sliderMax.setMaxWidth(Double.MAX_VALUE);
		panelLabels.setMaxWidth(Double.MAX_VALUE);
		btnRefresh.setMaxWidth(Double.MAX_VALUE);
		toggleShowMap.setMaxWidth(Double.MAX_VALUE);
		vbButtons.setFillWidth(true);
		
//		GridPane.setHgrow(colorMapperKey, Priority.ALWAYS);
		
		pane.setBottom(vbButtons);

		pane.setPadding(new Insets(10, 10, 10, 10));
		
		
		colorMapperKey.setOnMouseClicked(e -> promptForMap(e));
	}
	
	void promptForMap(MouseEvent e) {
		Map<String, ColorMapper> mappers = MeasurementMapper.getAvailableColorMappers();
		if (mappers.isEmpty() && mappers.size() == 1)
			return;
		
		ColorMapper currentMapper = mapper.getColorMapper();
		ContextMenu menu = new ContextMenu();
		ToggleGroup group = new ToggleGroup();
		for (var entry : mappers.entrySet()) {
			RadioMenuItem item = new RadioMenuItem(entry.getKey());
			group.getToggles().add(item);
			item.setSelected(currentMapper == entry.getValue());
			item.selectedProperty().addListener((v, o, n) -> {
				if (n) {
					mapper.setColorMapper(entry.getValue());
					showMap();
				}
			});
			menu.getItems().add(item);
		}

		menu.show(colorMapperKey, e.getScreenX(), e.getScreenY());
	}
	
	public Pane getPane() {
		return pane;
	}
	
	
	public void showMap() {
		String measurement = listMeasurements.getSelectionModel().getSelectedItem();
		QuPathViewer viewer = qupath.getViewer();
		if (viewer == null || measurement == null)
			return;
		// Reuse mappers if we can
		mapper = mapperMap.get(measurement);
		if (mapper == null) {
			mapper = new MeasurementMapper(measurement, viewer.getHierarchy().getObjects(null, null));
			if (mapper.isValid())
				mapperMap.put(measurement, mapper);
		}
		colorMapperKeyImage = createPanelKey(mapper.getColorMapper());
		updateColorMapperKey();
		mapper.setExcludeOutsideRange(cbExcludeOutside.isSelected());
		viewer.forceOverlayUpdate();
		updateMapperBrightnessContrast();
		OverlayOptions overlayOptions = viewer.getOverlayOptions();
		if (overlayOptions != null)
			overlayOptions.setMeasurementMapper(mapper);		
	}
	
	
	private void updateColorMapperKey() {
		GraphicsContext gc = colorMapperKey.getGraphicsContext2D();
		double w = colorMapperKey.getWidth();
		double h = colorMapperKey.getHeight();
		gc.clearRect(0, 0, w, h);
		if (colorMapperKeyImage != null)
			gc.drawImage(colorMapperKeyImage,
					0, 0, colorMapperKeyImage.getWidth(), colorMapperKeyImage.getHeight(),
					0, 0, w, h);
	}
	
	
	public void updateMapperBrightnessContrast() {
		if (mapper == null)
			return;
		double minValueSlider = (double)sliderMin.getValue() / sliderRange;
		double maxValueSlider = (double)sliderMax.getValue() / sliderRange;
		
		double minValue = mapper.getDataMinValue() + (mapper.getDataMaxValue() - mapper.getDataMinValue()) * minValueSlider;
		double maxValue = mapper.getDataMinValue() + (mapper.getDataMaxValue() - mapper.getDataMinValue()) * maxValueSlider;
		
		labelMin.setText(String.format("%.2f", minValue));
		labelMax.setText(String.format("%.2f", maxValue));
		
		mapper.setDisplayMinValue(minValue);
		mapper.setDisplayMaxValue(maxValue);
		mapper.setExcludeOutsideRange(cbExcludeOutside.isSelected());
	}
	
	
	
	public void hideMap() {
		QuPathViewer viewer = qupath.getViewer();
		if (viewer != null) {
			OverlayOptions overlayOptions = viewer.getOverlayOptions();
			if (overlayOptions != null)
				overlayOptions.resetMeasurementMapper();
//			viewer.resetMeasurementMapper();
		}
	}
	
	
	public void updateMeasurements() {
//		this.measurements.clear();
//		this.measurements.addAll(measurements);
		
		QuPathViewer viewer = qupath.getViewer();
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null) {
			baseList.clear();
			return;
		}
		
		Collection<PathObject> pathObjects = hierarchy.getDetectionObjects();
		Set<String> measurements = PathClassificationLabellingHelper.getAvailableFeatures(pathObjects);
		for (PathObject pathObject : pathObjects) {
			if (!Double.isNaN(pathObject.getClassProbability())) {
				measurements.add("Class probability");
				break;
			}
		}
		
		// Apply any changes
		baseList.setAll(measurements);
	}
	
	
	
	public void updateDisplay() {
		QuPathViewer viewer = qupath.getViewer();
		updateMapperBrightnessContrast();
		viewer.forceOverlayUpdate();
//		viewer.repaint();
	}
	
	
	
	static Image createPanelKey(final ColorMapper colorMapper) {
		BufferedImage imgKey = new BufferedImage(255, 10, BufferedImage.TYPE_INT_ARGB);
		if (colorMapper != null) {
			for (int i = 0; i < imgKey.getWidth(); i++) {
				Integer rgb = colorMapper.getColor(i, 0, 254);
				for (int j = 0; j < imgKey.getHeight(); j++) {
					imgKey.setRGB(i, j, rgb);
				}
			}
		}
		Image img = SwingFXUtils.toFXImage(imgKey, null);
		return img;
	}

}
