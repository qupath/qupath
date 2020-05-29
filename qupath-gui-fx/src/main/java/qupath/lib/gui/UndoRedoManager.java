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

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.ParallelTileObject;

/**
 * Helper class to add undo/redo support to QuPath.
 * <p>
 * This is restricted to tracking changes in the PathObjectHierarchy for individual viewers, 
 * and is intended mostly for cases where there aren't many objects - but where making mistakes 
 * would be especially annoying (e.g. laboriously annotating images).
 * <p>
 * Preferences are created to control the maximum number of levels of undo, and also the maximum hierarchy 
 * size.  This latter option is used to automatically turn off undo/redo support if the hierarchy size 
 * grows beyond a specified number of objects.
 * <p>
 * The reason is because of the (fairly simple) implementation: every time the hierarchy is changed, 
 * the <i>entire hierarchy</i> is serialized in case it becomes necessary to revert back.
 * <p>
 * This is a lot easier than trying to figure out how to computationally revert every conceivable change 
 * that the hierarchy might experience, but it is inevitably quite memory hungry and risks having a substantial 
 * impact on performance for large object hierarchies.
 * 
 * @author Pete Bankhead
 *
 */
public class UndoRedoManager implements ChangeListener<QuPathViewerPlus>, QuPathViewerListener, PathObjectHierarchyListener {
	
	private static Logger logger = LoggerFactory.getLogger(UndoRedoManager.class);
	
	private IntegerProperty maxUndoLevels = PathPrefs.createPersistentPreference("undoMaxLevels", 10);
	private IntegerProperty maxUndoHierarchySize = PathPrefs.createPersistentPreference("undoMaxHierarchySize", 10000);
	
	private ReadOnlyObjectProperty<QuPathViewerPlus> viewerProperty;
	
	private SimpleBooleanProperty canUndo = new SimpleBooleanProperty(false);
	private SimpleBooleanProperty canRedo = new SimpleBooleanProperty(false);
	
	private boolean undoingOrRedoing = false;
	
	private Map<QuPathViewer, SerializableUndoRedoStack<PathObjectHierarchy>> map = new WeakHashMap<>();
	
	UndoRedoManager(final QuPathGUI qupath) {
		this.viewerProperty = qupath.viewerProperty();
		this.viewerProperty.addListener(this);
		
		qupath.getPreferencePane().addPropertyPreference(maxUndoLevels, Integer.class, "Max undo levels", "Undo/Redo", "Maximum number of 'undo' levels");
		qupath.getPreferencePane().addPropertyPreference(maxUndoHierarchySize, Integer.class, "Max undo hierarchy size", "Undo/Redo", "Maximum number of objects in hierarchy before 'undo' switches off (for performance)");
		
		changed(this.viewerProperty, null, this.viewerProperty.get());
		
	}
	
