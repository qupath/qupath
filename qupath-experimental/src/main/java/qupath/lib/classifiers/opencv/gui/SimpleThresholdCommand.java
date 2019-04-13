package qupath.lib.classifiers.opencv.gui;

import java.util.ArrayList;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.lib.classifiers.gui.PixelClassificationOverlay;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.SimplePixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ChannelDisplayInfo.SingleChannelDisplayInfo;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.GridPaneTools;
import qupath.lib.objects.classes.PathClass;

public class SimpleThresholdCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	private Stage stage;
	
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
	
	private ComboBox<PathClass> classificationsBelow = new ComboBox<>();
	private ComboBox<PathClass> classificationsAbove = new ComboBox<>();
	
	private ComboBox<SingleChannelDisplayInfo> channels = new ComboBox<>();
	private Slider slider = new Slider();
	private ReadOnlyObjectProperty<SingleChannelDisplayInfo> selectedChannel = channels.getSelectionModel().selectedItemProperty();
	private DoubleProperty threshold = slider.valueProperty();
	
	
	private void showGUI() {
		
		var pane = new GridPane();

		classificationsAbove.setItems(qupath.getAvailablePathClasses());
		classificationsBelow.setItems(qupath.getAvailablePathClasses());
		
		int row = 0;
		Label label = new Label("Channel");
		label.setLabelFor(channels);
		GridPaneTools.addGridRow(pane, row++, 0, "Select channel to threshold", label, channels, channels);

		label = new Label("Threshold");
		label.setLabelFor(slider);
		Label labelThreshold = new Label();
		labelThreshold.textProperty().bind(
				Bindings.createStringBinding(() -> GeneralTools.formatNumber(threshold.get(), 2), threshold)
				);
		GridPaneTools.addGridRow(pane, row++, 0, "Select threshold value", label, slider, labelThreshold);

		label = new Label("Above threshold");
		label.setLabelFor(classificationsAbove);
		GridPaneTools.addGridRow(pane, row++, 0, "Select classification for pixels above the thresholds", label, classificationsAbove, classificationsAbove);

		label = new Label("Below threshold");
		label.setLabelFor(classificationsBelow);
		GridPaneTools.addGridRow(pane, row++, 0, "Select classification for pixels below the thresholds", label, classificationsBelow, classificationsBelow);

		
		channels.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			updateClassification();
			if (n != null) {
				slider.setMin(n.getMinAllowed());
				slider.setMax(n.getMaxAllowed());
				slider.setValue((n.getMinAllowed() + n.getMaxAllowed()) / 2.0);
				slider.setBlockIncrement((slider.getMax()-slider.getMin()) / 100.0);
			}
		});
		threshold.addListener((v, o, n) -> {
			if (!slider.isValueChanging())
				updateClassification();
			});
		slider.valueChangingProperty().addListener((v, o, n) -> {
			if (!n)
				updateClassification();
		});
		classificationsAbove.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		classificationsBelow.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		
		pane.setVgap(5.0);
		pane.setHgap(5.0);
		pane.setPadding(new Insets(10.0));
		
		updateGUI();
		
		stage = new Stage();
		stage.setTitle("Threshold");
		stage.initOwner(qupath.getStage());
		stage.setScene(new Scene(pane));
		stage.setAlwaysOnTop(true);
		stage.show();
		
		stage.sizeToScene();
	}
	
	
	private void updateGUI() {
		
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();
		if (imageData == null) {
			channels.getItems().clear();
			channels.setDisable(true);
			slider.setDisable(true);
			return;
		}
		channels.setDisable(false);
		slider.setDisable(false);
		
		var display = viewer.getImageDisplay();
		var validChannels = new ArrayList<SingleChannelDisplayInfo>();
		for (var channel : display.availableChannels()) {
			if (channel instanceof SingleChannelDisplayInfo)
				validChannels.add((SingleChannelDisplayInfo)channel);
		}
		channels.getItems().setAll(validChannels);

		var channel = selectedChannel.get();
		if (channel != null) {
			slider.setMin(channel.getMinAllowed());
			slider.setMax(channel.getMaxAllowed());
			slider.setValue((channel.getMinAllowed() + channel.getMaxAllowed()) / 2.0);
			slider.setBlockIncrement((slider.getMax()-slider.getMin()) / 100.0);
		}
		
	}
	
	private void updateClassification() {
		
		var viewer = qupath.getViewer();
		if (viewer == null)
			return;
		
		viewer.getCustomOverlayLayers().removeIf(o -> o instanceof PixelClassificationOverlay);
		
		var imageData = viewer.getImageData();
		if (imageData == null)
			return;
		
		var classifier = new SimplePixelClassifier(
				selectedChannel.get(),
				32,
				threshold.get(),
				classificationsBelow.getSelectionModel().getSelectedItem(),
				classificationsAbove.getSelectionModel().getSelectedItem());
		
		PixelClassificationImageServer server = new PixelClassificationImageServer(imageData, classifier);
		
//		// TODO: Write through the project entry instead
//		var task = new Task<Boolean>() {
//
//			@Override
//			protected Boolean call() throws Exception {
//				var tiles = server.getAllTileRequests();
//				int n = tiles.size();
//				int i = 0;
//				try {
//					for (var tile : tiles) {
//						updateProgress(i++, n);
//						var request = tile.getRegionRequest();
//						server.readBufferedImage(request);
//					}
//				} catch (IOException e) {
//					DisplayHelpers.showErrorMessage("Pixel classification", e);
//					return false;
//				} finally {
//					updateProgress(n, n);
//				}
//				return true;
//			}
//		};
//		
//		var progress = new ProgressDialog(task);
//		progress.setTitle("Pixel classification");
//		progress.setContentText("Applying threshold");
//		
//		var t = new Thread(task);
//		t.setDaemon(true);
//		t.start();
		
//		try {
//			if (task.get()) {
				var overlay = new PixelClassificationOverlay(viewer, server);
				overlay.setLivePrediction(true);
				viewer.getCustomOverlayLayers().add(overlay);
				imageData.setProperty("PIXEL_LAYER", server);
				imageData.getHierarchy().fireObjectMeasurementsChangedEvent(this, imageData.getHierarchy().getAnnotationObjects());
//			}
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		} catch (ExecutionException e) {
//			e.printStackTrace();
//		}
	}
	

}
