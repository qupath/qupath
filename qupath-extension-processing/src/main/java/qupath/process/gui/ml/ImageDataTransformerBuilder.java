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

package qupath.process.gui.ml;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;

import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;

/**
 * Helper class capable of building (or returning) a {@link ImageDataOp}.
 * 
 * @author Pete Bankhead
 */
abstract class ImageDataTransformerBuilder {
	
	private final static Logger logger = LoggerFactory.getLogger(ImageDataTransformerBuilder.class);

	public abstract ImageDataOp build(ImageData<BufferedImage> imageData, PixelCalibration resolution);

	public boolean canCustomize(ImageData<BufferedImage> imageData) {
		return false;
	}

	public boolean doCustomize(ImageData<BufferedImage> imageData) {
		throw new UnsupportedOperationException("Cannot customize this feature calculator!");
	}

	static Collection<ColorTransform> getAvailableChannels(ImageData<?> imageData) {
		List<ColorTransform> list = new ArrayList<>();
		for (var name : getAvailableUniqueChannelNames(imageData.getServer()))
			list.add(ColorTransforms.createChannelExtractor(name));
		var stains = imageData.getColorDeconvolutionStains();
		if (stains != null) {
			list.add(ColorTransforms.createColorDeconvolvedChannel(stains, 1));
			list.add(ColorTransforms.createColorDeconvolvedChannel(stains, 2));
			list.add(ColorTransforms.createColorDeconvolvedChannel(stains, 3));
		}
		return list;
	}
	
	/**
	 * Create a collection representing available unique channel names, logging a warning if a channel name is duplicated
	 * @param server server containing channels
	 * @return set of channel names
	 */
	static Collection<String> getAvailableUniqueChannelNames(ImageServer<?> server) {
		var set = new LinkedHashSet<String>();
		int i = 1;
		for (var c : server.getMetadata().getChannels()) {
			var name = c.getName();
			if (!set.contains(name))
				set.add(name);
			else
				logger.warn("Found duplicate channel name! Will skip channel " + i + " (name '" + name + "')");
			i++;
		}
		return set;
	}
	

//	static class ExtractNeighborsFeatureCalculatorBuilder extends FeatureCalculatorBuilder {
//		
//		private final static Logger logger = LoggerFactory.getLogger(ExtractNeighborsFeatureCalculatorBuilder.class);
//
//		private GridPane pane;
//		private CheckComboBox<String> comboChannels;
//		
//		private ObservableList<String> selectedChannels;
//		private ObservableValue<Integer> selectedRadius;
//			
//		
//		public ExtractNeighborsFeatureCalculatorBuilder(ImageData<BufferedImage> imageData) {
//			
//			int row = 0;
//
//			pane = new GridPane();
//
//			// Selected channels
//
//			var labelChannels = new Label("Channels");
//			comboChannels = new CheckComboBox<String>();
//			GuiTools.installSelectAllOrNoneMenu(comboChannels);
//
//			@SuppressWarnings("resource")
//			var server = imageData == null ? null : imageData.getServer();
//			if (server != null) {
//				comboChannels.getItems().setAll(getAvailableUniqueChannelNames(server));
//				comboChannels.getCheckModel().checkAll();
//			}
//			
//			comboChannels.titleProperty().bind(Bindings.createStringBinding(() -> {
//				int n = comboChannels.getCheckModel().getCheckedItems().size();
//				if (n == 0)
//					return "No channels selected!";
//				if (n == 1)
//					return "1 channel selected";
//				return n + " channels selected";
//			}, comboChannels.getCheckModel().getCheckedItems()));
//
//
//			selectedChannels = comboChannels.getCheckModel().getCheckedItems();
//
//			var comboScales = new ComboBox<Integer>();
//			var labelScales = new Label("Size");
//			comboScales.getItems().addAll(3, 5, 7, 9, 11, 13, 15);
//			comboScales.getSelectionModel().selectFirst();
//			selectedRadius = comboScales.getSelectionModel().selectedItemProperty();
//
//			selectedChannels = comboChannels.getCheckModel().getCheckedItems();
//
//			PaneTools.setMaxWidth(Double.MAX_VALUE, comboChannels, comboScales);
//
//			PaneTools.addGridRow(pane, row++, 0,
//					"Choose the image channels used to calculate features",
//					labelChannels, comboChannels);		
//
//			PaneTools.addGridRow(pane, row++, 0,
//					"Choose the feature scales",
//					labelScales, comboScales);					
//
//			pane.setHgap(5);
//			pane.setVgap(6);
//
//
//			pane.setHgap(5);
//			pane.setVgap(6);
//			
//		}
//		
//		@Override
//		public FeatureCalculator<BufferedImage> build(ImageData<BufferedImage> imageData, PixelCalibration resolution) {
//			return FeatureCalculators.createPatchFeatureCalculator(
//					selectedRadius.getValue(),
//					selectedChannels.toArray(String[]::new));
//		}
//
//		@Override
//		public boolean canCustomize(ImageData<BufferedImage> imageData) {
//			return true;
//		}
//
//		@Override
//		public boolean doCustomize(ImageData<BufferedImage> imageData) {
//			
//			@SuppressWarnings("resource")
//			var server = imageData == null ? null : imageData.getServer();
//			if (server != null) {
//				List<String> channels = new ArrayList<>(getAvailableUniqueChannelNames(server));
//				if (!comboChannels.getItems().equals(channels)) {
//					logger.warn("Image channels changed - will update & select all channels for the feature calculator");
//					comboChannels.getCheckModel().clearChecks();
//					comboChannels.getItems().setAll(channels);
//					comboChannels.getCheckModel().checkAll();
//				}
//			}
//
//			boolean success = Dialogs.showMessageDialog("Select features", pane);
//			if (success) {
//				if (selectedChannels == null || selectedChannels.isEmpty()) {
//					Dialogs.showErrorNotification("Pixel classifier", "No channels selected!");
//					return false;
//				}
//			}
//			return success;
//
//		}
//
//		@Override
//		public String toString() {
//			return "Extract neighbors";
//		}
//
//	}




