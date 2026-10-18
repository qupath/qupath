package qupath.process.gui.commands.ml;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;

/**
 * An item to display in a 2-column table.
 * This stores a key value pair, exposing them as observable values.
 * <p>
 * The key is always a string, while the value can be any object.
 * @param <T> the generic parameter of the value
 */
class TableItem<T> {

    private final boolean isEmpty;
    private final ObservableValue<String> name;
    private final ObservableValue<T> value;

    public static <T> TableItem<T> create(String name, T value) {
        return new TableItem<>(
                new SimpleStringProperty(name),
                new SimpleObjectProperty<>(value),
                false
        );
    }

    /**
     * Create a named item with no value.
     * This may be used in a {@link javafx.scene.control.TreeTableView} as a section title.
     * @param name the name property
     * @return the item
     * @param <T> generic parameter; note that it is not used, because the value is always null
     */
    public static <T> TableItem<T> createEmpty(String name) {
        return new TableItem<>(
                new SimpleStringProperty(name),
                new ReadOnlyObjectWrapper<T>().getReadOnlyProperty(),
                true);
    }

    TableItem(ObservableValue<String> name, ObservableValue<T> value, boolean isEmpty) {
        Objects.requireNonNull(name, "Name must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        this.name = name;
        this.value = value;
        this.isEmpty = isEmpty;
        if (this.isEmpty && value.getValue() != null)
            throw new IllegalArgumentException("Value must be null for an empty item");
    }

    /**
     * Observable exposing the name.
     * @return the name property
     */
    ObservableValue<String> nameProperty() {
        return name;
    }

    /**
     * Observable exposing the value.
     * @return the value property
     */
    ObservableValue<T> valueProperty() {
        return value;
    }

    /**
     * Query if this item is 'empty', i.e. its value should always be null.
     * Empty items are useful to define new sections in tree tables.
     * @return true if the item is empty, false otherwise
     */
    boolean isEmpty() {
        return isEmpty;
    }

    public static class StringItem extends TableItem<String> {

        private static final ObservableValue<String> EMPTY = Bindings.createStringBinding(() -> null);

        private StringItem(ObservableValue<String> name, ObservableValue<String> value, boolean isTitle) {
            super(name, value, isTitle);
        }

        public static StringItem createEmpty(String name) {
            return new StringItem(
                    new SimpleStringProperty(name),
                    EMPTY,
                    true);
        }

        public static StringItem create(String name, String value) {
            return new StringItem(
                    new SimpleStringProperty(name),
                    new SimpleStringProperty(value),
                    false);
        }

    }

    public static class NumberItem extends TableItem<Number> {

        private static final ObservableValue<Number> EMPTY = Bindings.createObjectBinding(() -> null);

        private NumberItem(ObservableValue<String> name, ObservableValue<Number> value, boolean isEmpty) {
            super(name, value, isEmpty);
        }

        public static NumberItem createEmpty(String name) {
            return new NumberItem(
                    new SimpleStringProperty(name),
                    EMPTY,
                    true
            );
        }

        public static NumberItem create(String name, double value) {
            return new NumberItem(
                    new SimpleStringProperty(name),
                    new SimpleDoubleProperty(value),
                    false
            );
        }

        public static NumberItem create(String name, long value) {
            return new NumberItem(
                    new SimpleStringProperty(name),
                    new SimpleLongProperty(value),
                    false
            );
        }

    }

    }
