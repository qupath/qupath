package qupath.lib.gui.viewer.tools.handlers;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

public class PathToolEventHandlers {
	
	public static EventHandler<MouseEvent> createMoveEventHandler() {
		return new MoveToolEventHandler();
	}
	
	public static EventHandler<MouseEvent> createRectangleEventHandler() {
		return new RectangleToolEventHandler();
	}
	
	public static EventHandler<MouseEvent> createEllipseEventHandler() {
		return new EllipseToolEventHandler();
	}
	
	public static EventHandler<MouseEvent> createLineEventHandler() {
		return new LineToolEventHandler();
	}
	
	public static EventHandler<MouseEvent> createPolygonEventHandler() {
		return new PolygonToolEventHandler();
	}

	public static EventHandler<MouseEvent> createPolylineEventHandler() {
		return new PolylineToolEventHandler();
	}
	
	public static EventHandler<MouseEvent> createPointsEventHandler() {
		return new PointsToolEventHandler();
	}
	
	public static EventHandler<MouseEvent> createBrushEventHandler() {
		return new BrushToolEventHandler();
	}
	
	public static ArrowToolEventHandler createArrowStartEventHandler() {
		return new ArrowToolEventHandler("<");
	}

	public static ArrowToolEventHandler createArrowEndEventHandler() {
		return new ArrowToolEventHandler(">");
	}

	public static ArrowToolEventHandler createDoubleArrowEventHandler() {
		return new ArrowToolEventHandler("<>");
	}


}
