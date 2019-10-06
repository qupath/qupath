package qupath.lib.gui.ml.commands;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.GridPaneTools;
import qupath.lib.gui.ml.PixelClassificationOverlay;
import qupath.lib.gui.ml.PixelClassifierImageSelectionPane;
import qupath.lib.gui.ml.PixelClassifierImageSelectionPane.ClassificationResolution;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ml.pixel.features.ColorTransforms;
import qupath.opencv.ml.pixel.features.ColorTransforms.ColorTransform;

/**
 * Apply simple thresholding to an image via the pixel classification framework to support 
 * thresholding at any resolution, optionally with visual feedback via an overlay.
 * <p>
 * TODO: This is currently unfinished!
 * 
 * @author Pete Bankhead
 *
 */
public class SimpleThresholdCommand implements PathCommand {
	
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
		if (stage == null || !stage.isShowing())
			showGUI();
		else
			stage.toFront();
	}
	
	private ComboBox<ClassificationResolution> comboResolutions = new ComboBox<>();
	private ReadOnlyObjectProperty<ClassificationResolution> selectedResolution = comboResolutions.getSelectionModel().selectedItemProperty();
	
	private ComboBox<PathClass> classificationsBelow = new ComboBox<>();
	private ComboBox<PathClass> classificationsAbove = new ComboBox<>();
	
	private ComboBox<ColorTransform> transforms = new ComboBox<>();
	private ReadOnlyObjectProperty<ColorTransform> selectedChannel = transforms.getSelectionModel().selectedItemProperty();
//	private Slider slider = new Slider();
//	private DoubleProperty threshold = slider.valueProperty();

	private Spinner<Double> spinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0));
	private ReadOnlyObjectProperty<Double> threshold = spinner.valueProperty();

	private Map<QuPathViewer, PathOverlay> map = new WeakHashMap<>();
	
	private void showGUI() {
		
		var pane = new GridPane();

		classificationsAbove.setItems(qupath.getAvailablePathClasses());
		classificationsBelow.setItems(qupath.getAvailablePathClasses());
		
		int row = 0;
		
		Label labelResolution = new Label("Resolution");
		labelResolution.setLabelFor(comboResolutions);
		GridPaneTools.addGridRow(pane, row++, 0, "Select image resolution to threshold", labelResolution, comboResolutions, comboResolutions);
		
		Label label = new Label("Channel");
		label.setLabelFor(transforms);
		GridPaneTools.addGridRow(pane, row++, 0, "Select channel to threshold", label, transforms, transforms);

		label = new Label("Threshold");
		label.setLabelFor(spinner);
		Label labelThreshold = new Label();
		labelThreshold.textProperty().bind(
				Bindings.createStringBinding(() -> GeneralTools.formatNumber(threshold.get(), 2), threshold)
				);
		GridPaneTools.addGridRow(pane, row++, 0, "Select threshold value", label, spinner, labelThreshold);

		label = new Label("Above threshold");
		label.setLabelFor(classificationsAbove);
		GridPaneTools.addGridRow(pane, row++, 0, "Select classification for pixels above the thresholds", label, classificationsAbove, classificationsAbove);

		label = new Label("Below threshold");
		label.setLabelFor(classificationsBelow);
		GridPaneTools.addGridRow(pane, row++, 0, "Select classification for pixels below the thresholds", label, classificationsBelow, classificationsBelow);

		
		transforms.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			updateClassification();
//			if (n != null) {
//				slider.setMin(n.getMinAllowed());
//				slider.setMax(n.getMaxAllowed());
//				slider.setValue((n.getMinAllowed() + n.getMaxAllowed()) / 2.0);
//				slider.setBlockIncrement((slider.getMax()-slider.getMin()) / 100.0);
//			}
		});
		threshold.addListener((v, o, n) -> updateClassification());
		selectedResolution.addListener((v, o, n) -> updateClassification());
		
