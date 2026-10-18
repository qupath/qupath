package qupath.process.gui.commands.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.util.Subscription;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.opencv.ml.ConfusionMatrix;


class MetricsPane<T> extends Control implements Skinnable {

    /**
     * Confusion matrix to display.
     */
    private final ObjectProperty<ConfusionMatrix<T>> confusionMatrix = new SimpleObjectProperty<>();

    public ObjectProperty<ConfusionMatrix<T>> confusionMatrixProperty() {
        return confusionMatrix;
    }

    public ConfusionMatrix<T> getConfusionMatrix() {
        return confusionMatrixProperty().get();
    }

    public void setConfusionMatrix(ConfusionMatrix<T> matrix) {
        confusionMatrixProperty().set(matrix);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ConfusionMatrixSkin<>(this);
    }

    private static class ConfusionMatrixSkin<T> implements Skin<MetricsPane<T>> {

        private final MetricsPane<T> skinnable;

        private final GridPane pane = new GridPane();
        private final BorderPane borderPane = new BorderPane(pane);
        private final ConfusionMatrixControl<T> confusionMatrixControl = new ConfusionMatrixControl<>();
        private final TreeTableView<TableItem.NumberItem> tableMetrics = new TreeTableView<>();

        private final TreeItem<TableItem.NumberItem> root = new TreeItem<>(TableItem.NumberItem.createEmpty("ROOT"));

        private Subscription subscription;

        private ConfusionMatrixSkin(MetricsPane<T> skinnable) {
            this.skinnable = skinnable;
            initializeTable();
            initialize();
        }

        private void initialize() {
            confusionMatrixControl.setPadding(new Insets(10));
            confusionMatrixControl.setMinHeight(Control.USE_COMPUTED_SIZE);
            confusionMatrixControl.setMaxHeight(Double.MAX_VALUE);

            var scrollConfusion = new ScrollPane(confusionMatrixControl);
            scrollConfusion.setFitToWidth(true);
            scrollConfusion.setFitToHeight(true);
            scrollConfusion.setMaxHeight(Double.MAX_VALUE);

            var splitPane = new SplitPane(
                    tableMetrics,
                    scrollConfusion
            );
            splitPane.setOrientation(Orientation.VERTICAL);

            borderPane.setCenter(splitPane);
        }

        private void initializeTable() {
            var colName = new TreeTableColumn<TableItem.NumberItem, String>("Metric");
            colName.setCellValueFactory(v -> v.getValue().getValue().nameProperty());

            var colValue = new TreeTableColumn<TableItem.NumberItem, Number>("Value");
            colValue.setCellValueFactory(v -> v.getValue().getValue().valueProperty());
            colValue.setCellFactory(this::createCell);

            tableMetrics.getColumns().add(colName);
            tableMetrics.getColumns().add(colValue);

            tableMetrics.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_LAST_COLUMN);
            tableMetrics.setRowFactory(t -> new Row());

            tableMetrics.setRoot(root);
            tableMetrics.setShowRoot(false);
        }


        private TreeTableCell<TableItem.NumberItem, Number> createCell(TreeTableColumn<TableItem.NumberItem, Number> column) {
            var cell = new PixelClassifierUI.NumberTreeTableCell<TableItem.NumberItem>(4);
            PixelClassifierUI.bindCellFillBackground(cell,
                    1.0,
                   ColorToolsFX.getColorWithOpacity(Color.ROYALBLUE, 0.1));
            return cell;
        }

        @Override
        public MetricsPane<T> getSkinnable() {
            return skinnable;
        }

        @Override
        public Node getNode() {
            return borderPane;
        }

        @Override
        public void install() {
            Skin.super.install();
            subscription = skinnable.confusionMatrixProperty().subscribe(this::updateMatrix);
            updateMatrix();
        }

        @Override
        public void dispose() {
            if (subscription != null)
                subscription.unsubscribe();
        }

        private void updateMatrix() {
            var matrix = skinnable.getConfusionMatrix();
            if (matrix == null) {
                confusionMatrixControl.setConfusionMatrix(null);
                root.getChildren().clear();
                return;
            }

            confusionMatrixControl.setConfusionMatrix(matrix);
            List<TreeItem<TableItem.NumberItem>> newItems = new ArrayList<>();

            var labels = matrix.getLabels();

            List<TreeItem<TableItem.NumberItem>> items = new ArrayList<>();
            if (labels.size() > 2) {
                items.add(createItem("Average", metricsForMatrix(matrix)));
            }
            for (var label : labels) {
                items.add(
                        createItem(Objects.toString(label), metricsForLabel(matrix, label)));
            }
            root.getChildren().setAll(items);
        }

        private static TreeItem<TableItem.NumberItem> createItem(String title, List<TreeItem<TableItem.NumberItem>> children) {
            var item = new TreeItem<>(TableItem.NumberItem.createEmpty(title));
            item.getChildren().addAll(children);
            item.setExpanded(true);
            return item;
        }

        private static List<TreeItem<TableItem.NumberItem>> metricsForMatrix(ConfusionMatrix<?> matrix) {
            return List.of(
                    createItem("Accuracy", matrix.getAccuracy()),
                    createItem("F1", matrix.getF1()),
                    createItem("Precision", matrix.getPrecision()),
                    createItem("Recall", matrix.getRecall())
            );
        }

        private static <T> List<TreeItem<TableItem.NumberItem>> metricsForLabel(ConfusionMatrix<T> matrix, T label) {
            return List.of(
                    createItem("F1", matrix.getF1(label)),
                    createItem("Precision", matrix.getPrecision(label)),
                    createItem("Recall", matrix.getRecall(label))
            );
        }

        private static TreeItem<TableItem.NumberItem> createItem(String name, double value) {
            return new TreeItem<>(TableItem.NumberItem.create(name, value));
        }

    }

    private static class Row extends TreeTableRow<TableItem.NumberItem> {

        @Override
        protected void updateItem(TableItem.NumberItem value, boolean empty) {
            super.updateItem(value, empty);
            String style = null;
            if (value != null && value.isEmpty()) {
                // Style as title
                style = "-fx-font-weight: bold;";
            }
            setStyle(style);
        }

    }

}
