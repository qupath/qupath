package qupath.process.gui.commands.ml;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.ScriptEditorControl;
import qupath.lib.gui.scripting.languages.JsonLanguage;
import qupath.lib.io.GsonTools;

class JsonDisplay<T> extends Control implements Skinnable {

    private static final Logger logger = LoggerFactory.getLogger(JsonDisplay.class);

    /**
     * The item to represent. It must be serializable to JSON using {@link GsonTools#getInstance()}
     */
    private final ObjectProperty<T> item = new SimpleObjectProperty<>();


    /**
     * JSON representation of the pixel classifier.
     */
    private final ReadOnlyStringWrapper json = new ReadOnlyStringWrapper();

    JsonDisplay() {
        super();
        json.bind(
                Bindings.createStringBinding(this::computeJson, item)
        );
    }

    public ObjectProperty<T> itemProperty() {
        return item;
    }

    public T getItem() {
        return itemProperty().get();
    }

    public void setItem(T item) {
        itemProperty().set(item);
    }

    public ReadOnlyStringProperty jsonProperty() {
        return json.getReadOnlyProperty();
    }

    public String getJson() {
        return jsonProperty().get();
    }

    private String computeJson() {
        var item = getItem();
        if (item == null)
            return null;
        try {
            return GsonTools.getPrettyPrintInstance()
                    .toJson(item);
        } catch (Exception e) {
            logger.error("Exception creating JSON", e);
            return "// Can't convert to JSON";
        }
    }


    protected Skin<?> createDefaultSkin() {
        return new ClassifierJsonPaneSkin<>(this);
    }

    private static class ClassifierJsonPaneSkin<T> implements Skin<JsonDisplay<T>> {

        private final JsonDisplay<T> skinnable;
        private final BorderPane pane = new BorderPane();
        private final StringProperty textProperty = new SimpleStringProperty();

        private ClassifierJsonPaneSkin(JsonDisplay<T> skinnable) {
            this.skinnable = skinnable;
            initialize();
        }

        private void initialize() {
            try {
                var qupath = QuPathGUI.getInstance();
                if (qupath != null && qupath.getScriptEditor() instanceof DefaultScriptEditor editor) {
                    ScriptEditorControl<?> control = editor.createNewEditor();
                    control.setLanguage(JsonLanguage.getInstance());
                    control.textProperty().bind(textProperty);
                    pane.setCenter(control.getRegion());
                }
            } catch (Exception e) {
                logger.debug("Unable to create script editor: {}", e.getMessage(), e);
                var textArea = new TextArea();
                textArea.textProperty().bind(textProperty);
                pane.setCenter(textArea);
            }
        }


        @Override
        public JsonDisplay<T> getSkinnable() {
            return skinnable;
        }

        @Override
        public Node getNode() {
            return pane;
        }

        @Override
        public void install() {
            Skin.super.install();
            textProperty.bind(skinnable.json);
        }

        @Override
        public void dispose() {
            textProperty.unbind();
        }
    }

}
