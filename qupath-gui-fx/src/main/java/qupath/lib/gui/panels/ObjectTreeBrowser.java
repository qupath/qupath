/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.panels;

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

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;



/**
 * Simple browser for exploring fields (including private fields) within an object by reflection.
 * 
 * @author Pete Bankhead
 *
 */
public class ObjectTreeBrowser {

	final private static Logger logger = LoggerFactory.getLogger(ObjectTreeBrowser.class);

	private BorderPane pane = new BorderPane();
	
	private TreeTableView<Object> treeTable = new TreeTableView<>();
	
	public ObjectTreeBrowser() {
		
		TreeTableColumn<Object, String> colName = new TreeTableColumn<>();
		colName.setCellValueFactory(c -> ((ObjectTreeItem)c.getValue()).observableName);
		
		TreeTableColumn<Object, String> colValue = new TreeTableColumn<>();
		colValue.setCellValueFactory(c -> ((ObjectTreeItem)c.getValue()).observableValue);
		
		treeTable.getColumns().add(colName);
		treeTable.getColumns().add(colValue);
		treeTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
		treeTable.setEditable(false);
		
		// Enable manual refreshing
		ContextMenu menu = new ContextMenu();
		MenuItem miRefresh = new MenuItem("Refresh");
		miRefresh.setOnAction(e -> {
			for (int i = 0; i < treeTable.getExpandedItemCount(); i++) {
				TreeItem<Object> item = treeTable.getTreeItem(i);
				if (item instanceof ObjectTreeItem)
					((ObjectTreeItem)item).invalidate();
			}
			treeTable.refresh();
		});
		menu.getItems().add(miRefresh);
		treeTable.setContextMenu(menu);
		
		pane.setCenter(treeTable);		
	}
	
	public Pane getPane() {
		return pane;
	}
	
	
	public void setObject(final String name, final Class<?> cls, final Object object) {
		if (object == null)
			treeTable.setRoot(null);
		else
			treeTable.setRoot(new ObjectTreeItem(name, cls, object));
	}
	
	public Object getObject() {
		return treeTable.getRoot() == null ? null : treeTable.getRoot().getValue();
	}



	static class ObjectTreeItem extends TreeItem<Object> {

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

		private String name;
		private Class<?> cls;
		private Object object;
		private boolean isLeaf = false;
		private boolean childrenComputed = false;
		
		private ObservableValue<String> observableName = Bindings.createStringBinding(() -> name);
		private ObservableValue<String> observableValue = Bindings.createObjectBinding(() -> String.valueOf(object));

		private ObjectTreeItem(final String name, final Class<?> cls, final Object object) {
			this.name = name;
			this.cls = cls;
			this.object = object;
			
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
				List<ObjectTreeItem> children = new ArrayList<>();
				
				for (Field f : getAllFields(null, cls).values()) {
					Object child;
					try {
						boolean tempAccessible = false;
						if (!f.isAccessible()) {
							f.setAccessible(true);
							tempAccessible = true;
						}
						child = f.get(object);
						children.add(new ObjectTreeItem(f.getName(), child == null ? f.getType() : child.getClass(), child));
						if (tempAccessible)
							f.setAccessible(false);
					} catch (Exception e) {
						logger.trace("Exception accessing field {} of {}: {}", f, object, e.getLocalizedMessage());
					}
				}
				
				if (cls.isArray()) {
					for (int i = 0; i < Array.getLength(object); i++) {
						Object child = Array.get(object, i);
						children.add(new ObjectTreeItem(name + "[" + i + "]", child == null ? Object.class : child.getClass(), child));
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
		
		@Override
		public String toString() {
			return name + ": " + object;
		}

	}


}
