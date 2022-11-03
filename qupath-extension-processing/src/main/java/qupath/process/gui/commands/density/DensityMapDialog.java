/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.process.gui.commands.density;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener.Change;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import qupath.lib.analysis.heatmaps.ColorModels;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.ColorModels.ColorModelBuilder;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapType;
import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.stores.ColorModelRenderer;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.ImageInterpolation;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectPredicates;
import qupath.lib.objects.PathObjectPredicates.PathObjectPredicate;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.process.gui.commands.density.DensityMapUI.DensityMapObjects;
import qupath.process.gui.commands.density.DensityMapUI.MinMax;

/**
 * Dialog for interactively generating a custom density map.
 * 
 * @author Pete Bankhead
 *
 */
public class DensityMapDialog {
	
	private static final Logger logger = LoggerFactory.getLogger(DensityMapDialog.class);
	
	private QuPathGUI qupath;
			
	private final Stage stage;
	
	/**
	 * Core DensityMapBuilder (doesn't bother with colormodel)
	 */
	private final ObservableDensityMapBuilder densityMapBuilder = new ObservableDensityMapBuilder();

	/**
	 * Color model builder
	 */
	private final ObservableColorModelBuilder colorModelBuilder = new ObservableColorModelBuilder();
	
	/**
	 * DensityMapBuilder that combines the observable builder and colormodel
	 */
	private final ObjectExpression<DensityMapBuilder> combinedBuilder = Bindings.createObjectBinding(() -> {
		var b = densityMapBuilder.getBuilderProperty().get();
		var c = colorModelBuilder.getBuilderProperty().get();
		if (b == null || c == null)
			return b;
		var builder2 = DensityMaps.builder(b).colorModel(c);
		return builder2;
	}, densityMapBuilder.getBuilderProperty(), colorModelBuilder.getBuilderProperty());

	private final ObjectProperty<ImageInterpolation> interpolation = new SimpleObjectProperty<>(ImageInterpolation.NEAREST);
			
	private HierarchyClassifierOverlayManager manager;
			
	private final double textFieldWidth = 80;
	private final double hGap = 5;
	private final double vGap = 5;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public DensityMapDialog(QuPathGUI qupath) {
		this.qupath = qupath;
		
		logger.debug("Constructing density map dialog");
		
		var paneParams = buildAllObjectsPane(densityMapBuilder);
		var titledPaneParams = new TitledPane("Create density map", paneParams);
		titledPaneParams.setExpanded(true);
		titledPaneParams.setCollapsible(false);
		PaneTools.simplifyTitledPane(titledPaneParams, true);
		
		var paneDisplay = buildDisplayPane(colorModelBuilder);
		
		var titledPaneDisplay = new TitledPane("Customize appearance", paneDisplay);
		titledPaneDisplay.setExpanded(false);
		PaneTools.simplifyTitledPane(titledPaneDisplay, true);
		
		var pane = createGridPane();
		int row = 0;
		PaneTools.addGridRow(pane, row++, 0, null, titledPaneParams, titledPaneParams, titledPaneParams);			
		PaneTools.addGridRow(pane, row++, 0, null, titledPaneDisplay, titledPaneDisplay, titledPaneDisplay);

		
		var btnAutoUpdate = new ToggleButton("Live update");
		btnAutoUpdate.setSelected(densityMapBuilder.autoUpdate.get());
		btnAutoUpdate.setMaxWidth(Double.MAX_VALUE);
		btnAutoUpdate.selectedProperty().bindBidirectional(densityMapBuilder.autoUpdate);
		
		PaneTools.addGridRow(pane, row++, 0, "Automatically update the density map. "
				+ "Turn this off if changing parameters and heatmap generation is slow.", btnAutoUpdate, btnAutoUpdate, btnAutoUpdate);
		
		var densityMapName = new SimpleStringProperty();
		var savePane = DensityMapUI.createSaveDensityMapPane(qupath.projectProperty(), combinedBuilder, densityMapName);
		PaneTools.addGridRow(pane, row++, 0, null, savePane, savePane, savePane);
		PaneTools.setToExpandGridPaneWidth(savePane);

		var buttonPane = DensityMapUI.createButtonPane(qupath, qupath.imageDataProperty(), combinedBuilder, densityMapName, Bindings.createObjectBinding(() -> manager == null ? null : manager.overlay), true);
		PaneTools.addGridRow(pane, row++, 0, null, buttonPane, buttonPane, buttonPane);
		PaneTools.setToExpandGridPaneWidth(btnAutoUpdate, buttonPane);

		pane.setPadding(new Insets(10));

		stage = new Stage();
		stage.setScene(new Scene(pane));
		stage.setResizable(false);
		stage.initOwner(qupath.getStage());
		stage.setTitle("Density map");
		
		// Update stage height when display options expanded/collapsed
		titledPaneDisplay.heightProperty().addListener((v, o, n) -> stage.sizeToScene());
		
		// Create new overlays for the viewers
		manager = new HierarchyClassifierOverlayManager(qupath, densityMapBuilder.builder, colorModelBuilder.colorModel, interpolation);
		manager.currentDensityMap.addListener((v, o, n) -> colorModelBuilder.updateDisplayRanges(n));
		stage.focusedProperty().addListener((v, o, n) -> {
			if (n)
				manager.updateViewers();
		});
	}
	
	
	private ObservableList<PathClass> createObservablePathClassList(PathClass... defaultClasses) {
		var available = qupath.getAvailablePathClasses();
		if (defaultClasses.length == 0)
			return available;
		var list = FXCollections.observableArrayList(defaultClasses);
		available.addListener((Change<? extends PathClass> c) -> updateList(list, available, defaultClasses));
		updateList(list, available, defaultClasses);
		return list;
	}
	
