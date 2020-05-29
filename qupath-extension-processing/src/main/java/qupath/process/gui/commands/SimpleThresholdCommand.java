/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.process.gui.commands;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;
import qupath.process.gui.ml.ClassificationResolution;
import qupath.process.gui.ml.PixelClassificationOverlay;
import qupath.process.gui.ml.PixelClassifierUI;

/**
 * Apply simple thresholding to an image via the pixel classification framework to support 
 * thresholding at any resolution, optionally with visual feedback via an overlay.
 * 
 * @author Pete Bankhead
 *
 */
public class SimpleThresholdCommand implements Runnable {
	
	private QuPathGUI qupath;
	
	private Stage stage;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public SimpleThresholdCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (qupath.getImageData() == null) {
			Dialogs.showNoImageError("Simple threshold");
			return;
		}
		if (stage == null || !stage.isShowing())
			showGUI();
		else
			stage.toFront();
	}
	
	
	private static enum Prefilter {
		
		GAUSSIAN,
		LAPLACIAN,
		EROSION,
		DILATION,
		OPENING,
		CLOSING,
		GRADIENT_MAG,
		WEIGHTED_STD;
		
		public ImageOp buildOp(double sigma) {
			if (sigma <= 0)
				return null;
			int radius = (int)Math.round(sigma * 2);
			switch (this) {
			case CLOSING:
				return ImageOps.Filters.closing(radius);
			case DILATION:
				return ImageOps.Filters.maximum(radius);
			case EROSION:
				return ImageOps.Filters.minimum(radius);
			case GAUSSIAN:
				return ImageOps.Filters.gaussianBlur(sigma);
			case GRADIENT_MAG:
				return ImageOps.Filters.features(Collections.singletonList(MultiscaleFeature.GRADIENT_MAGNITUDE), sigma, sigma);
			case LAPLACIAN:
				return ImageOps.Filters.features(Collections.singletonList(MultiscaleFeature.LAPLACIAN), sigma, sigma);
			case OPENING:
				return ImageOps.Filters.opening(radius);
			case WEIGHTED_STD:
				return ImageOps.Filters.features(Collections.singletonList(MultiscaleFeature.WEIGHTED_STD_DEV), sigma, sigma);
			default:
				throw new IllegalArgumentException("Unknown filter " + this);
			}
		}
		
		@Override
		public String toString() {
			switch (this) {
			case CLOSING:
				return "Morphological closing";
			case DILATION:
				return "Maximum (dilation)";
			case EROSION:
				return "Minimum (erosion)";
			case GAUSSIAN:
				return "Gaussian";
			case GRADIENT_MAG:
				return "Gradient magnitude";
			case LAPLACIAN:
				return "Laplacian of Gaussian";
			case OPENING:
				return "Morphological opening";
			case WEIGHTED_STD:
				return "Weighted deviation";
			default:
				throw new IllegalArgumentException("Unknown filter " + this);
			}
		}
		
	}
	
	
	private ComboBox<ClassificationResolution> comboResolutions = new ComboBox<>();
	private ReadOnlyObjectProperty<ClassificationResolution> selectedResolution = comboResolutions.getSelectionModel().selectedItemProperty();

	private ComboBox<Prefilter> comboPrefilter = new ComboBox<>();
	private ReadOnlyObjectProperty<Prefilter> selectedPrefilter = comboPrefilter.getSelectionModel().selectedItemProperty();

	private ComboBox<PathClass> classificationsBelow = new ComboBox<>();
	private ComboBox<PathClass> classificationsAbove = new ComboBox<>();
	
	private ComboBox<ColorTransform> transforms = new ComboBox<>();
	private ReadOnlyObjectProperty<ColorTransform> selectedChannel = transforms.getSelectionModel().selectedItemProperty();
	
	private Spinner<Double> sigmaSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 16.0, 0.0, 0.5));
	private ReadOnlyObjectProperty<Double> sigma = sigmaSpinner.valueProperty();

	private Map<ColorTransform, Double> availableTransforms = new LinkedHashMap<>();
	
	private SpinnerValueFactory.DoubleSpinnerValueFactory thresholdValueFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.5);
	private Spinner<Double> spinner = new Spinner<>(thresholdValueFactory);
	private ReadOnlyObjectProperty<Double> threshold = spinner.valueProperty();
	
	private ObjectProperty<PixelClassificationOverlay> selectedOverlay = new SimpleObjectProperty<>();
	private ObjectProperty<PixelClassifier> currentClassifier = new SimpleObjectProperty<>();

	/**
	 * Map to track where we have added an overlay
	 */
	private Map<QuPathViewer, PathOverlay> map = new WeakHashMap<>();
	
	
	
	private void showGUI() {
		
		var pane = new GridPane();

		classificationsAbove.setItems(qupath.getAvailablePathClasses());
		classificationsBelow.setItems(qupath.getAvailablePathClasses());
				
		int row = 0;
		
		Label labelResolution = new Label("Resolution");
		labelResolution.setLabelFor(comboResolutions);
		PaneTools.addGridRow(pane, row++, 0, "Select image resolution to threshold (higher values mean lower resolution, and faster thresholding)",
				labelResolution, comboResolutions, comboResolutions);

		Label label = new Label("Channel");
		label.setLabelFor(transforms);
		PaneTools.addGridRow(pane, row++, 0, "Select channel to threshold", label, transforms, transforms);
		
		Label labelPrefilter = new Label("Prefilter");
		labelPrefilter.setLabelFor(comboPrefilter);
		comboPrefilter.getItems().setAll(Prefilter.values());
		comboPrefilter.getSelectionModel().select(Prefilter.GAUSSIAN);
		PaneTools.addGridRow(pane, row++, 0, "Select image smoothing filter (Gaussian is usually best)", labelPrefilter, comboPrefilter, comboPrefilter);

		label = new Label("Smoothing sigma");
		label.setLabelFor(sigmaSpinner);
		Label labelSigma = new Label();
		labelSigma.textProperty().bind(
				Bindings.createStringBinding(() -> GeneralTools.formatNumber(sigma.get(), 2), sigma)
				);
		labelSigma.setMinWidth(25); // Thanks to Melvin, to stop it jumping around
		GuiTools.restrictSpinnerInputToNumber(sigmaSpinner, true);
		PaneTools.addGridRow(pane, row++, 0, "Select smoothing sigma value (higher values give a smoother result)", label, sigmaSpinner, labelSigma);

		label = new Label("Threshold");
		label.setLabelFor(spinner);
		Label labelThreshold = new Label();
		labelThreshold.textProperty().bind(
				Bindings.createStringBinding(() -> GeneralTools.formatNumber(threshold.get(), 2), threshold)
				);
		GuiTools.restrictSpinnerInputToNumber(spinner, true);
		PaneTools.addGridRow(pane, row++, 0, "Select threshold value", label, spinner, labelThreshold);

		Label labelAbove = new Label("Above threshold");
		labelAbove.setLabelFor(classificationsAbove);

		Label labelBelow = new Label("Below threshold");
		labelBelow.setLabelFor(classificationsBelow);
		PaneTools.addGridRow(pane, row++, 0, "Select classification for pixels above the threshold."
				+ "\nDouble-click on labels to switch above & below.", labelAbove, classificationsAbove, classificationsAbove);
		PaneTools.addGridRow(pane, row++, 0, "Select classification for pixels less than or equal to the threshold."
				+ "\nDouble-click on labels to switch above & below.", labelBelow, classificationsBelow, classificationsBelow);
		
		labelAbove.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2)
				switchClassifications();
		});
		labelBelow.setOnMouseClicked(labelAbove.getOnMouseClicked());

		var labelRegion = new Label("Region");
		var comboRegionFilter = PixelClassifierUI.createRegionFilterCombo(qupath.getOverlayOptions());
		PaneTools.addGridRow(pane,  row++, 0, "Control where the pixel classification is applied during preview",
				labelRegion, comboRegionFilter, comboRegionFilter);
		
