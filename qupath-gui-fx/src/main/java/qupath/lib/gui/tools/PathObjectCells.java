/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

import java.util.function.Function;

import org.kordamp.ikonli.javafx.StackedFontIcon;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.DetectionTreeDisplayModes;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;

/**
 * Create standardized {@link ListCell} and {@link TreeCell} instances for displaying a {@link PathObject}.
 * 
 * @author Pete Bankhead
 */
public class PathObjectCells {
	
	
	public static ListCell<PathObject> createListCell() {
		return createListCell(PathObject::toString);
	}
	
	public static ListCell<PathObject> createListCell(Function<PathObject, String> stringExtractor) {
		return new PathObjectListCell(stringExtractor);
	}
	
	public static TreeCell<PathObject> createTreeCell() {
		return createTreeCell(PathObject::toString);
	}
	
	public static TreeCell<PathObject> createTreeCell(Function<PathObject, String> stringExtractor) {
		return new PathObjectTreeCell(stringExtractor);
	}
	
	
	private static class PathObjectTreeCell extends TreeCell<PathObject> {
		
		private PathObjectMiniPane miniPane;
		
		PathObjectTreeCell(Function<PathObject, String> stringExtractor) {
			super();
			this.miniPane = new PathObjectMiniPane(stringExtractor);
			setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		}
		
		@Override
		protected void updateItem(PathObject value, boolean empty) {
			super.updateItem(value, empty);
			this.miniPane.setValue(empty ? null : value);
			if (value == null || empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			setGraphic(this.miniPane.getNode());
		}
		
	}


	private static class PathObjectListCell extends ListCell<PathObject> {
		
		private PathObjectMiniPane miniPane;
		
		PathObjectListCell(Function<PathObject, String> stringExtractor) {
			super();
			this.miniPane = new PathObjectMiniPane(stringExtractor);
			setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		}
		
		@Override
		protected void updateItem(PathObject value, boolean empty) {
			super.updateItem(value, empty);
			this.miniPane.setValue(empty ? null : value);
			if (value == null || empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			setGraphic(this.miniPane.getNode());
		}
		
	}
	
	
	/**
	 * Manage a small pane that can be used to display a {@link PathObject}.
	 * Intended for use creating standardized list and tree cells.
	 */
	private static class PathObjectMiniPane {

		private Tooltip tooltip;
		private Function<PathObject, String> fun;
		
		private int w = 16, h = 16;
		
		private StackedFontIcon stack;
		private Label label;
		private BorderPane pane;
		
		
		/**
		 * Constructor using a custom string extraction function.
		 * @param stringExtractor function to generate a String representation of the object.
		 */
		public PathObjectMiniPane(Function<PathObject, String> stringExtractor) {
			this.fun = stringExtractor;
			if (this.fun == null)
				this.fun = PathObject::toString;
			
			pane = new BorderPane();
			label = new Label();
			label.setTextOverrun(OverrunStyle.ELLIPSIS);

			var sp = new StackPane(label);
			sp.setPrefWidth(1.0);
			sp.setMinHeight(0.0);
			StackPane.setAlignment(label, Pos.CENTER_LEFT);
			pane.setCenter(sp);
			
			stack = new StackedFontIcon();
			stack.setIconCodeLiterals("ion4-md-lock");
			stack.setStyle("-fx-background-color: -fx-background; -fx-icon-color: -fx-text-fill;");
			var lockedTooltip = new Tooltip("Object locked");
			Tooltip.install(stack, lockedTooltip);
		}
		

		protected void setValue(PathObject value) {
			if (value == null) {
				label.setText(null);
				label.setGraphic(null);
				pane.setRight(null);
				return;
			}
			
			stack.setStyle("-fx-background-color: -fx-background; -fx-icon-color: -fx-text-fill; -fx-opacity: 0.6;");
			
			label.setText(fun.apply(value));
			updateTooltip(value);
			
			if (showRoiIcon(value)) {
				Node icon = IconFactory.createPathObjectIcon(value, w, h);
				label.setGraphic(icon);
			} else {
				label.setGraphic(null);
			}
			
			if (value.isLocked()) {
				pane.setRight(stack);
			} else {
				pane.setRight(null);
			}
			
		}
		
		
		private boolean showRoiIcon(PathObject pathObject) {
			if (pathObject == null || !pathObject.hasROI())
				return false;
			return !pathObject.isDetection() || PathPrefs.detectionTreeDisplayModeProperty().get() == DetectionTreeDisplayModes.WITH_ICONS;
		}
		
		
		/**
		 * Get the node to display.
		 * @return
		 */
		public Node getNode() {
			return pane;
		}

		private void updateTooltip(final PathObject pathObject) {
			if (tooltip == null) {
				if (pathObject == null || !pathObject.isAnnotation())
					return;
				tooltip = new Tooltip();
				label.setTooltip(tooltip);
			} else if (pathObject == null || !pathObject.isAnnotation()) {
				label.setTooltip(null);
				return;
			}
			if (pathObject instanceof PathAnnotationObject) {
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

	}

}
