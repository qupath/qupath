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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.MeasurementMapper;
import qupath.lib.gui.helpers.MeasurementMapper.ColorMapper;
import qupath.lib.gui.prefs.PathPrefs;
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
	
	private ObservableList<ColorMapper> colorMappers = FXCollections.observableArrayList(MeasurementMapper.loadColorMappers());
	private ObservableValue<ColorMapper> selectedColorMapper;
	
	private ObservableList<String> baseList = FXCollections.observableArrayList();
	private FilteredList<String> filteredList = new FilteredList<>(baseList);
	private ListView<String> listMeasurements = new ListView<>(filteredList);
	
	private Slider sliderMin = new Slider(0, 1, 0);
	private Slider sliderMax = new Slider(0, 1, 1);
	
	// For not painting values outside the mapper range
	private CheckBox cbExcludeOutside = new CheckBox("Exclude outside range");
	
	private Canvas colorMapperKey;
	private Image colorMapperKeyImage;
	
	private Label labelMin = new Label("");
	private Label labelMax = new Label("");
	
	private MeasurementMapper mapper = null;
	
	private BooleanProperty showMap;
	private boolean updatingSliders = false;
	
	
	private static StringProperty preferredMapperName = PathPrefs.createPersistentPreference("measurementMapperLUT", "viridis");
	
		
	public MeasurementMapPanel(final QuPathGUI qupath) {
		this.qupath = qupath;
		
		updateMeasurements();
		
		cbExcludeOutside.setSelected(false);
		
		final ToggleButton toggleShowMap = new ToggleButton("Show map");
		toggleShowMap.setTooltip(new Tooltip("Show/hide the map"));
		toggleShowMap.setSelected(true);
		showMap = toggleShowMap.selectedProperty();
		showMap.addListener((v, o, n) -> updateMap());
		
		listMeasurements.getSelectionModel().selectedItemProperty().addListener((e, f, g) -> updateMap());
		listMeasurements.setTooltip(new Tooltip("List of available measurements"));
		
		pane.setCenter(listMeasurements);
		
		cbExcludeOutside.selectedProperty().addListener((e, f, g) -> updateDisplay());
		
		sliderMin.valueProperty().addListener((e, f, g) -> updateDisplay());
		sliderMax.valueProperty().addListener((e, f, g) -> updateDisplay());
		
		sliderMin.setTooltip(new Tooltip("Min display value"));
		sliderMax.setTooltip(new Tooltip("Max display value"));
		
		BorderPane panelLabels = new BorderPane();
		labelMax.setTextAlignment(TextAlignment.RIGHT);
		labelMin.setTextAlignment(TextAlignment.LEFT);
		labelMin.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> setSliderValue(sliderMin, "Set minimum display"));
		labelMax.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> setSliderValue(sliderMax, "Set maximum display"));
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
			updateMap();
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
		
		// Create a color mapper combobox
		ComboBox<ColorMapper> comboMapper = new ComboBox<>(colorMappers);
		selectedColorMapper = comboMapper.getSelectionModel().selectedItemProperty();
		String name = preferredMapperName.get();
		if (name != null) {
			for (var mapper : colorMappers) {
				if (name.equalsIgnoreCase(mapper.getName())) {
					comboMapper.getSelectionModel().select(mapper);
					break;
				}
			}
		}
		if (comboMapper.getSelectionModel().isEmpty() && !comboMapper.getItems().isEmpty())
			comboMapper.getSelectionModel().selectFirst();
		comboMapper.setTooltip(new Tooltip("Select color map"));
		selectedColorMapper.addListener((v, o, n) -> {
			updateMap();
			if (n != null)
				preferredMapperName.set(n.getName());
		});
		comboMapper.setMaxWidth(Double.MAX_VALUE);

		VBox vbButtons = new VBox(
				paneFilter,
				comboMapper,
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
	}
	
	
	static void setSliderValue(Slider slider, String message) {
		Double val = DisplayHelpers.showInputDialog("Measurement mapper", message, slider.getValue());
		if (val != null && Double.isFinite(val)) {
			if (val > slider.getMax())
				slider.setMax(val);
			else if (val < slider.getMin())
				slider.setMin(val);
			slider.setValue(val);
		}
	}
	
	
	public Pane getPane() {
		return pane;
	}
	
	
	private void updateMap() {
		if (showMap.get())
			showMap();
		else
			hideMap();
	}
	
	public void showMap() {
		String measurement = listMeasurements.getSelectionModel().getSelectedItem();
		QuPathViewer viewer = qupath.getViewer();
		if (viewer == null || measurement == null)
			return;
		// Reuse mappers if we can
		mapper = mapperMap.get(measurement);
		var colorMapper = selectedColorMapper.getValue();
		if (mapper == null) {
			mapper = new MeasurementMapper(colorMapper, measurement, viewer.getHierarchy().getObjects(null, null));
			if (mapper.isValid())
				mapperMap.put(measurement, mapper);
		} else if (colorMapper != null) {
			mapper.setColorMapper(colorMapper);
		}
		if (mapper != null && mapper.isValid()) {
			updatingSliders = true;
			sliderMin.setMin(Math.min(mapper.getDataMinValue(), mapper.getDisplayMinValue()));
			sliderMin.setMax(Math.max(mapper.getDataMaxValue(), mapper.getDisplayMaxValue()));
			sliderMin.setValue(mapper.getDisplayMinValue());
			sliderMin.setBlockIncrement((sliderMin.getMax() - sliderMin.getMin()) / 100);
			
			sliderMax.setMin(Math.min(mapper.getDataMinValue(), mapper.getDisplayMinValue()));
			sliderMax.setMax(Math.max(mapper.getDataMaxValue(), mapper.getDisplayMaxValue()));
			sliderMax.setValue(mapper.getDisplayMaxValue());		
			sliderMax.setBlockIncrement((sliderMax.getMax() - sliderMax.getMin()) / 100);
			updatingSliders = false;
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
		if (mapper == null || updatingSliders)
			return;
		
		double minValue = sliderMin.getValue();
		double maxValue = sliderMax.getValue();
		
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
		Set<String> measurements = PathClassifierTools.getAvailableFeatures(pathObjects);
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
