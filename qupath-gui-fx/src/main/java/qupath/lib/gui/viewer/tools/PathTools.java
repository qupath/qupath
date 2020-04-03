package qupath.lib.gui.viewer.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.icons.PathIconFactory.PathIcons;
import qupath.lib.gui.viewer.QuPathViewer;

public class PathTools {
	
	public static final PathTool MOVE      = createTool(new MoveTool(), "Move", createIcon(PathIcons.MOVE_TOOL));
	public static final PathTool RECTANGLE = createTool(new RectangleTool(), "Rectangle", createIcon(PathIcons.RECTANGLE_TOOL));
	public static final PathTool ELLIPSE   = createTool(new EllipseTool(), "Ellipse", createIcon(PathIcons.ELLIPSE_TOOL));
	public static final PathTool LINE      = createTool(new LineTool(), "Line", createIcon(PathIcons.LINE_TOOL));
	public static final PathTool POLYGON   = createTool(new PolygonTool(), "Polygon", createIcon(PathIcons.POLYGON_TOOL));
	public static final PathTool POLYLINE  = createTool(new PolylineTool(), "Polyline", createIcon(PathIcons.POLYLINE_TOOL));
	public static final PathTool BRUSH     = createTool(new BrushTool(), "Brush", createIcon(PathIcons.BRUSH_TOOL));
	public static final PathTool POINTS    = createTool(new PointsTool(), "Points", createIcon(PathIcons.POINTS_TOOL));
	
	private static Node createIcon(PathIcons icon) {
		return PathIconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, icon);
	}
	
	
	public static <T extends Event> PathTool createTool(EventHandler<MouseEvent> handler, String name, Node icon) {
		return createTool(MouseEvent.ANY, handler, name, icon);
	}
	
	public static <T extends Event> PathTool createTool(EventType<T> type, EventHandler<T> handler, String name, Node icon) {
		return new DefaultPathTool<>(type, handler, name, icon);
	}
	
	
	static class DefaultPathTool<T extends Event> implements PathTool {
		
		private static final Logger logger = LoggerFactory.getLogger(DefaultPathTool.class);
		
		private QuPathViewer viewer;
		private String name;
		private Node icon;
		private EventType<T> type;
		private EventHandler<T> handler;
		
		DefaultPathTool(EventType<T> type, EventHandler<T> handler, String name, Node icon) {
			this.name = name;
			this.icon = icon;
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
		public String getName() {
			return name;
		}

		@Override
		public Node getIcon() {
			return icon;
		}
		
		@Override
		public String toString() {
			return getClass().getSimpleName() + ": " + name;
		}
		
	}

}
