package qupath.lib.classifiers.opencv.gui;

import java.util.ArrayList;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import qupath.lib.classifiers.gui.PixelClassificationOverlay;
import qupath.lib.display.ChannelDisplayInfo.SingleChannelDisplayInfo;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.objects.classes.PathClass;

public class SimpleThresholdCommand implements PathCommand {
	
	private QuPathGUI qupath;
	
	public SimpleThresholdCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		showGUI();
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

	}
	
	
	private void updateGUI() {
		
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();
		if (imageData == null) {
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
		
//		var classifier = new SimplePixelClassifier(
//				selectedChannel.get(),
//				4,
//				threshold,
//				classificationsBelow.getSelectionModel().getSelectedItem(),
//				classificationsAbove.getSelectionModel().getSelectedItem());
		
//		var overlay = new PixelClassificationOverlay(viewer, classifier);
//		viewer.getCustomOverlayLayers().add(overlay);
	}
	

}
