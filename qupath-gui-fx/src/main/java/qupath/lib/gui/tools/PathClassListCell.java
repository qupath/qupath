/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023, 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.tools;

import javafx.scene.control.ListCell;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import qupath.lib.objects.classes.PathClass;

import java.util.Objects;
import java.util.function.Function;

/**
 * A cell for displaying {@link PathClass} objects in a list view or combo box.
 * @since v0.6.0
 */
public class PathClassListCell extends ListCell<PathClass> {

    private final Function<PathClass, String> stringFunction;
    private final Rectangle rectangle = new Rectangle(10, 10);

    /**
     * Create a list cell using the specified string function.
     * @param stringFunction function to convert a path class (or null) to a string.
     */
    public PathClassListCell(Function<PathClass, String> stringFunction) {
        Objects.requireNonNull(stringFunction);
        this.stringFunction = stringFunction;
    }

    /**
     * Create a list cell using the default string function.
     * @see #defaultStringFunction(PathClass)
     */
    public PathClassListCell() {
        this(PathClassListCell::defaultStringFunction);
    }

    @Override
    protected void updateItem(PathClass value, boolean empty) {
        super.updateItem(value, empty);
        if (value == null || empty) {
            setText(null);
            setGraphic(null);
        } else {
            if (value == PathClass.NULL_CLASS) {
                rectangle.setFill(Color.TRANSPARENT);
            } else {
                rectangle.setFill(ColorToolsFX.getPathClassColor(value));
            }
            setText(stringFunction.apply(value));
            setGraphic(rectangle);
        }
    }

    /**
     * Default function to convert a PathClass to a string.
     * Returns {@code "None"} if the PathClass is {@code null} or {@link PathClass#NULL_CLASS},
     * otherwise uses {@link PathClass#toString()}.
     * @param pathClass input class
     * @return string representation
     */
    public static String defaultStringFunction(PathClass pathClass) {
        if (pathClass == null || pathClass == PathClass.NULL_CLASS)
            return "None";
        return pathClass.toString();
    }

}
