package qupath.lib.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
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


/**
 * A custom JavaFX circular-slider control
 */
public class CircularSlider extends Control {

    private static final class CircularSliderSkin extends SkinBase<CircularSlider> {
        protected CircularSliderSkin(CircularSlider control) {
            super(control);
        }
    }

    private final DoubleProperty rotationProperty = new SimpleDoubleProperty(0);
    private final BooleanProperty snapEnabled = new SimpleBooleanProperty(false);
    private final DoubleProperty tickSpacing = new SimpleDoubleProperty(10);
    private final Circle outerCircle = new Circle();
    private final Circle innerCircle = new Circle(5);
    private final Path tickMarks = new Path();
    private final Text angle = new Text("0.0\u00B0");

    public CircularSlider() {
        //add nodes
        getChildren().addAll(outerCircle, tickMarks, innerCircle, angle);
        //enable focus
        setFocusTraversable(true);
        //make sure circles are
        innerCircle.setManaged(false);
        outerCircle.setManaged(false);
        //add CSS
        getStylesheets().add(getClass().getResource("/css/circular-slider.css").toExternalForm());
        // set style class
        getStyleClass().setAll("circular-slider");
        outerCircle.getStyleClass().setAll("circular-slider-outer");
        innerCircle.getStyleClass().setAll("circular-slider-inner");
        angle.setFont(new Font(18));
        angle.getStyleClass().setAll("circular-slider-text");
        tickMarks.getStyleClass().setAll("circular-slider-track-mark");
        //prevent mouse events being trapped by some nodes
        innerCircle.setMouseTransparent(true);
        angle.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                e.consume();
                final Double rotation = Dialogs.showInputDialog("Set image rotation", "Rotation (degrees)", getValue());
                if (rotation != null) {
                    setValue(rotation);
                }
            }
        });

        tickMarks.setVisible(false);
        //add event handlers
        outerCircle.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> updateRotationWithMouseEvent(e));
        outerCircle.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> updateRotationWithMouseEvent(e));

        outerCircle.addEventHandler(ScrollEvent.ANY, e -> rotationProperty().set(rotationProperty().get() + (e.isShiftDown() ? e.getDeltaX() : e.getDeltaY()) * (isSnapToTicks() ? getTickSpacing() : 1)));
        outerCircle.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            final double[] vector = new double[]{e.getX() - outerCircle.getCenterX(), e.getY() - outerCircle.getCenterY()};
            if (!focusedProperty().get() && dot(vector, vector) <= outerCircle.getRadius() * outerCircle.getRadius()) {
                requestFocus();
            }
        });
        addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case LEFT:
                    rotationProperty().set(rotationProperty().get() - (isSnapToTicks() ? getTickSpacing() : 1));
                    break;
                case RIGHT:
                    rotationProperty().set(rotationProperty().get() + (isSnapToTicks() ? getTickSpacing() : 1));
                    break;

            }
        });
        rotationProperty.addListener((observable, oldValue, newValue) -> checkRotation());
        tickSpacing.addListener((observable, oldValue, newValue) -> {
            if (oldValue.doubleValue() == newValue.doubleValue()) {
                return;
            }
            updateSnapMarks();
        });
        setPrefSize(200, 200);
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
        if (isDisabled()) {
            return;
        }
        final double[] currentVector = normalize(e.getX() - outerCircle.getCenterX(), e.getY() - outerCircle.getCenterY());
        final double angle = Math.atan2(currentVector[0], -currentVector[1]);
        rotationProperty.set(Math.toDegrees(angle >= 0 ? angle : Math.PI + Math.PI + angle));// atan2 of dot product and determinant of current vector verses up (0,-1). As x of up vector is 0, can simplify
    }

    /**
     * ensure that the rotation rules are followed (e.g. min, max, snaps, etc.)
     */
    private void checkRotation() {
        if (getValue() < 0) {
            rotationProperty.set(360 + (getValue() % 360));
        }
        if (getValue() > 360) {
            rotationProperty.set(getValue() % 360);
        }
        if (isSnapToTicks()) {

            final double halfSnap = tickSpacing.get() * .5;

            if (getValue() < halfSnap || getValue() > 360 - halfSnap) {
                rotationProperty.set(0);
            } else {
                rotationProperty.set(tickSpacing.get() * (Math.round(getValue() / tickSpacing.get())));
            }
            rotationProperty.set(tickSpacing.get() * (Math.round(getValue() / tickSpacing.get())));

        }
        updateTextAndInnerCircle();
    }

    private void updateTextAndInnerCircle() {
        angle.setText(String.format("%.1f\u00B0", getValue()));
        final double radians = Math.toRadians(rotationProperty.get());
        final double[] currentVector = new double[]{
                Math.sin(radians),
                -Math.cos(radians)
        };
        final double x = outerCircle.getCenterX() + (outerCircle.getRadius() - innerCircle.getRadius() - 5) * currentVector[0];
        final double y = outerCircle.getCenterY() + (outerCircle.getRadius() - innerCircle.getRadius() - 5) * currentVector[1];
        innerCircle.setCenterX(x);
        innerCircle.setCenterY(y);
    }

    /**
     * calculate the dot product of two vectors
     *
     * @param a vector a
     * @param b vector b
     * @return dot product of both vectors
     */
    public static double dot(double[] a, double[] b) {
        double product = 0;
        final int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            product = Math.fma(a[i], b[i], product);
        }

        return product;
    }

    /**
     * calculate the length/magnitude of a vector
     *
     * @param vector vector whose magnitude to calculate
     * @return vector length
     */
    public static double length(double... vector) {
        return Math.sqrt(dot(vector, vector));
    }

    /**
     * normalize a vector to a unit vector
     *
     * @param vector input vector
     * @return unit vector
     */
    public static double[] normalize(double... vector) {
        final double[] result = new double[vector.length];
        final double length = length(vector);
        for (int i = 0; i < result.length; i++) {
            result[i] = vector[i] / length;
        }
        return result;
    }

    /**
     * @return the rotation property of this control
     */
    public DoubleProperty rotationProperty() {
        return rotationProperty;
    }

    /**
     * @return the current rotation in degrees
     */
    public double getValue() {
        return rotationProperty.get();
    }

    /**
     * set rotation value
     *
     * @param rotation value
     */
    public void setValue(double rotation) {
        this.rotationProperty.set(rotation);
    }

    /**
     * @return if the rotation is snapped to ticks
     */
    public boolean isSnapToTicks() {
        return snapEnabled.get();
    }

    /**
     * set the number of degrees between tick marks
     *
     * @param spacing the degrees between tick marks
     */
    public void setTickSpacing(double spacing) {
        tickSpacing.set(spacing);
    }

    /**
     * set whether to snap to ticks
     *
     * @param enabled whether to snap to ticks
     */
    public void setSnapToTicks(boolean enabled) {
        snapEnabled.set(enabled);
    }

    /**
     * set whether to show tick marks
     *
     * @param visible whether to show tick marks
     */
    public void setShowTickMarks(boolean visible) {
        tickMarks.setVisible(visible);
    }

    /**
     * set whether to display the angle
     *
     * @param visible whether to display the angle
     */
    public void setShowValue(boolean visible) {
        angle.setVisible(visible);
    }

    /**
     * get the spacing between ticks
     *
     * @return the spacing ticks
     */
    public double getTickSpacing() {
        return tickSpacing.get();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new CircularSliderSkin(this);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        outerCircle.setRadius((Math.min(getWidth(), getHeight()) * .5) - 5);
        outerCircle.setCenterX(getWidth() * .5);
        outerCircle.setCenterY(getHeight() * .5);
        innerCircle.setRadius(outerCircle.getRadius() * .05);
        updateTextAndInnerCircle();
        updateSnapMarks();

    }

}
