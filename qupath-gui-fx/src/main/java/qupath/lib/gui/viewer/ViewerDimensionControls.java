/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.GeneralTools;

/**
 * Controls to navigate z slices and time points in the viewer.
 * @since v0.6.0
 */
class ViewerDimensionControls {

    private final IntegerProperty zPositionProperty = new SimpleIntegerProperty();
    private final IntegerProperty zMaxProperty = new SimpleIntegerProperty();

    private final IntegerProperty tPositionProperty = new SimpleIntegerProperty();
    private final IntegerProperty tMaxProperty = new SimpleIntegerProperty();

    private final DoubleProperty contentOpacityProperty = new SimpleDoubleProperty(1.0);

    private final Spinner<Integer> spinnerZ = createSpinner(zPositionProperty, zMaxProperty, "Z-slice");
    private final Spinner<Integer> spinnerT = createSpinner(tPositionProperty, tMaxProperty, "Time point");

    private final ProgressBar progressZ = createProgressBar(zPositionProperty, zMaxProperty);
    private final ProgressBar progressT = createProgressBar(tPositionProperty, tMaxProperty);

    private final Label labelZ = createLabel("Z: ", spinnerZ);
    private final Label labelT = createLabel("Time: ", spinnerT);

    private final GridPane pane = new GridPane();

    ViewerDimensionControls() {
        pane.getStyleClass().addAll("viewer-overlay", "viewer-dims");
        pane.setOnMouseEntered(e -> contentOpacityProperty.set(1.0));
        pane.setOnMouseExited(e -> contentOpacityProperty.set(0.5));
        pane.setVgap(4);

        zMaxProperty.addListener(this::handleChange);
        tMaxProperty.addListener(this::handleChange);
        updateContents();
    }

    private ProgressBar createProgressBar(IntegerProperty property, IntegerProperty maxProperty) {
        var progress = new ProgressBar();
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefHeight(10);
        progress.progressProperty().bind(Bindings.createDoubleBinding(() -> property.doubleValue() / (maxProperty.get() - 1),
                property, maxProperty));
        progress.opacityProperty().bind(contentOpacityProperty);
        progress.setOnMouseClicked(e -> updateFromProgress(progress, e.getX(), property, maxProperty));
        progress.setOnMouseDragged(e -> updateFromProgress(progress, e.getX(), property, maxProperty));
        return progress;
    }

    private void updateFromProgress(ProgressBar progress, double x, IntegerProperty prop, IntegerProperty max) {
        int val = (int)Math.round(x / progress.getWidth() * max.doubleValue());
        prop.setValue(GeneralTools.clipValue(val, 0, max.get()));
    }

    private Spinner<Integer> createSpinner(IntegerProperty property, IntegerProperty maxProperty,
                                           String name) {
        var factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1);
        maxProperty.addListener((v, o, n) -> factory.setMax(Math.max(0, n.intValue() - 1)));
        var spinner = new Spinner<>(factory);
        factory.valueProperty().addListener((v, o, n) -> property.setValue(n));
        property.addListener((v, o, n) -> factory.setValue((Integer) n));
        spinner.setPrefWidth(70);
        spinner.setEditable(true);
        FXUtils.resetSpinnerNullToPrevious(spinner);

        var tooltip = new Tooltip();
        tooltip.textProperty().bind(
                Bindings.createStringBinding(
                        () -> name + " (" + property.get() + "/" + maxProperty.get() + ")",
                        property, maxProperty
                ));
        spinner.setTooltip(tooltip);

        spinner.opacityProperty().bind(contentOpacityProperty);
        return spinner;
    }

    private Label createLabel(String text, Node node) {
        var label = new Label(text);
        label.setLabelFor(node);
        label.setContentDisplay(ContentDisplay.RIGHT);
        label.setAlignment(Pos.CENTER_RIGHT);
        label.setMaxWidth(Double.MAX_VALUE);
        label.opacityProperty().bind(contentOpacityProperty);
        return label;
    }

    private void handleChange(ObservableValue<? extends Number> val, Number oldValue, Number newValue) {
        updateContents();
    }

    private void updateContents() {
        pane.getChildren().clear();
        int row = 0;
        if (tMaxProperty.get() > 1) {
            pane.addRow(row++, labelT, spinnerT);
            pane.add(progressT, 0, row++, 2, 1);
        }
        if (zMaxProperty.get() > 1) {
            if (row > 0)
                pane.add(new Separator(), 0, row++, 2, 1);
            pane.addRow(row++, labelZ, spinnerZ);
            pane.add(progressZ, 0, row++, 2, 1);
        }
        pane.setVisible(!pane.getChildren().isEmpty());
    }

    IntegerProperty zMaxProperty() {
        return zMaxProperty;
    }

    IntegerProperty tMaxProperty() {
        return tMaxProperty;
    }

    IntegerProperty zPositionProperty() {
        return zPositionProperty;
    }

    IntegerProperty tPositionProperty() {
        return tPositionProperty;
    }

    Pane getPane() {
        return pane;
    }

}
