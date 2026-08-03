package qupath.process.gui.commands.ml;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.opencv.ml.OpenCVClassifiers;
import qupath.opencv.ml.OpenCVClassifiers.RTreesClassifier.VariableImportance;

class FeatureDetailsPane extends Control implements Skinnable {

    private static final Logger logger = LoggerFactory.getLogger(FeatureDetailsPane.class);

    private final ObservableList<VariableImportance> importance =
            FXCollections.observableArrayList();

    private final ObservableList<VariableImportance> unmodifiableImportance =
            FXCollections.unmodifiableObservableList(importance);

    private final ReadOnlyBooleanWrapper hasImportance = new ReadOnlyBooleanWrapper(false);

    FeatureDetailsPane() {
        super();
    }

    void update(OpenCVClassifiers.OpenCVStatModel model,
                List<String> featureNames) {

        if (model instanceof OpenCVClassifiers.RTreesClassifier rtrees && rtrees.hasFeatureImportance() && !featureNames.isEmpty()) {
            // Always use feature importance for RTrees when available
            importance.setAll(rtrees.getVariableImportance(featureNames));
            hasImportance.set(true);
        } else if (!sameContents(importance.stream().map(VariableImportance::name).toList(), featureNames)) {
            // If we have feature importance values for the same features, don't overwrite them.
            importance.setAll(featureNames.stream()
                    .map(n -> new OpenCVClassifiers.RTreesClassifier.VariableImportance(n, Double.NaN))
                    .toList());
            hasImportance.set(false);
        }
    }

    /**
     * Read-only property to query if importance values are available.
     * @return
     */
    public ReadOnlyBooleanProperty hasImportanceProperty() {
        return hasImportance.getReadOnlyProperty();
    }

    private static <T> boolean sameContents(Collection<T> c1, Collection<T> c2) {
        return c1.size() == c2.size() && ensureSet(c1).equals(ensureSet(c2));
    }

    private static <T> Set<T> ensureSet(Collection<T> c) {
        if (c instanceof Set<T> set)
            return set;
        return Set.copyOf(c);
    }

    protected Skin<?> createDefaultSkin() {
        return new FeatureDetailsPaneSkin(this);
    }

    private static class FeatureDetailsPaneSkin implements Skin<FeatureDetailsPane> {

        private final FeatureDetailsPane skinnable;

        private final ObjectProperty<Color> fill = new SimpleObjectProperty<>(ColorToolsFX.getColorWithOpacity(Color.LIGHTGREEN, 0.5));

        private final ObservableList<VariableImportance> baseList = FXCollections.observableArrayList();
        private final SortedList<VariableImportance> sortedList = new SortedList<>(baseList);
        private final TableView<VariableImportance> table = new TableView<>(sortedList);
        private final TableColumn<VariableImportance, String> columnName = new TableColumn<>("Name");
        private final TableColumn<VariableImportance, Number> columnImportance = new TableColumn<>("Importance");

        private final BorderPane pane = new BorderPane(table);

        private final DoubleProperty maxImportance = new SimpleDoubleProperty(1.0);

        private FeatureDetailsPaneSkin(FeatureDetailsPane skinnable) {
            this.skinnable = skinnable;

            columnName.setCellValueFactory(this::extractName);
            columnImportance.setCellValueFactory(this::extractValue);

            columnImportance.setMinWidth(100);
            columnImportance.setMaxWidth(100);
            columnImportance.setCellFactory(this::createCell);

            sortedList.comparatorProperty().bind(table.comparatorProperty());

            table.getColumns().add(columnName);
            table.getColumns().add(columnImportance);
            table.getItems().addListener(this::handleListChange);
            table.setPlaceholder(new Label("No classifier trained"));
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_LAST_COLUMN);
        }

        private TableCell<VariableImportance, Number> createCell(TableColumn<VariableImportance, Number> column) {
            var cell = new PixelClassifierUI.NumberTableCell<VariableImportance>(5);
            PixelClassifierUI.bindCellFillBackground(cell, maxImportance, fill);
            return cell;
        }

        private ObservableValue<String> extractName(TableColumn.CellDataFeatures<VariableImportance, String> features) {
            var val = features.getValue();
            return new SimpleStringProperty(val.name());
        }

        private ObservableValue<Number> extractValue(TableColumn.CellDataFeatures<VariableImportance, Number> features) {
            var val = features.getValue();
            return new SimpleDoubleProperty(val.importance());
        }

        @Override
        public FeatureDetailsPane getSkinnable() {
            return skinnable;
        }

        @Override
        public Node getNode() {
            return pane;
        }

        @Override
        public void install() {
            Skin.super.install();
            Bindings.bindContent(baseList, skinnable.unmodifiableImportance);
        }

        @Override
        public void dispose() {
            Bindings.unbindContent(baseList, skinnable.unmodifiableImportance);
        }

        private void handleListChange(ListChangeListener.Change<? extends VariableImportance> event) {
            double maxImportance = 0.0;
            for (var v : event.getList()) {
                if (Double.isFinite(v.importance())) {
                    maxImportance = Math.max(v.importance(), maxImportance);
                }
            }
            boolean hasImportance = maxImportance > 0;
            columnImportance.setVisible(hasImportance);
            columnName.setSortable(hasImportance);
            this.maxImportance.set(maxImportance);
        }

    }

}
