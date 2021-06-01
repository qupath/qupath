/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import qupath.lib.gui.dialogs.Dialogs;

/**
 * A custom JavaFX circular-slider control.
 * Currently, this only supports a rotation value in degrees. 
 * This behavior may change in the future, e.g. to support other ranges.
 */
public class CircularSlider extends Control {

    private static final class CircularSliderSkin extends SkinBase<CircularSlider> {
        protected CircularSliderSkin(CircularSlider control) {
            super(control);
        }
    }

    private static final int MAX_DISPLAYED_VALUE = 360;
    private static final int MIN_DISPLAYED_VALUE = 0;

    private final DoubleProperty rotationProperty = new SimpleDoubleProperty(MIN_DISPLAYED_VALUE);	// In radians
    private final BooleanProperty snapEnabled = new SimpleBooleanProperty(false);
    private final DoubleProperty tickSpacing = new SimpleDoubleProperty(10);
    private final Circle outerCircle = new Circle();
    private final Circle innerCircle = new Circle(5);
    private final Circle textCircle = new Circle();
    private final Path tickMarks = new Path();
    private final Text angle = new Text("0.0\u00B0");

    /**
     * Create a circular slider
     */
    public CircularSlider() {
        //add nodes
        getChildren().addAll(outerCircle, textCircle, tickMarks, innerCircle, angle);
        //enable focus
        setFocusTraversable(true);
        disabledProperty().addListener(e -> setMouseTransparent(!isDisabled()));

        //make sure circles are
        innerCircle.setManaged(false);
        outerCircle.setManaged(false);
        textCircle.setManaged(false);
        //add CSS
        getStylesheets().add(getClass().getResource("/css/circular-slider.css").toExternalForm());
        // set style class
        getStyleClass().setAll("circular-slider");
        outerCircle.getStyleClass().setAll("circular-slider-outer");
        innerCircle.getStyleClass().setAll("circular-slider-inner");
        textCircle.getStyleClass().setAll("circular-slider-text-area");
        angle.setFont(new Font(18));
        angle.getStyleClass().setAll("circular-slider-text");
        tickMarks.getStyleClass().setAll("circular-slider-track-mark");
        //prevent mouse events being trapped by some nodes
        innerCircle.setMouseTransparent(true);
        angle.setMouseTransparent(true);
        textCircle.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                e.consume();
                Double rotation;
                if (e.isShiftDown())
                	rotation = Double.valueOf(MIN_DISPLAYED_VALUE);
                else
                	rotation = Dialogs.showInputDialog("Set rotation", "Rotation (degrees)", getValue());
                if (rotation != null && !rotation.isNaN())
                    setValue(rotation);
            }
        });

        tickMarks.setVisible(false);
        //add event handlers
        outerCircle.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::updateRotationWithMouseEvent);
        outerCircle.addEventHandler(MouseEvent.MOUSE_CLICKED, this::updateRotationWithMouseEvent);
        outerCircle.addEventHandler(ScrollEvent.ANY, e -> setValue(getValue() + (e.isShiftDown() ? e.getDeltaX() : e.getDeltaY()) * (isSnapToTicks() ? getTickSpacing() : 1)));
        outerCircle.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);

        rotationProperty.addListener((observable, oldValue, newValue) -> checkRotation());
        tickSpacing.addListener((observable, oldValue, newValue) -> {
            if (oldValue.doubleValue() == newValue.doubleValue()) {
                return;
            }
            updateSnapMarks();
        });
        setPrefSize(200, 200);
    }

    /**
     * @return the rotation property of this control
     */
    public DoubleProperty rotationProperty() {
        return rotationProperty;
    }

    /**
     * @return the text area of the circular slide
     */
    public Node getTextArea() {
        return textCircle;
    }

    /**
     * @return the current rotation in degrees
     */
    public double getValue() {
        return Math.toDegrees(rotationProperty.get());
    }

    /**
     * Set rotation value in degrees
     *
     * @param rotation value in degrees
     */
    public void setValue(double rotation) {
        rotationProperty.set(Math.toRadians(rotation));
    }

    /**
     * @return if the rotation is snapped to ticks
     */
    public boolean isSnapToTicks() {
        return snapEnabled.get();
    }

    /**
     * Set the number of degrees between tick marks
     *
     * @param spacing the degrees between tick marks
     */
    public void setTickSpacing(double spacing) {
        tickSpacing.set(spacing);
    }

    /**
     * Set whether to snap to ticks
     *
     * @param enabled whether to snap to ticks
     */
    public void setSnapToTicks(boolean enabled) {
        snapEnabled.set(enabled);
    }

    /**
     * Set whether to show tick marks
     *
     * @param visible whether to show tick marks
     */
    public void setShowTickMarks(boolean visible) {
        tickMarks.setVisible(visible);
    }

    /**
     * Set whether to display the angle
     *
     * @param visible whether to display the angle
     */
    public void setShowValue(boolean visible) {
        angle.setVisible(visible);
    }

    /**
     * @return the spacing ticks
     */
    public double getTickSpacing() {
        return tickSpacing.get();
    }

    private void updateSnapMarks() {
        tickMarks.getElements().clear();

        final double radians = Math.toRadians(tickSpacing.get());
        for (double i = 0; i < Math.PI * 2; i += radians) {
            final double x = Math.sin(i);
            final double y = -Math.cos(i);

            final double x0 = outerCircle.getCenterX() + (outerCircle.getRadius() - innerCircle.getRadius() - 2) * x;
            final double y0 = outerCircle.getCenterY() + (outerCircle.getRadius() - innerCircle.getRadius() - 2) * y;
            final double x1 = outerCircle.getCenterX() + (outerCircle.getRadius() - innerCircle.getRadius() - innerCircle.getRadius()) * x;
            final double y1 = outerCircle.getCenterY() + (outerCircle.getRadius() - innerCircle.getRadius() - innerCircle.getRadius()) * y;
            tickMarks.getElements().addAll(
                    new MoveTo(x0, y0),
                    new LineTo(x1, y1)
            );

        }
    }

    /**
     * @param e calculate the rotation based on the mouse event
     */
    private void updateRotationWithMouseEvent(MouseEvent e) {
        if (isDisabled())
            return;

        double x = e.getX() - outerCircle.getCenterX(),
                y = e.getY() - outerCircle.getCenterY();
        double dot = (x * x) + (y * y);
        double length = Math.sqrt(dot);
        final double angle = Math.atan2(x / length, -y / length);
        rotationProperty.set(angle >= 0 ? angle : Math.PI + Math.PI + angle);// atan2 of dot product and determinant of current vector verses up (0,-1). As x of up vector is 0, can simplify

    }

    /**
     * Ensure that the rotation rules are followed (e.g. min, max, snaps, etc.)
     */
    private void checkRotation() {
        if (getValue() < 0)
            setValue(MAX_DISPLAYED_VALUE + (getValue() % MAX_DISPLAYED_VALUE));

        if (getValue() >= MAX_DISPLAYED_VALUE)
            setValue(getValue() % MAX_DISPLAYED_VALUE);

        if (isSnapToTicks()) {

            final double halfSnap = tickSpacing.get() * .5;

            if (getValue() < halfSnap || getValue() > 360 - halfSnap)
                setValue(0);
            else
                setValue(tickSpacing.get() * (Math.round(getValue() / tickSpacing.get())));
            
            setValue(tickSpacing.get() * (Math.round(getValue() / tickSpacing.get())));

        }
        updateTextAndInnerCircle();
    }

    private void updateTextAndInnerCircle() {
        angle.setText(String.format("%.1f\u00B0", getValue()));
        final double radians = rotationProperty.get();
        double vecX = Math.sin(radians),
                vecY = -Math.cos(radians);
        final double x = outerCircle.getCenterX() + (outerCircle.getRadius() - innerCircle.getRadius() - 5) * vecX;
        final double y = outerCircle.getCenterY() + (outerCircle.getRadius() - innerCircle.getRadius() - 5) * vecY;
        innerCircle.setCenterX(x);
        innerCircle.setCenterY(y);
        textCircle.setCenterX(outerCircle.getCenterX());
        textCircle.setCenterY(outerCircle.getCenterY());
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new CircularSliderSkin(this);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        outerCircle.setRadius((Math.min(getWidth(), getHeight()) * .5) - 5);
        textCircle.setRadius((outerCircle.getRadius() * .8) - 2);
        outerCircle.setCenterX(getWidth() * .5);
        outerCircle.setCenterY(getHeight() * .5);
        innerCircle.setRadius(outerCircle.getRadius() * .05);
        updateTextAndInnerCircle();
        updateSnapMarks();

    }

    private void onMousePressed(MouseEvent e) {
        double x = e.getX() - outerCircle.getCenterX();
        double y = e.getY() - outerCircle.getCenterY();
        if (!focusedProperty().get() && ((x * x) + (y * y)) <= outerCircle.getRadius() * outerCircle.getRadius()) {
            requestFocus();
        }
    }

    private void onKeyPressed(KeyEvent e) {
    	switch (e.getCode()) {
    	case LEFT:
    		setValue(getValue() - (isSnapToTicks() ? getTickSpacing() : 1));
    		break;
    	case RIGHT:
    		setValue(getValue() + (isSnapToTicks() ? getTickSpacing() : 1));
    		break;
    	default:
    		break;
    	}
    }
}
