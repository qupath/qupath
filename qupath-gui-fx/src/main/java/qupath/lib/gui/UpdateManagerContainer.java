package qupath.lib.gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import qupath.lib.gui.prefs.PathPrefs;

import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * A container displaying a list of available updates.
 */
class UpdateManagerContainer extends VBox {

    @FXML
    private TableView<UpdateEntry> updateEntries;
    @FXML
    private TableColumn<UpdateEntry, String> nameColumn;
    @FXML
    private TableColumn<UpdateEntry, String> currentVersionColumn;
    @FXML
    private TableColumn<UpdateEntry, String> newVersionColumn;
    @FXML
    private ComboBox<PathPrefs.AutoUpdateType> updateType;

    /**
     * An entry for an available update.
     *
     * @param name the name of the entity to update
     * @param currentVersion the current version of the entity
     * @param newVersion the new available version of the entity
     * @param onClick a function to perform when the user clicks on this entry. The function will be called from the
     *                JavaFX Application Thread
     * @param onClickDescription a text describing the action performed by {@link #onClick}
     */
    public record UpdateEntry(String name, String currentVersion, String newVersion, Runnable onClick, String onClickDescription) {}

    /**
     * Create the container.
     *
     * @param updateEntries the update entries to display
     * @throws IOException if an error occurs while loading the FXML file describing this window
     */
    public UpdateManagerContainer(List<UpdateEntry> updateEntries) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                UpdateManagerContainer.class.getResource("update-manager-container.fxml"),
                ResourceBundle.getBundle("qupath/lib/gui/localization/qupath-gui-strings")
        );
        loader.setRoot(this);
        loader.setController(this);
        loader.load();

        setTable(updateEntries);
        setColumns();
        setUpdateType();
    }

    /**
     * @return the selected update type. Can be null
     */
    public PathPrefs.AutoUpdateType getSelectedUpdateType() {
        return updateType.getSelectionModel().getSelectedItem();
    }

    private void setTable(List<UpdateEntry> updateEntries) {
        this.updateEntries.getItems().setAll(updateEntries);
        this.updateEntries.setRowFactory(ignored -> {
            var row = new TableRow<UpdateEntry>();

            row.itemProperty().addListener((v, o, n) -> {
                if (n == null) {
                    row.setTooltip(null);
                    row.setOnMouseClicked(null);
                } else {
                    row.setTooltip(new Tooltip(n.onClickDescription()));
                    row.setOnMouseClicked(mouseEvent -> {
                        if (mouseEvent.getClickCount() > 1) {
                            n.onClick.run();
                        }
                    });
                }
            });

            return row;
        });
    }

    private void setColumns() {
        nameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().name())
        );
        currentVersionColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().currentVersion())
        );
        newVersionColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().newVersion())
        );
    }

    private void setUpdateType() {
        updateType.getItems().setAll(PathPrefs.AutoUpdateType.values());
        updateType.getSelectionModel().select(PathPrefs.autoUpdateCheckProperty().get());
    }
}
