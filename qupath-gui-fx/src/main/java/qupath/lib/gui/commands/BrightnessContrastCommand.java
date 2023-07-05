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

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.HistogramPanelFX;
import qupath.lib.gui.charts.HistogramPanelFX.HistogramData;
import qupath.lib.gui.charts.HistogramPanelFX.ThresholdedChartWrapper;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Command to show a Brightness/Contrast dialog to adjust the image display.
 * 
 * @author Pete Bankhead
 *
 */
public class BrightnessContrastCommand implements Runnable {
	
	private static Logger logger = LoggerFactory.getLogger(BrightnessContrastCommand.class);
	
	private static DecimalFormat df = new DecimalFormat("#.###");

	/**
	 * Style used for labels that display warning text.
	 */
	private static String WARNING_STYLE = "-fx-text-fill: -qp-script-error-color;";

	private static double BUTTON_SPACING = 5;

	private QuPathGUI qupath;
	private QuPathViewer viewer;
	private ImageDisplay imageDisplay;

	private ImageDataPropertyChangeListener imageDataPropertyChangeListener = new ImageDataPropertyChangeListener();

	private SelectedChannelsChangeListener selectedChannelsChangeListener = new SelectedChannelsChangeListener();

	private Slider sliderMin;
	private Slider sliderMax;
	private Slider sliderGamma;
	private Stage dialog;
	
	private boolean slidersUpdating = false;

	private TableView<ChannelDisplayInfo> table = new TableView<>();
	private StringProperty filterText = new SimpleStringProperty("");
	private BooleanProperty useRegex = PathPrefs.createPersistentPreference("brightnessContrastFilterRegex", false);
	private ObjectBinding<Predicate<ChannelDisplayInfo>> predicate = createChannelDisplayPredicateBinding(filterText);

	/**
	 * Checkbox used to quickly turn on or off all channels
	 */
	private CheckBox cbShowAll = new CheckBox();

	private ColorPicker picker = new ColorPicker();
	
	private HistogramPanelFX histogramPanel = new HistogramPanelFX();
	private ThresholdedChartWrapper chartWrapper = new ThresholdedChartWrapper(histogramPanel.getChart());
	
	private Tooltip chartTooltip = new Tooltip(); // Basic stats go here now
	private ContextMenu popup;
	private BooleanProperty showGrayscale = new SimpleBooleanProperty(false);
	private BooleanProperty invertBackground = new SimpleBooleanProperty(false);
	
