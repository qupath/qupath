package qupath.process.gui.commands.ml;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import org.controlsfx.glyphfont.FontAwesome;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.opencv.ml.ConfusionMatrix;

public class MetricsBrowser<T> extends BorderPane {

    /**
     * Observable list of all confusion matrices to display.
     * These are typically from different cross-validation folds.
     */
    private final ObservableList<ConfusionMatrix<T>> confusionMatrices = FXCollections.observableArrayList();

    private final Button btnLeft = new Button(null, IconFactory.createNode(FontAwesome.Glyph.CARET_LEFT));
    private final Button btnRight = new Button(null, IconFactory.createNode(FontAwesome.Glyph.CARET_RIGHT));
    private final TitledPane titled = GuiTools.createLeftRightTitledPane("", btnLeft, btnRight);
    private final MetricsPane<T> metricsPane = new MetricsPane<>();
    private final SelectionModel<ConfusionMatrix<T>> selectionModel = new SelectionModel<>(confusionMatrices);

    public MetricsBrowser() {
        super();
        init();
        getStyleClass().add("metrics-browser");
        titled.getStyleClass().add("bold-title");
    }

    public ObservableList<ConfusionMatrix<T>> getConfusionMatrices() {
        return confusionMatrices;
    }

    private void init() {
        initButtons();
        confusionMatrices.addListener(this::handleListChange);
        titled.setContent(metricsPane);
        titled.setTextOverrun(OverrunStyle.ELLIPSIS);
        titled.setMinWidth(100);
        metricsPane.confusionMatrixProperty().bind(selectionModel.selectedItemProperty());
        titled.textProperty().bind(selectionModel.selectedItemProperty().map(ConfusionMatrix::getName).orElse(""));
        setCenter(titled);
        addEventHandler(KeyEvent.KEY_RELEASED, this::handleKeyEvent);
        titled.addEventFilter(KeyEvent.KEY_RELEASED, this::handleKeyEvent);
    }

    private void handleKeyEvent(KeyEvent event) {
        if (event.isConsumed())
            return;
        if (event.getCode() == KeyCode.RIGHT) {
            increment();
            event.consume();
        } else if (event.getCode() == KeyCode.LEFT) {
            decrement();
            event.consume();
        }
    }

    private void initButtons() {
        btnLeft.setOnAction(e -> decrement());
        btnRight.setOnAction(e -> increment());

        var size = Bindings.size(confusionMatrices);
        var showButtons = size.greaterThan(1);
        btnLeft.visibleProperty().bind(showButtons);
        btnRight.visibleProperty().bind(showButtons);

        btnLeft.setTooltip(new Tooltip("See previous metrics"));
        btnRight.setTooltip(new Tooltip("See next metrics"));

        btnLeft.disableProperty().bind(selectionModel.selectedIndexProperty().lessThanOrEqualTo(0));
        btnRight.disableProperty().bind(selectionModel.selectedIndexProperty().greaterThanOrEqualTo(size.subtract(1)));
    }

    private void handleListChange(ListChangeListener.Change<? extends ConfusionMatrix<T>> change) {
        int currentSelection = selectionModel.getSelectedIndex();
        if (confusionMatrices.isEmpty())
            selectionModel.clearSelection();
        else if (currentSelection < 0 || currentSelection >= confusionMatrices.size())
            selectionModel.clearAndSelect(0);
        else
            selectionModel.clearAndSelect(currentSelection);
    }

    private void increment() {
        int ind = selectionModel.getSelectedIndex();
        if (ind < selectionModel.getItemCount()-1)
            selectionModel.select(ind+1);
    }

    private void decrement() {
        int ind = selectionModel.getSelectedIndex();
        if (ind > 0)
            selectionModel.select(ind-1);
    }

    private static class SelectionModel<S> extends SingleSelectionModel<S> {

        private final ObservableList<S> items;

        private SelectionModel(ObservableList<S> items) {
            this.items = items;
        }

        @Override
        protected S getModelItem(int index) {
            if (index < 0 || index >= getItemCount())
                return null;
            else
                return items.get(index);
        }

        @Override
        protected int getItemCount() {
            return items.size();
        }
    }

}