	/**
	 * Refresh the properties for the current active viewer.
	 * These can be bound to the disabled status of GUI elements.
	 */
	private void refreshProperties() {
		// Call refresh on the JavaFX application thread, because it's likely that GUI elements depend on these
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> refreshProperties());
			return;
		}
		SerializableUndoRedoStack<PathObjectHierarchy> undoRedo = map.get(viewerProperty.get());
		if (undoRedo == null) {
			canUndo.set(false);
			canRedo.set(false);
		} else {
			canUndo.set(undoRedo.canUndo());			
			canRedo.set(undoRedo.canRedo());			
		}
	}
	
	/**
	 * Request to 'undo' the last observed hierarchy change for the current active viewer.
	 * 
	 * @return {@code true} if any changes were made, false otherwise.
	 */
	public boolean undoOnce() {
		return undoOnce(viewerProperty.get());
	}
	
	/**
	 * The total number of bytes used for all viewers.
	 * @return 
	 */
	public long totalBytes() {
		long total = 0L;
		for (var manager : map.values()) {
			if (manager != null)
				total += manager.totalBytes();
		}
		return total;
	}
	
	/**
	 * Clear all undo/redo stacks (useful when memory is low).
	 */
	public void clear() {
		for (var manager : map.values()) {
			manager.clear();
		}
		refreshProperties();
	}
	
	/**
	 * Request to 'redo' the last 'undone' hierarchy change for the current active viewer.
	 * 
	 * @return True if any changes were made, false otherwise.
	 */
	public boolean redoOnce() {
		return redoOnce(viewerProperty.get());
	}
	
	/**
	 * Request to 'undo' the last observed hierarchy change for the specified viewer.
	 * 
	 * @param viewer
	 * @return {@code true} if any changes were made, {@code false} otherwise.
	 */
	boolean undoOnce(final QuPathViewer viewer) {
		if (viewer == null) {
			logger.warn("Undo requested, but no viewer specified.");
			return false;
		}
		
		SerializableUndoRedoStack<PathObjectHierarchy> undoRedo = map.get(viewer);
		if (undoRedo == null) {
			logger.warn("Undo requested, but undo stack available.");
			return false;
		}
		
		// Update the hierarchy
		PathObjectHierarchy hierarchy = undoRedo.undoOnce();
		if (hierarchy == null) {
			logger.warn("Unable to call 'undo' for {}", viewer);
			return false;
		}
		undoingOrRedoing = true;
		logger.debug("Called 'undo' for {}", viewer);
		// Need to make sure we've no selection, since selected objects can linger
		viewer.getImageData().getHierarchy().getSelectionModel().clearSelection();
		viewer.getImageData().getHierarchy().setHierarchy(hierarchy);
		undoingOrRedoing = false;
		refreshProperties();
		
		return true;
	}

	/**
	 * Request to 'redo' the last 'undone' hierarchy change for the specified viewer.
	 * 
	 * @param viewer
	 * @return True if any changes were made, false otherwise.
	 */
	boolean redoOnce(final QuPathViewer viewer) {
		if (viewer == null) {
			logger.warn("Redo requested, but no viewer specified.");
			return false;
		}
		
		SerializableUndoRedoStack<PathObjectHierarchy> undoRedo = map.get(viewer);
		if (undoRedo == null) {
			logger.warn("Redo requested, but redo stack available.");
			return false;
		}
		
		// Update the hierarchy
		PathObjectHierarchy hierarchy = undoRedo.redoOnce();
		if (hierarchy == null) {
			logger.warn("Unable to call 'redo' for {}", viewer);
			return false;
		}
		undoingOrRedoing = true;
		logger.debug("Called 'redo' for {}", viewer);
		// Need to make sure we've no selection, since selected objects can linger
		viewer.getImageData().getHierarchy().getSelectionModel().clearSelection();
		viewer.getImageData().getHierarchy().setHierarchy(hierarchy);
		undoingOrRedoing = false;
		refreshProperties();
		
		return true;
	}

	/**
	 * True if it's possible to call undoOnce for the currently-active viewer in QuPath, false otherwise.
	 * @return
	 */
	public ReadOnlyBooleanProperty canUndo() {
		return canUndo;
	}
	
	/**
	 * True if it's possible to call redoOnce for the currently-active viewer in QuPath, false otherwise.
	 * @return
	 */
	public ReadOnlyBooleanProperty canRedo() {
		return canRedo;
	}
	
	
	
	@Override
	public void changed(ObservableValue<? extends QuPathViewerPlus> observable, QuPathViewerPlus oldValue, QuPathViewerPlus newValue) {
		
		if (newValue == null)
			return;
		
		// Start listening for changes in the hierarchy for the current viewer
		if (!map.containsKey(newValue)) {
			newValue.addViewerListener(this);
			imageDataChanged(newValue, null, newValue.getImageData());
		}
		
		refreshProperties();
	}
	
	
	

	
	/**
	 * Serialize objects on request for use in undo/redo.
	 *
	 * @param <T>
	 */
	static class SerializableUndoRedoStack<T> {
		
		private byte[] current = null;
		private Deque<byte[]> undoStack = new ArrayDeque<>();
		private Deque<byte[]> redoStack = new ArrayDeque<>();
		
		SerializableUndoRedoStack(T object) {
			if (object != null)
				current = serialize(object, 1024);
		}
		
		/**
		 * Returns true if the undo stack is not empty.
		 * @return
		 */
		public boolean canUndo() {
			return !undoStack.isEmpty();
		}
		
		/**
		 * Returns true if the redo stack is not empty.
		 * @return
		 */
		public boolean canRedo() {
			return !redoStack.isEmpty();
		}
		
		/**
		 * Get the total number of allocated bytes.
		 * @return
		 */
		public synchronized long totalBytes() {
			long total = 0L;
			if (current != null)
				total = current.length;
			for (byte[] bytes : undoStack)
				total += bytes.length;
			for (byte[] bytes : redoStack)
				total += bytes.length;
			return total;
		}
		
		/**
		 * Clear the undo and redo stacks, in addition to the current bytes.
		 */
		public synchronized void clear() {
			current = null;
			undoStack.clear();
			redoStack.clear();
		}
		
		/**
		 * Request redo once, updating the current (serialized) object, 
		 * and return the deserialized version of the object at the top of the 'redo' stack.
		 * @return
		 */
		public synchronized T redoOnce() {
			if (redoStack.isEmpty()) {
				logger.debug("Cannot redo! Stack is empty.");
				return null;
			}
			if (current != null) {
				undoStack.push(current);
			}
			current = redoStack.pop();
			return deserialize(current);
		}
		
		/**
		 * Request undo once, updating the current (serialized) object, 
		 * and return the deserialized version of the object at the top of the 'undo' stack.
		 * @return
		 */
		public synchronized T undoOnce() {
			if (undoStack.isEmpty()) {
				logger.debug("Cannot undo! Stack is empty.");
				return null;
			}
			if (current != null) {
				redoStack.push(current);
			}
			current = undoStack.pop();
			return deserialize(current);
		}
		
		/**
		 * Record a new change event, updating the current object.
		 * This will clear any redo status, on the assumption that redo is no longer possible.
		 * 
		 * @param object
		 * @param historySize
		 */
		public synchronized void addLatest(final T object, int historySize) {
			int initialSize = 1024;
			if (current != null) {
				// If we are low on memory, clear the undo stack
				long remainingMemory = GeneralTools.estimateAvailableMemory();
				if (remainingMemory < current.length * 1.5) {
					undoStack.clear();
				} else {
					// Default to something a bit bigger than the last things we had
					initialSize = Math.min(Integer.MAX_VALUE-64, (int)(current.length * 1.1));
					undoStack.push(current);
				}
			}
			current = serialize(object, initialSize);
			// Reset the ability to redo
			redoStack.clear();
			// Check the history size
			if (historySize > 0) {
				while (undoStack.size() > historySize)
					undoStack.pollLast();
			}
		}
		
		/**
		 * Serialize an object to an array of bytes.
		 * Providing an estimate of the initial array size can help performance.
		 * 
		 * @param object
		 * @param initialSize
		 * @return
		 */
		private byte[] serialize(T object, int initialSize) {
			try (ByteArrayOutputStream stream = new ByteArrayOutputStream(initialSize)) {
				ObjectOutputStream out = new ObjectOutputStream(stream);
				out.writeObject(object);
				out.flush();
				return stream.toByteArray();
			} catch (IOException e) {
				logger.error("Error serializing " + object, e);
				return null;
			}
		}
		
		/**
		 * Deserialize an object from an array of bytes.
		 * It's assumed at the object is of the generic type used elsewhere in this class.
		 * 
		 * @param bytes
		 * @return
		 */
		@SuppressWarnings("unchecked")
		private T deserialize(byte[] bytes) {
			try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
				ObjectInputStream in = new ObjectInputStream(stream);
				return (T)in.readObject();
			} catch (ClassNotFoundException | IOException e) {
				logger.error("Error deserializing object", e);
				return null;
			}
		}
		
	}



	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {
		
		// Stop listening for changes on the old image
		if (imageDataOld != null) {
			imageDataOld.getHierarchy().removePathObjectListener(this);
		}
		
		// Start listening for changes on the new image... if we can
		PathObjectHierarchy hierarchy = imageDataNew == null ? null : imageDataNew.getHierarchy();
		if (hierarchy == null) {
			map.put(viewer, (SerializableUndoRedoStack<PathObjectHierarchy>)null);
		} else {
			int maxSize = maxUndoHierarchySize.get();
			if (maxSize >= hierarchy.nObjects())
				map.put(viewer, new SerializableUndoRedoStack<>(hierarchy));
			else
				map.put(viewer, new SerializableUndoRedoStack<>(null));
			// Listen for changes
			hierarchy.addPathObjectListener(this);
		}
		
		refreshProperties();
	}

	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}

	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

	@Override
	public void viewerClosed(QuPathViewer viewer) {
		map.remove(viewer);
		viewer.removeViewerListener(this);
	}

	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		// Try to avoid calling too often
		if (undoingOrRedoing || event.isChanging() || maxUndoHierarchySize.get() <= 0 || event.getChangedObjects().stream().allMatch(p -> p instanceof ParallelTileObject))
			return;
		
		// *Potentially* we might have the same hierarchy in multiple viewers
		// Since we don't have the viewer stored in the event, check to see what viewers are impacted
		QuPathViewer[] viewers = map.keySet().toArray(new QuPathViewer[map.size()]);
		PathObjectHierarchy hierarchy = event.getHierarchy();
		int maxSize = maxUndoHierarchySize.get();
		boolean sizeOK = hierarchy.nObjects() <= maxSize;
		for (QuPathViewer viewer : viewers) {
			if (viewer.getHierarchy() == hierarchy) {
				
				SerializableUndoRedoStack<PathObjectHierarchy> undoRedo = map.get(viewer);
				// If the size is ok, register the change for potential undo-ing
				if (sizeOK) {
					// Consider trying to reduce these calls
//					if (event.getEventType() == HierarchyEventType.CHANGE_MEASUREMENTS && 
//							!event.getChangedObjects().stream().anyMatch(p -> p.isDetection()))
//						continue;
					
					if (undoRedo == null)
						map.put(viewer, new SerializableUndoRedoStack<>(hierarchy));
					else
						undoRedo.addLatest(hierarchy, maxUndoLevels.get());
				} else {
					// If the hierarchy is too big turn off undo/redo
					map.put(viewer, (SerializableUndoRedoStack<PathObjectHierarchy>)null);
				}
			}
		}
		refreshProperties();
	}

}