	static class DefaultFeatureCalculatorBuilder extends ImageDataTransformerBuilder {
		
		private final static Logger logger = LoggerFactory.getLogger(DefaultFeatureCalculatorBuilder.class);
		
		private static enum NormalizationType {
			NONE,
			GAUSSIAN_MEAN,
			GAUSSIAN_MEAN_VARIANCE;
			
			@Override
			public String toString() {
				switch(this) {
				case GAUSSIAN_MEAN:
					return "Local mean subtraction only";
				case GAUSSIAN_MEAN_VARIANCE:
					return "Local mean & variance";
				case NONE:
					return "None";
				default:
					throw new IllegalArgumentException("Unknown normalization " + this);
				}
			}
		}

		private GridPane pane;
		private CheckComboBox<ColorTransform> comboChannels;
		
		private ObservableList<ColorTransform> selectedChannels;
		private ObservableList<Double> selectedSigmas;
		private ObservableList<MultiscaleFeature> selectedFeatures;
		
		private ObservableList<NormalizationType> localNormalizations = FXCollections.observableArrayList(NormalizationType.values());

		private ObservableObjectValue<NormalizationType> normalization;
		private ObservableObjectValue<Double> normalizationSigma;
//		private ObservableBooleanValue do3D;
		
		public DefaultFeatureCalculatorBuilder(ImageData<BufferedImage> imageData) {
			
			int row = 0;

			pane = new GridPane();

			// Selected channels

			var labelChannels = new Label("Channels");
			comboChannels = new CheckComboBox<ColorTransform>();
			GuiTools.installSelectAllOrNoneMenu(comboChannels);
			//			var btnChannels = new Button("Select");
			//			btnChannels.setOnAction(e -> selectChannels());
			@SuppressWarnings("resource")
			var server = imageData == null ? null : imageData.getServer();
			if (server != null) {
				comboChannels.getItems().setAll(getAvailableChannels(imageData));
				comboChannels.getCheckModel().checkIndices(IntStream.range(0, imageData.getServer().nChannels()).toArray());
			}
			
			comboChannels.titleProperty().bind(Bindings.createStringBinding(() -> {
				int n = comboChannels.getCheckModel().getCheckedItems().size();
				if (n == 0)
					return "No channels selected!";
				if (n == 1)
					return "1 channel selected";
				return n + " channels selected";
			}, comboChannels.getCheckModel().getCheckedItems()));


			var comboScales = new CheckComboBox<Double>();
			GuiTools.installSelectAllOrNoneMenu(comboScales);
			var labelScales = new Label("Scales");
			comboScales.getItems().addAll(0.5, 1.0, 2.0, 4.0, 8.0);
			comboScales.getCheckModel().check(1);
			selectedSigmas = comboScales.getCheckModel().getCheckedItems();
			//			comboScales.getCheckModel().check(1.0);

			selectedChannels = comboChannels.getCheckModel().getCheckedItems();


			var comboFeatures = new CheckComboBox<MultiscaleFeature>();
			GuiTools.installSelectAllOrNoneMenu(comboFeatures);
			var labelFeatures = new Label("Features");
			comboFeatures.getItems().addAll(MultiscaleFeature.values());
			comboFeatures.getCheckModel().check(MultiscaleFeature.GAUSSIAN);
			selectedFeatures = comboFeatures.getCheckModel().getCheckedItems();
			//			comboFeatures.getCheckModel().check(MultiscaleFeature.GAUSSIAN);
			//			selectedChannels.addListener((Change<? extends Integer> c) -> updateFeatureCalculator());
			comboFeatures.titleProperty().bind(Bindings.createStringBinding(() -> {
				int n = selectedFeatures.size();
				if (n == 0)
					return "No features selected!";
				if (n == 1)
					return "1 feature selected";
				return n + " features selected";
			},
					selectedFeatures));

			var labelNormalize = new Label("Local normalization");
			var comboNormalize = new ComboBox<>(localNormalizations);
			normalization = comboNormalize.getSelectionModel().selectedItemProperty();
			comboNormalize.getSelectionModel().selectFirst();
			
			var labelNormalizeScale = new Label("Local normalization scale");
			var spinnerNormalize = new Spinner<Double>(0.0, 32.0, 8.0, 1.0);
			normalizationSigma = spinnerNormalize.valueProperty();
			spinnerNormalize.setEditable(true);
			GuiTools.restrictSpinnerInputToNumber(spinnerNormalize, true);
			spinnerNormalize.focusedProperty().addListener((v, o, n) -> {
				if (spinnerNormalize.getEditor().getText().equals(""))
					spinnerNormalize.getValueFactory().valueProperty().set(0.0);
			});
			
//			var cb3D = new CheckBox("Use 3D filters");
//			do3D = cb3D.selectedProperty();


			PaneTools.setMaxWidth(Double.MAX_VALUE, comboChannels, comboFeatures, comboScales,
					comboNormalize, spinnerNormalize);

			PaneTools.addGridRow(pane, row++, 0,
					"Choose the image channels used to calculate features",
					labelChannels, comboChannels);		

			PaneTools.addGridRow(pane, row++, 0,
					"Choose the feature scales",
					labelScales, comboScales);		

			PaneTools.addGridRow(pane, row++, 0,
					"Choose the features",
					labelFeatures, comboFeatures);		

//			PaneTools.addGridRow(pane, row++, 0,
//					"Use 3D filters (rather than 2D)",
//					cb3D, cb3D);	

			PaneTools.addGridRow(pane, row++, 0,
					"Apply local intensity (Gaussian-weighted) normalization before calculating features",
					labelNormalize, comboNormalize);
			
			PaneTools.addGridRow(pane, row++, 0,
					"Amount of smoothing to apply for local normalization",
					labelNormalizeScale, spinnerNormalize);

			//			GridPaneTools.addGridRow(pane, row++, 0,
			//					"Choose the image channels used to calculate features",
			//					labelChannels, comboChannels, btnChannels);




			pane.setHgap(5);
			pane.setVgap(6);
			
		}


