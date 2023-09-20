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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
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
import qupath.lib.projects.ResourceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A pane to save and load display settings, using a {@link ResourceManager}.
 */
public class BrightnessContrastSettingsPane extends GridPane {

    private static final Logger logger = LoggerFactory.getLogger(BrightnessContrastSettingsPane.class);

    private ObjectProperty<ResourceManager.Manager<ImageDisplaySettings>> resourceManagerProperty = new SimpleObjectProperty<>();

    private ObservableList<ImageDisplaySettings> savedDisplayResources = FXCollections.observableArrayList();

    private ObjectProperty<ImageDisplay> imageDisplayObjectProperty = new SimpleObjectProperty<>();

    public BrightnessContrastSettingsPane() {
        imageDisplayObjectProperty.addListener((v, o, n) -> refreshResources());
        initializePane();
    }

    private void initializePane() {
        setHgap(5);
        var label = new Label("Settings");
        var combo = new SearchableComboBox<>(savedDisplayResources);
        label.setLabelFor(combo);
        combo.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
            if (n != null)
                tryToApplySettings(n);
        });
        combo.setCellFactory(c -> FXUtils.createCustomListCell(ImageDisplaySettings::getName));
        combo.setButtonCell(combo.getCellFactory().call(null));
        combo.setPlaceholder(new Text("No saved settings"));
        resourceManagerProperty.addListener((v, o, n) -> refreshResources());
        var btnSave = new Button("Save");
        btnSave.disableProperty().bind(resourceManagerProperty.isNull());
        btnSave.setOnAction(e -> promptToSaveSettings(combo.getSelectionModel().getSelectedItem() == null ? "" : combo.getSelectionModel().getSelectedItem().getName()));
        int col = 0;
        add(label, col++, 0);
        add(combo, col++, 0);
        add(btnSave, col, 0);
        GridPaneUtils.setToExpandGridPaneWidth(combo);
    }

    /**
     * The current image display.
     * @return
     */
    public ObjectProperty<ImageDisplay> imageDisplayObjectProperty() {
        return imageDisplayObjectProperty;
    }

    /**
     * The resource manager used to handle saving/loading.
     * @return
     */
    public ObjectProperty<ResourceManager.Manager<ImageDisplaySettings>> resourceManagerProperty() {
        return resourceManagerProperty;
    }

    private void promptToSaveSettings(String name) {
        var manager = resourceManagerProperty.get();
        var imageDisplay = imageDisplayObjectProperty.get();
        if (manager == null)
            logger.warn("No resource manager available!");
        else if (imageDisplay == null)
            logger.warn("No image display available!");
        else {
            String response = Dialogs.showInputDialog("Save display settings", "Display settings names", name);
            if (response == null || response.isEmpty())
                return;
            try {
                var settings = DisplaySettingUtils.displayToSettings(imageDisplay, response);
                manager.put(response, settings);
                refreshResources();
            } catch (IOException e) {
                Dialogs.showErrorMessage("Save display settings", "Can't save settings " + name);
                logger.error("Error saving display settings", e);
            }
        }
    }


    private void refreshResources() {
        var manager = resourceManagerProperty.get();
        var imageDisplay = imageDisplayObjectProperty.get();
        if (manager == null || imageDisplay == null) {
            // TODO: Reset the combo box? This gave a NoSuchElementException once
            savedDisplayResources.clear();
        } else {
            try {
                var names = new ArrayList<>(manager.getNames());
                Collections.sort(names);
                List<ImageDisplaySettings> compatibleSettings = new ArrayList<>();
                for (var name : names) {
                    var settings = manager.get(name);
                    if (DisplaySettingUtils.settingsCompatibleWithDisplay(imageDisplay, settings))
                        compatibleSettings.add(settings);
                }
                savedDisplayResources.setAll(compatibleSettings);
            } catch (IOException e) {
                logger.error("Error loading display settings", e);
            }
        }
    }

    private void tryToApplySettings(ImageDisplaySettings settings) {
        var imageDisplay = imageDisplayObjectProperty.get();
        if (settings == null || imageDisplay == null)
            return;
        try {
            DisplaySettingUtils.applySettingsToDisplay(imageDisplay, settings);
            PathPrefs.viewerGammaProperty().set(settings.getGamma());
        } catch (Exception e) {
            Dialogs.showErrorMessage("Apply display settings", "Can't apply settings " + settings.getName());
            logger.error("Error applying display settings", e);
        }
    }

}
