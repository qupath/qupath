package qupath.process.gui.commands.ml;

import java.util.Objects;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;
import javafx.scene.layout.Background;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.util.Subscription;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.color.ColorMaps;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.opencv.ml.ConfusionMatrix;

/**
 * Control to display a confusion matrix.
 * @param <T> generic parameter for the confusion matrix labels
 * @since v0.8.0
 */
class ConfusionMatrixControl<T> extends Control implements Skinnable {

    /**
     * Default function to extract a string representation of the confusion matrix values.
     * This is used for target and prediction labels.
     */
    public final Function<T, String> DEFAULT_STRING_EXTRACTOR = Objects::toString;

    /**
     * Default function to extract a color for each cell, based upon a normalized count.
     */
    public final DoubleFunction<Color> DEFAULT_COLOR_EXTRACTOR = this::extractColor;

    /**
     * The confusion matrix being displayed.
     */
    private final ObjectProperty<ConfusionMatrix<T>> confusionMatrix = new SimpleObjectProperty<>();

    /**
     * Main label text shown to the left of the confusion matrix.
     */
    private final StringProperty rowLabel = new SimpleStringProperty("Target");

    /**
     * Main label text shown above the confusion matrix.
     */
    private final StringProperty columnLabel = new SimpleStringProperty("Prediction");

    /**
     * Show percentages as well as counts in each cell.
     */
    private final BooleanProperty showPercent = new SimpleBooleanProperty(true);

    /**
     * Function to extract a string representation of the confusion matrix values.
     * This is used for target and prediction labels.
     */
    private final ObjectProperty<Function<T, String>> stringExtractor = new SimpleObjectProperty<>(DEFAULT_STRING_EXTRACTOR);

    /**
     * Default function to extract a color for each cell, based upon a normalized count.
     * This is the count of the cell divided by the maximum count in the matrix.
     */
    private final ObjectProperty<DoubleFunction<Color>> colorExtractor = new SimpleObjectProperty<>(DEFAULT_COLOR_EXTRACTOR);

    private final ColorMaps.ColorMap DEFAULT_COLORMAP = ColorToolsFX.createColorMap(
            "Confusion colors",
            Color.WHITE, Color.ROYALBLUE
    );

    private Color extractColor(double value) {
        if (Double.isNaN(value))
            return null;
        return ColorToolsFX.getCachedColor(
                DEFAULT_COLORMAP.getColor(value, 0.0, 1.0)
        );
    }

    public ObjectProperty<ConfusionMatrix<T>> confusionMatrixProperty() {
        return confusionMatrix;
    }

    public ConfusionMatrix<T> getConfusionMatrix() {
        return confusionMatrixProperty().get();
    }

    public void setConfusionMatrix(ConfusionMatrix<T> confusionMatrix) {
        confusionMatrixProperty().set(confusionMatrix);
    }

    public BooleanProperty showPercentProperty() {
        return showPercent;
    }

    public boolean doShowPercent() {
        return showPercentProperty().get();
    }

    public void setShowPercent(boolean showPercent) {
        showPercentProperty().set(showPercent);
    }

    public StringProperty rowLabelProperty() {
        return rowLabel;
    }

    public String getRowLabel() {
        return rowLabelProperty().get();
    }

    public void setRowLabel(String label) {
        rowLabelProperty().set(label);
    }

    public StringProperty columnLabelProperty() {
        return columnLabel;
    }

    public String getColumnLabel() {
        return columnLabelProperty().get();
    }

    public void setColumnLabelLabel(String label) {
        columnLabelProperty().set(label);
    }

    public ObjectProperty<Function<T, String>> stringExtractorProperty() {
        return stringExtractor;
    }

    public Function<T, String> getStringExtractor() {
        return stringExtractorProperty().get();
    }

    public void setStringExtractor(Function<T, String> extractor) {
        stringExtractorProperty().set(extractor);
    }

    public ObjectProperty<DoubleFunction<Color>> colorExtractorProperty() {
        return colorExtractor;
    }

    public DoubleFunction<Color> getColorExtractor() {
        return colorExtractorProperty().get();
    }

    public void setColorExtractor(DoubleFunction<Color> extractor) {
        colorExtractorProperty().set(extractor);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ConfusionMatrixSkin<>(this);
    }

    private static class ConfusionMatrixSkin<T> implements Skin<ConfusionMatrixControl<T>> {

        private final ConfusionMatrixControl<T> skinnable;

        private final GridPane pane = new GridPane();
        private Subscription subscription;

        private ConfusionMatrixSkin(ConfusionMatrixControl<T> skinnable) {
            this.skinnable = skinnable;
            initialize();
        }