		@Override
		public ImageDataOp build(ImageData<BufferedImage> imageData, PixelCalibration resolution) {
			
			if (selectedFeatures == null || selectedSigmas == null)
				throw new IllegalArgumentException("Features and scales must be selected!");

			
			// Extract features, removing any that are incompatible
			MultiscaleFeature[] features;
//			if (do3D.get())
//				features = selectedFeatures.stream().filter(f -> f.supports3D()).toArray(MultiscaleFeature[]::new);
//			else
				features = selectedFeatures.stream().filter(f -> f.supports2D()).toArray(MultiscaleFeature[]::new);

			double[] sigmas = selectedSigmas.stream().mapToDouble(d -> d).toArray();
			
			double varianceScaleRatio = 1.0; // TODO: Make the variance scale ratio editable
			// TODO: Consider reinstating 3D
//			SmoothingScale scale;
//				scale = SmoothingScale.get3DIsotropic(localNormalizeSigma);
//			scale = SmoothingScale.get2D(localNormalizeSigma);

			List<ImageOp> ops = new ArrayList<>();
			for (var sigma : sigmas) {
				ops.add(ImageOps.Filters.features(Arrays.asList(features), sigma, sigma));
			}
			var op = ImageOps.Core.splitMerge(ops);
			
			// Handle normalization if needed
			double localNormalizeSigma = normalizationSigma.get();
			ImageOp opNormalize = null;
			if (localNormalizeSigma > 0) {
				switch (normalization.get()) {
				case GAUSSIAN_MEAN:
					opNormalize = ImageOps.Normalize.localNormalization(localNormalizeSigma, 0);
					break;
				case GAUSSIAN_MEAN_VARIANCE:
					opNormalize = ImageOps.Normalize.localNormalization(localNormalizeSigma, localNormalizeSigma * varianceScaleRatio);
					break;
				case NONE:
				default:
					break;
				}
			}
			if (opNormalize != null)
				op = ImageOps.Core.sequential(opNormalize, op);
//				op = ImageOps.Core.sequential(op, opNormalize);
						
			return ImageOps.buildImageDataOp(selectedChannels).appendOps(op);
		}