	private BrightnessContrastKeyTypedListener keyListener = new BrightnessContrastKeyTypedListener();
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public BrightnessContrastCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.qupath.imageDataProperty().addListener(this::handleImageDataChange);
	}

	@Override
	public void run() {
		if (dialog == null)
			dialog = createDialog();
		dialog.show();
		updateShowTableColumnHeader();
	}

	private void initializeColorPicker() {
		// Add 'pure' red, green & blue to the available color picker colors
		picker.getCustomColors().setAll(
				ColorToolsFX.getCachedColor(255, 0, 0),
				ColorToolsFX.getCachedColor(0, 255, 0),
				ColorToolsFX.getCachedColor(0, 0, 255),
				ColorToolsFX.getCachedColor(255, 255, 0),
				ColorToolsFX.getCachedColor(0, 255, 255),
				ColorToolsFX.getCachedColor(255, 0, 255));
	}


	private Stage createDialog() {
		if (isInitialized())
			throw new RuntimeException("createDialog() called after initialization!");

		initializeShowAllCheckbox();
		initializeSliders();
		initializeColorPicker();
		initializePopup();

		handleImageDataChange(null, null, qupath.getImageData());

		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Brightness & contrast");

		GridPane pane = new GridPane();
		int row = 0;

		// Create color/channel display table
		table = createChannelDisplayTable();
		pane.add(table, 0, row++);
		GridPane.setFillHeight(table, Boolean.TRUE);
		GridPane.setVgrow(table, Priority.ALWAYS);

		Pane paneTextFilter = createTextFilterPane();
		pane.add(paneTextFilter, 0, row++);

		Pane paneCheck = createCheckboxPane();
		pane.add(paneCheck, 0, row++);

		histogramPanel.setShowTickLabels(false);
		histogramPanel.getChart().setAnimated(false);
		var chartPane = chartWrapper.getPane();
		chartPane.setPrefWidth(200);
		chartPane.setPrefHeight(150);
		pane.add(chartPane, 0, row++);
//		pane.add(histogramPanel.getChart(), 0, row++);

		Pane paneSliders = createSliderPane();
		paneSliders.prefWidthProperty().bind(pane.widthProperty());
		pane.add(paneSliders, 0, row++);

		Pane paneButtons = createAutoResetButtonPane();
		pane.add(paneButtons, 0, row++);

		Pane paneWarnings = createWarningPane();
		pane.add(paneWarnings, 0, row++);

		Pane paneKeepSettings = createKeepSettingsPane();
		pane.add(paneKeepSettings, 0, row++);

		pane.setPadding(new Insets(10, 10, 10, 10));
		pane.setVgap(5);

		Scene scene = new Scene(pane, 350, 580);
		scene.addEventHandler(KeyEvent.KEY_TYPED, keyListener);
		dialog.setScene(scene);
		dialog.setMinWidth(300);
		dialog.setMinHeight(400);
		dialog.setMaxWidth(600);

		updateTable();

		if (!table.getItems().isEmpty())
			table.getSelectionModel().select(0);

		setShowChannel(getCurrentInfo());
		updateHistogram();
		updateSliders();

		// Update sliders when receiving focus - in case the display has been updated elsewhere
		dialog.focusedProperty().addListener(this::handleDialogFocusChanged);

		paneWarnings.heightProperty().addListener((v, o, n) -> {
			dialog.setHeight(dialog.getHeight() + n.doubleValue() - o.doubleValue());
		});

		return dialog;
	}


	private Pane createSliderPane() {
		GridPane box = new GridPane();
		String blank = "      ";
		Label labelMin = new Label("Min display");
		Tooltip tooltipMin = new Tooltip("Set minimum lookup table value - double-click the value to edit manually");
		Label labelMinValue = new Label(blank);
		labelMinValue.setTooltip(tooltipMin);
		labelMin.setTooltip(tooltipMin);
		sliderMin.setTooltip(tooltipMin);
		labelMin.setLabelFor(sliderMin);
		labelMinValue.textProperty().bind(createSliderTextBinding(sliderMin));
		box.add(labelMin, 0, 0);
		box.add(sliderMin, 1, 0);
		box.add(labelMinValue, 2, 0);

		Label labelMax = new Label("Max display");
		Tooltip tooltipMax = new Tooltip("Set maximum lookup table value - double-click the value to edit manually");
		labelMax.setTooltip(tooltipMax);
		Label labelMaxValue = new Label(blank);
		labelMaxValue.setTooltip(tooltipMax);
		sliderMax.setTooltip(tooltipMax);
		labelMax.setLabelFor(sliderMax);
		labelMaxValue.textProperty().bind(createSliderTextBinding(sliderMax));
		box.add(labelMax, 0, 1);
		box.add(sliderMax, 1, 1);
		box.add(labelMaxValue, 2, 1);
		box.setVgap(5);

		Label labelGamma = new Label("Gamma");
		Label labelGammaValue = new Label(blank);
		Tooltip tooltipGamma = new Tooltip("Set gamma value, for all viewers.\n"
				+ "Double-click the value to edit manually, shift-click to reset to 1.\n"
				+ "It is recommended to leave this value at 1, to avoid unnecessary nonlinear contrast adjustment.");
		labelGammaValue.setTooltip(tooltipGamma);
		labelGammaValue.textProperty().bind(createGammaLabelBinding(sliderGamma.valueProperty()));
		sliderGamma.setTooltip(tooltipGamma);
		labelGamma.setLabelFor(sliderGamma);
		labelGamma.setTooltip(tooltipGamma);
		labelGammaValue.setOnMouseClicked(this::handleGammaLabelClicked);
		labelGammaValue.styleProperty().bind(createGammaLabelStyleBinding(sliderGamma.valueProperty()));

		box.add(labelGamma, 0, 2);
		box.add(sliderGamma, 1, 2);
		box.add(labelGammaValue, 2, 2);

		GridPane.setFillWidth(sliderMin, Boolean.TRUE);
		GridPane.setFillWidth(sliderMax, Boolean.TRUE);
//		box.setPadding(new Insets(5, 0, 5, 0));
		GridPane.setHgrow(sliderMin, Priority.ALWAYS);
		GridPane.setHgrow(sliderMax, Priority.ALWAYS);

		// In the absence of a better way, make it possible to enter display range values
		// manually by double-clicking on the corresponding label
		labelMinValue.setOnMouseClicked(this::handleMinLabelClick);
		labelMaxValue.setOnMouseClicked(this::handleMaxLabelClick);
		return box;
	}


	private Pane createKeepSettingsPane() {
		CheckBox cbKeepDisplaySettings = new CheckBox("Keep settings");
		cbKeepDisplaySettings.selectedProperty().bindBidirectional(PathPrefs.keepDisplaySettingsProperty());
		cbKeepDisplaySettings.setTooltip(new Tooltip("Retain same display settings where possible when opening similar images"));
		return new BorderPane(cbKeepDisplaySettings);
	}


	private Pane createAutoResetButtonPane() {
		Button btnAuto = new Button("Auto");
		btnAuto.setOnAction(this::handleAutoButtonClicked);

		Button btnReset = new Button("Reset");
		btnReset.setOnAction(this::handleResetButtonClicked);

		GridPane pane = GridPaneUtils.createColumnGridControls(
				btnAuto,
				btnReset
		);
		if (BUTTON_SPACING > 0)
			pane.setHgap(BUTTON_SPACING);
		return pane;
	}


	private Pane createWarningPane() {
		var labelWarning = new Label("Inverted background - interpret colors cautiously!");
		labelWarning.setTooltip(new Tooltip("Inverting the background uses processing trickery that reduces the visual information in the image.\n"
				+ "Be careful about interpreting colors, especially for images with multiple channels"));
		labelWarning.setStyle(WARNING_STYLE);
		labelWarning.setAlignment(Pos.CENTER);
		labelWarning.setTextAlignment(TextAlignment.CENTER);
		labelWarning.visibleProperty().bind(invertBackground.and(showGrayscale.not()));
		labelWarning.setMaxWidth(Double.MAX_VALUE);
		labelWarning.managedProperty().bind(labelWarning.visibleProperty()); // Remove if not visible

		var labelWarningGamma = new Label("Gamma is not equal to 1.0 - shift+click to reset");
		labelWarningGamma.setOnMouseClicked(this::handleGammaWarningClicked);
		labelWarningGamma.setTooltip(new Tooltip("Adjusting the gamma results in a nonlinear contrast adjustment -\n"
				+ "in science, such changes should usually be disclosed in any figure legends"));
		labelWarningGamma.setStyle(WARNING_STYLE);
		labelWarningGamma.setAlignment(Pos.CENTER);
		labelWarningGamma.setTextAlignment(TextAlignment.CENTER);
		labelWarningGamma.visibleProperty().bind(sliderGamma.valueProperty().isNotEqualTo(1.0, 0.0));
		labelWarningGamma.setMaxWidth(Double.MAX_VALUE);
		labelWarningGamma.managedProperty().bind(labelWarningGamma.visibleProperty()); // Remove if not visible

		var vboxWarnings = new VBox();
		vboxWarnings.getChildren().setAll(labelWarning, labelWarningGamma);
		return vboxWarnings;
	}


	private TableView<ChannelDisplayInfo> createChannelDisplayTable() {
		TableView<ChannelDisplayInfo> table = new TableView<>(imageDisplay == null ? FXCollections.observableArrayList() : imageDisplay.availableChannels());
		var textPlaceholder = new Text("No channels available");
		textPlaceholder.setStyle("-fx-fill: -fx-text-base-color;");
		table.setPlaceholder(textPlaceholder);
		table.addEventHandler(KeyEvent.KEY_PRESSED, new ChannelTableKeypressedListener());

		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		table.getSelectionModel().selectedItemProperty().addListener(this::handleSelectedChannelChanged);

		TableColumn<ChannelDisplayInfo, ChannelDisplayInfo> col1 = new TableColumn<>("Channel");
		col1.setId("channel-column");
		col1.setCellValueFactory(this::channelCellValueFactory);
		col1.setCellFactory(column -> new ChannelDisplayTableCell()); // Not using shared custom color list!
																	  // Could change in the future if needed

		col1.setSortable(false);
		TableColumn<ChannelDisplayInfo, Boolean> col2 = new TableColumn<>("Show");
		col2.setId("show-column");
		col2.setCellValueFactory(this::showChannelCellValueFactory);
		col2.setCellFactory(column -> new ShowChannelDisplayTableCell());
		col2.setSortable(false);
		col2.setEditable(true);
		col2.setResizable(false);


		// Handle color change requests when an appropriate row is double-clicked
		table.setRowFactory(this::createTableRow);

		table.getColumns().add(col1);
		table.getColumns().add(col2);
		table.setEditable(true);
		table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
		col1.prefWidthProperty().bind(table.widthProperty().subtract(col2.widthProperty()).subtract(25)); // Hack... space for a scrollbar

		return table;
	}

	private void initializeShowAllCheckbox() {
		cbShowAll.setTooltip(new Tooltip("Show/hide all channels"));
		cbShowAll.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		cbShowAll.setIndeterminate(true);
		// Use action listener because we may change selection status elsewhere
		// in response to the selected channels being modified elsewhere
		cbShowAll.setOnAction(e -> syncShowAllToCheckbox());
	}

	private void syncShowAllToCheckbox() {
		if (imageDisplay == null || cbShowAll.isIndeterminate())
			return;
		if (cbShowAll.isSelected()) {
			setShowChannels(table.getItems());
		} else {
			setHideChannels(table.getItems());
		}
	}

	/**
	 * Install the checkbox for showing all channels
	 */
	private void updateShowTableColumnHeader() {
		var header = table.lookup("#show-column > .label");
		if (header instanceof Label label) {
			label.setContentDisplay(ContentDisplay.RIGHT);
			label.setGraphicTextGap(5);
			if (cbShowAll.isVisible())
				label.setGraphic(cbShowAll);
			// Bind visibility property to whether the checkbox is added to the label or not
			cbShowAll.visibleProperty().addListener((v, o, n) -> label.setGraphic(n ? cbShowAll : null));
		}
	}


