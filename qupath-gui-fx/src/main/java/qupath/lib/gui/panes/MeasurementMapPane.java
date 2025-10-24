/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.Text;
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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import qupath.fx.controls.PredicateTextField;
import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.UserDirectoryManager;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MeasurementMapper;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;


/**
 * Component used for displaying measurement maps, whereby detection objects are recolored 
 * according to measurement values.
 * 
 * @author Pete Bankhead
 *
 */
public class MeasurementMapPane {
	
	private static final Logger logger = LoggerFactory.getLogger(MeasurementMapPane.class);
	
	private QuPathGUI qupath;
	
	private Map<String, MeasurementMapper> mapperMap = new HashMap<>();

	private BorderPane pane = new BorderPane();

	private ObjectProperty<PathObjectFilter> selectedFilter = new SimpleObjectProperty<>(PathObjectFilter.DETECTIONS_ALL);
	
	private ObservableList<ColorMap> colorMaps = FXCollections.observableArrayList();
	private ObservableValue<ColorMap> selectedColorMap;
	
	private ObservableList<String> baseList = FXCollections.observableArrayList();
	private FilteredList<String> filteredList = new FilteredList<>(baseList);
	private ListView<String> listMeasurements = new ListView<>(filteredList);
	
	private Slider sliderMin = new Slider(0, 1, 0);
	private Slider sliderMax = new Slider(0, 1, 1);
	
	// For not painting values outside the mapper range
	private CheckBox cbExcludeOutside = new CheckBox("Exclude outside range");
	
	private Canvas colorMapKey;
	private Image colorMapKeyImage;
	
	private Label labelMin = new Label("");
	private Label labelMax = new Label("");
	
	private MeasurementMapper mapper = null;
	
