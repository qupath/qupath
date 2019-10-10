package qupath.lib.gui.ml;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.GridPane;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.opencv.ml.pixel.features.FeatureCalculator;
import qupath.opencv.ml.pixel.features.FeatureCalculators;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;

/**
 * Helper class capable of building (or returning) a FeatureCalculator.
 * 
 * @author Pete Bankhead
 */
abstract class FeatureCalculatorBuilder {

	public abstract FeatureCalculator<BufferedImage> build(ImageData<BufferedImage> imageData, PixelCalibration resolution);

	public boolean canCustomize(ImageData<BufferedImage> imageData) {
		return false;
	}

	public boolean doCustomize(ImageData<BufferedImage> imageData) {
		throw new UnsupportedOperationException("Cannot customize this feature calculator!");
	}

	//		public String getName() {
	//			OpenCVFeatureCalculator calculator = build(1);
	//			if (calculator == null)
	//				return "No feature calculator";
	//			return calculator.toString();
	//		}


	/**
	 * Add a context menu to a CheckComboBox to quickly select all items, or clear selection.
	 * @param combo
	 */
	static void installSelectAllOrNoneMenu(CheckComboBox<?> combo) {
		var miAll = new MenuItem("Select all");
		var miNone = new MenuItem("Select none");
		miAll.setOnAction(e -> combo.getCheckModel().checkAll());
		miNone.setOnAction(e -> combo.getCheckModel().clearChecks());
		var menu = new ContextMenu(miAll, miNone);
		combo.setContextMenu(menu);
	}
	

	static class ExtractNeighborsFeatureCalculatorBuilder extends FeatureCalculatorBuilder {
		
		private final static Logger logger = LoggerFactory.getLogger(ExtractNeighborsFeatureCalculatorBuilder.class);

		private GridPane pane;
		private CheckComboBox<String> comboChannels;
		
		private ObservableList<String> selectedChannels;
		private ObservableValue<Integer> selectedRadius;
			
		
		public ExtractNeighborsFeatureCalculatorBuilder(ImageData<BufferedImage> imageData) {
			
			int row = 0;

			pane = new GridPane();

			// Selected channels

			var labelChannels = new Label("Channels");
			comboChannels = new CheckComboBox<String>();
			installSelectAllOrNoneMenu(comboChannels);

			@SuppressWarnings("resource")
			var server = imageData == null ? null : imageData.getServer();
			if (server != null) {
				for (var c : server.getMetadata().getChannels())
					comboChannels.getItems().add(c.getName());
				comboChannels.getCheckModel().checkAll();
			}
			
			comboChannels.titleProperty().bind(Bindings.createStringBinding(() -> {
				int n = comboChannels.getCheckModel().getCheckedItems().size();
				if (n == 0)
					return "No channels selected!";
				if (n == 1)
					return "1 channel selected";
				return n + " channels selected";
			}, comboChannels.getCheckModel().getCheckedItems()));


			selectedChannels = comboChannels.getCheckModel().getCheckedItems();

			var comboScales = new ComboBox<Integer>();
			var labelScales = new Label("Size");
			comboScales.getItems().addAll(3, 5, 7, 9, 11, 13, 15);
			comboScales.getSelectionModel().selectFirst();
			selectedRadius = comboScales.getSelectionModel().selectedItemProperty();

			selectedChannels = comboChannels.getCheckModel().getCheckedItems();

			PaneToolsFX.setMaxWidth(Double.MAX_VALUE, comboChannels, comboScales);

			PaneToolsFX.addGridRow(pane, row++, 0,
					"Choose the image channels used to calculate features",
					labelChannels, comboChannels);		

			PaneToolsFX.addGridRow(pane, row++, 0,
					"Choose the feature scales",
					labelScales, comboScales);					

			pane.setHgap(5);
			pane.setVgap(6);


			pane.setHgap(5);
			pane.setVgap(6);
			
		}
		
		@Override
		public FeatureCalculator<BufferedImage> build(ImageData<BufferedImage> imageData, PixelCalibration resolution) {
			return FeatureCalculators.createPatchFeatureCalculator(
					selectedRadius.getValue(),
					selectedChannels.toArray(String[]::new));
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
				List<String> channels = new ArrayList<>();
				for (var c : server.getMetadata().getChannels())
					channels.add(c.getName());		
				if (!comboChannels.getItems().equals(channels)) {
					logger.warn("Image channels changed - will update & select all channels for the feature calculator");
					comboChannels.getCheckModel().clearChecks();
					comboChannels.getItems().setAll(channels);
					comboChannels.getCheckModel().checkAll();
				}
			}

			boolean success = DisplayHelpers.showMessageDialog("Select features", pane);
			if (success) {
				if (selectedChannels == null || selectedChannels.isEmpty()) {
					DisplayHelpers.showErrorNotification("Pixel classifier", "No channels selected!");
					return false;
				}
			}
			return success;

		}

