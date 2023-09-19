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

class BrightnessContrastSettingsPane extends GridPane {

    private static final Logger logger = LoggerFactory.getLogger(BrightnessContrastSettingsPane.class);

    private ObjectProperty<ResourceManager.Manager<ImageDisplaySettings>> resourceManagerProperty = new SimpleObjectProperty<>();

    private ObservableList<ImageDisplaySettings> savedDisplayResources = FXCollections.observableArrayList();

    private ObjectProperty<ImageDisplay> imageDisplayObjectProperty = new SimpleObjectProperty<>();

    BrightnessContrastSettingsPane() {
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
//		var btnApply = new Button("Apply");
//		btnApply.disableProperty().bind(qupath.imageDataProperty().isNull().or(resourceManagerProperty.isNull()));
//		btnApply.setOnAction(e -> tryToApplySettings(combo.getSelectionModel().getSelectedItem()));
        var btnSave = new Button("Save");
        btnSave.disableProperty().bind(resourceManagerProperty.isNull());
        btnSave.setOnAction(e -> promptToSaveSettings(combo.getSelectionModel().getSelectedItem() == null ? "" : combo.getSelectionModel().getSelectedItem().getName()));
        int col = 0;
        add(label, col++, 0);
        add(combo, col++, 0);
//		add(btnApply, col++, 0);
        add(btnSave, col++, 0);
        GridPaneUtils.setToExpandGridPaneWidth(combo);
    }

    public ObjectProperty<ImageDisplay> imageDisplayObjectProperty() {
        return imageDisplayObjectProperty;
    }

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
        if (manager == null || imageDisplay == null)
            savedDisplayResources.clear();
        else {
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
