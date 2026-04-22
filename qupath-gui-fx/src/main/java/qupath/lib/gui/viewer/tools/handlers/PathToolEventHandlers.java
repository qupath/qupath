/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer.tools.handlers;

import javafx.event.EventHandler;
import javafx.scene.input.InputEvent;
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
	
	public static EventHandler<InputEvent> createBrushEventHandler() {
		return new BrushToolEventHandler();
	}
	
	public static EventHandler<MouseEvent> createArrowStartEventHandler() {
		return new ArrowToolEventHandler("<");
	}

	public static EventHandler<MouseEvent> createArrowEndEventHandler() {
		return new ArrowToolEventHandler(">");
	}

	public static EventHandler<MouseEvent> createDoubleArrowEventHandler() {
		return new ArrowToolEventHandler("<>");
	}


}