        private void initialize() {
            pane.getChildren().clear();

            var matrix = skinnable.confusionMatrix.get();
            if (matrix == null) {
                var labelPlaceholder = new Label("Nothing to show");
                labelPlaceholder.setAlignment(Pos.CENTER);
                GridPaneUtils.setToExpandGridPaneWidth(labelPlaceholder);
                GridPaneUtils.setToExpandGridPaneHeight(labelPlaceholder);
                pane.add(labelPlaceholder, 0, 0);
                return;
            }

            var labels = matrix.getLabels();

            double padding = 5.0;

            var columnLabel = createHorizontalLabel(padding);
            columnLabel.textProperty().bind(skinnable.columnLabel);
            columnLabel.setStyle("-fx-font-weight: bold;");
            pane.add(columnLabel, 2, 0, GridPane.REMAINING, 1);

            var rowLabel = createVerticalLabel(padding);
            rowLabel.textProperty().bind(skinnable.rowLabel);
            rowLabel.setStyle("-fx-font-weight: bold;");
            pane.add(new Group(rowLabel), 0, 2, 1, GridPane.REMAINING);

            for (int i = 0; i < labels.size(); i++) {
                var binding = createLabelStringBinding(labels.get(i));

                var gridRowLabel = createHorizontalLabel(padding);
                gridRowLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                gridRowLabel.textProperty().bind(binding);
                pane.add(gridRowLabel, 2+i, 1, 1, 1);

                var gridColLabel = createVerticalLabel(padding);
                gridColLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                gridColLabel.textProperty().bind(binding);
                // See https://stackoverflow.com/questions/29031565/rotating-label-90-degrees-takes-up-unnecessary-horizontal-space
                pane.add(new Group(gridColLabel), 1, 2+i, 1, 1);
            }

            int r = 2;
            for (var row : labels) {
                int c = 2;
                for (var col : labels) {
                    var label = createGridLabel(matrix, row, col);
                    pane.add(label, c, r);
                    c++;
                }
                r++;
            }
        }

        private void subscribe() {
            subscription = skinnable.confusionMatrixProperty().subscribe(this::initialize);
        }

        private void unsubscribe() {
            if (subscription != null)
                subscription.unsubscribe();
        }

        private StringBinding createLabelStringBinding(T label) {
            return Bindings.createStringBinding(() -> {
                return skinnable.getStringExtractor().apply(label);
            }, skinnable.stringExtractor);
        }

        private Label createHorizontalLabel(double padding) {
            var label = new Label();
            label.setMaxWidth(Double.MAX_VALUE);
            label.setAlignment(Pos.CENTER);
            label.setWrapText(true);
            GridPane.setHgrow(label, Priority.ALWAYS);
            GridPane.setHalignment(label, HPos.CENTER);
            GridPane.setFillWidth(label, Boolean.TRUE);
            if (padding > 0)
                label.setPadding(new Insets(padding));
            return label;
        }

        private Label createVerticalLabel(double padding) {
            var label = new Label();
            label.setMaxWidth(Double.MAX_VALUE);
            label.setWrapText(true);
            label.setAlignment(Pos.CENTER);
            label.setRotate(-90);
            GridPane.setVgrow(label, Priority.ALWAYS);
            GridPane.setValignment(label, VPos.CENTER);
            GridPane.setFillHeight(label, Boolean.TRUE);
            if (padding > 0)
                label.setPadding(new Insets(padding));
            return label;
        }

        private Node createGridLabel(ConfusionMatrix<T> matrix, T row, T col) {
            int count = matrix.getCount(row, col);
            var label = new Label(Integer.toString(count));
            label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            label.setAlignment(Pos.CENTER);
            var bgColorBinding = createBackgroundColorBinding((double)count / matrix.getMax());
            label.backgroundProperty().bind(bgColorBinding.map(ConfusionMatrixSkin::colorToBackground));
            label.textFillProperty().bind(bgColorBinding.map(ConfusionMatrixSkin::backgroundColorToTextFill));

            var labelPercent = new Label();
            double percent = (count * 100.0) / matrix.getTotal();
            labelPercent.setText(GeneralTools.formatNumber(percent, 1) + "%");
            labelPercent.setStyle("-fx-font-size: 90%;");
            labelPercent.setOpacity(0.5);
            labelPercent.textFillProperty().bind(label.textFillProperty());
            label.setContentDisplay(ContentDisplay.BOTTOM);
            label.graphicProperty().bind(
                    Bindings.when(skinnable.showPercent).then(labelPercent).otherwise((Label)null)
            );

            GridPane.setFillWidth(label, Boolean.TRUE);
            GridPane.setFillHeight(label, Boolean.TRUE);
            GridPane.setHgrow(label, Priority.ALWAYS);
            GridPane.setVgrow(label, Priority.ALWAYS);
            return label;
        }

        private ObjectBinding<Color> createBackgroundColorBinding(double normalizedCount) {
            return Bindings.createObjectBinding(() -> {
                return skinnable.colorExtractor.get().apply(normalizedCount);
            }, skinnable.colorExtractor);
        }

        private static Background colorToBackground(Color color) {
            return color == null ? null : Background.fill(color);
        }

        private static Color backgroundColorToTextFill(Color color) {
            if (color == null)
                return null;
            if (color.getBrightness() < 0.9)
                return Color.WHITE;
            else
                return Color.BLACK;
        }

        @Override
        public ConfusionMatrixControl<T> getSkinnable() {
            return skinnable;
        }

        @Override
        public Node getNode() {
            return pane;
        }

        @Override
        public void install() {
            Skin.super.install();
            // This seems quite inelegant, extending SkinBase may be the 'right' way...
            // but this is at least something
            pane.paddingProperty().bind(skinnable.paddingProperty());
            pane.effectProperty().bind(skinnable.effectProperty());
            subscribe();
            initialize();
        }

        @Override
        public void dispose() {
            pane.getChildren().clear();
            unsubscribe();
            pane.paddingProperty().unbind();
            pane.effectProperty().unbind();
        }

    }

}
