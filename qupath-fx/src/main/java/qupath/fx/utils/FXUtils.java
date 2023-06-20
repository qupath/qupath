package qupath.fx.utils;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FXUtils {

    private static final Logger logger = LoggerFactory.getLogger(FXUtils.class);

    /**
     * Pattern object to match any letter except E/e
     */
    private static final Pattern pattern = Pattern.compile("[a-zA-Z&&[^Ee]]+");

    /**
     * Return a result after executing a Callable on the JavaFX Platform thread.
     *
     * @param callable
     * @return
     */
    public static <T> T callOnApplicationThread(final Callable<T> callable) {
        if (Platform.isFxApplicationThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                logger.error("Error calling directly on Platform thread", e);
                return null;
            }
        }

        CountDownLatch latch = new CountDownLatch(1);
        ObjectProperty<T> result = new SimpleObjectProperty<>();
        Platform.runLater(() -> {
            T value;
            try {
                value = callable.call();
                result.setValue(value);
            } catch (Exception e) {
                logger.error("Error calling on Platform thread", e);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting result", e);
        }
        return result.getValue();
    }

    /**
     * Run on the application thread and wait until this is complete.
     * @param runnable
     */
    public static void runOnApplicationThread(final Runnable runnable) {
        callOnApplicationThread(() -> {
            runnable.run();
            return runnable;
        });
    }

    /**
     * Get the {@link Window} containing a specific {@link Node}.
     * @param node
     * @return
     */
    public static Window getWindow(Node node) {
        var scene = node.getScene();
        return scene == null ? null : scene.getWindow();
    }

    /**
     * Make a stage moveable by click and drag on the scene.
     * This is useful for undecorated stages.
     * @param stage
     * @implNote currently this does not handle changes of scene; the scene must be
     *           set before calling this method, and not changed later.
     */
    public static void makeDraggableStage(Stage stage) {
        new MoveablePaneHandler(stage);
    }

    /**
     * Request that a window retains its position and size even when hidden.
     * @param window
     */
    public static void retainWindowPosition(Window window) {
        window.setX(window.getX());
        window.setY(window.getY());
        window.setWidth(window.getWidth());
        window.setHeight(window.getHeight());
    }

    /**
     * Make a tab undockable, via a context menu available on right-click.
     * When undocked, the tab will become a floating window.
     * If the window is closed, it will be added back to its original tab pane.
     * @param tab
     * @since v0.4.0
     */
    public static void makeTabUndockable(Tab tab) {
        var miUndock = new MenuItem("Undock tab");
        var popup = new ContextMenu(miUndock);
        tab.setContextMenu(popup);
        miUndock.setOnAction(e -> handleUndock(tab));
    }

    private static void handleUndock(Tab tab) {
        var tabPane = tab.getTabPane();
        var parent = tabPane.getScene() == null ? null : tabPane.getScene().getWindow();

        double width = tabPane.getWidth();
        double height = tabPane.getHeight();
        tabPane.getTabs().remove(tab);
        var stage = new Stage();
        stage.initOwner(parent);
        stage.setTitle(tab.getText());
        var content = tab.getContent();
        tab.setContent(null);
        var tabContent = new BorderPane(content);
        stage.setScene(new Scene(tabContent, width, height));
        stage.show();

        stage.setOnCloseRequest(e2 -> {
            tabContent.getChildren().remove(tabContent);
            tab.setContent(content);
            tabPane.getTabs().add(tab);
        });
    }

    /**
     * Restrict the {@link TextField} input to positive/negative integer (or double) format (including scientific notation).
     * <p>
     * N.B: the {@code TextArea} might still finds itself in an invalid state at any moment, as:
     * <li> character deletion is always permitted (e.g. -1.5e5 -&gt; -1.5e; deletion of last character).</li>
     * <li>users are allowed to input a minus sign, in order to permit manual typing, which then needs to accept intermediate (invalid) states.</li>
     * <li>users are allowed to input an 'E'/'e' character, in order to permit manual typing as well, which then needs to accept intermediate (invalid) states.</li>
     * <li>copy-pasting is not as strictly restricted (e.g. -1.6e--5 and 1.6e4e9 are accepted, but won't be parsed).</li>
     * <p>
     * Some invalid states are accepted and should therefore be caught after this method returns.
     * <p>
     * P.S: 'copy-pasting' an entire value (e.g. {@code '' -> '1.2E-6'}) is regarded as the opposite of 'manual typing' (e.g. {@code '' -> '-', '-' -> '-1', ...}).
     *
     * @param textField
     * @param allowDecimals
     * @implNote this is often used alongside {@link #resetSpinnerNullToPrevious(Spinner)}
     */
    public static void restrictTextFieldInputToNumber(TextField textField, boolean allowDecimals) {
        NumberFormat format;
        if (allowDecimals)
            format = NumberFormat.getNumberInstance();
        else
            format = NumberFormat.getIntegerInstance();

        UnaryOperator<TextFormatter.Change> filter = c -> {
            if (c.isContentChange()) {
                String text = c.getControlText().toUpperCase();
                String newText = c.getControlNewText().toUpperCase();

                // Check for invalid characters (weak check)
                Matcher matcher = pattern.matcher(newText);
                if (matcher.find())
                    return null;

                // Accept minus sign if starting character OR if following 'E'
                if ((newText.length() == 1 || text.toUpperCase().endsWith("E")) && newText.endsWith("-"))
                    return c;

                // Accept 'E' (scientific notation) if not starting character
                if ((newText.length() > 1 && !newText.startsWith("-") || (newText.length() > 2 && newText.startsWith("-"))) &&
                        !text.toUpperCase().contains("E") &&
                        newText.toUpperCase().contains("E"))
                    return c;

//		    	// Accept any deletion of characters (which means the text area might be left in an invalid state)
                // Note: This was removed, because it could result in errors if selecting a longer number
                // and replacing it with an invalid character in a single edit (e.g. '=')
//		    	if (newText.length() < text.length())
//		    		return c;

                // Accept removing everything (which means the text area might be left in an invalid state)
                if (newText.isEmpty())
                    return c;

                ParsePosition parsePosition = new ParsePosition(0);
                format.parse(newText, parsePosition);
                if (parsePosition.getIndex() < c.getControlNewText().length()) {
                    return null;
                }
            }
            return c;
        };
        TextFormatter<Integer> normalizeFormatter = new TextFormatter<>(filter);
        textField.setTextFormatter(normalizeFormatter);
    }

    /**
     * Add a listener to the value of a spinner, resetting it to its previous value if it
     * becomes null.
     * @param <T>
     * @param spinner
     * @implNote this is often used alongside {@link #restrictTextFieldInputToNumber(TextField, boolean)}
     */
    public static <T> void resetSpinnerNullToPrevious(Spinner<T> spinner) {
        spinner.valueProperty().addListener((v, o, n) -> {
            try {
                if (n == null) {
                    spinner.getValueFactory().setValue(o);
                }
            } catch (Exception e) {
                logger.warn(e.getLocalizedMessage(), e);
            }
        });
    }

    /**
     * Bind the value of a slider and contents of a text field with a default number of decimal places,
     * so that both may be used to set a numeric (double) value.
     * <p>
     * This aims to overcome the challenge of keeping both synchronized, while also quietly handling
     * parsing errors that may occur whenever the text field is being edited.
     *
     * @param slider slider that may be used to adjust the value
     * @param tf text field that may also be used to adjust the value and show it visually
     * @param expandLimits optionally expand slider min/max range to suppose the text field input; if this is false, the text field
     *                     may contain a different value that is unsupported by the slider
     * @return a property representing the value represented by the slider and text field
     */
    public static DoubleProperty bindSliderAndTextField(Slider slider, TextField tf, boolean expandLimits) {
        return bindSliderAndTextField(slider, tf, expandLimits, -1);
    }

    /**
     * Bind the value of a slider and contents of a text field, so that both may be used to
     * set a numeric (double) value.
     * <p>
     * This aims to overcome the challenge of keeping both synchronized, while also quietly handling
     * parsing errors that may occur whenever the text field is being edited.
     *
     * @param slider slider that may be used to adjust the value
     * @param tf text field that may also be used to adjust the value and show it visually
     * @param expandLimits optionally expand slider min/max range to suppose the text field input; if this is false, the text field
     *                     may contain a different value that is unsupported by the slider
     * @param ndp if &ge; 0, this will be used to define the number of decimal places shown in the text field
     * @return a property representing the value represented by the slider and text field
     */
    public static DoubleProperty bindSliderAndTextField(Slider slider, TextField tf, boolean expandLimits, int ndp) {
        var numberProperty = new SimpleDoubleProperty(slider.getValue());
        new NumberAndText(numberProperty, tf.textProperty(), ndp).synchronizeTextToNumber();
        if (expandLimits) {
            numberProperty.addListener((v, o, n) -> {
                double val = n.doubleValue();
                if (Double.isFinite(val)) {
                    if (val < slider.getMin())
                        slider.setMin(val);
                    if (val > slider.getMax())
                        slider.setMax(val);
                    slider.setValue(val);
                }
            });
            slider.valueProperty().addListener((v, o, n) -> numberProperty.setValue(n));
        } else {
            slider.valueProperty().bindBidirectional(numberProperty);
        }
        return numberProperty;
//		new NumberAndText(slider.valueProperty(), tf.textProperty(), ndp).synchronizeTextToNumber();
//		return slider.valueProperty();
    }


    /**
     * Add a context menu to a CheckComboBox to quickly select all items, or clear selection.
     * @param combo
     */
    public static void installSelectAllOrNoneMenu(CheckComboBox<?> combo) {
        var miAll = new MenuItem("Select all");
        var miNone = new MenuItem("Select none");
        miAll.setOnAction(e -> combo.getCheckModel().checkAll());
        miNone.setOnAction(e -> combo.getCheckModel().clearChecks());
        var menu = new ContextMenu(miAll, miNone);
        combo.setContextMenu(menu);
    }

    /**
     * Create a {@link ListCell} with custom methods to derive text and a graphic for a specific object.
     * @param <T>
     * @param stringFun function to extract a string
     * @param graphicFun function to extract a graphic
     * @return a new list cell
     */
    public static <T> ListCell<T> createCustomListCell(Function<T, String> stringFun, Function<T, Node> graphicFun) {
        return new CustomListCell<>(stringFun, graphicFun);
    }

    /**
     * Create a {@link ListCell} with custom methods to derive text for a specific object.
     * @param <T>
     * @param stringFun function to extract a string
     * @return a new list cell
     */
    public static <T> ListCell<T> createCustomListCell(Function<T, String> stringFun) {
        return createCustomListCell(stringFun, t -> null);
    }

    /**
     * Create a new {@link Spinner} for double values with a step size that adapts according to the absolute value of
     * the current spinner value. This is useful for cases where the possible values cover a wide range
     * (e.g. potential brightness/contrast values).
     * @param minValue
     * @param maxValue
     * @param defaultValue
     * @param minStepValue
     * @param scale number of decimal places to shift the step size relative to the log10 of the value (suggested default = 1)
     * @return
     */
    public static Spinner<Double> createDynamicStepSpinner(double minValue, double maxValue, double defaultValue, double minStepValue, int scale) {
        var factory = new SpinnerValueFactory.DoubleSpinnerValueFactory(minValue, maxValue, defaultValue);
        factory.amountToStepByProperty().bind(createStepBinding(factory.valueProperty(), minStepValue, scale));
        var spinner = new Spinner<>(factory);
        return spinner;
    }

    /**
     * Create a binding that may be used with a {@link Spinner} to adjust the step size dynamically
     * based upon the absolute value of the input.
     *
     * @param value current value for the {@link Spinner}
     * @param minStep minimum step size (should be &gt; 0)
     * @param scale number of decimal places to shift the step size relative to the log10 of the value (suggested default = 1)
     * @return a binding that may be attached to a {@link SpinnerValueFactory.DoubleSpinnerValueFactory#amountToStepByProperty()}
     */
    public static DoubleBinding createStepBinding(ObservableValue<Double> value, double minStep, int scale) {
        return Bindings.createDoubleBinding(() -> {
            double val= value.getValue();
            if (!Double.isFinite(val))
                return 1.0;
            val = Math.abs(val);
            return Math.max(Math.pow(10, Math.floor(Math.log10(val) - scale)), minStep);
        }, value);
    }

    /**
     * Call .refresh() on all the ListView, TreeView, TableView and TreeTableViews
     * found throughout the application.
     * This is particularly useful after a locale change.
     */
    public static void refreshAllListsAndTables() {
        for (var window : Window.getWindows()) {
            if (!window.isShowing())
                continue;
            var scene = window.getScene();
            if (scene != null)
                refreshAllListsAndTables(scene.getRoot());
        }
    }

    /**
     * Call .refresh() on all the ListView, TreeView, TableView and TreeTableViews
     * found under a parent component, searching recursively.
     * This is particularly useful after a locale change.
     * @param parent
     */
    public static void refreshAllListsAndTables(final Parent parent) {
        if (parent == null)
            return;
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof TreeView<?>)
                ((TreeView<?>)child).refresh();
            else if (child instanceof ListView<?>)
                ((ListView<?>)child).refresh();
            else if (child instanceof TableView<?>)
                ((TableView<?>)child).refresh();
            else if (child instanceof TreeTableView<?>)
                ((TreeTableView<?>)child).refresh();
            else if (child instanceof Parent)
                refreshAllListsAndTables((Parent)child);
        }
    }

    /**
     * Get the nodes that are included within a {@link Parent}, optionally adding other nodes recursively.
     * Without the recursive search, this is similar to {@link Parent#getChildrenUnmodifiable()} in most cases,
     * except that a separate collection is used. However in some cases {@code getItems()} must be used instead.
     * Currently this applies only to {@link SplitPane} but this may be used elsewhere if appropriate.
     * @param parent
     * @param collection
     * @param doRecursive
     * @return
     */
    public static Collection<Node> getContents(Parent parent, Collection<Node> collection, boolean doRecursive) {
        if (collection == null) {
            collection = new ArrayList<>();
        }
        var children = parent.getChildrenUnmodifiable();
        if (children.isEmpty() && parent instanceof SplitPane) {
            children = ((SplitPane)parent).getItems();
        }
        for (var child : children) {
            collection.add(child);
            if (doRecursive && child instanceof Parent)
                getContents((Parent)child, collection, doRecursive);
        }
        return collection;
    }

    /**
     * Get the nodes of type T that are contained within a {@link Parent}, optionally adding other nodes
     * recursively. This can be helpful, for example, to extract all the Buttons or Regions within a pane
     * in order to set some property of all of them.
     * @param <T>
     * @param parent
     * @param cls
     * @param doRecursive
     * @return
     *
     * @see #getContents(Parent, Collection, boolean)
     */
    public static <T extends Node> Collection<T> getContentsOfType(Parent parent, Class<T> cls, boolean doRecursive) {
        return getContents(parent, new ArrayList<>(), doRecursive).stream()
                .filter(p -> cls.isInstance(p))
                .map(p -> cls.cast(p))
                .toList();
    }

    /**
     * Simplify the appearance of a {@link TitledPane} using CSS.
     * This is useful if using a {@link TitledPane} to define expanded options, which should be displayed unobtrusively.
     *
     * @param pane the pane to simplify
     * @param boldTitle if true, the title should be displayed in bold
     */
    public static void simplifyTitledPane(TitledPane pane, boolean boldTitle) {
        var css = GridPaneUtils.class.getClassLoader().getResource("css/titled_plain.css").toExternalForm();
        pane.getStylesheets().add(css);
        if (boldTitle) {
            var css2 = GridPaneUtils.class.getClassLoader().getResource("css/titled_bold.css").toExternalForm();
            pane.getStylesheets().add(css2);
        }
    }

    /**
     * Enable an undecorated stage to be moved by clicking and dragging within it.
     * Requires the scene to be set. Note that this will set mouse event listeners.
     */
    private static class MoveablePaneHandler implements EventHandler<MouseEvent> {

        private Stage stage;

        private double xOffset = 0;
        private double yOffset = 0;

        private MoveablePaneHandler(Stage stage) {
            this.stage = stage;
            var scene = stage.getScene();
            if (scene == null)
                throw new IllegalArgumentException("Scene must be set on the stage!");
            scene.addEventFilter(MouseEvent.ANY, this);
        }

        @Override
        public void handle(MouseEvent event) {
            if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
                xOffset = stage.getX() - event.getScreenX();
                yOffset = stage.getY() - event.getScreenY();
            } else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                stage.setX(event.getScreenX() + xOffset);
                stage.setY(event.getScreenY() + yOffset);
            }
        }

    }


    /**
     * Helper class to synchronize a properties between a Slider and TextField.
     */
    private static class NumberAndText {

        private static Logger logger = LoggerFactory.getLogger(NumberAndText.class);

        private boolean synchronizingNumber = false;
        private boolean synchronizingText = false;

        private DoubleProperty number;
        private StringProperty text;
        private int ndp;

        private NumberFormat defaultFormatter;
        private Map<Integer, NumberFormat> formatters = new HashMap<>();

        NumberAndText(DoubleProperty number, StringProperty text, int ndp) {
            this.number = number;
            this.text = text;
            this.number.addListener((v, o, n) -> synchronizeTextToNumber());
            this.text.addListener((v, o, n) -> synchronizeNumberToText());
            this.ndp = ndp;
            this.defaultFormatter = formatters.computeIfAbsent(ndp, NumberAndText::createFormatter);
        }

        public void synchronizeNumberToText() {
            if (synchronizingText)
                return;
            synchronizingNumber = true;
            String value = text.get();
            if (value.isBlank())
                return;
            try {
                var n = defaultFormatter.parse(value);
                number.setValue(n);
            } catch (Exception e) {
                logger.debug("Error parsing number from '{}' ({})", value, e.getLocalizedMessage());
            }
            synchronizingNumber = false;
        }


        public void synchronizeTextToNumber() {
            if (synchronizingNumber)
                return;
            synchronizingText = true;
            double value = number.get();
            String s;
            if (Double.isNaN(value))
                s = "";
            else if (Double.isFinite(value)) {
                if (ndp < 0) {
                    double log10 = Math.round(Math.log10(value));
                    int ndp2 = (int)Math.max(4, -log10 + 2);
                    s = formatters.computeIfAbsent(ndp2, NumberAndText::createFormatter).format(value);
                } else
                    s = defaultFormatter.format(value);
            } else
                s = Double.toString(value);
            text.set(s);
            synchronizingText = false;
        }


        private static NumberFormat createFormatter(final int nDecimalPlaces) {
            NumberFormat nf = NumberFormat.getInstance();
            nf.setMaximumFractionDigits(nDecimalPlaces);
            return nf;
        }

    }



    private static class CustomListCell<T> extends ListCell<T> {

        private Function<T, String> funString;
        private Function<T, Node> funGraphic;

        /**
         * Constructor.
         * @param funString function capable of generating a String representation of an object.
         * @param funGraphic function capable of generating a Graphic representation of an object.
         */
        private CustomListCell(Function<T, String> funString, Function<T, Node> funGraphic) {
            super();
            this.funString = funString;
            this.funGraphic = funGraphic;
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                setText(funString.apply(item));
                setGraphic(funGraphic.apply(item));
            }
        }

    }

}