	private static void updateList(ObservableList<PathClass> mainList, ObservableList<PathClass> originalList, PathClass... additionalItems) {
		Set<PathClass> temp = new LinkedHashSet<>();
		for (var t : additionalItems)
			temp.add(t);
		temp.addAll(originalList);
		mainList.setAll(temp);
	}
	
	
	
	private Pane buildAllObjectsPane(ObservableDensityMapBuilder params) {
		ComboBox<DensityMapObjects> comboObjectType = new ComboBox<>();
		comboObjectType.getItems().setAll(DensityMapObjects.values());
		comboObjectType.getSelectionModel().select(DensityMapObjects.DETECTIONS);
		params.allObjectTypes.bind(comboObjectType.getSelectionModel().selectedItemProperty());

		ComboBox<PathClass> comboAllObjects = new ComboBox<>(createObservablePathClassList(DensityMapUI.ANY_CLASS));
		comboAllObjects.setButtonCell(GuiTools.createCustomListCell(p -> classificationText(p)));
		comboAllObjects.setCellFactory(c -> GuiTools.createCustomListCell(p -> classificationText(p)));
		params.allObjectClass.bind(comboAllObjects.getSelectionModel().selectedItemProperty());
		comboAllObjects.getSelectionModel().selectFirst();
		
		ComboBox<PathClass> comboPrimary = new ComboBox<>(createObservablePathClassList(DensityMapUI.ANY_CLASS, DensityMapUI.ANY_POSITIVE_CLASS));
		comboPrimary.setButtonCell(GuiTools.createCustomListCell(p -> classificationText(p)));
		comboPrimary.setCellFactory(c -> GuiTools.createCustomListCell(p -> classificationText(p)));
		params.densityObjectClass.bind(comboPrimary.getSelectionModel().selectedItemProperty());
		comboPrimary.getSelectionModel().selectFirst();
		
		ComboBox<DensityMapType> comboDensityType = new ComboBox<>();
		comboDensityType.getItems().setAll(DensityMapType.values());
		comboDensityType.getSelectionModel().select(DensityMapType.SUM);
		params.densityType.bind(comboDensityType.getSelectionModel().selectedItemProperty());
		
		var pane = createGridPane();
		int row = 0;
		
		var labelObjects = createTitleLabel("Choose all objects to include");
		PaneTools.addGridRow(pane, row++, 0, null, labelObjects, labelObjects, labelObjects);
		
		PaneTools.addGridRow(pane, row++, 0, "Select objects used to generate the density map.\n"
				+ "Use 'All detections' to include all detection objects (including cells and tiles).\n"
				+ "Use 'All cells' to include cell objects only.\n"
				+ "Use 'Point annotations' to use annotated points rather than detections.",
				new Label("Object type"), comboObjectType, comboObjectType);
		
		PaneTools.addGridRow(pane, row++, 0, "Select object classifications to include.\n"
				+ "Use this to filter out detections that should not contribute to the density map at all.\n"
				+ "For example, this can be used to selectively consider tumor cells and ignore everything else.\n"
				+ "If used in combination with 'Secondary class' and 'Density type: Objects %', the 'Secondary class' defines the numerator and the 'Main class' defines the denominator.",
				new Label("Main class"), comboAllObjects, comboAllObjects);

		var labelDensities = createTitleLabel("Define density map");
		PaneTools.addGridRow(pane, row++, 0, null, labelDensities);
		
		PaneTools.addGridRow(pane, row++, 0, "Calculate the density of objects containing a specified classification.\n"
				+ "If used in combination with 'Main class' and 'Density type: Objects %', the 'Secondary class' defines the numerator and the 'Main class' defines the denominator.\n"
				+ "For example, choose 'Main class: Tumor', 'Secondary class: Positive' and 'Density type: Objects %' to define density as the proportion of tumor cells that are positive.",
				new Label("Secondary class"), comboPrimary, comboPrimary);
		
		PaneTools.addGridRow(pane, row++, 0, "Select method of normalizing densities.\n"
				+ "Choose whether to show raw counts, or normalize densities by area or the number of objects locally.\n"
				+ "This can be used to distinguish between the total number of objects in an area with a given classification, "
				+ "and the proportion of objects within the area with that classification.\n"
				+ "Gaussian weighting gives a smoother result, but it can be harder to interpret.",
				new Label("Density type"), comboDensityType, comboDensityType);
		
		
		var sliderRadius = new Slider(0, 1000, params.radius.get());
		sliderRadius.valueProperty().bindBidirectional(params.radius);
		initializeSliderSnapping(sliderRadius, 50, 1, 0.1);
		var tfRadius = createTextField();
		
		boolean expandSliderLimits = true;
		
		GuiTools.bindSliderAndTextField(sliderRadius, tfRadius, expandSliderLimits, 2);
		GuiTools.installRangePrompt(sliderRadius);
		PaneTools.addGridRow(pane, row++, 0, "Select smoothing radius used to calculate densities.\n"
				+ "This is defined in calibrated pixel units (e.g. " + GeneralTools.micrometerSymbol() + " if available).", new Label("Density radius"), sliderRadius, tfRadius);
		
		PaneTools.setToExpandGridPaneWidth(comboObjectType, comboPrimary, comboAllObjects, comboDensityType, sliderRadius);

		return pane;
	}
	
	
	private Pane buildDisplayPane(ObservableColorModelBuilder displayParams) {
		
		var comboColorMap = new ComboBox<ColorMap>();
		comboColorMap.getItems().setAll(ColorMaps.getColorMaps().values());
		if (comboColorMap.getSelectionModel().getSelectedItem() == null)
			comboColorMap.getSelectionModel().select(ColorMaps.getDefaultColorMap());
		displayParams.colorMap.bind(comboColorMap.getSelectionModel().selectedItemProperty());
		
		var comboInterpolation = new ComboBox<ImageInterpolation>();
		
		var paneDisplay = createGridPane();
		
		int rowDisplay = 0;
		
		// Colormap
		var labelColormap = createTitleLabel("Colors");
		PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Customize the colors of the density map", labelColormap, labelColormap, labelColormap);			
		PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Choose the colormap to use for display", new Label("Colormap"), comboColorMap, comboColorMap);

		var spinnerGrid = new GridPane();
		int spinnerRow = 0;
		
		var spinnerMin = createSpinner(displayParams.minDisplay, 10);
		var spinnerMax = createSpinner(displayParams.maxDisplay, 10);
		spinnerGrid.setHgap(hGap);
		spinnerGrid.setVgap(vGap);
		
		var toggleAuto = new ToggleButton("Auto");
		toggleAuto.selectedProperty().bindBidirectional(displayParams.autoUpdateDisplayRange);
		spinnerMin.disableProperty().bind(toggleAuto.selectedProperty());
		spinnerMax.disableProperty().bind(toggleAuto.selectedProperty());
		PaneTools.setToExpandGridPaneWidth(spinnerMin, spinnerMax);
		
		PaneTools.addGridRow(spinnerGrid, spinnerRow++, 0, null, new Label("Min"), spinnerMin, new Label("Max"), spinnerMax, toggleAuto);
		PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, 
				"Set the min/max density values for the colormap.\n"
				+ "This determines how the colors in the colormap relate to density values.\n"
				+ "Choose 'Auto' to assign colors based upon the full range of the values in the current density map.",
				new Label("Range"), spinnerGrid, spinnerGrid);

