/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import qupath.lib.io.GsonTools;



/**
 * Simple browser for exploring fields (including private fields) within an object by reflection.
 * 
 * @author Pete Bankhead
 *
 */
public class ObjectTreeBrowser {

	final private static Logger logger = LoggerFactory.getLogger(ObjectTreeBrowser.class);

	private static <T> TreeTableView<T> createTreeTable() {
		
		var treeTable = new TreeTableView<T>();
		
		// TODO: There appears to be a rendering bug when collapsing nodes (text lives on longer than it should)
		
		TreeTableColumn<T, String> colName = new TreeTableColumn<>("Key");
		colName.setCellValueFactory(c -> {
			if (c.getValue() instanceof ObjectTreeItem)
				return new ReadOnlyObjectWrapper<>(((ObjectTreeItem<?>)c.getValue()).getName());
			return new ReadOnlyObjectWrapper<>();
		});
//		colName.setCellFactory(c -> new TreeTableCell<>() {
//			public void updateItem(String item, boolean empty) {
//				super.updateItem(item, empty);
//				if (item == null || empty) {
//					setText(null);
//					setGraphic(null);
//					return;
//				}
//				setText(item);
//			}
//		});
		
		TreeTableColumn<T, String> colValue = new TreeTableColumn<>("Value");
		colValue.setCellValueFactory(c -> {
			if (c.getValue() instanceof ObjectTreeItem)
				return new ReadOnlyObjectWrapper<>(String.valueOf(((ObjectTreeItem<?>)c.getValue()).getValue()));
			return new ReadOnlyObjectWrapper<>();
		});
//		colValue.setCellFactory(c -> new TreeTableCell<>() {
//			public void updateItem(String item, boolean empty) {
//				super.updateItem(item, empty);
//				if (item == null || empty) {
//					setText(null);
//					setGraphic(null);
//					return;
//				}
//				setText(item);
//			}
//		});
		
		treeTable.getColumns().add(colName);
		treeTable.getColumns().add(colValue);
		treeTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
		treeTable.setEditable(false);
		
		// Enable manual refreshing
		ContextMenu menu = new ContextMenu();
		MenuItem miRefresh = new MenuItem("Refresh");
		miRefresh.setOnAction(e -> {
			for (int i = 0; i < treeTable.getExpandedItemCount(); i++) {
				TreeItem<T> item = treeTable.getTreeItem(i);
				if (item instanceof ReflectiveObjectTreeItem)
					((ReflectiveObjectTreeItem)item).invalidate();
			}
			treeTable.refresh();
		});
		menu.getItems().add(miRefresh);
		treeTable.setContextMenu(menu);
		return treeTable;
	}
	
	/**
	 * Create a {@link TreeTableView} showing the names and values of object fields, accessed via reflection.
	 * @param name root name used to identify the provided object
	 * @param object the object whose fields should be inspected
	 * @return a view depicting object fields
	 */
	public static TreeTableView<Object> createObjectTreeBrowser(String name, Object object) {
		TreeTableView<Object> tree = createTreeTable();
		var cls = object == null ? null : object.getClass();
		tree.setRoot(new ReflectiveObjectTreeItem(name, cls, object));
		return tree;
	}
	
	/**
	 * Create a {@link TreeTableView} showing the names and values of fields within a {@link JsonElement}.
	 * @param name root name used to identify the provided object
	 * @param  object the object whose fields should be inspected. If this is not already a {@link JsonElement}, 
	 *                an attempt will be made to convert it using {@link GsonTools}.
	 * @return a view depicting element fields
	 */
	public static TreeTableView<JsonElement> createJsonTreeBrowser(String name, Object object) {
		JsonElement element = object instanceof JsonElement ? (JsonElement)object : GsonTools.getInstance().toJsonTree(object);
		TreeTableView<JsonElement> tree = createTreeTable();
		tree.setRoot(new JsonTreeItem(name, element));
		return tree;
	}

	static class ObjectTreeItem<T> extends TreeItem<T> {
		
		protected String name;
		
		private ObjectTreeItem(final String name, final T object) {
			super(object);
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
		
		@Override
		public String toString() {
			return name + ": " + getValue();
		}
		
	}
	
	
	static class JsonTreeItem extends ObjectTreeItem<JsonElement> {
		