//		var nodeLimit = PixelClassifierTools.createLimitToAnnotationsControl(qupath.getOverlayOptions());
//		PaneTools.addGridRow(pane,  row++, 0, null,
//				nodeLimit, nodeLimit, nodeLimit);
		
		var btnSave = new Button("Save as pixel classifier");
//		btnSave.disableProperty().bind(currentClassifier.isNull());
//		btnSave.setOnAction(e -> {
//			try {
//				PixelClassifierUI.promptToSaveClassifier(qupath.getProject(), currentClassifier.get());
//			} catch (IOException ex) {
//				Dialogs.showErrorMessage("Save classifier", ex);
//			}
//		});
//		PaneTools.addGridRow(pane, row++, 0, "Save current thresholder as a pixel classifier", btnSave, btnSave, btnSave);

		
		var classifierName = new SimpleStringProperty(null);
		var tilePane = PaneTools.createRowGrid(
				PixelClassifierUI.createSavePixelClassifierPane(qupath.projectProperty(), currentClassifier, classifierName),
				PixelClassifierUI.createPixelClassifierButtons(qupath.imageDataProperty(), currentClassifier, classifierName)
				);
		tilePane.setVgap(5);
		PaneTools.addGridRow(pane, row++, 0, null, tilePane, tilePane, tilePane);
		
