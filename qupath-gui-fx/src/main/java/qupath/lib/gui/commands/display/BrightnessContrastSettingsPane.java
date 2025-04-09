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

package qupath.lib.gui.commands.display;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.controlsfx.control.SearchableComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.display.ImageDisplay;
import qupath.lib.display.settings.DisplaySettingUtils;
import qupath.lib.display.settings.ImageDisplaySettings;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.projects.ResourceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A pane to save and load display settings, using a {@link ResourceManager}.
 */
public class BrightnessContrastSettingsPane extends GridPane {

    private static final Logger logger = LoggerFactory.getLogger(BrightnessContrastSettingsPane.class);

    private ObjectProperty<ResourceManager.Manager<ImageDisplaySettings>> resourceManagerProperty = new SimpleObjectProperty<>();

    private ObjectProperty<ImageDisplay> imageDisplayProperty = new SimpleObjectProperty<>();

    private ComboBox<ImageDisplaySettings> comboSettings = new SearchableComboBox<>();

    private StringProperty defaultName = null;

    /**
     * Whether the settings *may have* changed since they were last applied.
     * This does not perform a full check to reduce any performance impact.
     */
    private BooleanProperty settingsChanged = new SimpleBooleanProperty(false);
    private ObservableValue<Number> lastChangeTimestamp;

    public BrightnessContrastSettingsPane() {
        comboSettings.setOnShowing(e -> refreshResources());
        initializePane();
        tryToKeepSearchText();
        // Change on invalidation
        lastChangeTimestamp = imageDisplayProperty.flatMap(ImageDisplay::eventCountProperty);
        lastChangeTimestamp.addListener(this::timestampInvalidated);
        settingsChanged.addListener((v, o, n) -> {
            if (!n)
                lastChangeTimestamp.getValue(); // Evaluate in preparation for future invalidation events
        });
    }

    private void timestampInvalidated(Observable observable) {
        settingsChanged.set(true);
    }


    private void initializePane() {
        setHgap(5);
        var label = new Label("Settings");
        label.setLabelFor(comboSettings);
        comboSettings.setTooltip(new Tooltip("Compatible display settings saved in the project"));
        comboSettings.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
            if (n != null)
                tryToApplySettings(n);
        });
        // Although we use a custom cell factory, we need to set the string
        // converter for a searchable combo box to apply its filter properly
        comboSettings.setConverter(new StringConverter<>() {
            @Override
            public String toString(ImageDisplaySettings object) {
                return object == null ? null : object.getName();
            }

            @Override
            public ImageDisplaySettings fromString(String string) {
                if (string == null)
                    return null;
                else return comboSettings.getItems().stream()
                        .filter(s -> Objects.equals(s.getName(), string))
                        .findFirst()
                        .orElse(null);
            }
        });
        comboSettings.setCellFactory(c -> FXUtils.createCustomListCell(ImageDisplaySettings::getName));
        comboSettings.setButtonCell(new SettingListCell(settingsChanged));
        comboSettings.setPlaceholder(GuiTools.createPlaceholderText("No compatible settings"));
        resourceManagerProperty.addListener((v, o, n) -> refreshResources());
        var btnSave = new Button("Save");
        btnSave.setTooltip(new Tooltip("Save the current display settings in the project"));
        btnSave.disableProperty().bind(resourceManagerProperty.isNull());
        btnSave.setOnAction(e -> promptToSaveSettings());
        int col = 0;
        add(label, col++, 0);
        add(comboSettings, col++, 0);
        add(btnSave, col, 0);
        label.setMinWidth(Label.USE_PREF_SIZE);
        btnSave.setMinWidth(Button.USE_PREF_SIZE);
        GridPaneUtils.setToExpandGridPaneWidth(comboSettings);