	private BooleanProperty showMap;
	private boolean updatingSliders = false;
	
	
	private static StringProperty preferredMapperName = PathPrefs.createPersistentPreference("measurementMapperLUT", ColorMaps.getDefaultColorMap().getName());
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public MeasurementMapPane(final QuPathGUI qupath) {
		this.qupath = qupath;
		
		logger.trace("Creating Measurement Map Pane");

		var comboFilter = new ComboBox<PathObjectFilter>();
		comboFilter.setMaxWidth(Double.MAX_VALUE);
		comboFilter.getItems().setAll(PathObjectFilter.DETECTIONS_ALL, PathObjectFilter.CELLS, PathObjectFilter.TILES, PathObjectFilter.ANNOTATIONS);
		comboFilter.getSelectionModel().selectFirst();
		selectedFilter.bind(comboFilter.getSelectionModel().selectedItemProperty());
		selectedFilter.addListener((v, o, n) -> updateMeasurements());
		pane.setTop(comboFilter);

		
		ColorMaps.installColorMaps(getUserColormapPaths().toArray(Path[]::new));
		colorMaps.setAll(ColorMaps.getColorMaps().values());
		
		updateMeasurements();
		
		cbExcludeOutside.setSelected(false);
		
		final ToggleButton toggleShowMap = new ToggleButton("Show map");
		toggleShowMap.setTooltip(new Tooltip("Show/hide the map"));
		toggleShowMap.setSelected(true);
		showMap = toggleShowMap.selectedProperty();
		showMap.addListener((v, o, n) -> updateMap());

		var noMeasurements = new Text("No measurements found");
		noMeasurements.setStyle("-fx-fill: -fx-text-base-color;");
//		listMeasurements.setPadding(new Insets(5, 0, 0, 0));
		listMeasurements.setPlaceholder(noMeasurements);
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
		colorMapKey = new Canvas() {
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
		Tooltip.install(colorMapKey, new Tooltip("Measurement map key"));

		ContextMenu colorMapContextMenu = new ContextMenu();
		MenuItem copyColorMap = new MenuItem("Copy");
		copyColorMap.setOnAction(event -> {
			if (colorMapKeyImage != null) {
				Clipboard clipboard = Clipboard.getSystemClipboard();
				ClipboardContent content = new ClipboardContent();
				content.putImage(colorMapKeyImage);
				clipboard.setContent(content);
				Dialogs.showInfoNotification(
						"Color map",
						"Color map copied to clipboard"
				);
			}
		});
		colorMapContextMenu.getItems().add(copyColorMap);
		colorMapKey.setOnMousePressed(event -> {
            if (event.isSecondaryButtonDown()) {
                colorMapContextMenu.show(pane, event.getScreenX(), event.getScreenY());
            }
        });

		// Filter to reduce visible measurements
		var tfFilter = new PredicateTextField<String>();
		var tooltip = new Tooltip("Enter text to filter measurement list");
		Tooltip.install(tfFilter, tooltip);
		tfFilter.setPromptText("Filter measurement list");
		filteredList.predicateProperty().bind(tfFilter.predicateProperty());
		BorderPane paneFilter = new BorderPane();
		paneFilter.setPadding(new Insets(5, 0, 10, 0));
		paneFilter.setCenter(tfFilter);
		
		// Create a color mapper combobox
		ComboBox<ColorMap> comboMapper = new ComboBox<>(colorMaps);
		selectedColorMap = comboMapper.getSelectionModel().selectedItemProperty();
		String name = preferredMapperName.get();
		if (name != null) {
			for (var mapper : colorMaps) {
				if (name.equalsIgnoreCase(mapper.getName())) {
					comboMapper.getSelectionModel().select(mapper);
					break;
				}
			}
		}
		if (comboMapper.getSelectionModel().isEmpty() && !comboMapper.getItems().isEmpty())
			comboMapper.getSelectionModel().selectFirst();
		comboMapper.setTooltip(new Tooltip("Select color map"));
		selectedColorMap.addListener((v, o, n) -> {
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
				colorMapKey,
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
	
	
	private static Collection<Path> getUserColormapPaths() {
		Path dirUser = UserDirectoryManager.getInstance().getColormapsDirectoryPath();
        if (dirUser != null && Files.isDirectory(dirUser)) {
        	return Collections.singletonList(dirUser);
        }
        return Collections.emptyList();
	}
	
	
	
	static void setSliderValue(Slider slider, String message) {
		Double val = Dialogs.showInputDialog("Measurement mapper", message, slider.getValue());
		if (val != null && Double.isFinite(val)) {
			if (val > slider.getMax())
				slider.setMax(val);
			else if (val < slider.getMin())
				slider.setMin(val);
			slider.setValue(val);
		}
	}
	
	/**
	 * Get the pane, which can be added to a scene for display.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}
	
	
	private void updateMap() {
		if (showMap.get())
			showMap();
		else
			hideMap();
	}
	
	private void showMap() {
		String measurement = listMeasurements.getSelectionModel().getSelectedItem();
		QuPathViewer viewer = qupath.getViewer();
		if (viewer == null || measurement == null)
			return;
		// Reuse mappers if we can
		mapper = mapperMap.get(measurement);
		var colorMapper = selectedColorMap.getValue();
		if (mapper == null) {
			var hierarchy = viewer.getHierarchy();
			var filter = selectedFilter.get();
			var inputObjects = hierarchy.getAllObjects(false)
					.stream()
					.filter(filter)
					.toList();
			mapper = new MeasurementMapper(colorMapper, measurement, inputObjects, filter);
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

		colorMapKeyImage = createPanelKey(mapper.getColorMapper());
		updateColorMapperKey();
		mapper.setExcludeOutsideRange(cbExcludeOutside.isSelected());
		viewer.forceOverlayUpdate();
		updateMapperBrightnessContrast();
		OverlayOptions overlayOptions = viewer.getOverlayOptions();
		if (overlayOptions != null)
			overlayOptions.setMeasurementMapper(mapper);		
	}
	
	
	private void updateColorMapperKey() {
		GraphicsContext gc = colorMapKey.getGraphicsContext2D();
		double w = colorMapKey.getWidth();
		double h = colorMapKey.getHeight();
		gc.clearRect(0, 0, w, h);
		if (colorMapKeyImage != null)
			gc.drawImage(colorMapKeyImage,
					0, 0, colorMapKeyImage.getWidth(), colorMapKeyImage.getHeight(),
					0, 0, w, h);
	}
	
	
	private void updateMapperBrightnessContrast() {
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
	
	
	
	private void hideMap() {
		QuPathViewer viewer = qupath.getViewer();
		if (viewer != null) {
			OverlayOptions overlayOptions = viewer.getOverlayOptions();
			if (overlayOptions != null)
				overlayOptions.resetMeasurementMapper();
//			viewer.resetMeasurementMapper();
		}
	}
	
	/**
	 * Update the measurements according to the current image
	 */
	public void updateMeasurements() {
		QuPathViewer viewer = qupath.getViewer();
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null) {
			baseList.clear();
			return;
		}

		var filter = selectedFilter.get();
		Collection<PathObject> pathObjects = hierarchy.getAllObjects(false).stream().filter(filter).toList();
		Set<String> measurements = PathObjectTools.getAvailableFeatures(pathObjects);
		for (PathObject pathObject : pathObjects) {
			if (!Double.isNaN(pathObject.getClassProbability())) {
				measurements.add("Class probability");
				break;
			}
		}
		
		// Apply any changes
		baseList.setAll(measurements);
	}
	
	
	
	private void updateDisplay() {
		updateMapperBrightnessContrast();
		for (var viewer : qupath.getAllViewers())
			viewer.forceOverlayUpdate();
//		viewer.repaint();
	}
	
	
	
	private static Image createPanelKey(final ColorMap colorMapper) {
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
