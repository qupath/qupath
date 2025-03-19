/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.commands;

import com.google.gson.JsonElement;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import jfxtras.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.display.settings.DisplaySettingUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.InfoMessage;
import qupath.lib.gui.commands.display.BrightnessContrastChannelPane;
import qupath.lib.gui.commands.display.BrightnessContrastHistogramPane;
import qupath.lib.gui.commands.display.BrightnessContrastSettingsPane;
import qupath.lib.gui.commands.display.BrightnessContrastSliderPane;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;

/**
 * Command to show a Brightness/Contrast dialog to adjust the image display.
 */
public class BrightnessContrastCommand implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(BrightnessContrastCommand.class);

	private static final String WARNING_STYLE_CLASS = "warn-label-text";

	private static final double BUTTON_SPACING = 5;

	private final QuPathGUI qupath;

	private final ObjectProperty<QuPathViewer> viewerProperty = new SimpleObjectProperty<>();
	private final ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();
	private final ObjectProperty<ImageDisplay> imageDisplayProperty = new SimpleObjectProperty<>();

	private final ImageDataPropertyChangeListener imageDataPropertyChangeListener = new ImageDataPropertyChangeListener();

	private final ImageDisplayTimestampListener timestampChangeListener = new ImageDisplayTimestampListener();

	private final ObjectProperty<ChannelDisplayInfo> currentChannelProperty = new SimpleObjectProperty<>();

	private final Stage dialog;

	private final BrightnessContrastChannelPane table = new BrightnessContrastChannelPane();

	private final BooleanProperty showGrayscale = new SimpleBooleanProperty(false);
	private final BooleanProperty invertBackground = new SimpleBooleanProperty(false);

	private final BooleanBinding blockChannelAdjustment = table.currentChannelVisible().not();

	private final BrightnessContrastHistogramPane chartPane = new BrightnessContrastHistogramPane();

	private final BrightnessContrastKeyTypedListener keyListener = new BrightnessContrastKeyTypedListener();

	private BrightnessContrastSliderPane sliderPane;

	private BrightnessContrastSettingsPane settingsPane;


	/**
	 * Constructor.
	 * @param qupath
	 */
	public BrightnessContrastCommand(final QuPathGUI qupath) {
		this.qupath = qupath;

		currentChannelProperty.bind(table.currentChannelProperty());

		viewerProperty.bind(qupath.viewerProperty());
		imageDataProperty.bind(viewerProperty.flatMap(QuPathViewer::imageDataProperty));
		imageDisplayProperty.bind(viewerProperty.map(QuPathViewer::getImageDisplay));

		viewerProperty.addListener(this::handleViewerChanged);
		imageDataProperty.addListener(this::handleImageDataChange);
		imageDisplayProperty.addListener(this::handleImageDisplayChanged);

		table.disableToggleMenuItemsProperty().bind(showGrayscale);

		currentChannelProperty.addListener((v, o, n) -> chartPane.updateHistogram(imageDisplayProperty.get(), n));
		chartPane.updateHistogram(imageDisplayProperty.get(), currentChannelProperty.get());

		dialog = createDialog();

		qupath.getDefaultDragDropListener().addJsonDropHandler(this::handleDisplaySettingsDrop);
	}

	/**
	 * Handle drag & drop for display settings on a specified viewer.
	 * @param viewer
	 * @param elements
	 * @return
	 */
	private boolean handleDisplaySettingsDrop(QuPathViewer viewer, List<JsonElement> elements) {
		if (elements.size() != 1 || viewer == null || viewer.getImageDisplay() == null)
			return false;
		var element = elements.get(0);
		var settings = DisplaySettingUtils.parseDisplaySettings(element).orElse(null);
		if (settings != null) {
			if (DisplaySettingUtils.applySettingsToDisplay(viewer.getImageDisplay(), settings)) {
				maybeSyncSettingsAcrossViewers(viewer.getImageDisplay());
				logger.info("Applied display settings: {}", settings.getName());
			} else {
				logger.warn("Unable to apply display settings: {}", settings.getName());
			}
			return true;
		}
		return false;
	}


	@Override
	public void run() {
		if (!dialog.isShowing()) {
			dialog.show();
			chartPane.updateHistogram(imageDisplayProperty.get(), currentChannelProperty.get());
		}
		if (table.getSelectionModel().isEmpty()) {
			var channel = currentChannelProperty.get();
			if (channel != null)
				table.getSelectionModel().select(channel);
		}
	}


	private Stage createDialog() {
		if (dialog != null)
			throw new RuntimeException("createDialog() called after initialization!");

		table.imageDisplayProperty().bind(imageDisplayProperty);

		sliderPane = new BrightnessContrastSliderPane();
		sliderPane.imageDisplayProperty().bind(imageDisplayProperty);
		sliderPane.selectedChannelProperty().bind(currentChannelProperty);
		sliderPane.disableMinMaxAdjustmentProperty().bind(blockChannelAdjustment);

		PathPrefs.keepDisplaySettingsProperty().addListener((v, o, n) -> maybeSyncSettingsAcrossViewers());

		handleImageDataChange(null, null, qupath.getImageData());

		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		FXUtils.addCloseWindowShortcuts(dialog);
		dialog.setTitle("Brightness & contrast");

		GridPane pane = new GridPane();
		int row = 0;

		// Create color/channel display table
		pane.add(table, 0, row++);
		GridPane.setFillHeight(table, Boolean.TRUE);
		GridPane.setVgrow(table, Priority.ALWAYS);

		settingsPane = new BrightnessContrastSettingsPane();
		settingsPane.resourceManagerProperty().bind(
				qupath.projectProperty().map(DisplaySettingUtils::getResourcesForProject));
		pane.add(settingsPane, 0, row++);

		Pane paneCheck = createCheckboxPane();
		paneCheck.setPadding(new Insets(5, 0, 5, 0));
		pane.add(paneCheck, 0, row++);

		pane.add(createSeparator(), 0, row++);

		chartPane.minValueProperty().bindBidirectional(sliderPane.minValueProperty());
		chartPane.maxValueProperty().bindBidirectional(sliderPane.maxValueProperty());
		chartPane.disableProperty().bind(blockChannelAdjustment);

		pane.add(chartPane, 0, row++);

		GridPane paneChannels = new GridPane();
		paneChannels.setHgap(4);
		Label labelChannel = new Label();
		labelChannel.textProperty().bind(Bindings.createStringBinding(() -> {
			var selected = table.currentChannelProperty().get();
			return selected == null ? "" : selected.getName();
		}, table.currentChannelProperty()));
		labelChannel.setStyle("-fx-font-weight: bold;");
		paneChannels.add(labelChannel, 0, 0);
		GridPaneUtils.setToExpandGridPaneWidth(labelChannel);
		GridPane.setHgrow(labelChannel, Priority.SOMETIMES);

		Label labelChannelHidden = new Label();
		labelChannelHidden.setText("(hidden)");
		labelChannelHidden.visibleProperty().bind(
				currentChannelProperty.isNotNull().and(table.currentChannelVisible().not()));
		labelChannelHidden.setStyle("-fx-font-weight: bold; -fx-font-style: italic; " +
				"-fx-font-size: 90%;");
		GridPaneUtils.setToExpandGridPaneWidth(labelChannelHidden);
		paneChannels.add(labelChannelHidden, 1, 0);

		CheckBox cbLogHistogram = new CheckBox("Log histogram");
		cbLogHistogram.selectedProperty().bindBidirectional(chartPane.doLogCountsProperty());
		cbLogHistogram.setTooltip(new Tooltip("Show log values of histogram counts.\n" +
				"This can help to see differences when the histogram values are low relative to the mode."));
		paneChannels.add(cbLogHistogram, 2, 0);

		pane.add(paneChannels, 0, row++);

		sliderPane.prefWidthProperty().bind(pane.widthProperty());
		pane.add(sliderPane, 0, row++);

		Pane paneButtons = createAutoResetButtonPane();
		pane.add(paneButtons, 0, row++);

		pane.add(createSeparator(), 0, row++);

		Pane paneKeepSettings = createKeepSettingsPane();
		pane.add(paneKeepSettings, 0, row++);

		Pane paneWarnings = createWarningPane();
		pane.add(paneWarnings, 0, row++);

		pane.setPadding(new Insets(10, 10, 10, 10));
		pane.setVgap(5);

		Scene scene = new Scene(pane);
		scene.addEventHandler(KeyEvent.KEY_TYPED, keyListener);
		dialog.setScene(scene);
		dialog.setMinWidth(300);
		dialog.setMinHeight(600);
//		dialog.setMaxWidth(600);
		FXUtils.addCloseWindowShortcuts(dialog);

		table.updateTable();

		if (!table.isEmpty())
			table.getSelectionModel().select(0);

		table.setShowChannel(currentChannelProperty.get());
		updateSliders();

		// Update sliders when receiving focus - in case the display has been updated elsewhere
		dialog.focusedProperty().addListener(this::handleDialogFocusChanged);

		paneWarnings.heightProperty().addListener((v, o, n) -> {
			dialog.setHeight(dialog.getHeight() + n.doubleValue() - o.doubleValue());
		});

		return dialog;
	}

	private static Separator createSeparator() {
		var separator = new Separator();
		// Padding helps if we have multiple separators at the top, but not if we have just one at the bottom
//		separator.setPadding(new Insets(5));
		return separator;
	}



	private static Pane createKeepSettingsPane() {
		CheckBox cbKeepDisplaySettings = new CheckBox("Apply to similar images");
		cbKeepDisplaySettings.selectedProperty().bindBidirectional(PathPrefs.keepDisplaySettingsProperty());
		cbKeepDisplaySettings.setTooltip(new Tooltip("Retain same display settings where possible when opening similar images"));
		return new BorderPane(cbKeepDisplaySettings);
	}



	private Pane createAutoResetButtonPane() {

		Button btnAuto = new Button("Auto");
		btnAuto.setOnAction(this::handleAutoButtonClicked);
		btnAuto.disableProperty().bind(blockChannelAdjustment);

		Button btnReset = new Button("Reset");
		btnReset.setOnAction(this::handleResetButtonClicked);
		btnReset.disableProperty().bind(blockChannelAdjustment);

		GridPane pane = GridPaneUtils.createColumnGridControls(
				btnAuto,
				btnReset
		);
		pane.setHgap(BUTTON_SPACING);
		return pane;
	}

	private void handleResetButtonClicked(ActionEvent e) {
		var imageDisplay = imageDisplayProperty.getValue();
		for (ChannelDisplayInfo info : table.getSelectionModel().getSelectedItems()) {
			imageDisplay.setMinMaxDisplay(info, info.getMinAllowed(), info.getMaxAllowed());
		}
		sliderPane.resetAllSliders();
	}

	private void handleAutoButtonClicked(ActionEvent e) {
		var imageDisplay = imageDisplayProperty.getValue();
		if (imageDisplay == null)
			return;
		ChannelDisplayInfo channel = currentChannelProperty.get();
		double saturation = PathPrefs.autoBrightnessContrastSaturationPercentProperty().get()/100.0;
		imageDisplay.autoSetDisplayRange(channel, saturation);
		for (ChannelDisplayInfo info2 : table.getSelectionModel().getSelectedItems()) {
			imageDisplay.autoSetDisplayRange(info2, saturation);
		}
		updateSliders();
	}

	private ObservableList<String> warningList = FXCollections.observableArrayList();

	private ObjectExpression<InfoMessage> infoMessage = Bindings.createObjectBinding(() -> {
		if (warningList.isEmpty())
			return null;
		return InfoMessage.warning(new SimpleIntegerProperty(warningList.size()));
	}, warningList);

	/**
	 * Get a string expression to draw attention to any warnings associated with the current display settings.
	 * This can be used to notify the user that something is amiss, even if the dialog is not open.
	 * @return a string expression that evaluates to the warning text, or null if there are no warnings
	 */
	public ObjectExpression infoMessage() {
		return infoMessage;
	}


	private Pane createWarningPane() {
		var labelWarning = new Label("Inverted background - interpret colors cautiously!");
		labelWarning.setTooltip(new Tooltip("Inverting the background uses processing trickery that reduces the visual information in the image.\n"
				+ "Be careful about interpreting colors, especially for images with multiple channels"));
		labelWarning.getStyleClass().add(WARNING_STYLE_CLASS);
		labelWarning.setAlignment(Pos.CENTER);
		labelWarning.setTextAlignment(TextAlignment.CENTER);
		labelWarning.visibleProperty().bind(invertBackground.and(showGrayscale.not()));
		labelWarning.setMaxWidth(Double.MAX_VALUE);
		labelWarning.managedProperty().bind(labelWarning.visibleProperty()); // Remove if not visible
		labelWarning.visibleProperty().addListener((v, o, n) -> {
			if (n)
				warningList.add(labelWarning.getText());
			else
				warningList.remove(labelWarning.getText());
		});
		if (labelWarning.isVisible())
			warningList.add(labelWarning.getText());

		var labelWarningGamma = new Label("Gamma is not equal to 1.0 - shift+click to reset");
		labelWarningGamma.setOnMouseClicked(this::handleGammaWarningClicked);
		labelWarningGamma.setTooltip(new Tooltip("Adjusting the gamma results in a nonlinear contrast adjustment -\n"
				+ "in science, such changes should usually be disclosed in any figure legends"));
		labelWarningGamma.getStyleClass().add(WARNING_STYLE_CLASS);
		labelWarningGamma.setAlignment(Pos.CENTER);
		labelWarningGamma.setTextAlignment(TextAlignment.CENTER);
		labelWarningGamma.visibleProperty().bind(sliderPane.gammaValueProperty().isNotEqualTo(1.0, 0.0));
		labelWarningGamma.setMaxWidth(Double.MAX_VALUE);
		labelWarningGamma.managedProperty().bind(labelWarningGamma.visibleProperty()); // Remove if not visible

		labelWarningGamma.visibleProperty().addListener((v, o, n) -> {
			if (n)
				warningList.add(labelWarningGamma.getText());
			else
				warningList.remove(labelWarningGamma.getText());
		});
		if (labelWarningGamma.isVisible())
			warningList.add(labelWarningGamma.getText());


		var vboxWarnings = new VBox();
		vboxWarnings.getChildren().setAll(labelWarning, labelWarningGamma);
		return vboxWarnings;
	}




	private Pane createCheckboxPane() {
		CheckBox cbShowGrayscale = new CheckBox("Show grayscale");
		cbShowGrayscale.selectedProperty().bindBidirectional(showGrayscale);
		cbShowGrayscale.setTooltip(new Tooltip("Show single channel with grayscale lookup table"));
//		showGrayscale.addListener(this::handleDisplaySettingInvalidated);

		CheckBox cbInvertBackground = new CheckBox("Invert background");
		cbInvertBackground.selectedProperty().bindBidirectional(invertBackground);
		cbInvertBackground.setTooltip(new Tooltip("Invert the background for display (i.e. switch between white and black).\n"
				+ "Use cautiously to avoid becoming confused about how the 'original' image looks (e.g. brightfield or fluorescence)."));
//		invertBackground.addListener(this::handleDisplaySettingInvalidated);

		HBox paneCheck = new HBox();
		paneCheck.setAlignment(Pos.CENTER);
		paneCheck.getChildren().add(cbShowGrayscale);
		paneCheck.getChildren().add(cbInvertBackground);
		paneCheck.setSpacing(10);
		paneCheck.setMaxHeight(Double.MAX_VALUE);

		return paneCheck;
	}


	private void handleGammaWarningClicked(MouseEvent e) {
		if (e.isShiftDown())
			sliderPane.gammaValueProperty().setValue(1.0);
	}



	/**
	 * Update sliders when receiving focus - in case the display has been updated elsewhere
	 */
	private void handleDialogFocusChanged(ObservableValue<? extends Boolean> observable, Boolean oldValue,
										  Boolean newValue) {
		if (newValue)
			updateSliders();
	}


	/**
	 * Sync settings from the current main viewer to all compatible viewers,
	 * if the relevant preference is selected.
	 */
	private void maybeSyncSettingsAcrossViewers() {
		var viewer = viewerProperty.get();
		if (viewer != null && viewer.hasServer())
			maybeSyncSettingsAcrossViewers(viewer.getImageDisplay());
	}

	/**
	 * Sync settings from the specified display to all compatible viewers,
	 * if the relevant preference is selected.
	 * @param display
	 */
	private void maybeSyncSettingsAcrossViewers(ImageDisplay display) {
		if (display == null || !PathPrefs.keepDisplaySettingsProperty().get())
			return;
		for (var otherViewer : qupath.getAllViewers()) {
			if (!otherViewer.hasServer() || Objects.equals(display, otherViewer.getImageDisplay()))
				continue;
			otherViewer.getImageDisplay().updateFromDisplay(display);
		}
	}



	private void updateSliders() {
		sliderPane.refreshSliders();
	}



	private void handleViewerChanged(ObservableValue<? extends QuPathViewer> source, QuPathViewer oldValue, QuPathViewer newValue) {
		if (oldValue != null) {
			oldValue.getView().removeEventHandler(KeyEvent.KEY_TYPED, keyListener);
		}
		if (newValue != null) {
			newValue.getView().addEventHandler(KeyEvent.KEY_TYPED, keyListener);
		}
	}

	private void handleImageDisplayChanged(ObservableValue<? extends ImageDisplay> source, ImageDisplay oldValue, ImageDisplay newValue) {
		if (oldValue != null) {
			showGrayscale.unbindBidirectional(oldValue.useGrayscaleLutProperty());
			oldValue.useGrayscaleLutProperty().unbindBidirectional(showGrayscale);

			invertBackground.unbindBidirectional(oldValue.useInvertedBackgroundProperty());
			oldValue.useInvertedBackgroundProperty().unbindBidirectional(invertBackground);
			oldValue.switchToGrayscaleChannelProperty().unbind();

			oldValue.eventCountProperty().removeListener(timestampChangeListener);
		}
		if (newValue != null) {
			showGrayscale.bindBidirectional(newValue.useGrayscaleLutProperty());
			invertBackground.bindBidirectional(newValue.useInvertedBackgroundProperty());

			newValue.switchToGrayscaleChannelProperty().bind(currentChannelProperty);
			newValue.eventCountProperty().addListener(timestampChangeListener);

		}
		settingsPane.imageDisplayObjectProperty().set(newValue);
	}

	private void handleImageDataChange(ObservableValue<? extends ImageData<BufferedImage>> source,
			ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {

		if (oldValue != null)
			oldValue.removePropertyChangeListener(imageDataPropertyChangeListener);

		if (newValue != null)
			newValue.addPropertyChangeListener(imageDataPropertyChangeListener);

		updateSliders();
		Platform.runLater(() -> chartPane.updateHistogram(imageDisplayProperty.get(), currentChannelProperty.get()));
	}





	/**
	 * Listener to respond to changes in the ImageData properties (which includes the image type)
	 */
	private class ImageDataPropertyChangeListener implements PropertyChangeListener {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			// Don't respond to changes in the ImageDisplay (which we may have triggered...)
			if (evt.getPropertyName().equals("qupath.lib.display.ImageDisplay"))
				return;

			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(() -> propertyChange(evt));
				return;
			}

			logger.trace("Property change: {}", evt);

			// Update display if we changed something relevant, including
			// - server metadata (including channel names/LUTs)
			// - image type
			// Note that we don't need to respond to all changes
			var imageDisplay = imageDisplayProperty.getValue();
			if (imageDisplay != null) {
				if (evt.getPropertyName().equals("serverMetadata") ||
						((evt.getSource() instanceof ImageData<?>) && evt.getPropertyName().equals("imageType"))) {
					var available = List.copyOf(imageDisplay.availableChannels());
					imageDisplay.refreshChannelOptions();
					// When channels change (e.g. setting RGB image to fluorescence),
					// this is needed to trigger viewer repaint & to save the channels in the properties -
					// otherwise we can get a black image if we save now and reload.
					if (!available.equals(imageDisplay.availableChannels())) {
						imageDisplay.saveChannelColorProperties();
					}
				}
			}

			table.updateTable();
			updateSliders();
		}

	}


	/**
	 * Listener to update the display when the user presses a key.
	 */
	private class BrightnessContrastKeyTypedListener implements EventHandler<KeyEvent> {

		@Override
		public void handle(KeyEvent event) {
			var imageDisplay = imageDisplayProperty.getValue();
			if (imageDisplay == null || event.getEventType() != KeyEvent.KEY_TYPED)
				return;
			String character = event.getCharacter();
			if (character != null && !character.isEmpty()) {
				int c = (int)event.getCharacter().charAt(0) - '0';
				if (c >= 1 && c <= Math.min(9, imageDisplay.availableChannels().size())) {
					if (table != null) {
						table.getSelectionModel().clearAndSelect(c-1);
					}
					table.toggleShowHideChannel(imageDisplay.availableChannels().get(c-1));
					event.consume();
				}
			}
		}

	}

	/**
	 * Listen to the timestamp of the current image display.
	 * This can be used to sync settings across viewers, without needing to listen to many different properties.
	 */
	class ImageDisplayTimestampListener implements InvalidationListener {

		@Override
		public void invalidated(Observable observable) {
			// Delay until the next JavaFX update because it's possible there will be multiple changes in quick succession
			Platform.runLater(() -> maybeSyncSettingsAcrossViewers());
		}
	}

}