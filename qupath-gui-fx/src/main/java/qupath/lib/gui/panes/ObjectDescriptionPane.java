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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import qupath.lib.gui.QuPathGUI;
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
 */
public class ObjectDescriptionPane {
	
	private ObjectObserver<BufferedImage> observer;
	
	private TextArea textArea = new TextArea();
	private BorderPane pane = new BorderPane();
	
	public ObjectDescriptionPane(QuPathGUI qupath) {
		observer = new ObjectObserver<>(qupath.imageDataProperty());
		
		observer.getSelectedObjectProperty().addListener((v, o, n) -> updateText(n));
		observer.addHierarchyListener(event -> {
			updateText(observer.getSelectedObjectProperty().get());
		});
		
		pane.setCenter(textArea);
	}
	
	
	private void updateText(PathObject n) {
		var annotation = n == null || !n.isAnnotation() ? null : (PathAnnotationObject)n;
		var description = annotation == null ? null : annotation.getDescription();
		if (description == null)
			textArea.setText("");
		else {
			textArea.setText(description);
		}
	}
	
	
	public Pane getPane() {
		return pane;
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
			imageDataOriginal.addListener((v, o, n) -> {
				imageDataProperty.set(n);
			});
//			imageDataProperty.bind(imageDataOriginal); // This didn't work... not sure why not
			hierarchyProperty.bind(hierarchyBinding);
			hierarchyProperty.addListener(this);
		}
		
		public ReadOnlyObjectProperty<ImageData<T>> getImageDataProperty() {
			return imageDataProperty;
		}
		
		public ReadOnlyObjectProperty<PathObjectHierarchy> getHierarchyProperty() {
			return hierarchyProperty;
		}
		
		public ReadOnlyObjectProperty<PathObject> getSelectedObjectProperty() {
			return selectedObject;
		}
		
		public ObservableList<PathObject> getSelectedObjectsProperty() {
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
				oldValue.removePathObjectListener(this);
				oldValue.getSelectionModel().removePathObjectSelectionListener(this);
			}
			
			if (newValue != null) {
				newValue.addPathObjectListener(this);
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