//		threshold.addListener((v, o, n) -> {
//			if (!slider.isValueChanging())
//				updateClassification();
//			});
//		slider.valueChangingProperty().addListener((v, o, n) -> {
//			if (!n)
//				updateClassification();
//		});
		classificationsAbove.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		classificationsBelow.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		
		pane.setVgap(5.0);
		pane.setHgap(5.0);
		pane.setPadding(new Insets(10.0));
		
		GridPaneTools.setMaxWidth(Double.MAX_VALUE, comboResolutions, transforms, spinner, classificationsAbove, classificationsBelow);
		GridPaneTools.setFillWidth(Boolean.TRUE, comboResolutions, transforms, spinner, classificationsAbove, classificationsBelow);
		
		updateGUI();
		
		stage = new Stage();
		stage.setTitle("Threshold");
		stage.initOwner(qupath.getStage());
		stage.setScene(new Scene(pane));
		stage.setAlwaysOnTop(true);
		stage.show();
		
		stage.sizeToScene();
		
		stage.setOnHiding(e -> {
			for (var entry : map.entrySet()) {
				if (entry.getKey().getCustomPixelLayerOverlay() == entry.getValue())
					entry.getKey().resetCustomPixelLayerOverlay();
			}
		});
	}
	
	
	private void updateGUI() {
		
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();
		if (imageData == null) {
			transforms.getItems().clear();
			transforms.setDisable(true);
			spinner.setDisable(true);
			return;
		}
		transforms.setDisable(false);
		spinner.setDisable(false);
		
		spinner.setEditable(true);
		
		comboResolutions.getItems().setAll(PixelClassifierImageSelectionPane.getDefaultResolutions(imageData, selectedResolution.get()));
		if (selectedResolution.get() == null)
			comboResolutions.getSelectionModel().selectLast();
		
		transforms.getItems().setAll(getAvailableTransforms(imageData));
		if (transforms.getSelectionModel().getSelectedItem() == null)
			transforms.getSelectionModel().selectFirst();
		
//		var display = viewer.getImageDisplay();
//		var validChannels = new ArrayList<SingleChannelDisplayInfo>();
//		for (var channel : display.availableChannels()) {
//			if (channel instanceof SingleChannelDisplayInfo)
//				validChannels.add((SingleChannelDisplayInfo)channel);
//		}
//		transforms.getItems().setAll(validChannels);

//		var channel = selectedChannel.get();
//		if (channel != null) {
//			slider.setMin(channel.getMinAllowed());
//			slider.setMax(channel.getMaxAllowed());
//			slider.setValue((channel.getMinAllowed() + channel.getMaxAllowed()) / 2.0);
//			slider.setBlockIncrement((slider.getMax()-slider.getMin()) / 100.0);
//		}
		
	}
	
	private void updateClassification() {
		
		var viewer = qupath.getViewer();
		if (viewer == null)
			return;
		
		var imageData = viewer.getImageData();
		if (imageData == null) {
			viewer.resetCustomPixelLayerOverlay();
			return;
		}
		
		var transform = selectedChannel.get();
		var thresholdValue = threshold.get();
		var resolution = selectedResolution.get();
		if (transform == null || thresholdValue == null || resolution == null)
			return;
		
		var classifier = PixelClassifiers.createThresholdingClassifier(
				transform,
				resolution.getPixelCalibration(),
				thresholdValue,
				classificationsBelow.getSelectionModel().getSelectedItem(),
				classificationsAbove.getSelectionModel().getSelectedItem());
		
//		PixelClassificationImageServer server = new PixelClassificationImageServer(imageData, classifier);

		var overlay = new PixelClassificationOverlay(viewer, classifier);
		overlay.setLivePrediction(true);
		viewer.setCustomPixelLayerOverlay(overlay);
		map.put(viewer, overlay);
		imageData.getHierarchy().fireObjectMeasurementsChangedEvent(this, imageData.getHierarchy().getAnnotationObjects());

	}
	

	/**
	 * Get a list of relevant color transforms for a specific image.
	 * @param imageData
	 * @return
	 */
	public static List<ColorTransform> getAvailableTransforms(ImageData<BufferedImage> imageData) {
		var validChannels = new ArrayList<ColorTransform>();
		var server = imageData.getServer();
		for (var channel : server.getMetadata().getChannels()) {
			validChannels.add(ColorTransforms.createChannelExtractor(channel.getName()));
		}
		return validChannels;
	}
	
	
}