		@Override
		public boolean canCustomize(ImageData<BufferedImage> imageData) {
			return true;
		}

		@Override
		public boolean doCustomize(ImageData<BufferedImage> imageData) {
			
			@SuppressWarnings("resource")
			var server = imageData == null ? null : imageData.getServer();
			if (server != null) {
				var channels = new ArrayList<>(getAvailableChannels(imageData));
				if (!comboChannels.getItems().equals(channels)) {
					logger.warn("Image channels changed - will update & select all channels for the feature calculator");
					comboChannels.getCheckModel().clearChecks();
					comboChannels.getItems().setAll(channels);
					comboChannels.getCheckModel().checkIndices(IntStream.range(0, imageData.getServer().nChannels()).toArray());
				}
			}
			
			

			boolean success = Dialogs.showMessageDialog("Select features", pane);
			if (success) {
				if (selectedChannels == null || selectedChannels.isEmpty()) {
					Dialogs.showErrorNotification("Pixel classifier", "No channels selected!");
					return false;
				}

				if (selectedFeatures == null || selectedFeatures.isEmpty()) {
					Dialogs.showErrorNotification("Pixel classifier", "No features selected!");
					return false;
				}
			}
			return success;

		}

		@Override
		public String toString() {
			return "Default multiscale features";
		}

	}



}