		// Alpha
		var labelAlpha = createTitleLabel("Opacity");
		PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Customize the opacity (alpha) of the density map.\n"
				+ "Note that this is based upon the count of all objects.", labelAlpha, labelAlpha, labelAlpha);			
		
		var spinnerGridAlpha = new GridPane();
		spinnerRow = 0;
		
		var spinnerMinAlpha = createSpinner(displayParams.minAlpha, 10);
		var spinnerMaxAlpha = createSpinner(displayParams.maxAlpha, 10);
		spinnerGridAlpha.setHgap(hGap);
		spinnerGridAlpha.setVgap(vGap);
		
		var toggleAutoAlpha = new ToggleButton("Auto");
		toggleAutoAlpha.selectedProperty().bindBidirectional(displayParams.autoUpdateAlphaRange);
		spinnerMinAlpha.disableProperty().bind(toggleAutoAlpha.selectedProperty());
		spinnerMaxAlpha.disableProperty().bind(toggleAutoAlpha.selectedProperty());
		PaneTools.setToExpandGridPaneWidth(spinnerMinAlpha, spinnerMaxAlpha);
//		PaneTools.setToExpandGridPaneWidth(toggleAutoAlpha);

		PaneTools.addGridRow(spinnerGridAlpha, spinnerRow++, 0, null, new Label("Min"), spinnerMinAlpha, new Label("Max"), spinnerMaxAlpha, toggleAutoAlpha);
		PaneTools.addGridRow(paneDisplay, rowDisplay++, 0,
				"Set the min/max density values for the opacity range.\n"
				+ "This can used in combination with 'Gamma' to adjust the opacity according to the "
				+ "number or density of objects. Use 'Auto' to use the full display range for the current image.",
				new Label("Range"), spinnerGridAlpha, spinnerGridAlpha);

		var sliderGamma = new Slider(0, 5, displayParams.gamma.get());
		sliderGamma.valueProperty().bindBidirectional(displayParams.gamma);
		initializeSliderSnapping(sliderGamma, 0.1, 1, 0.1);
		var tfGamma = createTextField();
		GuiTools.bindSliderAndTextField(sliderGamma, tfGamma, false, 1);
		PaneTools.addGridRow(paneDisplay, rowDisplay++, 0,
				"Control how the opacity of the density map changes between min & max values.\n"
				+ "Choose zero for an opaque map.", new Label("Gamma"), sliderGamma, tfGamma);

		// Interpolation
		var labelSmoothness = createTitleLabel("Smoothness");
		PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Customize density map interpolation (visual smoothness)", labelSmoothness);			
		
		PaneTools.addGridRow(paneDisplay, rowDisplay++, 0, "Choose how the density map should be interpolated.\n"
				+ "This impacts the visual smoothness, especially if the density radius is small and the image is viewed while zoomed in.", new Label("Interpolation"), comboInterpolation, comboInterpolation);

		comboInterpolation.getItems().setAll(ImageInterpolation.values());
		comboInterpolation.getSelectionModel().select(ImageInterpolation.NEAREST);
		interpolation.bind(comboInterpolation.getSelectionModel().selectedItemProperty());

		PaneTools.setToExpandGridPaneWidth(comboColorMap, comboInterpolation, sliderGamma);
		
		return paneDisplay;
	}
	
	Spinner<Double> createSpinner(ObjectProperty<Double> property, double step) {
		var spinner = GuiTools.createDynamicStepSpinner(0, Double.MAX_VALUE, 1, 0.1, 1);
		property.bindBidirectional(spinner.getValueFactory().valueProperty());
		spinner.setEditable(true);
		spinner.getEditor().setPrefColumnCount(6);
		GuiTools.restrictTextFieldInputToNumber(spinner.getEditor(), true);
		GuiTools.resetSpinnerNullToPrevious(spinner);
		return spinner;
	}
	
	Label createTitleLabel(String text) {
		var label = new Label(text);
		label.setStyle("-fx-font-weight: bold;");
		label.setMaxWidth(Double.MAX_VALUE);
		return label;
	}
	
	
	/**
	 * Create a {@link GridPane} with standard gaps.
	 * @return
	 */
	GridPane createGridPane() {
		var pane = new GridPane();
		pane.setVgap(vGap);
		pane.setHgap(hGap);
		return pane;
	}
	
	
	/**
	 * Create a {@link TextField} with a standard width;
	 * @return
	 */
	TextField createTextField() {
		var textField = new TextField();
		textField.setMaxWidth(textFieldWidth);
		return textField;
	}
			
	
	/**
	 * Update default parameters with a specified {@link ImageData}.
	 * This can be called before showing the dialog to give better starting values.
	 * @param imageData
	 * @return 
	 */
	public boolean updateDefaults(ImageData<BufferedImage> imageData) {
		if (imageData == null)
			return false;
		
		logger.debug("Updating density map defaults for {}", imageData);
		
		var server = imageData.getServer();
		double pixelSize = Math.round(server.getPixelCalibration().getAveragedPixelSize().doubleValue() * 10);
		pixelSize *= 100;
//		if (server.nResolutions() > 1)
//			pixelSize *= 10;
		pixelSize = Math.min(pixelSize, Math.min(server.getHeight(), server.getWidth())/20.0);
		densityMapBuilder.radius.set(pixelSize);
		return true;
	}
	
	
	private void initializeSliderSnapping(Slider slider, double blockIncrement, double majorTicks, double minorTicks) {
		slider.setBlockIncrement(blockIncrement);
		slider.setMajorTickUnit(majorTicks);
		slider.setMinorTickCount((int)Math.round(majorTicks / minorTicks) - 1);
		slider.setSnapToTicks(true);
	}
	
	
	private String classificationText(PathClass pathClass) {
		if (pathClass == null)
			pathClass = PathClass.NULL_CLASS;
		if (pathClass == DensityMapUI.ANY_CLASS)
			return "Any";
		if (pathClass == DensityMapUI.ANY_POSITIVE_CLASS)
			return "Positive (inc. 1+, 2+, 3+)";
		return pathClass.toString();
	}

	
	/**
	 * Deregister listeners. This should be called when the stage is closed if it will not be used again.
	 */
	public void deregister() {
		logger.debug("Deregistering density map dialog");
		manager.shutdown();
	}
	
	/**
	 * Get the stage.
	 * @return
	 */
	public Stage getStage() {
		return stage;
	}
	
	
	
	/**
	 * Encapsulate the stuff we need to build an {@link ImageRenderer}.
	 */
	static class ObservableColorModelBuilder {
		
		private static final Logger logger = LoggerFactory.getLogger(ObservableColorModelBuilder.class);

		private final ObjectProperty<ColorMap> colorMap = new SimpleObjectProperty<>();

		// Not observable, since the user can't adjust them (and we don't want unnecessary events fired)
		private int alphaCountBand = -1;

		// Because these will be bound to a spinner, we need an object property - 
		// and we can't use DoubleProperty(0.0).asObject() because of premature garbage collection 
		// breaking the binding
		private ObjectProperty<Double> minAlpha = new SimpleObjectProperty<>(0.0);
		private ObjectProperty<Double> maxAlpha = new SimpleObjectProperty<>(1.0);

		// Observable, so we can update them in the UI
		private final DoubleProperty gamma = new SimpleDoubleProperty(1.0);

		private final ObjectProperty<Double> minDisplay = new SimpleObjectProperty<>(0.0);
		private final ObjectProperty<Double> maxDisplay = new SimpleObjectProperty<>(1.0);

		private final BooleanProperty autoUpdateDisplayRange = new SimpleBooleanProperty(true);
		private final BooleanProperty autoUpdateAlphaRange = new SimpleBooleanProperty(true);

		private final ObjectProperty<ColorModelBuilder> builder = new SimpleObjectProperty<>();
		private final ObservableValue<ColorModel> colorModel = Bindings.createObjectBinding(() -> {
			var b = builder.get();
			return b == null ? null : b.build();
		}, builder);

		/*
		 * Flag to delay responding to all listeners when updating multiple properties
		 */
		private boolean updating = false;

		private ImageServer<BufferedImage> lastMap;

		ObservableColorModelBuilder() {
			colorMap.addListener((v, o, n) -> updateColorModel());

			minDisplay.addListener((v, o, n) -> updateColorModel());
			maxDisplay.addListener((v, o, n) -> updateColorModel());

			minAlpha.addListener((v, o, n) -> updateColorModel());
			maxAlpha.addListener((v, o, n) -> updateColorModel());
			gamma.addListener((v, o, n) -> updateColorModel());

			autoUpdateDisplayRange.addListener((v, o, n) -> {
				if (n)
					updateDisplayRanges(lastMap);
			});
			autoUpdateAlphaRange.addListener((v, o, n) -> {
				if (n)
					updateDisplayRanges(lastMap);
			});
		}
		
		/**
		 * The {@link DensityMapBuilder} being created and updated.
		 * @return
		 */
		public ObjectProperty<ColorModelBuilder> getBuilderProperty() {
			return builder;
		}


		private void updateDisplayRanges(ImageServer<BufferedImage> densityMapServer) {
			this.lastMap = densityMapServer;
			if (densityMapServer == null)
				return;

			assert Platform.isFxApplicationThread();

			try {
				updating = true;
				boolean autoUpdateSomething = autoUpdateDisplayRange.get() || autoUpdateAlphaRange.get();

				// If the last channel is 'counts', then it is used for normalization
				int alphaCountBand = -1;
				if (densityMapServer.getChannel(densityMapServer.nChannels()-1).getName().equals(DensityMaps.CHANNEL_ALL_OBJECTS))
					alphaCountBand = densityMapServer.nChannels()-1;

				// Compute min/max values if we need them
				List<MinMax> minMax = null;
				if (alphaCountBand > 0 || autoUpdateSomething) {
					try {
						minMax = DensityMapUI.getMinMax(densityMapServer);
						// This can happen if the density map is replaced too quickly, so min/max calculations aren't performed
						if (minMax == null) {
							logger.debug("Min/max calculation interrupted!");
							return;
						}
					} catch (IOException e) {
						logger.warn("Error setting display ranges: " + e.getLocalizedMessage(), e);
					}
				}

				// Determine min/max values for alpha in count channel, if needed
				if (autoUpdateAlphaRange.get()) {
					minAlpha.set(1e-6);
					int band = Math.max(alphaCountBand, 0); 
					maxAlpha.set(Math.max(minAlpha.get(), (double)minMax.get(band).getMaxValue()));
				}
				this.alphaCountBand = alphaCountBand;

				double maxDisplayValue = minMax == null ? maxDisplay.get() : minMax.get(0).getMaxValue();
				if (maxDisplayValue <= 0)
					maxDisplayValue = 1;
				double minDisplayValue = 0;
				if (autoUpdateDisplayRange.get()) {
					minDisplay.set(minDisplayValue);
					maxDisplay.set(maxDisplayValue);
				}
			} finally {
				updating = false;
			}
			updateColorModel();
		}

		private void updateColorModel() {
			// Stop events if multiple updates in progress
			if (updating) {
				logger.trace("Skipping color model update (updating flag is 'true')");
				return;
			}

			var selectedColorMap = colorMap.get();
			if (selectedColorMap == null) {
				logger.warn("Selected color map is null! Cannot create color model.");
				return;
			}
			logger.debug("Updating color model for {}", selectedColorMap.getName());
			int band = 0;
			var newBuilder = ColorModels.createColorModelBuilder(
					ColorModels.createBand(selectedColorMap.getName(), band, minDisplay.get(), maxDisplay.get()),
					ColorModels.createBand(null, alphaCountBand, minAlpha.get(), maxAlpha.get(), gamma.get()));
			var oldBuilder = builder.getValue();
			
			try {
				// We need to avoid triggering too many updates, but also can't rely upon the builder having a proper equals method...
				// So here we (awkwardly) go through JSON.
				var gson = GsonTools.getInstance();
				if (oldBuilder != null && Objects.equals(gson.toJson(newBuilder), gson.toJson(oldBuilder))) {
					return;
				}
			} catch (Exception e) {
				logger.debug("Exception testing color model builders: " + e.getLocalizedMessage(), e);
			}
			builder.set(newBuilder);
		}


	}
	
	
	
	/**
	 * Encapsulate the parameters needed to generate a density map in a JavaFX-friendly way.
	 */
	static class ObservableDensityMapBuilder {
		
		private static final Logger logger = LoggerFactory.getLogger(ObservableDensityMapBuilder.class);

		private ObjectProperty<DensityMapObjects> allObjectTypes = new SimpleObjectProperty<>(DensityMapObjects.DETECTIONS);
		private ObjectProperty<PathClass> allObjectClass = new SimpleObjectProperty<>(DensityMapUI.ANY_CLASS);
		private ObjectProperty<PathClass> densityObjectClass = new SimpleObjectProperty<>(DensityMapUI.ANY_CLASS);
		private ObjectProperty<DensityMapType> densityType = new SimpleObjectProperty<>(DensityMapType.SUM);

		private DoubleProperty radius = new SimpleDoubleProperty(10.0);

		/**
		 * Automatically update the density maps and overlays.
		 */
		private final BooleanProperty autoUpdate = new SimpleBooleanProperty(true);

		private final ObjectProperty<DensityMapBuilder> builder = new SimpleObjectProperty<>();

		private Gson gson = GsonTools.getInstance();

		ObservableDensityMapBuilder() {
			allObjectTypes.addListener((v, o, n) -> updateBuilder());
			allObjectClass.addListener((v, o, n) -> updateBuilder());
			densityObjectClass.addListener((v, o, n) -> updateBuilder());
			densityType.addListener((v, o, n) -> updateBuilder());
			radius.addListener((v, o, n) -> updateBuilder());
			autoUpdate.addListener((v, o, n) -> updateBuilder());
		}
		
		/**
		 * The {@link DensityMapBuilder} being created and updated.
		 * @return
		 */
		public ObjectProperty<DensityMapBuilder> getBuilderProperty() {
			return builder;
		}

		/**
		 * Update the classifier. Note that this can only be done if there is an active {@link ImageData}, 
		 * which is used to get pixel calibration information.
		 */
		private void updateBuilder() {
			if (!autoUpdate.get())
				return;
			// Only update the classifier if it is different from the current classifier
			// To test this, we rely upon the JSON representation
			var newBuilder = createBuilder();
			var currentBuilder = builder.get();
			if (newBuilder != null && !Objects.equals(newBuilder, currentBuilder)) {
				if (currentBuilder == null || !Objects.equals(gson.toJson(currentBuilder), gson.toJson(newBuilder))) {
					logger.debug("Setting DensityMapBuilder to {}", newBuilder);
					builder.set(newBuilder);
					return;
				}
			}
			logger.trace("Skipping DensityMapBuilder update");
		}

		private PathObjectPredicate updatePredicate(PathObjectPredicate predicate, PathClass pathClass) {
			if (pathClass == DensityMapUI.ANY_CLASS)
				return predicate;
			PathObjectPredicate pathClassPredicate;
			if (pathClass == DensityMapUI.ANY_POSITIVE_CLASS)
				pathClassPredicate = PathObjectPredicates.positiveClassification(true);
			else if (pathClass == null || pathClass.getName() == null)
				pathClassPredicate = PathObjectPredicates.exactClassification((PathClass)null);
			else if (pathClass.isDerivedClass())
				pathClassPredicate = PathObjectPredicates.exactClassification(pathClass);
			else
				pathClassPredicate = PathObjectPredicates.containsClassification(pathClass.getName());
			return predicate == null ? pathClassPredicate : predicate.and(pathClassPredicate);
		}

		private DensityMapBuilder createBuilder() {
			
			logger.trace("Creating new DensityMapBuilder");
			
			// Determine all objects filter
			PathObjectPredicate allObjectsFilter = allObjectTypes.get().getPredicate();
			PathClass primaryClass = allObjectClass.get();
			allObjectsFilter = updatePredicate(allObjectsFilter, primaryClass);
			
			var densityType = this.densityType.get();
			boolean isPercent = densityType == DensityMapType.PERCENT;

			// Determine density objects filter
			var densityClass = densityObjectClass.get();

			// Sometimes the density class is null - in which case we can't build
			if (densityClass == null) {
				densityClass = DensityMapUI.ANY_CLASS;
			}
			
			// If the density class is 'anything' & we're looking at object percentages
			// match the density class it to the main class, 
			// since basically that's what the filter will end up accepting
			if (densityClass == DensityMapUI.ANY_CLASS && primaryClass != null)
				densityClass = primaryClass;

			PathObjectPredicate densityObjectsFilter = updatePredicate(null, densityClass);
			
			// There is an awkward corner case where both classes are 'Any' and the density type is %
			// For this, we need to make sure we still retain a count channel
			boolean bothAnyObject = isPercent && densityClass == DensityMapUI.ANY_CLASS && primaryClass == DensityMapUI.ANY_CLASS;
			if (densityObjectsFilter == null && bothAnyObject)
				densityObjectsFilter = allObjectsFilter;

			// Create map
			var builder = DensityMaps.builder(allObjectsFilter);

			builder.type(densityType);

			if (densityObjectsFilter != null) {
				String filterName;
				String densityClassName = densityClass.toString();
				if (densityClass == DensityMapUI.ANY_POSITIVE_CLASS)
					densityClassName = "Positive";
				else if (bothAnyObject)
					densityClassName = "Density";
				
				String primaryClassName = primaryClass == null ? PathClass.NULL_CLASS.toString()
						                                       : primaryClass.toString();
				
				if (primaryClass == DensityMapUI.ANY_CLASS)
					filterName = densityClassName;
				else if (!isPercent && densityClass == primaryClass)
					filterName = densityClassName;
				else
					filterName = primaryClassName + ": " + densityClassName;
//				if (isPercent)
//					filterName += " %";
				builder.addDensities(filterName, densityObjectsFilter);
			}

			builder.radius(radius.get());
			
			logger.debug("Created {}", builder);
			
			return builder;
		}
	}
	
	
	
	/**
	 * Manage a single {@link PixelClassificationOverlay} that may be applied across multiple viewers.
	 * This is written to potentially support different kinds of classifier that require updates on a hierarchy change.
	 * When the classifier changes, it is applied to all viewers in a background thread and then the viewers repainted when complete 
	 * (to avoid flickering). As such it's assumed classifiers are all quite fast to apply and don't have large memory requirements.
	 */
	static class HierarchyClassifierOverlayManager implements PathObjectHierarchyListener, QuPathViewerListener {
		
		private static final Logger logger = LoggerFactory.getLogger(HierarchyClassifierOverlayManager.class);

		private final QuPathGUI qupath;

		private final Set<QuPathViewer> currentViewers = new HashSet<>();

		private ObservableValue<ImageRenderer> renderer;
		private final PixelClassificationOverlay overlay;
		private final ObservableValue<DensityMapBuilder> builder;
		// Cache a server
		private Map<ImageData<BufferedImage>, ImageServer<BufferedImage>> classifierServerMap = Collections.synchronizedMap(new HashMap<>());

		private ExecutorService pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("density-maps", true));;
		private Map<QuPathViewer, Future<?>> tasks = Collections.synchronizedMap(new HashMap<>());

		private ObjectProperty<ImageServer<BufferedImage>> currentDensityMap = new SimpleObjectProperty<>();

		HierarchyClassifierOverlayManager(QuPathGUI qupath, ObservableValue<DensityMapBuilder> builder, ObservableValue<ColorModel> colorModel, ObservableValue<ImageInterpolation> interpolation) {
			this.qupath = qupath;
			this.builder = builder;
			var options = qupath.getOverlayOptions();
			renderer = Bindings.createObjectBinding(() -> new ColorModelRenderer(colorModel.getValue()), colorModel);

			overlay = PixelClassificationOverlay.create(options, classifierServerMap, renderer.getValue());
			updateViewers();
			overlay.interpolationProperty().bind(interpolation);
			overlay.interpolationProperty().addListener((v, o, n) -> qupath.repaintViewers());

			overlay.rendererProperty().bind(renderer);
			renderer.addListener((v, o, n) -> qupath.repaintViewers());

			overlay.setLivePrediction(true);
			builder.addListener((v, o, n) -> updateDensityServers());
			updateDensityServers();
		}

		/**
		 * Ensure the overlay is present on all viewers
		 */
		void updateViewers() {
			logger.trace("Updating density server for all viewers");
			for (var viewer : qupath.getViewers()) {
				viewer.setCustomPixelLayerOverlay(overlay);
				if (!currentViewers.contains(viewer)) {
					viewer.addViewerListener(this);
					var hierarchy = viewer.getHierarchy();
					if (hierarchy != null)
						hierarchy.addListener(this);
					currentViewers.add(viewer);
					updateDensityServer(viewer);
				}
			}
		}


		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			if (event.isChanging())
				return;
			qupath.getViewers().stream().filter(v -> v.getHierarchy() == event.getHierarchy()).forEach(v -> updateDensityServer(v));
		}

		@Override
		public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
				ImageData<BufferedImage> imageDataNew) {

			logger.debug("ImageData changed from {} to {}", imageDataOld, imageDataNew);
			if (imageDataOld != null)
				imageDataOld.getHierarchy().removeListener(this);

			if (imageDataNew != null) {
				imageDataNew.getHierarchy().addListener(this);
			}
			updateDensityServer(viewer);
		}

		private void updateDensityServers() {
			classifierServerMap.clear(); // TODO: Check if this causes any flickering
			for (var viewer : qupath.getViewers()) {
				updateDensityServer(viewer);
			}
		}

		private void updateDensityServer(QuPathViewer viewer) {
			if (Platform.isFxApplicationThread()) {
				synchronized (tasks) {
					var task = tasks.get(viewer);
					if (task != null && !task.isDone())
						task.cancel(true);
					if (!pool.isShutdown()) {
						logger.trace("Submitting updateDensityServer request for {}", viewer);
						task = pool.submit(() -> updateDensityServer(viewer));
					} else {
						logger.debug("Skipping updateDensityServer request for {} - pool shutdown", viewer);
					}
					tasks.put(viewer, task);
				}
				return;
			}
			var imageData = viewer.getImageData();
			var builder = this.builder.getValue();
			if (imageData == null || builder == null) {
				logger.debug("Removing density server for viewer {}", viewer);
				classifierServerMap.remove(imageData);
			} else {
				if (Thread.interrupted()) {
					logger.trace("Thread interrupted, skipping density server update");
					return;
				}
				// Create server with a unique ID, because it may change with the object hierarchy & we don't want caching to mask this
				var tempServer = builder.buildServer(imageData);
				if (Thread.interrupted()) {
					logger.trace("Thread interrupted, skipping density server update");
					return;
				}
				logger.debug("Setting density server {} for {}", tempServer, imageData);
				// If the viewer is the main viewer, update the current map (which can then impact colors)
				if (viewer == qupath.getViewer())
					Platform.runLater(() -> currentDensityMap.set(tempServer));
				classifierServerMap.put(imageData, tempServer);
				if (viewer == qupath.getViewer())
					currentDensityMap.set(tempServer);
				viewer.repaint();
			}
		}

		@Override
		public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}

		@Override
		public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

		@Override
		public void viewerClosed(QuPathViewer viewer) {
			imageDataChanged(viewer, viewer.getImageData(), null);
			viewer.removeViewerListener(this);
			currentViewers.remove(viewer);
		}

		public void shutdown() {
			tasks.values().stream().forEach(t -> t.cancel(true));
			pool.shutdown();
			for (var viewer : qupath.getViewers()) {
				imageDataChanged(viewer, viewer.getImageData(), null);
				viewer.removeViewerListener(this);
				if (viewer.getCustomPixelLayerOverlay() == overlay)
					viewer.resetCustomPixelLayerOverlay();				
			}
			if (overlay != null) {
				overlay.stop();
			}
		}


	}


	
}