//        GridPane.setHgrow(label, Priority.NEVER);
//        GridPane.setHgrow(btnSave, Priority.NEVER);
    }

    /**
     * The current image display.
     * @return
     */
    public ObjectProperty<ImageDisplay> imageDisplayObjectProperty() {
        return imageDisplayProperty;
    }

    /**
     * The resource manager used to handle saving/loading.
     * @return
     */
    public ObjectProperty<ResourceManager.Manager<ImageDisplaySettings>> resourceManagerProperty() {
        return resourceManagerProperty;
    }

    /**
     * The logic is quite tortured... but we want to access the contents of the search field just before
     * pressing 'save', because the user might use it to type a name they want.
     * However, the search field is only available through the skin, and the text isn't publicly accessible.
     * And, to make it harder, the text is reset before the 'save' action is handled.
     */
    private void tryToKeepSearchText() {
        comboSettings.sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).addListener((v, o, n) -> {
            if (n && defaultName == null)
                handleComboShowing();
        });
    }

    private void handleComboShowing() {
        if (comboSettings.lookup("#search") instanceof TextField tf) {
            defaultName = new SimpleStringProperty();
            tf.textProperty().addListener((v, o, n) -> {
                if (tf.isVisible())
                    defaultName.set(n);
            });
        }
    }

    private String tryToGetDefaultName() {
        var selected = comboSettings.getSelectionModel().getSelectedItem();
        if (selected != null)
            return selected.getName();
        return defaultName == null ? null : defaultName.get();
    }

    private void promptToSaveSettings() {
        var manager = resourceManagerProperty.get();
        var imageDisplay = imageDisplayProperty.get();
        if (manager == null)
            logger.warn("No resource manager available!");
        else if (imageDisplay == null)
            logger.warn("No image display available!");
        else {
            String name = promptForName();
            if (name == null || name.isEmpty())
                return;
            try {
                var settings = DisplaySettingUtils.displayToSettings(imageDisplay, name);
                manager.put(name, settings);
                refreshResources();
                settingsChanged.set(false);
                comboSettings.getSelectionModel().select(settings);
            } catch (IOException e) {
                Dialogs.showErrorMessage("Save display settings", "Can't save settings " + name);
                logger.error("Error saving display settings", e);
            }
        }
    }

    private String promptForName() {
        String defaultName = tryToGetDefaultName();
        var tf = new TextField(defaultName);
        tf.setPromptText("Name of settings");
        tf.setPrefColumnCount(16);
        var labelWarning = new Label("Settings with the same name will be overwritten!");
        var allNames = getAllNames();
        labelWarning.visibleProperty().bind(
                Bindings.createBooleanBinding(() -> allNames.contains(tf.getText()),
                        tf.textProperty()));
        labelWarning.getStyleClass().addAll("warn-label-text");
        var labelPrompt = new Label("Enter a name for the display settings");
        var pane = new VBox();
        pane.setSpacing(5.0);
        pane.getChildren().addAll(labelPrompt, tf, labelWarning);
        Platform.runLater(tf::requestFocus);
        if (Dialogs.builder()
                .title("Save display settings")
                .content(pane)
                .owner(FXUtils.getWindow(this))
                .buttons(ButtonType.OK, ButtonType.CANCEL)
                .showAndWait()
                .orElse(ButtonType.CANCEL).equals(ButtonType.OK)) {
            return tf.getText();
        } else
            return null;
    }


    private List<String> getAllNames() {
        var manager = this.resourceManagerProperty.get();
        if (manager == null)
            return Collections.emptyList();
        try {
            var names = new ArrayList<>(manager.getNames());
            Collections.sort(names);
            return names;
        } catch (IOException e) {
            logger.error("Error loading display settings", e);
            return Collections.emptyList();
        }
    }


    private void refreshResources() {
        logger.trace("Refreshing resources");
        var manager = resourceManagerProperty.get();
        var imageDisplay = imageDisplayProperty.get();
        // If comboSettings is a SearchableComboBox, then we need to set the items to avoid an IndexOutOfBounds exception
        // See https://github.com/controlsfx/controlsfx/issues/1320
        if (manager == null || imageDisplay == null || imageDisplay.availableChannels().isEmpty()) {
            comboSettings.setItems(FXCollections.observableArrayList());
        } else {
            try {
                var names = getAllNames();
                ObservableList<ImageDisplaySettings> compatibleSettings = FXCollections.observableArrayList();
                for (var name : names) {
                    var settings = manager.get(name);
                    if (DisplaySettingUtils.settingsCompatibleWithDisplay(imageDisplay, settings))
                        compatibleSettings.add(settings);
                }
                comboSettings.setItems(compatibleSettings);
            } catch (IOException e) {
                logger.error("Error loading display settings", e);
            }
        }
    }

    private void tryToApplySettings(ImageDisplaySettings settings) {
        var imageDisplay = imageDisplayProperty.get();
        if (settings == null || imageDisplay == null)
            return;
        try {
            DisplaySettingUtils.applySettingsToDisplay(imageDisplay, settings);
            PathPrefs.viewerGammaProperty().set(settings.getGamma());
            settingsChanged.set(false);
        } catch (Exception e) {
            Dialogs.showErrorMessage("Apply display settings", "Can't apply settings " + settings.getName());
            logger.error("Error applying display settings", e);
        }
    }


    /**
     * List cell that can indicate when settings have changed (at least potentially).
     */
    private static class SettingListCell extends ListCell<ImageDisplaySettings> {

        private BooleanProperty settingsChanged;

        private SettingListCell(BooleanProperty settingsChanged) {
            super();
            this.settingsChanged = settingsChanged;
            this.settingsChanged.addListener((v, o, n) -> updateTextAndStyle());
        }

        @Override
        protected void updateItem(ImageDisplaySettings item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                updateTextAndStyle();
            }
        }

        private void updateTextAndStyle() {
            if (!Platform.isFxApplicationThread()) {
                Platform.runLater(this::updateTextAndStyle);
                return;
            }
            var item = getItem();
            if (item == null) {
                setText(null);
                setStyle(null);
            } else {
                if (settingsChanged.get()) {
                    setText(item.getName() + "*");
                    setStyle("-fx-font-style: italic;");
                } else {
                    setText(item.getName());
                    setStyle(null);
                }
            }
        }

    }


}
