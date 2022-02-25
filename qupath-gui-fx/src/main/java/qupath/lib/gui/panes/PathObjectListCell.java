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

package qupath.lib.gui.panes;

import java.util.function.Function;

import org.kordamp.ikonli.javafx.StackedFontIcon;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

/**
 * A {@link ListCell} for displaying {@linkplain PathObject PathObjects}, including {@link ROI} icons.
 */
public class PathObjectListCell extends ListCell<PathObject> {

	private Tooltip tooltip;
	private Function<PathObject, String> fun;
	
	private int w = 16, h = 16;
	
	private StackedFontIcon stack;
	private Label label;
	private BorderPane pane;
	
	/**
	 * Constructor, using default {@link PathObject#toString()} method to generate the String representation.
	 */
	public PathObjectListCell() {
		this(PathObject::toString);
	}
	
	/**
	 * Constructor using a custom string extraction function.
	 * @param stringExtractor function to generate a String representation of the object.
	 */
	public PathObjectListCell(Function<PathObject, String> stringExtractor) {
		this.fun = stringExtractor;
		
		pane = new BorderPane();
		label = new Label();
		label.setTextOverrun(OverrunStyle.ELLIPSIS);

		var sp = new StackPane(label);
		sp.setPrefWidth(1.0);
		sp.setMinHeight(0.0);
		StackPane.setAlignment(label, Pos.CENTER_LEFT);
		pane.setCenter(sp);
		
		setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		
		stack = new StackedFontIcon();
		stack.setIconCodeLiterals("ion4-md-lock");
		stack.setStyle("-fx-background-color: -fx-background; -fx-icon-color: -fx-text-fill;");
		var lockedTooltip = new Tooltip("Annotation locked (right-click to unlock)");
		Tooltip.install(stack, lockedTooltip);
	}
	


	@Override
	protected void updateItem(PathObject value, boolean empty) {
		super.updateItem(value, empty);
		if (value == null || empty) {
			setText(null);
			setGraphic(null);
			updateTooltip(value);
			return;
		}
		label.setText(fun.apply(value));
		updateTooltip(value);
		
		if (value.hasROI()) {
			Node icon = IconFactory.createPathObjectIcon(value, w, h);
			label.setGraphic(icon);
		} else {
			label.setGraphic(null);
		}
		setGraphic(pane);
		
		if (value.isLocked()) {
			pane.setRight(stack);
		} else {
			pane.setRight(null);
		}
		
	}

	void updateTooltip(final PathObject pathObject) {
		if (tooltip == null) {
			if (pathObject == null || !pathObject.isAnnotation())
				return;
			tooltip = new Tooltip();
			label.setTooltip(tooltip);
		} else if (pathObject == null || !pathObject.isAnnotation()) {
			label.setTooltip(null);
			return;
		}
		PathAnnotationObject annotation = (PathAnnotationObject)pathObject;
		String description = annotation.getDescription();
		if (description == null)
			description = label.getText();
		if (description == null) {
			label.setTooltip(null);
		} else {
			tooltip.setText(description);
			label.setTooltip(tooltip);
		}
	}

}