package qupath.lib.gui.commands.display;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;

import java.awt.image.BufferedImage;
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

/**
 * A pane responsible for the display and selection of channels from an {@link qupath.lib.display.ImageDisplay}.
 */
public class BrightnessContrastChannelPane extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(BrightnessContrastChannelPane.class);

    private final ObjectProperty<ImageDisplay> imageDisplayObjectProperty = new SimpleObjectProperty<>();

    private final ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

    private final TableView<ChannelDisplayInfo> table = new TableView<>();

    private final BrightnessContrastTableFilter filter = new BrightnessContrastTableFilter(table);

    private final SelectedChannelsChangeListener selectedChannelsChangeListener = new SelectedChannelsChangeListener();

    private final ColorPicker picker = new ColorPicker();

    private final ContextMenu popup = new ContextMenu();

    private final ReadOnlyBooleanWrapper activeChannelVisible = new ReadOnlyBooleanWrapper(false);

    private final BooleanProperty disableToggleMenuItems = new SimpleBooleanProperty(false);

    /**
     * Checkbox used to quickly turn on or off all channels
     */
    private final CheckBox cbShowAll = new CheckBox();


    BrightnessContrastChannelPane() {
        imageDataProperty.bind(imageDisplayObjectProperty.map(ImageDisplay::getImageData));

        imageDisplayProperty().addListener(this::handleImageDisplayChanged);
        imageDataProperty.addListener(this::handleImageDataChange);

        createChannelDisplayTable();

        table.getItems().addListener(this::handleTableItemsChange);
        table.sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).addListener((v, o, n) -> {
            if (n) updateShowTableColumnHeader();
        });
        initializeShowAllCheckbox();
        initializeColorPicker();
        initializeShowAllCheckbox();

        initializePopup();
        initialize();
    }

    private void initialize() {
        setCenter(table);
        setBottom(filter);
        BorderPane.setMargin(filter, new Insets(5, 0, 0, 0));
    }


    public BooleanProperty disableToggleMenuItemsProperty() {
        return disableToggleMenuItems;
    }

    /**
     * Popup menu to toggle additive channels on/off.
     */
    private void initializePopup() {
        MenuItem miTurnOn = new MenuItem("Show channels");
        miTurnOn.setOnAction(e -> setTableSelectedChannels(true));
        miTurnOn.disableProperty().bind(disableToggleMenuItems);

        MenuItem miTurnOff = new MenuItem("Hide channels");
        miTurnOff.setOnAction(e -> setTableSelectedChannels(false));
        miTurnOff.disableProperty().bind(disableToggleMenuItems);

        MenuItem miToggle = new MenuItem("Toggle channels");
        miToggle.setOnAction(e -> toggleTableSelectedChannels());
        miToggle.disableProperty().bind(disableToggleMenuItems);

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
        var imageDisplay = imageDisplayProperty().getValue();
        if (imageDisplay == null)
            return;
        for (ChannelDisplayInfo info : table.getSelectionModel().getSelectedItems()) {
            imageDisplay.setChannelSelected(info, showChannels);
        }
		table.refresh();
    }

    /**
     * Request that channels currently selected (highlighted) in the table have their
     * selected status inverted.  This allows multiple channels to be turned on/off
     * in one step.
     *
     * @see #setTableSelectedChannels(boolean)
     */
    private void toggleTableSelectedChannels() {
        var imageDisplay = imageDisplayProperty().getValue();
        if (imageDisplay == null)
            return;
        Set<ChannelDisplayInfo> selected = new HashSet<>(imageDisplay.selectedChannels());
        for (ChannelDisplayInfo info : table.getSelectionModel().getSelectedItems()) {
            imageDisplay.setChannelSelected(info, !selected.contains(info));
        }
		table.refresh();
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

    private void handleImageDataChange(ObservableValue<? extends ImageData<BufferedImage>> source,
                                       ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {

        // Update the table - attempting to preserve the same selected object
        var selectedItem = table.getSelectionModel().getSelectedItem();
        updateTable();
        if (selectedItem != null) {
            for (var item : table.getItems()) {
                if (Objects.equals(selectedItem.getName(), item.getName())) {
                    table.getSelectionModel().select(item);
                    break;
                }
            }
        }
    }

    private void createChannelDisplayTable() {
        var imageDisplay = imageDisplayObjectProperty.getValue();
        if (imageDisplay != null)
            table.setItems(imageDisplay.availableChannels());
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
    }


    private void handleImageDisplayChanged(ObservableValue<? extends ImageDisplay> source, ImageDisplay oldValue, ImageDisplay newValue) {
        if (oldValue != null) {
            oldValue.selectedChannels().removeListener(selectedChannelsChangeListener);
        }
        if (newValue != null) {
            newValue.selectedChannels().addListener(selectedChannelsChangeListener);
        }
        updateTable();
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


    public ReadOnlyBooleanProperty activeChannelVisible() {
        return activeChannelVisible.getReadOnlyProperty();
    }


    private boolean isChannelShowing(ChannelDisplayInfo channel) {
        var imageDisplay = imageDisplayProperty().getValue();
        return imageDisplay != null && imageDisplay.selectedChannels().contains(channel);
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
        if (cbShowAll.isIndeterminate())
            return;
        if (cbShowAll.isSelected()) {
            setShowChannels(table.getItems());
        } else {
            setHideChannels(table.getItems());
        }
    }

    public void setShowChannel(ChannelDisplayInfo channel) {
        if (channel != null)
            setShowChannels(Collections.singleton(channel));
    }

    private void setShowChannels(Collection<? extends ChannelDisplayInfo> channels) {
        var imageDisplay = imageDisplayProperty().getValue();
        if (imageDisplay == null || channels.isEmpty())
            return;
        for (var channel : channels)
            imageDisplay.setChannelSelected(channel, true);
        table.refresh();
    }

    private void setHideChannel(ChannelDisplayInfo channel) {
        if (channel != null)
            setHideChannels(Collections.singleton(channel));
    }

    private void setHideChannels(Collection<? extends ChannelDisplayInfo> channels) {
        var imageDisplay = imageDisplayProperty().getValue();
        if (imageDisplay == null || channels.isEmpty())
            return;
        for (var channel : channels)
            imageDisplay.setChannelSelected(channel, false);
        table.refresh();
    }

    public void toggleShowHideChannel(ChannelDisplayInfo channel) {
        if (channel == null)
            table.refresh();
        else
            toggleShowHideChannels(Collections.singletonList(channel));
    }

    private void toggleShowHideChannels(Collection<? extends ChannelDisplayInfo> channels) {
        var imageDisplay = imageDisplayProperty().getValue();
        if (imageDisplay == null || channels.isEmpty())
            return;
        for (var channel : channels)
            imageDisplay.setChannelSelected(channel, !isChannelShowing(channel));
        table.refresh();
    }

    void updateTable() {
        // Update table appearance (maybe colors changed etc.)
        var imageDisplay = imageDisplayProperty().getValue();
        if (imageDisplay == null) {
            table.setItems(FXCollections.emptyObservableList());
        } else {
            table.setItems(imageDisplay.availableChannels().filtered(filter.predicateProperty().get()));
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


    private TableRow<ChannelDisplayInfo> createTableRow(TableView<ChannelDisplayInfo> table) {
        TableRow<ChannelDisplayInfo> row = new TableRow<>();
        row.setOnMouseClicked(e -> handleTableRowMouseClick(row, e));
        return row;
    }

    private void handleTableRowMouseClick(TableRow<ChannelDisplayInfo> row, MouseEvent event) {
        if (event.getClickCount() != 2)
            return;

        ChannelDisplayInfo info = row.getItem();
        var imageData = imageDataProperty.getValue();
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
        var imageData = imageDataProperty.getValue();
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
        var imageDisplay = imageDisplayProperty().getValue();
        if (imageDisplay != null)
            imageDisplay.saveChannelColorProperties();
//        updateHistogram();
        table.refresh();
    }


    public ReadOnlyObjectProperty<ChannelDisplayInfo> currentChannelProperty() {
        return table.getSelectionModel().selectedItemProperty();
    }

    public ObservableList<ChannelDisplayInfo> getChannels() {
        return table.getItems();
    }

    public MultipleSelectionModel<ChannelDisplayInfo> getSelectionModel() {
        return table.getSelectionModel();
    }

    private void handleTableItemsChange(ListChangeListener.Change<? extends ChannelDisplayInfo> change) {
        // Select the first item if nothing is selected
        // TODO: Check if this behaves sensibly
        var items = change.getList();
        if (table.getSelectionModel().getSelectedItem() == null && ! items.isEmpty())
            table.getSelectionModel().selectFirst();
    }


    public ObjectProperty<ImageDisplay> imageDisplayProperty() {
        return imageDisplayObjectProperty;
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



    /**
     * Respond to changes in the main selected channel in the table
     */
    private void handleSelectedChannelChanged(ObservableValue<? extends ChannelDisplayInfo> observableValue,
                                              ChannelDisplayInfo oldValue, ChannelDisplayInfo newValue) {
//        updateHistogram();
//        updateSliders();
        activeChannelVisible.set(newValue != null && isChannelShowing(newValue));
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
            // Hack - this updates with the current info (and we don't have an observable property for the info)
            currentChannelProperty().addListener((observable, oldValue, newValue) -> updateStyle());
            updateStyle();
        }

        private void updateStyle() {
            if (getItem() == currentChannelProperty().get())
                setStyle("-fx-font-weight: bold");
//				setStyle("-fx-font-style: italic");
            else
                setStyle("");
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
            updateStyle();

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
                if (event.getTarget() == this && channel != null) {
                    toggleShowHideChannel(channel);
                }
                event.consume();
            }
        }

    }

    class SelectedChannelsChangeListener implements ListChangeListener<ChannelDisplayInfo> {

        @Override
        public void onChanged(Change<? extends ChannelDisplayInfo> c) {
            var imageDisplay = imageDisplayProperty().getValue();
            if (imageDisplay == null) {
                activeChannelVisible.set(false);
                return;
            }
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
            var current = currentChannelProperty().get();
            activeChannelVisible.set(current != null && isChannelShowing(current));
        }
    }


    public TableView<ChannelDisplayInfo> getTable() {
        return table;
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
            } else if (imageDisplayProperty().getValue() != null) {
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
            var imageDisplay = imageDisplayProperty().getValue();
            if (mainSelectedChannel != null && imageDisplay != null &&
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
            ImageData<BufferedImage> imageData = imageDataProperty.getValue();
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

}