//	private Pane createCheckboxPane() {
//		ToggleButton cbShowGrayscale = new ToggleButton("Show grayscale");
//		cbShowGrayscale.selectedProperty().bindBidirectional(showGrayscale);
//		cbShowGrayscale.setTooltip(new Tooltip("Show single channel with grayscale lookup table"));
//		if (imageDisplay != null)
//			cbShowGrayscale.setSelected(!imageDisplay.useColorLUTs());
//		showGrayscale.addListener(this::handleDisplaySettingInvalidated);
//
//		ToggleButton cbInvertBackground = new ToggleButton("Invert background");
//		cbInvertBackground.selectedProperty().bindBidirectional(invertBackground);
//		cbInvertBackground.setTooltip(new Tooltip("Invert the background for display (i.e. switch between white and black).\n"
//				+ "Use cautiously to avoid becoming confused about how the 'original' image looks (e.g. brightfield or fluorescence)."));
//		if (imageDisplay != null)
//			cbInvertBackground.setSelected(imageDisplay.useInvertedBackground());
//		invertBackground.addListener(this::handleDisplaySettingInvalidated);
//
//		return GridPaneUtils.createColumnGridControls(cbShowGrayscale, cbInvertBackground);
//	}


	private Pane createCheckboxPane() {
		CheckBox cbShowGrayscale = new CheckBox("Show grayscale");
		cbShowGrayscale.selectedProperty().bindBidirectional(showGrayscale);
		cbShowGrayscale.setTooltip(new Tooltip("Show single channel with grayscale lookup table"));
		if (imageDisplay != null)
			cbShowGrayscale.setSelected(!imageDisplay.useColorLUTs());
		showGrayscale.addListener(this::handleDisplaySettingInvalidated);

		CheckBox cbInvertBackground = new CheckBox("Invert background");
		cbInvertBackground.selectedProperty().bindBidirectional(invertBackground);
		cbInvertBackground.setTooltip(new Tooltip("Invert the background for display (i.e. switch between white and black).\n"
				+ "Use cautiously to avoid becoming confused about how the 'original' image looks (e.g. brightfield or fluorescence)."));
		if (imageDisplay != null)
			cbInvertBackground.setSelected(imageDisplay.useInvertedBackground());
		invertBackground.addListener(this::handleDisplaySettingInvalidated);

		FlowPane paneCheck = new FlowPane();
		paneCheck.setAlignment(Pos.CENTER);
		paneCheck.setVgap(5);
		paneCheck.getChildren().add(cbShowGrayscale);
		paneCheck.getChildren().add(cbInvertBackground);
		paneCheck.setHgap(15);
		paneCheck.setMaxHeight(Double.MAX_VALUE);
		return paneCheck;
	}



	private ObservableValue<ChannelDisplayInfo> channelCellValueFactory(
			TableColumn.CellDataFeatures<ChannelDisplayInfo, ChannelDisplayInfo> features) {
		return new SimpleObjectProperty<>(features.getValue());
	}

	private ObservableValue<Boolean> showChannelCellValueFactory(
			TableColumn.CellDataFeatures<ChannelDisplayInfo, Boolean> features) {
		SimpleBooleanProperty property = new SimpleBooleanProperty(
				isChannelShowing(features.getValue()));
		property.addListener((v, o, n) -> {
			if (n)
				setShowChannel(features.getValue());
			else
				setHideChannel(features.getValue());
		});
		return property;
	}

	private static ObservableValue<String> createGammaLabelBinding(ObservableValue<? extends Number> gammaValue) {
		return Bindings.createStringBinding(() ->
						GeneralTools.formatNumber(gammaValue.getValue().doubleValue(), 2),
				gammaValue);
	}

	private static ObservableValue<String> createGammaLabelStyleBinding(ObservableValue<? extends Number> gammaValue) {
		return Bindings.createStringBinding(() -> {
			if (gammaValue.getValue().doubleValue() == 1.0)
				return null;
			return WARNING_STYLE;
		}, gammaValue);
	}

	private void handleGammaLabelClicked(MouseEvent event) {
		if (event.getClickCount() >= 3 || event.isShiftDown()) {
			// Reset gamma to 1.0
			sliderGamma.setValue(1.0);
		} else {
			var newGamma = Dialogs.showInputDialog("Gamma", "Set gamma value", sliderGamma.getValue());
			if (newGamma != null)
				sliderGamma.setValue(newGamma);
		}
	}

	private void handleMaxLabelClick(MouseEvent event) {
		if (event.getClickCount() != 2)
			return;
		ChannelDisplayInfo infoVisible = getCurrentInfo();
		if (infoVisible == null)
			return;

		Double value = Dialogs.showInputDialog("Display range", "Set display range maximum", (double)infoVisible.getMaxDisplay());
		if (value != null && !Double.isNaN(value)) {
			sliderMax.setValue(value);
			// Update display directly if out of slider range
			if (value < sliderMax.getMin() || value > sliderMax.getMax()) {
				imageDisplay.setMinMaxDisplay(infoVisible, (float)infoVisible.getMinDisplay(), (float)value.floatValue());
				updateSliders();
				viewer.repaintEntireImage();
			}
		}
	}


	private void handleMinLabelClick(MouseEvent event) {
		if (event.getClickCount() != 2)
			return;
		ChannelDisplayInfo infoVisible = getCurrentInfo();
		if (infoVisible == null)
			return;

		Double value = Dialogs.showInputDialog("Display range", "Set display range minimum", (double)infoVisible.getMinDisplay());
		if (value != null && !Double.isNaN(value)) {
			sliderMin.setValue(value);
			// Update display directly if out of slider range
			if (value < sliderMin.getMin() || value > sliderMin.getMax()) {
				imageDisplay.setMinMaxDisplay(infoVisible, value.floatValue(), infoVisible.getMaxDisplay());
				updateSliders();
				viewer.repaintEntireImage();
			}
		}
	}


	/**
	 * Simple invalidation listener to request an image repaint when a display setting changes.
	 */
	private void handleDisplaySettingInvalidated(Observable observable) {
		if (imageDisplay == null)
			return;
		Platform.runLater(() -> viewer.repaintEntireImage());
		table.refresh();
	}


	private void handleGammaWarningClicked(MouseEvent e) {
		if (e.isShiftDown())
			sliderGamma.setValue(1.0);
	}


	private ObservableValue<String> createSliderTextBinding(Slider slider) {
		return Bindings.createStringBinding(() -> {
			double value = slider.getValue();
			if (value == (int)value)
				return String.format("%d", (int) value);
			else if (value < 1)
				return String.format("%.3f", value);
			else if (value < 10)
				return String.format("%.2f", value);
			else
				return String.format("%.1f", value);
		}, slider.valueProperty());
	}


	private void handleResetButtonClicked(ActionEvent e) {
		for (ChannelDisplayInfo info : table.getSelectionModel().getSelectedItems()) {
			imageDisplay.setMinMaxDisplay(info, info.getMinAllowed(), info.getMaxAllowed());
		}
		sliderMin.setValue(sliderMin.getMin());
		sliderMax.setValue(sliderMax.getMax());
		sliderGamma.setValue(1.0);
	}

	private void handleAutoButtonClicked(ActionEvent e) {
		if (imageDisplay == null)
			return;
		ChannelDisplayInfo info = getCurrentInfo();
		double saturation = PathPrefs.autoBrightnessContrastSaturationPercentProperty().get()/100.0;
		imageDisplay.autoSetDisplayRange(info, saturation);
		for (ChannelDisplayInfo info2 : table.getSelectionModel().getSelectedItems()) {
			imageDisplay.autoSetDisplayRange(info2, saturation);
		}
		updateSliders();
		applyMinMaxSliderChanges();
	}
	
	private boolean isInitialized() {
		return sliderMin != null && sliderMax != null;
	}
	
	private void initializeSliders() {
		sliderMin = new Slider(0, 255, 0);
		sliderMax = new Slider(0, 255, 255);
		sliderMin.valueProperty().addListener(this::handleMinMaxSliderValueChange);
		sliderMax.valueProperty().addListener(this::handleMinMaxSliderValueChange);
		sliderGamma = new Slider(0.01, 5, 0.01);
		sliderGamma.valueProperty().bindBidirectional(PathPrefs.viewerGammaProperty());
	}
	
	private Pane createTextFilterPane() {
		TextField tfFilter = new TextField("");
		tfFilter.textProperty().bindBidirectional(filterText);
		tfFilter.setTooltip(new Tooltip("Enter text to find specific channels by name"));
		tfFilter.promptTextProperty().bind(Bindings.createStringBinding(() -> {
			if (useRegex.get())
				return "Filter channels by regular expression";
			else
				return "Filter channels by name";
		}, useRegex));
		predicate.addListener((v, o, n) -> updatePredicate());

		ToggleButton btnRegex = new ToggleButton(".*");
		btnRegex.setTooltip(new Tooltip("Use regular expressions for channel filter"));
		btnRegex.selectedProperty().bindBidirectional(useRegex);

		GridPane pane = new GridPane();
		GridPaneUtils.setToExpandGridPaneWidth(tfFilter);
		pane.add(tfFilter, 0, 0);
		pane.add(btnRegex, 1, 0);
		if (BUTTON_SPACING > 0)
			pane.setHgap(BUTTON_SPACING);
		return pane;
	}


	private TableRow<ChannelDisplayInfo> createTableRow(TableView<ChannelDisplayInfo> table) {
		TableRow<ChannelDisplayInfo> row = new TableRow<>();
		row.setOnMouseClicked(e -> handleTableRowMouseClick(row, e));
		return row;
	}

	private void handleTableRowMouseClick(TableRow<ChannelDisplayInfo> row, MouseEvent event) {
		if (event.getClickCount() != 2)
			return;

		ChannelDisplayInfo info = row.getItem();
		var imageData = viewer.getImageData();
		if (info instanceof DirectServerChannelInfo && imageData != null) {
			DirectServerChannelInfo multiInfo = (DirectServerChannelInfo)info;
			int c = multiInfo.getChannel();
			var channel = imageData.getServer().getMetadata().getChannel(c);

			Color color = ColorToolsFX.getCachedColor(multiInfo.getColor());
			picker.setValue(color);


			Dialog<ButtonType> colorDialog = new Dialog<>();
			colorDialog.setTitle("Channel properties");

			colorDialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);

			var paneColor = new GridPane();
			int r = 0;
			var labelName = new Label("Channel name");
			var tfName = new TextField(channel.getName());
			labelName.setLabelFor(tfName);
			GridPaneUtils.addGridRow(paneColor, r++, 0,
					"Enter a name for the current channel", labelName, tfName);
			var labelColor = new Label("Channel color");
			labelColor.setLabelFor(picker);
			GridPaneUtils.setFillWidth(Boolean.TRUE, picker, tfName);
			GridPaneUtils.addGridRow(paneColor, r++, 0,
					"Choose the color for the current channel", labelColor, picker);
			paneColor.setVgap(5.0);
			paneColor.setHgap(5.0);

			colorDialog.getDialogPane().setContent(paneColor);
			Platform.runLater(() -> tfName.requestFocus());
			Optional<ButtonType> result = colorDialog.showAndWait();
			if (result.orElse(ButtonType.CANCEL) == ButtonType.APPLY) {
				String name = tfName.getText().trim();
				if (name.isEmpty()) {
					Dialogs.showErrorMessage("Set channel name", "The channel name must not be empty!");
					return;
				}
				Color color2 = picker.getValue();
				if (color == color2 && name.equals(channel.getName()))
					return;

				// Update the server metadata
				updateChannelColor(multiInfo, name, color2);
			}
		}
	}

	private void updateChannelColor(DirectServerChannelInfo channel,
										   String newName, Color newColor) {
		var imageData = viewer.getImageData();
		if (imageData == null) {
			logger.warn("Cannot update channel color: no image data");
			return;
		}
		var server = imageData.getServer();
		if (server.isRGB()) {
			logger.warn("Cannot update channel color for RGB images");
			return;
		}
		Objects.requireNonNull(channel, "Channel cannot be null");
		Objects.requireNonNull(newName, "Channel name cannot be null");
		Objects.requireNonNull(newColor, "Channel color cannot be null");

		int channelIndex = channel.getChannel();
		var metadata = server.getMetadata();
		var channels = new ArrayList<>(metadata.getChannels());
		channels.set(channelIndex, ImageChannel.getInstance(newName, ColorToolsFX.getRGB(newColor)));
		var metadata2 = new ImageServerMetadata.Builder(metadata)
				.channels(channels).build();
		imageData.updateServerMetadata(metadata2);


		// Update the display
		channel.setLUTColor(
				(int)(newColor.getRed() * 255),
				(int)(newColor.getGreen() * 255),
				(int)(newColor.getBlue() * 255)
		);

		// Add color property
		imageDisplay.saveChannelColorProperties();
		viewer.repaintEntireImage();
		updateHistogram();
		table.refresh();
	}


	/**
	 * Respond to changes in the main selected channel in the table
	 */
	private void handleSelectedChannelChanged(ObservableValue<? extends ChannelDisplayInfo> observableValue,
											  ChannelDisplayInfo oldValue, ChannelDisplayInfo newValue) {
		updateHistogram();
		updateSliders();
	}


	/**
	 * Update sliders when receiving focus - in case the display has been updated elsewhere
	 */
	private void handleDialogFocusChanged(ObservableValue<? extends Boolean> observable, Boolean oldValue,
										  Boolean newValue) {
		if (newValue)
			updateSliders();
	}

	
	private void updateHistogram() {
		if (table == null || !isInitialized())
			return;

		boolean areaPlot = true; // Use area plot rather than proper histogram 'bars'

		ChannelDisplayInfo infoSelected = getCurrentInfo();
		Histogram histogram = (imageDisplay == null || infoSelected == null) ? null : imageDisplay.getHistogram(infoSelected);
//		histogram = histogramMap.get(infoSelected);
		if (histogram == null) {
//			histogramPanel.getHistogramData().clear();
			
			// Try to show RGB channels together
			if (infoSelected != null && imageDisplay != null && 
					imageDisplay.getImageData() != null && 
					imageDisplay.getImageData().getServer().isRGB() &&
					"original".equalsIgnoreCase(infoSelected.getName())) {
				List<HistogramData> data = new ArrayList<>();
				for (var c : imageDisplay.availableChannels()) {
					var method = c.getMethod();
					if (method == null)
						continue;
					switch (method) {
					case Red:
					case Green:
					case Blue:
						var hist = imageDisplay.getHistogram(c);
						if (hist != null) {
							var histogramData = HistogramPanelFX.createHistogramData(hist, areaPlot, c.getColor());
							histogramData.setNormalizeCounts(true);
							data.add(histogramData);
							if (histogram == null || hist.getMaxCount() > histogram.getMaxCount())
								histogram = hist;
						}
						break;
					default:
						break;
					}
				}
				histogramPanel.getHistogramData().setAll(data);
			} else
				histogramPanel.getHistogramData().clear();
		} else {
			// Any animation is slightly nicer if we can modify the current data, rather than creating a new one
			if (histogramPanel.getHistogramData().size() == 1) {
				Color color = infoSelected.getColor() == null ? ColorToolsFX.TRANSLUCENT_BLACK_FX : ColorToolsFX.getCachedColor(infoSelected.getColor());
				histogramPanel.getHistogramData().get(0).setHistogram(histogram, color);
			} else {
				HistogramData histogramData = HistogramPanelFX.createHistogramData(histogram, areaPlot, infoSelected.getColor());
				histogramData.setNormalizeCounts(true);
				histogramPanel.getHistogramData().setAll(histogramData);
			}
		}

		NumberAxis xAxis = (NumberAxis)histogramPanel.getChart().getXAxis();
		if (infoSelected != null && infoSelected.getMaxAllowed() == 255 && infoSelected.getMinAllowed() == 0) {
			xAxis.setAutoRanging(false);
			xAxis.setLowerBound(0);
			xAxis.setUpperBound(255);
		} else if (infoSelected != null) {
			xAxis.setAutoRanging(false);
			xAxis.setLowerBound(infoSelected.getMinAllowed());
			xAxis.setUpperBound(infoSelected.getMaxAllowed());
//			xAxis.setAutoRanging(true);
		}
		if (infoSelected != null)
			xAxis.setTickUnit(infoSelected.getMaxAllowed() - infoSelected.getMinAllowed());
		
		// Don't use the first or last count if it's an outlier & we have many bins
		NumberAxis yAxis = (NumberAxis)histogramPanel.getChart().getYAxis();
		if (infoSelected != null && histogram != null) {
			long maxCountExcludingEndBins = 0L;
			for (int i = 1; i < histogram.nBins()-1; i++)
				maxCountExcludingEndBins = Math.max(maxCountExcludingEndBins, histogram.getCountsForBin(i));
			double outlierThreshold = maxCountExcludingEndBins * 4;
			double yMax = Math.min(histogram.getMaxCount(), outlierThreshold) / histogram.getCountSum();
			yAxis.setAutoRanging(false);
			yAxis.setLowerBound(0);
			yAxis.setUpperBound(yMax);
		}
		
		histogramPanel.getChart().getXAxis().setTickLabelsVisible(true);
//		histogramPanel.getChart().getXAxis().setLabel("Pixel value");
		histogramPanel.getChart().getYAxis().setTickLabelsVisible(true);
//		histogramPanel.getChart().getYAxis().setLabel("Frequency");
		
		GridPane pane = new GridPane();
		pane.setHgap(4);
		pane.setVgap(2);
		int row = 0;
		if (histogram != null) {
			pane.add(new Label("Min"), 0, row);
			pane.add(new Label(df.format(histogram.getMinValue())), 1, row);
			row++;
			pane.add(new Label("Max"), 0, row);
			pane.add(new Label(df.format(histogram.getMaxValue())), 1, row);
			row++;
			pane.add(new Label("Mean"), 0, row);
			pane.add(new Label(df.format(histogram.getMeanValue())), 1, row);
			row++;
			pane.add(new Label("Std.dev"), 0, row);
			pane.add(new Label(df.format(histogram.getStdDev())), 1, row);
			row++;
		}
		chartTooltip.setGraphic(pane);
		
		if (row == 0)
			Tooltip.uninstall(histogramPanel.getChart(), chartTooltip);
		else
			Tooltip.install(histogramPanel.getChart(), chartTooltip);
		
	}

	private void setShowChannel(ChannelDisplayInfo channel) {
		if (channel != null)
			setShowChannels(Collections.singleton(channel));
	}

	private void setShowChannels(Collection<? extends ChannelDisplayInfo> channels) {
		if (imageDisplay == null || channels.isEmpty())
			return;
		for (var channel : channels)
			imageDisplay.setChannelSelected(channel, true);
		refreshTableAndViewer();
	}

	private void setHideChannel(ChannelDisplayInfo channel) {
		if (channel != null)
			setHideChannels(Collections.singleton(channel));
	}

	private void setHideChannels(Collection<? extends ChannelDisplayInfo> channels) {
		if (imageDisplay == null || channels.isEmpty())
			return;
		for (var channel : channels)
			imageDisplay.setChannelSelected(channel, false);
		refreshTableAndViewer();
	}

	private void toggleShowHideChannel(ChannelDisplayInfo channel) {
		if (channel == null)
			refreshTableAndViewer();
		else
			toggleShowHideChannels(Collections.singletonList(channel));
	}

	private void toggleShowHideChannels(Collection<? extends ChannelDisplayInfo> channels) {
		if (imageDisplay == null || channels.isEmpty())
			return;
		for (var channel : channels)
			imageDisplay.setChannelSelected(channel, !isChannelShowing(channel));
		refreshTableAndViewer();
	}

	private boolean isChannelShowing(ChannelDisplayInfo channel) {
		return imageDisplay != null && imageDisplay.selectedChannels().contains(channel);
	}

	private void refreshTableAndViewer() {
		// If the table isn't null, we are displaying something
		if (table != null) {
			updateHistogram();
			table.refresh();
		}
		viewer.repaintEntireImage();
	}


	private ChannelDisplayInfo getCurrentInfo() {
		ChannelDisplayInfo info = table.getSelectionModel().getSelectedItem();
		// Default to first, if we don't have a selection
		if (info == null && !table.getItems().isEmpty())
			info = table.getItems().get(0);
		return info;
	}


	private void handleMinMaxSliderValueChange(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
		applyMinMaxSliderChanges();
	}

	private void applyMinMaxSliderChanges() {
		if (slidersUpdating)
			return;
		ChannelDisplayInfo infoVisible = getCurrentInfo();
		if (infoVisible == null)
			return;
		double minValue = sliderMin.getValue();
		double maxValue = sliderMax.getValue();
		imageDisplay.setMinMaxDisplay(infoVisible, (float)minValue, (float)maxValue);
		viewer.repaintEntireImage();
	}
	
	
	private void updateSliders() {
		if (!isInitialized())
			return;
		ChannelDisplayInfo infoVisible = getCurrentInfo();
		if (infoVisible == null) {
			sliderMin.setDisable(true);
			sliderMax.setDisable(true);
			return;
		}
		float range = infoVisible.getMaxAllowed() - infoVisible.getMinAllowed();
		int n = (int)range;
		boolean is8Bit = range == 255 && infoVisible.getMinAllowed() == 0 && infoVisible.getMaxAllowed() == 255;
		if (is8Bit)
			n = 256;
		else if (n <= 20)
			n = (int)(range / .001);
		else if (n <= 200)
			n = (int)(range / .01);
		slidersUpdating = true;
		
		double maxDisplay = Math.max(infoVisible.getMaxDisplay(), infoVisible.getMinDisplay());
		double minDisplay = Math.min(infoVisible.getMaxDisplay(), infoVisible.getMinDisplay());
		double minSlider = Math.min(infoVisible.getMinAllowed(), minDisplay);
		double maxSlider = Math.max(infoVisible.getMaxAllowed(), maxDisplay);
		
		sliderMin.setMin(minSlider);
		sliderMin.setMax(maxSlider);
		sliderMin.setValue(infoVisible.getMinDisplay());
		sliderMax.setMin(minSlider);
		sliderMax.setMax(maxSlider);
		sliderMax.setValue(infoVisible.getMaxDisplay());
		
		if (is8Bit) {
			sliderMin.setMajorTickUnit(1);
			sliderMax.setMajorTickUnit(1);
			sliderMin.setMinorTickCount(n);
			sliderMax.setMinorTickCount(n);
		} else {
			sliderMin.setMajorTickUnit(1);
			sliderMax.setMajorTickUnit(1);
			sliderMin.setMinorTickCount(n);
			sliderMax.setMinorTickCount(n);
		}
		slidersUpdating = false;
		sliderMin.setDisable(false);
		sliderMax.setDisable(false);
		
		chartWrapper.getThresholds().clear();
		chartWrapper.addThreshold(sliderMin.valueProperty());
		chartWrapper.addThreshold(sliderMax.valueProperty());
		chartWrapper.setIsInteractive(true);
//		chartWrapper.getThresholds().setAll(sliderMin.valueProperty(), sliderMax.valueProperty());
		
//		histogramPanel.setVerticalLines(new double[]{infoVisible.getMinDisplay(), infoVisible.getMaxDisplay()}, ColorToolsFX.TRANSLUCENT_BLACK_FX);
	}
	

	/**
	 * Popup menu to toggle additive channels on/off.
	 */
	private void initializePopup() {
		popup = new ContextMenu();
		
		MenuItem miTurnOn = new MenuItem("Show channels");
		miTurnOn.setOnAction(e -> setTableSelectedChannels(true));
		miTurnOn.disableProperty().bind(showGrayscale);
		MenuItem miTurnOff = new MenuItem("Hide channels");
		miTurnOff.setOnAction(e -> setTableSelectedChannels(false));
		miTurnOff.disableProperty().bind(showGrayscale);
		MenuItem miToggle = new MenuItem("Toggle channels");
		miToggle.setOnAction(e -> toggleTableSelectedChannels());
		miToggle.disableProperty().bind(showGrayscale);
		
		popup.getItems().addAll(
				miTurnOn,
				miTurnOff,
				miToggle
				);
	}
	
	/**
	 * Request that channels currently selected (highlighted) in the table have their 
	 * selected status changed accordingly.  This allows multiple channels to be turned on/off 
	 * in one step.
	 * @param showChannels
	 * 
	 * @see #toggleTableSelectedChannels()
	 */
	private void setTableSelectedChannels(boolean showChannels) {
		if (!isInitialized())
			return;
		for (ChannelDisplayInfo info : table.getSelectionModel().getSelectedItems()) {
			imageDisplay.setChannelSelected(info, showChannels);
		}
		table.refresh();
		if (viewer != null) {
//			viewer.updateThumbnail();
			viewer.repaintEntireImage();
		}
	}

	/**
	 * Request that channels currently selected (highlighted) in the table have their 
	 * selected status inverted.  This allows multiple channels to be turned on/off 
	 * in one step.
	 * 
	 * @see #setTableSelectedChannels(boolean)
	 */
	private void toggleTableSelectedChannels() {
		if (!isInitialized())
			return;
		Set<ChannelDisplayInfo> selected = new HashSet<>(imageDisplay.selectedChannels());
		for (ChannelDisplayInfo info : table.getSelectionModel().getSelectedItems()) {
			imageDisplay.setChannelSelected(info, !selected.contains(info));
		}
		table.refresh();
		if (viewer != null) {
			viewer.repaintEntireImage();
		}
	}

	private void updatePredicate() {
		var items = table.getItems();
		if (items instanceof FilteredList) {
			((FilteredList<ChannelDisplayInfo>)items).setPredicate(predicate.get());
		}
	}
	
	void updateTable() {
		if (!isInitialized())
			return;

		// Update table appearance (maybe colors changed etc.)
		if (imageDisplay == null) {
			table.setItems(FXCollections.emptyObservableList());
		} else {
			table.setItems(imageDisplay.availableChannels().filtered(predicate.get()));
			showGrayscale.bindBidirectional(imageDisplay.useGrayscaleLutProperty());
			invertBackground.bindBidirectional(imageDisplay.useInvertedBackgroundProperty());
		}
		table.refresh();
		
		// If all entries are additive, allow bulk toggling by right-click or with checkbox
		int n = table.getItems().size();
		if (n > 0 && n == table.getItems().stream().filter(c -> c.isAdditive()).count()) {
			table.setContextMenu(popup);
			cbShowAll.setVisible(true);
		} else {
			table.setContextMenu(null);
			cbShowAll.setVisible(false);
		}
	}


	private void handleImageDataChange(ObservableValue<? extends ImageData<BufferedImage>> source,
			ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		// TODO: Consider different viewers but same ImageData
		if (imageDataOld == imageDataNew)
			return;
				
		QuPathViewer viewerNew = qupath.getViewer();
		if (viewer != viewerNew) {
			if (viewer != null)
				viewer.getView().removeEventHandler(KeyEvent.KEY_TYPED, keyListener);
			if (viewerNew != null)
				viewerNew.getView().addEventHandler(KeyEvent.KEY_TYPED, keyListener);
			viewer = viewerNew;
		}
		
		if (imageDisplay != null) {
			showGrayscale.unbindBidirectional(imageDisplay.useGrayscaleLutProperty());
			imageDisplay.useGrayscaleLutProperty().unbindBidirectional(showGrayscale);
			
			invertBackground.unbindBidirectional(imageDisplay.useInvertedBackgroundProperty());
			imageDisplay.useInvertedBackgroundProperty().unbindBidirectional(invertBackground);
		}

		if (imageDisplay != null)
			imageDisplay.selectedChannels().removeListener(selectedChannelsChangeListener);

		imageDisplay = viewer == null ? null : viewer.getImageDisplay();
		if (imageDisplay != null)
			imageDisplay.selectedChannels().addListener(selectedChannelsChangeListener);

		if (imageDataOld != null)
			imageDataOld.removePropertyChangeListener(imageDataPropertyChangeListener);
		if (imageDataNew != null)
			imageDataNew.addPropertyChangeListener(imageDataPropertyChangeListener);
		
		updateTable();
		
//		updateHistogramMap();
		// Update if we aren't currently initializing
		updateHistogram();
		updateSliders();
	}


	private ObjectBinding<Predicate<ChannelDisplayInfo>> createChannelDisplayPredicateBinding(StringProperty filterText) {
		return Bindings.createObjectBinding(() -> {
			if (useRegex.get())
				return createChannelDisplayPredicateFromRegex(filterText.get());
			else
				return createChannelDisplayPredicateFromText(filterText.get());
		}, filterText, useRegex);
	}

	private static Predicate<ChannelDisplayInfo> createChannelDisplayPredicateFromRegex(String regex) {
		if (regex == null || regex.isBlank())
			return info -> true;
		try {
			Pattern pattern = Pattern.compile(regex);
			return info -> channelDisplayFromRegex(info, pattern);
		} catch (PatternSyntaxException e) {
			logger.warn("Invalid channel display: {} ({})", regex, e.getMessage());
			return info -> false;
		}
	}

	private static boolean channelDisplayFromRegex(ChannelDisplayInfo info, Pattern pattern) {
		return pattern.matcher(info.getName()).find();
	}

	private static Predicate<ChannelDisplayInfo> createChannelDisplayPredicateFromText(String filterText) {
		if (filterText == null || filterText.isBlank())
			return info -> true;
		String text = filterText.toLowerCase();
		return info -> channelDisplayContainsText(info, text);
	}

	private static boolean channelDisplayContainsText(ChannelDisplayInfo info, String text) {
		return info.getName().toLowerCase().contains(text);
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
			if (evt.getPropertyName().equals("serverMetadata") ||
					((evt.getSource() instanceof ImageData<?>) && evt.getPropertyName().equals("imageType")))
				imageDisplay.refreshChannelOptions();

			updateTable();
			updateSliders();

			if (viewer != null) {
				viewer.repaintEntireImage();
			}
		}

	}


	/**
	 * Listener to update the display when the user presses a key.
	 */
	private class BrightnessContrastKeyTypedListener implements EventHandler<KeyEvent> {

		@Override
		public void handle(KeyEvent event) {
			if (imageDisplay == null || event.getEventType() != KeyEvent.KEY_TYPED)
				return;
			String character = event.getCharacter();
			if (character != null && character.length() > 0) {
				int c = (int)event.getCharacter().charAt(0) - '0';
				if (c >= 1 && c <= Math.min(9, imageDisplay.availableChannels().size())) {
					if (table != null) {
						table.getSelectionModel().clearAndSelect(c-1);
					}
					toggleShowHideChannel(imageDisplay.availableChannels().get(c-1));
					event.consume();
				}
			}
		}
		
	}

	/**
	 * Listener to support key presses for the channel table.
	 * This is used for two main purposes:
	 * <ol>
	 *     <li>Copy/paste channel names</li>
	 *     <li>Toggle show/hide channels</li>
	 * </ol>
	 */
	private class ChannelTableKeypressedListener implements EventHandler<KeyEvent> {
		
		private KeyCombination copyCombo = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
		private KeyCombination pasteCombo = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);

		// Show/hide/toggle combos use S, H and T
		private KeyCombination showCombo = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_ANY);
		private KeyCombination hideCombo = new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_ANY);
		private KeyCombination toggleCombo = new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_ANY);

		// Because S and H are awkward to find in the keyboard, show/hide/toggle can also be done with
		// enter, backspace, and space
		private KeyCombination spaceCombo = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.SHORTCUT_ANY);
		private KeyCombination enterCombo = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_ANY);
		private KeyCombination backspaceCombo = new KeyCodeCombination(KeyCode.BACK_SPACE, KeyCombination.SHORTCUT_ANY);

		@Override
		public void handle(KeyEvent event) {
			if (event.getEventType() != KeyEvent.KEY_PRESSED)
				return;
			if (copyCombo.match(event)) {
				doCopy(event);
				event.consume();
			} else if (pasteCombo.match(event)) {
				doPaste(event);
				event.consume();
			} else if (imageDisplay != null) {
				if (isToggleChannelsEvent(event)) {
					toggleShowHideChannels(getSelectedChannelsToUpdate());
					event.consume();
				} else if (isShowChannelsEvent(event)) {
					setShowChannels(getSelectedChannelsToUpdate());
					event.consume();
				} else if (isHideChannelsEvent(event)) {
					setHideChannels(getSelectedChannelsToUpdate());
					event.consume();
				}
			}
		}

		private boolean isToggleChannelsEvent(KeyEvent event) {
			return spaceCombo.match(event) || toggleCombo.match(event);
		}

		private boolean isShowChannelsEvent(KeyEvent event) {
			return enterCombo.match(event) || showCombo.match(event);
		}

		private boolean isHideChannelsEvent(KeyEvent event) {
			return backspaceCombo.match(event) || hideCombo.match(event);
		}

		/**
		 * Get the channels to update, based on the current selection.
		 * If the main selected channel is not additive, or we're in grayscale mode, return it alone.
		 * Otherwise, return all selected channels from the table.
		 * This is to ensure that, if just one channel is changed, then it's the main one - and not just
		 * the last selected channel in the list.
		 * @return
		 */
		private Collection<ChannelDisplayInfo> getSelectedChannelsToUpdate() {
			var mainSelectedChannel = table.getSelectionModel().getSelectedItem();
			if (mainSelectedChannel != null &&
					(imageDisplay.useGrayscaleLuts()) || !mainSelectedChannel.isAdditive())
				return Collections.singletonList(mainSelectedChannel);
			else
				return table.getSelectionModel().getSelectedItems();
		}

		
		/**
		 * Copy the channel names to the clipboard
		 * @param event
		 */
		void doCopy(KeyEvent event) {
			var names = table.getSelectionModel().getSelectedItems().stream().map(c -> c.getName()).toList();
			var clipboard = Clipboard.getSystemClipboard();
			var content = new ClipboardContent();
			content.putString(String.join(System.lineSeparator(), names));
			clipboard.setContent(content);
		}

		/**
		 * Paste channel names from the clipboard, if possible
		 * @param event
		 */
		void doPaste(KeyEvent event) {
			ImageData<BufferedImage> imageData = viewer.getImageData();
			if (imageData == null)
				return;
			ImageServer<BufferedImage> server = imageData.getServer();
			
			var clipboard = Clipboard.getSystemClipboard();
			var string = clipboard.getString();
			if (string == null)
				return;
			var selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
			if (selected.isEmpty())
				return;
			
			if (server.isRGB()) {
				logger.warn("Cannot set channel names for RGB images");
			}
			var names = string.lines().toList();
			if (selected.size() != names.size()) {
				Dialogs.showErrorNotification("Paste channel names", "The number of lines on the clipboard doesn't match the number of channel names to replace!");
				return;
			}
			if (names.size() != new HashSet<>(names).size()) {
				Dialogs.showErrorNotification("Paste channel names", "Channel names should be unique!");
				return;
			}
			var metadata = server.getMetadata();
			var channels = new ArrayList<>(metadata.getChannels());
			List<String> changes = new ArrayList<>();
			for (int i = 0; i < selected.size(); i++) {
				if (!(selected.get(i) instanceof DirectServerChannelInfo))
					continue;
				var info = (DirectServerChannelInfo)selected.get(i);
				if (info.getName().equals(names.get(i)))
					continue;
				int c = info.getChannel();
				var oldChannel = channels.get(c);
				var newChannel = ImageChannel.getInstance(names.get(i), channels.get(c).getColor());
				changes.add(oldChannel.getName() + " -> " + newChannel.getName());
				channels.set(c, newChannel);
			}
			List<String> allNewNames = channels.stream().map(c -> c.getName()).toList();
			Set<String> allNewNamesSet = new LinkedHashSet<>(allNewNames);
			if (allNewNames.size() != allNewNamesSet.size()) {
				Dialogs.showErrorMessage("Channel", "Cannot paste channels - names would not be unique \n(check log for details)");
				for (String n : allNewNamesSet)
					allNewNames.remove(n);
				logger.warn("Requested channel names would result in duplicates: " + String.join(", ", allNewNames));
				return;
			}
			if (changes.isEmpty()) {
				logger.debug("Channel names pasted, but no changes to make");
			}
			else {
				var dialog = new Dialog<ButtonType>();
				dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
				dialog.setTitle("Channels");
				dialog.setHeaderText("Confirm new channel names?");
				dialog.getDialogPane().setContent(new TextArea(String.join("\n", changes)));
				if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.APPLY) {
					var newMetadata = new ImageServerMetadata.Builder(metadata)
							.channels(channels).build();
					imageData.updateServerMetadata(newMetadata);
				}
			}
		}
		
	}


	/**
	 * Table cell to display the main information about a channel (name, color).
	 */
	private class ChannelDisplayTableCell extends TableCell<ChannelDisplayInfo, ChannelDisplayInfo> {

		private static int MAX_CUSTOM_COLORS = 60;

		private ColorPicker colorPicker;
		private ObservableList<Color> customColors;
		private boolean updatingTableCell = false;

		private Comparator<Color> comparator = Comparator.comparingDouble((Color c) -> c.getHue())
				.thenComparingDouble(c -> c.getSaturation())
				.thenComparingDouble(c -> c.getBrightness())
				.thenComparingDouble(c -> c.getOpacity()) // Regular equality uses RGB + opacity
				.thenComparingDouble(c -> c.getRed())
				.thenComparingDouble(c -> c.getGreen())
				.thenComparingDouble(c -> c.getBrightness());

		/**
		 * Create a new cell, with the optional shared list of custom colors.
		 * @param customColors if not null, reuse the same list of custom colors and append the current color as needed.
		 *                     If null, include only the current color as a custom color each time the picker is shown.
		 */
		private ChannelDisplayTableCell(ObservableList<Color> customColors) {
			if (customColors != null)
				this.customColors = customColors;
			else
				this.customColors = null;
			// Minimal color picker - just a small, clickable colored square
			colorPicker = new ColorPicker();
			colorPicker.getStyleClass().addAll("button", "minimal-color-picker", "always-opaque");
			colorPicker.valueProperty().addListener(this::handleColorChange);
			setGraphic(colorPicker);
			setEditable(true);
		}

		private ChannelDisplayTableCell() {
			this(null);
		}

		@Override
		protected void updateItem(ChannelDisplayInfo item, boolean empty) {
			super.updateItem(item, empty);
			if (item == null || empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			setText(item.getName());
			setGraphic(colorPicker);
			Integer rgb = item.getColor();
			// Can only set the color for direct, non-RGB channels
			boolean canChangeColor = rgb != null && item instanceof DirectServerChannelInfo;
			colorPicker.setDisable(!canChangeColor);
			colorPicker.setOnShowing(null);
			if (rgb == null) {
				colorPicker.setValue(Color.TRANSPARENT);
			} else {
				Color color = ColorToolsFX.getCachedColor(rgb);
				setColorQuietly(color);
				colorPicker.setOnShowing(e -> {
					if (customColors == null)
						colorPicker.getCustomColors().setAll(color);
					else {
						// When the picker is being shown, ensure the current color is included in the custom color list
						if (!customColors.contains(color)) {
							// Reset the custom color list if it's becoming extremely long
							if (customColors.size() > MAX_CUSTOM_COLORS)
								customColors.clear();
							customColors.add(color);
							Collections.sort(customColors, comparator);
						}
						colorPicker.getCustomColors().setAll(customColors);
					}
				});
			}
		}

		private void setColorQuietly(Color color) {
			updatingTableCell = true;
			colorPicker.setValue(color);
			updatingTableCell = false;
		}

		private void handleColorChange(ObservableValue<? extends Color> observable, Color oldValue, Color newValue) {
			if (updatingTableCell)
				return;
			if (newValue == null) {
				logger.debug("Attempting to set channel color to null!");
				if (oldValue != null)
					setColorQuietly(oldValue);
				return;
			}
			var item = this.getItem();
			if (item instanceof DirectServerChannelInfo)
				updateChannelColor((DirectServerChannelInfo)item, item.getName(), newValue);
			else
				logger.debug("Invalid channel type - cannot set color for {}", item);
		}

	}

	/**
	 * Table cell to handle the "show" status for a channel.
	 */
	private class ShowChannelDisplayTableCell extends CheckBoxTableCell<ChannelDisplayInfo, Boolean> {

		public ShowChannelDisplayTableCell() {
			super();
			addEventFilter(MouseEvent.MOUSE_CLICKED, this::filterMouseClicks);
		}

		private void filterMouseClicks(MouseEvent event) {
			// Select cells when clicked - means a click anywhere within the row forces selection.
			// Previously, clicking within the checkbox didn't select the row.
			if (event.isPopupTrigger())
				return;
			int ind = getIndex();
			var tableView = getTableView();
			if (ind < tableView.getItems().size()) {
				if (event.isShiftDown())
					tableView.getSelectionModel().select(ind);
				else
					tableView.getSelectionModel().clearAndSelect(ind);
				var channel = getTableRow().getItem();
				// Handle clicks within the cell but outside the checkbox
				if (event.getTarget() == this && channel != null && imageDisplay != null) {
					toggleShowHideChannel(channel);
				}
				event.consume();
			}
		}

	}

	class SelectedChannelsChangeListener implements ListChangeListener<ChannelDisplayInfo> {

		@Override
		public void onChanged(Change<? extends ChannelDisplayInfo> c) {
			if (imageDisplay == null)
				return;
			if (imageDisplay.availableChannels().size() == imageDisplay.selectedChannels().size()) {
				cbShowAll.setIndeterminate(false);
				cbShowAll.setSelected(true);
			} else if (imageDisplay.selectedChannels().isEmpty()) {
				cbShowAll.setIndeterminate(false);
				cbShowAll.setSelected(false);
			} else {
				cbShowAll.setIndeterminate(true);
			}
			// Only necessary because it's possible that the channel selection is changed externally
			table.refresh();
		}
	}

}