		@Override
		public String toString() {
			return "Extract neighbors";
		}

	}




	static class DefaultFeatureCalculatorBuilder extends FeatureCalculatorBuilder {
		
		private final static Logger logger = LoggerFactory.getLogger(DefaultFeatureCalculatorBuilder.class);

		private GridPane pane;
		private CheckComboBox<String> comboChannels;
		
		private ObservableList<String> selectedChannels;
		private ObservableList<Double> selectedSigmas;
		private ObservableList<MultiscaleFeature> selectedFeatures;

		private ObservableBooleanValue doNormalize;
		private ObservableBooleanValue do3D;
		
		public DefaultFeatureCalculatorBuilder(ImageData<BufferedImage> imageData) {
			
			int row = 0;

			pane = new GridPane();

			// Selected channels

			var labelChannels = new Label("Channels");
			comboChannels = new CheckComboBox<String>();
			installSelectAllOrNoneMenu(comboChannels);
			//			var btnChannels = new Button("Select");
			//			btnChannels.setOnAction(e -> selectChannels());
			@SuppressWarnings("resource")
			var server = imageData == null ? null : imageData.getServer();
			if (server != null) {
				for (var c : server.getMetadata().getChannels())
					comboChannels.getItems().add(c.getName());
				comboChannels.getCheckModel().checkAll();
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
			installSelectAllOrNoneMenu(comboScales);
			var labelScales = new Label("Scales");
			comboScales.getItems().addAll(0.5, 1.0, 2.0, 4.0, 8.0);
			comboScales.getCheckModel().check(1);
			selectedSigmas = comboScales.getCheckModel().getCheckedItems();
			//			comboScales.getCheckModel().check(1.0);

			selectedChannels = comboChannels.getCheckModel().getCheckedItems();


			var comboFeatures = new CheckComboBox<MultiscaleFeature>();
			installSelectAllOrNoneMenu(comboFeatures);
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


			var cbNormalize = new CheckBox("Do local normalization");
			doNormalize = cbNormalize.selectedProperty();

			var cb3D = new CheckBox("Use 3D filters");
			do3D = cb3D.selectedProperty();


			PaneToolsFX.setMaxWidth(Double.MAX_VALUE, comboChannels, comboFeatures, comboScales,
					cbNormalize, cb3D);

			PaneToolsFX.addGridRow(pane, row++, 0,
					"Choose the image channels used to calculate features",
					labelChannels, comboChannels);		

			PaneToolsFX.addGridRow(pane, row++, 0,
					"Choose the feature scales",
					labelScales, comboScales);		

			PaneToolsFX.addGridRow(pane, row++, 0,
					"Choose the features",
					labelFeatures, comboFeatures);		

			PaneToolsFX.addGridRow(pane, row++, 0,
					"Apply local intensity normalization before calculating features",
					cbNormalize, cbNormalize);		

			PaneToolsFX.addGridRow(pane, row++, 0,
					"Use 3D filters (rather than 2D)",
					cb3D, cb3D);	

			//			GridPaneTools.addGridRow(pane, row++, 0,
			//					"Choose the image channels used to calculate features",
			//					labelChannels, comboChannels, btnChannels);




			pane.setHgap(5);
			pane.setVgap(6);
			
		}


		@Override
		public FeatureCalculator<BufferedImage> build(ImageData<BufferedImage> imageData, PixelCalibration resolution) {
			
			if (selectedFeatures == null || selectedSigmas == null)
				throw new IllegalArgumentException("Features and scales must be selected!");

			
			// Extract features, removing any that are incompatible
			MultiscaleFeature[] features;
			if (do3D.get())
				features = selectedFeatures.stream().filter(f -> f.supports3D()).toArray(MultiscaleFeature[]::new);
			else
				features = selectedFeatures.stream().filter(f -> f.supports2D()).toArray(MultiscaleFeature[]::new);

			double[] sigmas = selectedSigmas.stream().mapToDouble(d -> d).toArray();
			String[] channels = selectedChannels.toArray(String[]::new);
			return FeatureCalculators.createMultiscaleFeatureCalculator(
					channels,
					sigmas,
					doNormalize.get() && sigmas.length >= 1 ? sigmas[sigmas.length-1] * 4.0 : 0,
							do3D.get() ? true : false,
									features
					);
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
				List<String> channels = new ArrayList<>();
				for (var c : server.getMetadata().getChannels())
					channels.add(c.getName());			
				if (!comboChannels.getItems().equals(channels)) {
					logger.warn("Image channels changed - will update & select all channels for the feature calculator");
					comboChannels.getCheckModel().clearChecks();
					comboChannels.getItems().setAll(channels);
					comboChannels.getCheckModel().checkAll();
				}
			}
			
			

			boolean success = DisplayHelpers.showMessageDialog("Select features", pane);
			if (success) {
				if (selectedChannels == null || selectedChannels.isEmpty()) {
					DisplayHelpers.showErrorNotification("Pixel classifier", "No channels selected!");
					return false;
				}

				if (selectedFeatures == null || selectedFeatures.isEmpty()) {
					DisplayHelpers.showErrorNotification("Pixel classifier", "No features selected!");
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