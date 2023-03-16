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

package qupath.lib.gui.viewer.tools;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.tools.handlers.PathToolEventHandlers;

/**
 * Default {@link PathTool} implementations.
 * 
 * @author Pete Bankhead
 */
public class PathTools {
	
	/**
	 * Move tool
	 */
	public static final PathTool MOVE = createTool(
			PathToolEventHandlers.createMoveEventHandler(),
			QuPathResources.getString("Tools.move"),
			createIcon(PathIcons.MOVE_TOOL));
	/**
	 * Rectangle drawing tool
	 */
	public static final PathTool RECTANGLE = createTool(
			PathToolEventHandlers.createRectangleEventHandler(),
			QuPathResources.getString("Tools.rectangle"),
			createIcon(PathIcons.RECTANGLE_TOOL));
	/**
	 * Ellipse drawing tool
	 */
	public static final PathTool ELLIPSE = createTool(
			PathToolEventHandlers.createEllipseEventHandler(),
			QuPathResources.getString("Tools.ellipse"),
			createIcon(PathIcons.ELLIPSE_TOOL));
	/**
	 * Line drawing tool
	 */
	public static final PathTool LINE = createTool(
			PathToolEventHandlers.createLineEventHandler(),
			QuPathResources.getString("Tools.line"),
			createIcon(PathIcons.LINE_TOOL));

	/**
	 * Arrow drawing tool, with arrowhead at the start
	 */
	public static final PathTool ARROW_START = createTool(
			PathToolEventHandlers.createArrowStartEventHandler(),
			QuPathResources.getString("Tools.arrowStart"),
			createIcon(PathIcons.ARROW_START_TOOL));

	/**
	 * Arrow drawing tool, with arrowhead at the end
	 */
	public static final PathTool ARROW_END = createTool(
			PathToolEventHandlers.createArrowEndEventHandler(),
			QuPathResources.getString("Tools.arrowEnd"),
			createIcon(PathIcons.ARROW_END_TOOL));

	/**
	 * Arrow drawing tool, with arrowhead at both ends
	 */
	public static final PathTool ARROW_DOUBLE = createTool(
			PathToolEventHandlers.createDoubleArrowEventHandler(),
			QuPathResources.getString("Tools.arrowDouble"),
			createIcon(PathIcons.ARROW_DOUBLE_TOOL));
	
	/**
	 * Extended {@link PathTool} that can switch between drawing lines or arrows.
	 */
	public static final PathTool LINE_OR_ARROW = createExtendedTool(
			LINE, ARROW_START, ARROW_END, ARROW_DOUBLE
			);
	
	/**
	 * Polygon drawing tool (closed)
	 */
	public static final PathTool POLYGON = createTool(
			PathToolEventHandlers.createPolygonEventHandler(),
			QuPathResources.getString("Tools.polygon"),
			createIcon(PathIcons.POLYGON_TOOL));
	/**
	 * Polyline drawing tool (open)
	 */
	public static final PathTool POLYLINE = createTool(
			PathToolEventHandlers.createPolylineEventHandler(),
			QuPathResources.getString("Tools.polyline"),
			createIcon(PathIcons.POLYLINE_TOOL));
	/**
	 * Brush drawing tool
	 */
	public static final PathTool BRUSH = createTool(
			PathToolEventHandlers.createBrushEventHandler(),
			QuPathResources.getString("Tools.brush"),
			createIcon(PathIcons.BRUSH_TOOL));
	/**
	 * Points annotation and counting tool
	 */
	public static final PathTool POINTS = createTool(
			PathToolEventHandlers.createPointsEventHandler(),
			QuPathResources.getString("Tools.points"),
			createIcon(PathIcons.POINTS_TOOL));
	
	
	private static List<PathTool> ALL_TOOLS = Arrays.asList(
			MOVE, RECTANGLE, ELLIPSE, LINE, POLYGON, POLYLINE, BRUSH, POINTS
			);
	
	private static Node createIcon(PathIcons icon) {
		return IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, icon);
	}
		
	/**
	 * Create a tool from the specified {@link MouseEvent} handler.
	 * When the tool is registered, the handler will be called for any mouse event.
	 * @param handler the mouse event handler
	 * @param name the name of the tool
	 * @param icon the (toolbar) icon of the tool
	 * @return a new {@link PathTool}
	 */
	public static PathTool createTool(EventHandler<MouseEvent> handler, String name, Node icon) {
		return createTool(MouseEvent.ANY, handler, name, icon);
	}
	
	
	public static PathTool createExtendedTool(PathTool... tools) {
		if (tools.length == 0)
			throw new IllegalArgumentException("An extended tool should have at least 1 available mode!");
		return new ExtendedPathTool(
				Arrays.asList(tools)
				);
	}
	
	/**
	 * Create a tool from the specified event handler.
	 * @param type the type of the event that should be handled
	 * @param handler the event handler
	 * @param name the name of the tool
	 * @param icon the (toolbar) icon of the tool
	 * @return a new {@link PathTool}
	 */
	public static <T extends Event> PathTool createTool(EventType<T> type, EventHandler<T> handler, String name, Node icon) {
		return new DefaultPathTool<>(type, handler, name, icon);
	}
	
	/**
	 * Return the PathTool corresponding to the specified String.
	 * @param pathToolString
	 * @return pathTool
	 */
	public static PathTool getTool(String pathToolString) {
		for (var t : ALL_TOOLS) {
			if (t.getName().toLowerCase().equals(pathToolString))
				return t;
		}
		return null;
	}
	
	
	static class DefaultPathTool<T extends Event> implements PathTool {
		
		private static final Logger logger = LoggerFactory.getLogger(DefaultPathTool.class);
		
		private QuPathViewer viewer;
		private StringProperty name;
		private ObjectProperty<Node> icon;
		private EventType<T> type;
		private EventHandler<T> handler;
		
		DefaultPathTool(EventType<T> type, EventHandler<T> handler, String name, Node icon) {
			this.name = new SimpleStringProperty(name);
			this.icon = new SimpleObjectProperty<>(icon);
			this.type = type;
			this.handler = handler;
		}

		@Override
		public void registerTool(QuPathViewer viewer) {
			// Disassociate from any previous viewer
			if (this.viewer != null)
				deregisterTool(this.viewer);
			
			// Associate with new viewer
			this.viewer = viewer;
			if (viewer != null) {
				logger.trace("Registering {} to viewer {}", getName(), viewer);
				Node canvas = viewer.getView();
				canvas.addEventHandler(type, handler);
			}
		}

		@Override
		public void deregisterTool(QuPathViewer viewer) {
			if (this.viewer == viewer) {
				logger.trace("Deregistering {} from viewer {}", getName(), viewer);
				this.viewer = null;
				Node canvas = viewer.getView();
				canvas.removeEventHandler(type, handler);
			}
		}

		@Override
		public StringProperty nameProperty() {
			return name;
		}

		@Override
		public ObjectProperty<Node> iconProperty() {
			return icon;
		}
		
		@Override
		public String toString() {
			return getClass().getSimpleName() + ": " + name;
		}
		
	}

}