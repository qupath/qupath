/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PathObjectLabels;
import qupath.lib.gui.tools.WebViews;
import qupath.lib.gui.tools.PathObjectLabels.PathObjectMiniPane;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;

/**
 * Wraps a pane used to display an object description, if one is available.
 * 
 * @author Pete Bankhead
 * @param <T> generic parameter for {@link ImageData}
 * @since v0.4.0
 */
public class ObjectDescriptionPane<T> {
	
	private static final Logger logger = LoggerFactory.getLogger(ObjectDescriptionPane.class);
	
	private ObservableValue<ImageData<T>> imageData;
	private ObjectObserver<T> observer;
	private BorderPane pane;
	
	private PathObjectMiniPane pathObjectPane;
	
	private MarkdownConverter converter = new MarkdownConverter();
	private WebView webview = WebViews.create(true);
	
	private ObjectDescriptionPane(ObservableValue<ImageData<T>> imageData, boolean includeTitle) {
		this.imageData = imageData;
		this.observer = new ObjectObserver<>(imageData);
		

		observer.selectedObjectProperty().addListener((v, o, n) -> updateItem());
		observer.addHierarchyListener(event -> updateItem());
		
		pane = new BorderPane();
		pane.setCenter(webview);
		
		if (includeTitle) {
			// Add a pane at the top to indicate the object & optionally edit the description
			pathObjectPane = PathObjectLabels.createPane();
			var title = pathObjectPane.getNode();
			title.setStyle("-fx-padding: 5 10 5 5;");
			
			var topPane = new BorderPane(pathObjectPane.getNode());
			topPane.setStyle("-fx-padding: 5px; -fx-border-color: -fx-body-color;");
			
			var btnEdit = new Button("Edit");
			btnEdit.disableProperty().bind(Bindings.createBooleanBinding(() -> {
				var selected = observer.selectedObjectProperty().get();
				return !(selected instanceof PathAnnotationObject);
			}, observer.selectedObjectProperty()));
			
			btnEdit.setOnAction(e -> {
				GuiTools.promptToSetActiveAnnotationProperties(observer.hierarchyProperty().get());
			});
			btnEdit.setTooltip(new Tooltip("Edit properties (only available for annotations)"));
			
			BorderPane.setAlignment(btnEdit, Pos.CENTER_RIGHT);
			topPane.setRight(btnEdit);
			
			pane.setTop(topPane);
		} else
			pane.setCenter(webview);
		
		webview.setPageFill(Color.TRANSPARENT);
		webview.setFontScale(0.9);
		
				
		
		updateItem();
	}

	private Pane getPane() {
		return pane;
	}

	
	public static <T> Pane createPane(ObservableValue<ImageData<T>> imageData) {
		return new ObjectDescriptionPane<>(imageData, false).getPane();
	}
	
	public static <T> Pane createPane(ObservableValue<ImageData<T>> imageData, boolean includeCell) {
		return new ObjectDescriptionPane<>(imageData, includeCell).getPane();
	}
	
	public static <T> Stage createWindow(QuPathGUI qupath) {
		var pane = createPane(qupath.imageDataProperty(), true);
		var scene = new Scene(pane);
		var stage = new Stage();
		stage.setScene(scene);
		stage.initOwner(qupath.getStage());
		stage.setTitle("Object description");
		return stage;
	}
	
	
	
