/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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


package qupath.lib.gui.viewer;

import javafx.event.EventHandler;
import javafx.scene.input.ScrollEvent;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.tools.PathTools;

class ScrollEventPanningFilter implements EventHandler<ScrollEvent> {
		
		private QuPathViewer viewer;
		private boolean lastTouchEvent = false;
		private double deltaX = 0;
		private double deltaY = 0;
		private long lastTimestamp = 0L;
		
		ScrollEventPanningFilter(final QuPathViewer viewer) {
			this.viewer = viewer;
		}

		@Override
		public void handle(ScrollEvent e) {
			
			// Check if we'd rather be using scroll to do something else (e.g. zoom, adjust opacity)
			boolean wouldRatherDoSomethingElse = e.getTouchCount() == 0 && (!PathPrefs.useScrollGesturesProperty().get() || e.isShiftDown() || e.isShortcutDown());
			if (wouldRatherDoSomethingElse) {
				return;
			}
			
			// Don't pan with inertia events (use the 'mover' instead)
			if (e.isInertia()) {
				e.consume();
				return;
			}
			
			// Return if we aren't using a touchscreen, and we don't want to handle scroll gestures - 
			// but don't consume the event so that it can be handled elsewhere
			lastTouchEvent = e.getTouchCount() != 0;
			if (!lastTouchEvent && !PathPrefs.useScrollGesturesProperty().get() || e.isShiftDown() || e.isShortcutDown()) {
				return;
			}
			// Swallow the event if we're using a touch screen without the move tool selected - we want to draw instead
			if (lastTouchEvent && viewer.getActiveTool() != PathTools.MOVE) {
				e.consume();
				return;
			}
			
			// If this is a SCROLL_FINISHED event, continue moving with the last starting velocity - but ignore inertia
			if (!lastTouchEvent && e.getEventType() == ScrollEvent.SCROLL_FINISHED) {
				if (System.currentTimeMillis() - lastTimestamp < 100L) {
					viewer.requestStartMoving(deltaX, deltaY);
					viewer.requestDecelerate();					
				}
				deltaX = 0;
				deltaY = 0;
				e.consume();
				return;
			}
			
			// Use downsample since shift will be defined in full-resolution pixel coordinates
			double dx = e.getDeltaX() * viewer.getDownsampleFactor();
			double dy = e.getDeltaY() * viewer.getDownsampleFactor();
			
			// Flip scrolling direction if necessary
			if (PathPrefs.invertScrollingProperty().get()) {
				dx = -dx;
				dy = -dy;
			}
			
			// Handle rotation
			if (viewer.isRotated()) {
				double cosTheta = Math.cos(-viewer.getRotation());
				double sinTheta = Math.sin(-viewer.getRotation());
				double dx2 = cosTheta*dx - sinTheta*dy;
				double dy2 = sinTheta*dx + cosTheta*dy;
				dx = dx2;
				dy = dy2;
			}

			// Shift the viewer
			viewer.setCenterPixelLocation(
					viewer.getCenterPixelX() - dx,
					viewer.getCenterPixelY() - dy);
			
			// Retain deltas in case we need to decelerate later
			deltaX = dx;
			deltaY = dy;
			lastTimestamp = System.currentTimeMillis();
			
			e.consume();
		}
		
	}