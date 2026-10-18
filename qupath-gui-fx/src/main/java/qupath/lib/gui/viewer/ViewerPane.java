package qupath.lib.gui.viewer;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;

import java.awt.image.BufferedImage;


class ViewerPane extends StackPane {

    private final Canvas canvas = new Canvas();

    private final KeyCombination comboSpace = new KeyCodeCombination(KeyCode.SPACE);

    private final StringProperty placeholderText = new SimpleStringProperty();
    private final BooleanProperty placeholderVisible = new SimpleBooleanProperty();

    private final BooleanProperty spaceDown = new SimpleBooleanProperty(false);
    private final ObjectProperty<Point2D> mouseLocation = new SimpleObjectProperty<>();

    /**
     * Width of the border line used to indicate that a viewer is active.
     */
    private final DoubleProperty borderLineWidthProperty = new SimpleDoubleProperty(6);

    /**
     * Color of the border around the viewer.
     * This can be set to indicate that a viewer is active.
     */
    private final ObjectProperty<Color> borderColorProperty = new SimpleObjectProperty<>();

    private WritableImage imgCacheFX;

    ViewerPane() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        setAlignment(Pos.CENTER);
        var placeholder = createPlaceholder();
        getChildren().add(placeholder);

        // Resize to anything
        setMinWidth(1);
        setMinHeight(1);
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        addEventFilter(MouseEvent.ANY, e -> {
            mouseLocation.set(new Point2D(e.getX(), e.getY()));
        });

        addEventFilter(KeyEvent.ANY, this::checkForSpacebar);
    }

    private void checkForSpacebar(KeyEvent event) {
        if (comboSpace.match(event)) {
            if (event.getEventType() == KeyEvent.KEY_PRESSED)
                spaceDown.set(true);
            else if (event.getEventType() == KeyEvent.KEY_RELEASED)
                spaceDown.set(false);
        }
    }

    StringProperty placeholderTextProperty() {
        return placeholderText;
    }

    BooleanProperty placeholderVisibleProperty() {
        return placeholderVisible;
    }

    BooleanProperty spaceDownProperty() {
        return spaceDown;
    }

    ObjectProperty<Point2D> mouseLocationProperty() {
        return mouseLocation;
    }

    Canvas getCanvas() {
        return canvas;
    }

    DoubleProperty borderLineWidthProperty() {
        return borderLineWidthProperty;
    }

    ObjectProperty<Color> borderColorProperty() {
        return borderColorProperty;
    }

    public void drawImage(BufferedImage image) {
        imgCacheFX = SwingFXUtils.toFXImage(image, imgCacheFX);

        GraphicsContext context = canvas.getGraphicsContext2D();
        context.drawImage(imgCacheFX, 0, 0);

        var borderColor = borderColorProperty().get();
        var borderLineWidth = borderLineWidthProperty().get();
        if (borderColor != null && borderLineWidth > 0) {
            context.setStroke(borderColor);
            context.setLineWidth(borderLineWidth);
            context.strokeRect(0, 0, canvas.getWidth(), canvas.getHeight());
        }
    }



    private Label createPlaceholder() {
        var placeholder = new Label(placeholderText.getValueSafe());
        placeholder.setWrapText(true);
        placeholder.setTextAlignment(TextAlignment.CENTER);
        placeholder.setPadding(new Insets(5.0));
        placeholder.textProperty().bind(placeholderText);
        placeholder.styleProperty().bind(Bindings.createStringBinding(() -> {
            Integer rgb = PathPrefs.viewerBackgroundColorProperty().getValue();
            var c = rgb == null ? Color.BLACK : ColorToolsFX.getCachedColor(rgb);
            if (c.getBrightness() > 0.5)
                return "-fx-text-fill: black;";
            else
                return "-fx-text-fill: white";
        }, PathPrefs.viewerBackgroundColorProperty()));
        placeholder.setOpacity(0.7);
        placeholder.visibleProperty().bind(placeholderVisible);
        return placeholder;
    }

}