	private void updateItem() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::updateItem);
			return;
		}
			
				
		PathObject n = null;
		if (observer != null) {
			n = observer.selectedObjectProperty().get();
			if (n == null) {
				var hierarchy = observer.hierarchyProperty().get();
				n = hierarchy == null ? null : hierarchy.getRootObject();
			}
		}
		
		logger.debug("Updating details pane for {}", n);

		var annotation = n == null || !n.isAnnotation() ? null : (PathAnnotationObject)n;
		var description = annotation == null ? null : annotation.getDescription();
		var engine = webview.getEngine();
		if (description == null) {
			String msg = "No description available";
			if (n == null)
				msg = "No object selected";
			else if (!n.isAnnotation())
				msg = "Selected object doesn't support descriptions";
			else
				msg = "No description set";
			
			String spanString = "<span style=\"color: rgba(127, 127, 127, 0.8);\">%s</span>";
			engine.loadContent(String.format(spanString, msg));
		} else {
			if (description.startsWith("https://")) {
				engine.load(description.strip());
				return;
			}
			String html;
			if (description.trim().startsWith("<html>"))
				html = description;
			else
				html = converter.toHtml(description);
			
			engine.loadContent(html);
		}
		
		
		if (pathObjectPane != null) {
			pathObjectPane.setPathObject(n);
		}
		
	}
	
	
	static class MarkdownConverter {
		
		private Map<String, String> cache = new WeakHashMap<>();
		
		private Parser parser = Parser.builder().build();
		private HtmlRenderer renderer = HtmlRenderer.builder().build();
		
		public String toHtml(String markdown) {
			if (markdown == null)
				return "";
			return cache.computeIfAbsent(markdown, this::convertToHtml);
		}
		
		private String convertToHtml(String markdown) {
			var doc = parser.parse(markdown);
			return renderer.render(doc);
		}
		
	}
	
	
	/**
	 * Helper class that looks helps with listening for changes to the current image and its hierarchy, whatever that may be.
	 * <p>
	 * Reduces the need for individual classes to need to register/deregister listeners and/or update bindings whenever 
	 * the current image is changed.
	 * 
	 * @author Pete Bankhead
	 *
	 * @param <T>
	 */
	static class ObjectObserver<T> implements PathObjectSelectionListener, ChangeListener<PathObjectHierarchy>, PathObjectHierarchyListener {
		
		private ObservableValue<ImageData<T>> imageDataOriginal;
		
		private ObjectProperty<ImageData<T>> imageDataProperty = new SimpleObjectProperty<>();
		
		private ObjectBinding<PathObjectHierarchy> hierarchyBinding = Bindings.createObjectBinding(() -> {
			var imageData2 = imageDataProperty.get();
			return imageData2 == null ? null : imageData2.getHierarchy();
		}, imageDataProperty);
		
		private ObjectProperty<PathObjectHierarchy> hierarchyProperty = new SimpleObjectProperty<>();
		
		private ObjectProperty<PathObject> selectedObject = new SimpleObjectProperty<>();
		private ObservableList<PathObject> selectedObjects = FXCollections.observableArrayList();
		private ObservableList<PathObject> selectedObjectsUnmodifiable = FXCollections.unmodifiableObservableList(selectedObjects);
		
		private List<PathObjectHierarchyListener> hierarchyListeners = new ArrayList<>();
		
		private ObjectObserver(ObservableValue<ImageData<T>> imageData) {
			imageDataOriginal = imageData;
//			imageDataOriginal.addListener((v, o, n) -> {
//				imageDataProperty.set(n);
//			});
//			imageDataProperty.set(imageDataOriginal.getValue());
//			imageDataProperty.bind(imageDataOriginal); // This didn't work... not sure why not
			hierarchyProperty.bind(hierarchyBinding);
			hierarchyProperty.addListener(this);
			
			imageDataProperty.set(imageDataOriginal.getValue());
			imageDataOriginal.addListener((v, o, n) -> {
				imageDataProperty.set(n);
			});
		}
		
		public ReadOnlyObjectProperty<ImageData<T>> imageDataProperty() {
			return imageDataProperty;
		}
		
		public ReadOnlyObjectProperty<PathObjectHierarchy> hierarchyProperty() {
			return hierarchyProperty;
		}
		
		public ReadOnlyObjectProperty<PathObject> selectedObjectProperty() {
			return selectedObject;
		}
		
		public ObservableList<PathObject> selectedObjectsProperty() {
			return selectedObjectsUnmodifiable;
		}
		
		public void addHierarchyListener(PathObjectHierarchyListener listener) {
			this.hierarchyListeners.add(listener);
		}

		public void removeHierarchyListener(PathObjectHierarchyListener listener) {
			this.hierarchyListeners.remove(listener);
		}

		@Override
		public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject,
				Collection<PathObject> allSelected) {
			
			selectedObject.set(pathObjectSelected);
			selectedObjects.setAll(allSelected);
		}

		@Override
		public void changed(ObservableValue<? extends PathObjectHierarchy> observable, PathObjectHierarchy oldValue,
				PathObjectHierarchy newValue) {
			
			if (oldValue != null) {
				oldValue.removeListener(this);
				oldValue.getSelectionModel().removePathObjectSelectionListener(this);
			}
			
			if (newValue != null) {
				newValue.addListener(this);
				newValue.getSelectionModel().addPathObjectSelectionListener(this);
				selectedObject.set(newValue.getSelectionModel().getSelectedObject());
				selectedObjects.setAll(newValue.getSelectionModel().getSelectedObjects());
			} else {
				selectedObject.set(null);
				selectedObjects.clear();				
			}
			
		}

		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			for (var listener : hierarchyListeners)
				listener.hierarchyChanged(event);
		}
		
	}

}