		private boolean isLeaf = false;
		private boolean childrenComputed = false;
		
		private JsonTreeItem(final String name, final JsonElement object) {
			super(name, object);
			isLeaf = object == null || object.isJsonPrimitive() || object.isJsonNull();
		}
		
		@Override
		public ObservableList<TreeItem<JsonElement>> getChildren() {
			if (!isLeaf() && !childrenComputed) {
				List<JsonTreeItem> children = new ArrayList<>();
				var object = getValue();
				if (object.isJsonArray()) {
					var array = object.getAsJsonArray();
					for (int i = 0; i < array.size(); i++)
						children.add(new JsonTreeItem(Integer.toString(i), array.get(i)));
				} else if (object.isJsonObject()) {
					for (var entry : object.getAsJsonObject().entrySet())
						children.add(new JsonTreeItem(entry.getKey(), entry.getValue()));
				}
				childrenComputed = true;
				super.getChildren().setAll(children);
			}
			return super.getChildren();
		}

		@Override
		public boolean isLeaf() {
			return isLeaf;
		}
		
	}
	

	static class ReflectiveObjectTreeItem extends ObjectTreeItem<Object> {

		private static Set<Class<?>> leafClasses = new HashSet<>(Arrays.asList(
				Enum.class,
				Boolean.class,
				Byte.class,
				Short.class,
				Integer.class,
				Long.class,
				Float.class,
				Double.class,
				Character.class,
				String.class,
				Array.class
				));

		private Class<?> cls;
		private boolean isLeaf = false;
		private boolean childrenComputed = false;
		
		private ReflectiveObjectTreeItem(final String name, final Class<?> cls, final Object object) {
			super(name, object);
			this.cls = cls;
			
			// Don't show primitives (including boxed ones) or long arrays
			isLeaf = object == null || cls.isPrimitive() || leafClasses.contains(cls) || (cls.isArray() && Array.getLength(object) > 100);
			if (!isLeaf) {
				for (Class<?> leafClass : leafClasses) {
					if (leafClass.isInstance(object)) {
						isLeaf = true;
						break;
					}
				}
			}
		}
		
		/**
		 * Recursively get all fields from a class & superclasses, ignoring those that are masked.
		 * 
		 * @param fields
		 * @param type
		 * @return
		 */
		private static Map<String, Field> getAllFields(Map<String, Field> fields, Class<?> type) {
			if (fields == null)
				fields = new TreeMap<>();
			
			for (Field f : type.getDeclaredFields()) {
				if (!fields.containsKey(f.getName()))
					fields.put(f.getName(), f);
			}
		    if (type.getSuperclass() != null) {
		        fields = getAllFields(fields, type.getSuperclass());
		    }
		    return fields;
		}
		
		
		public void invalidate() {
			childrenComputed = false;
		}
		

		@Override
		public ObservableList<TreeItem<Object>> getChildren() {
			if (!isLeaf() && !childrenComputed) {
				List<ReflectiveObjectTreeItem> children = new ArrayList<>();
				
				var object = getValue();
				for (Field f : getAllFields(null, cls).values()) {
					Object child;
					try {
						boolean tempAccessible = false;
						if (!f.canAccess(object)) {
							f.setAccessible(true);
							tempAccessible = true;
						}
						child = f.get(object);
						children.add(new ReflectiveObjectTreeItem(f.getName(), child == null ? f.getType() : child.getClass(), child));
						if (tempAccessible)
							f.setAccessible(false);
					} catch (Exception e) {
						logger.trace("Exception accessing field {} of {}: {}", f, object, e.getLocalizedMessage());
					}
				}
				
				if (cls.isArray()) {
					for (int i = 0; i < Array.getLength(object); i++) {
						Object child = Array.get(object, i);
						children.add(new ReflectiveObjectTreeItem(name + "[" + i + "]", child == null ? Object.class : child.getClass(), child));
					}
				}
				
				childrenComputed = true;
				super.getChildren().setAll(children);
			}
			return super.getChildren();
		}

		@Override
		public boolean isLeaf() {
			return isLeaf;
		}
		

	}


}
