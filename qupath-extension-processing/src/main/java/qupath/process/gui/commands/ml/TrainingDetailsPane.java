package qupath.process.gui.commands.ml;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeSortMode;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.util.Subscription;
import qupath.lib.common.GeneralTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.plugins.parameters.Parameter;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.opencv.ml.OpenCVClassifiers;

class TrainingDetailsPane extends Control implements Skinnable {

    private final ObservableMap<String, String> classifierDetails = FXCollections.observableMap(new LinkedHashMap<>());

    private final ObjectProperty<Duration> trainingTime = new SimpleObjectProperty<>();
    private final ObjectProperty<ParameterList> parameters = new SimpleObjectProperty<>();

    TrainingDetailsPane() {
        super();
    }

    void update(OpenCVClassifiers.OpenCVStatModel model,
                Map<PathClass, Integer> labels,
                Duration trainingTime) {

        if (model == null) {
            this.classifierDetails.clear();
            this.parameters.set(null);
            this.trainingTime.set(null);
        } else {
            var map = createClassifierDetailsMap(model);
            classifierDetails.clear();
            classifierDetails.putAll(map);
            var params = model.getParameterList();
            this.parameters.set(params == null ? null : params.duplicate());
            this.trainingTime.set(trainingTime);
        }

    }

    private static Map<String, String> createClassifierDetailsMap(OpenCVClassifiers.OpenCVStatModel model) {
        if (model == null)
            return Map.of();
        var map = new LinkedHashMap<String, String>();
        map.put("Type", model.getName());
        if (model instanceof OpenCVClassifiers.RTreesClassifier rtrees) {
            double oob = rtrees.getOOBError();
            if (Double.isFinite(oob)) {
                map.put("OOB error", GeneralTools.formatNumber(oob, 5));
            }
        } else if (model instanceof OpenCVClassifiers.ANNClassifier ann) {
            int[] layers = ann.getLayerSizes();
            String postfix = "";
            if (layers.length >= 2) {
                postfix = layers.length == 3 ?
                        "   (1 hidden layer)" :
                        "   (" + (layers.length - 2) + " hidden)";
            }
            map.put("Layers", Arrays.toString(layers) + postfix);
        }
        map.put("Supports missing values", Boolean.toString(model.supportsMissingValues()));
        map.put("Supports probabilities", Boolean.toString(model.supportsProbabilities()));
        return map;
    }

    protected Skin<?> createDefaultSkin() {
        return new PixelClassifierDetailsPaneSkin(this);
    }

    private static class PixelClassifierDetailsPaneSkin implements Skin<TrainingDetailsPane> {

        private final TrainingDetailsPane skinnable;

        private final TreeTableView<TableItem.StringItem> treeTable = new TreeTableView<>();

        private final TreeItem<TableItem.StringItem> tiClassifier = new TreeItem<>(TableItem.StringItem.createEmpty("Classifier"));
        private final TreeItem<TableItem.StringItem> tiParameters = new TreeItem<>(TableItem.StringItem.createEmpty("Parameters"));

        private Subscription subscription;

        private PixelClassifierDetailsPaneSkin(TrainingDetailsPane skinnable) {
            this.skinnable = skinnable;
            initializeTable();
        }

        private void initializeTable() {
            var root = new TreeItem<>(TableItem.StringItem.createEmpty("ROOT"));
            root.getChildren().add(tiClassifier);
            root.getChildren().add(tiParameters);

            root.setExpanded(true);
            tiClassifier.setExpanded(true);
            tiParameters.setExpanded(true);

            var colNames = new TreeTableColumn<TableItem.StringItem, String>("Key");
            colNames.setCellValueFactory(i -> i.getValue().getValue().nameProperty());
            colNames.setSortable(false);


            var colValues = new TreeTableColumn<TableItem.StringItem, String>("Value");
            colValues.setCellValueFactory(i -> i.getValue().getValue().valueProperty());
            colValues.setSortable(false);

            treeTable.setRoot(root);
            treeTable.setRowFactory(n -> new Row());
            treeTable.getColumns().add(colNames);
            treeTable.getColumns().add(colValues);
            treeTable.setShowRoot(false);
            treeTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_LAST_COLUMN);
            treeTable.setSortMode(TreeSortMode.ONLY_FIRST_LEVEL);
        }

        private void updateClassifier() {
            var classifierDetails = skinnable.classifierDetails;
            if (classifierDetails.isEmpty()) {
                tiClassifier.getChildren().clear();
            } else {
                var newItems = new ArrayList<TreeItem<TableItem.StringItem>>();
                for (var entry : classifierDetails.entrySet()) {
                    newItems.add(
                            createTreeItem(entry.getKey(), entry.getValue())
                    );
                }
                var duration = skinnable.trainingTime.get();
                if (duration != null) {
                    long millis = duration.toMillis();
                    if (millis >= 1) {
                        newItems.add(
                                createTreeItem("Training time", duration.toMillis() + " ms"));
                    } else {
                        newItems.add(
                                createTreeItem("Training time", "<1 ms"));
                    }
                }
                tiClassifier.getChildren().setAll(newItems);
            }
        }

        private void updateParameters() {
            var params = skinnable.parameters.get();
            Map<String, Parameter<?>> map = params == null ? Map.of() : params.getParameters();
            List<TreeItem<TableItem.StringItem>> newItems = new ArrayList<>();
            for (var param : map.values()) {
                var val = param.getValueOrDefault();
                var stringVal = val == null ? null : Objects.toString(val);
                if (val != null && val.equals(param.getDefaultValue()))
                    stringVal += " (default)";
                newItems.add(
                        createTreeItem(
                                param.getPrompt(),
                                stringVal)
                );
            }
            tiParameters.getChildren().setAll(newItems);
        }

        private static TreeItem<TableItem.StringItem> createTreeItem(String name, String value) {
            return new TreeItem<>(TableItem.StringItem.create(name, value));
        }

        @Override
        public TrainingDetailsPane getSkinnable() {
            return skinnable;
        }

        @Override
        public Node getNode() {
            return treeTable;
        }

        @Override
        public void install() {
            Skin.super.install();
            this.subscription = skinnable.classifierDetails.subscribe(this::updateClassifier)
                    .and(skinnable.trainingTime.subscribe(this::updateClassifier))
                    .and(skinnable.parameters.subscribe(this::updateParameters));
            updateClassifier();
            updateParameters();
        }

        @Override
        public void dispose() {
            if (subscription != null) {
                subscription.unsubscribe();
                subscription = null;
            }
        }
    }

    private static class Row extends TreeTableRow<TableItem.StringItem> {

        @Override
        protected void updateItem(TableItem.StringItem value, boolean empty) {
            super.updateItem(value, empty);
            String style = null;
            if (value != null) {
                var val = value.valueProperty().getValue();
                if (val == null) {
                    // Style as title
                    style = "-fx-font-weight: bold;";
                } else if (val.endsWith("(default)")) {
                    // Make defaults less prominent
                    // (Ideally, we'd change the font only... but not sure how here)
                    style = "-fx-opacity: 0.65;";
                }
            }
            setStyle(style);
        }

    }

}