//		var tilePane = PixelClassifierUI.createPixelClassifierButtons(qupath.imageDataProperty(), currentClassifier);
//		PaneTools.addGridRow(pane, row++, 0, null, tilePane, tilePane, tilePane);
		
		selectedPrefilter.addListener((v, o, n) -> updateClassification());
		transforms.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			thresholdValueFactory.setAmountToStepBy(availableTransforms.getOrDefault(n, 0.5));
			updateClassification();
		});
		sigma.addListener((v, o, n) -> updateClassification());
		threshold.addListener((v, o, n) -> updateClassification());
		selectedResolution.addListener((v, o, n) -> updateClassification());
		
		classificationsAbove.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		classificationsBelow.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		
		pane.setVgap(6.0);
		pane.setHgap(6.0);
		pane.setPadding(new Insets(10.0));
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, comboResolutions, comboPrefilter,
				transforms, spinner, sigmaSpinner, classificationsAbove, classificationsBelow,
				btnSave, tilePane);
		PaneTools.setFillWidth(Boolean.TRUE, comboResolutions, comboPrefilter,
				transforms, spinner, sigmaSpinner, classificationsAbove, classificationsBelow,
				btnSave, tilePane);
		PaneTools.setHGrowPriority(Priority.ALWAYS, comboResolutions, comboPrefilter,
				transforms, spinner, sigmaSpinner, classificationsAbove, classificationsBelow,
				btnSave, tilePane);
		
		updateGUI();
		
		pane.setMinSize(GridPane.USE_COMPUTED_SIZE, GridPane.USE_COMPUTED_SIZE);
		pane.setMaxSize(GridPane.USE_COMPUTED_SIZE, GridPane.USE_COMPUTED_SIZE);
		
		stage = new Stage();
		stage.setTitle("Simple threshold");
		stage.initOwner(qupath.getStage());
		stage.setScene(new Scene(pane));
		stage.sizeToScene();
		stage.setResizable(false);
		stage.show();
		
		stage.setOnHiding(e -> resetOverlays());
	}
	
	
	
	private void resetOverlay(QuPathViewer viewer, PathOverlay overlay) {
		if (viewer.getCustomPixelLayerOverlay() == overlay) {
			viewer.resetCustomPixelLayerOverlay();
			var imageData = viewer.getImageData();
			if (imageData != null)
				PixelClassificationImageServer.setPixelLayer(imageData, null);
		}
	}
	
	private void resetOverlays() {
		for (var entry : map.entrySet()) {
			resetOverlay(entry.getKey(), entry.getValue());
		}
		selectedOverlay.set(null);
	}
	
	
	/**
	 * Switch high and low classifications.
	 */
	private void switchClassifications() {
		var below = classificationsBelow.getSelectionModel().getSelectedItem();
		var above = classificationsAbove.getSelectionModel().getSelectedItem();
		classificationsBelow.getSelectionModel().select(above);
		classificationsAbove.getSelectionModel().select(below);
	}
	
	
	private void updateGUI() {
		
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();
		if (imageData == null) {
			transforms.getItems().clear();
			transforms.setDisable(true);
			spinner.setDisable(true);
			sigmaSpinner.setDisable(true);
			return;
		}
		transforms.setDisable(false);
		spinner.setDisable(false);
		sigmaSpinner.setDisable(false);
		
		sigmaSpinner.setEditable(true);
		spinner.setEditable(true);
		
		comboResolutions.getItems().setAll(ClassificationResolution.getDefaultResolutions(imageData, selectedResolution.get()));
		if (selectedResolution.get() == null)
			comboResolutions.getSelectionModel().selectLast();
		
		transforms.getItems().setAll(getAvailableTransforms(imageData));
		if (transforms.getSelectionModel().getSelectedItem() == null)
			transforms.getSelectionModel().selectFirst();
		
	}
	
	private void updateClassification() {
		
//		for (var viewer : qupath.getViewers()) {
//			var imageData = viewer.getImageData();
//			if (imageData == null) {
//				selectedOverlay.set(null);
//				viewer.resetCustomPixelLayerOverlay();
//			}			
//		}
		
		var channel = selectedChannel.get();
		var thresholdValue = threshold.get();
		var resolution = selectedResolution.get();
		if (channel == null || thresholdValue == null || resolution == null) {
			resetOverlays();
			return;
		}
		
		var feature = selectedPrefilter.get();
		double sigmaValue = sigma.get();
		
		PixelClassifier classifier;
		
		List<ImageOp> ops = new ArrayList<>();
		if (feature != null && sigmaValue > 0) {
			ops.add(feature.buildOp(sigmaValue));
		}
		
		ops.add(ImageOps.Threshold.threshold(threshold.get()));
		Map<Integer, PathClass> classifications = new LinkedHashMap<>();
		classifications.put(0, classificationsBelow.getSelectionModel().getSelectedItem());
		classifications.put(1, classificationsAbove.getSelectionModel().getSelectedItem());
		
		var op = ImageOps.Core.sequential(ops);
		
		var transformer = ImageOps.buildImageDataOp(channel).appendOps(op);
		
		classifier = PixelClassifiers.createClassifier(
				transformer,
				resolution.getPixelCalibration(),
				classifications);

		// Create classifier
		var overlay = PixelClassificationOverlay.createPixelClassificationOverlay(qupath.getOverlayOptions(), classifier);
		overlay.setLivePrediction(true);
		selectedOverlay.set(overlay);
		this.currentClassifier.set(classifier);
		
		// Try (admittedly unsuccessfully) to reduce flicker
		for (var viewer : qupath.getViewers()) {
			var imageData = viewer.getImageData();
			if (imageData == null) {
				resetOverlay(viewer, map.get(viewer));
				continue;
			}
			
			viewer.setMinimumRepaintSpacingMillis(1000L);
			viewer.repaint();
			
			viewer.setCustomPixelLayerOverlay(overlay);
			map.put(viewer, overlay);
			imageData.getHierarchy().fireObjectMeasurementsChangedEvent(this, imageData.getHierarchy().getAnnotationObjects());
	
			viewer.resetMinimumRepaintSpacingMillis();
		}
	}
	
	
	
	

	/**
	 * Get a list of relevant color transforms for a specific image.
	 * @param imageData
	 * @return
	 */
	private Collection<ColorTransform> getAvailableTransforms(ImageData<BufferedImage> imageData) {
		var validChannels = new LinkedHashMap<ColorTransform, Double>();
		var server = imageData.getServer();
		double increment = server.getPixelType().isFloatingPoint() ? 0.1 : 0.5;
		double incrementDeconvolved = 0.05;

		for (var channel : server.getMetadata().getChannels()) {
			validChannels.put(ColorTransforms.createChannelExtractor(channel.getName()), increment);
		}
		var stains = imageData.getColorDeconvolutionStains();
		if (stains != null) {
			validChannels.put(ColorTransforms.createColorDeconvolvedChannel(stains, 1), incrementDeconvolved);
			validChannels.put(ColorTransforms.createColorDeconvolvedChannel(stains, 2), incrementDeconvolved);
			validChannels.put(ColorTransforms.createColorDeconvolvedChannel(stains, 3), incrementDeconvolved);
		}
		if (server.nChannels() > 1) {
			validChannels.put(ColorTransforms.createMeanChannelTransform(), increment);
			validChannels.put(ColorTransforms.createMaximumChannelTransform(), increment);
			validChannels.put(ColorTransforms.createMinimumChannelTransform(), increment);
		}
		availableTransforms = validChannels;
		return validChannels.keySet();
	}
	
	
}