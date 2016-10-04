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

package qupath.lib.gui.commands;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.ViewerManager;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Command to zoom in or out a selected viewer (with a little bit of animation).
 * 
 * @author Pete Bankhead
 *
 */
public class ZoomCommand implements PathCommand {
	
	private ViewerManager<?> qupath;
	private int zoomAmount;

	public ZoomCommand(final ViewerManager<?> qupath, final int zoomAmount) {
		this.qupath = qupath;
		this.zoomAmount = zoomAmount;
	}

	private Timeline timer;
	
	@Override
	public void run() {
		QuPathViewer viewer = qupath.getViewer();
		if (viewer != null) {
			if (timer != null)
				timer.stop();
			timer = new Timeline(
					new KeyFrame(
							Duration.ZERO,
							actionEvent -> viewer.zoomIn((int)(zoomAmount/(timer.getCurrentTime().toMillis()/10+1)))
							),
					new KeyFrame(
							Duration.millis(20)
							)
					);
			timer.setCycleCount(15);
			timer.playFromStart();
		}
//			viewer.zoomIn(zoomAmount);
	}
	
	
	public static class ZoomIn extends ZoomCommand {

		public ZoomIn(final QuPathGUI qupath) {
			super(qupath, -10);
		}
				
	}

	public static class ZoomOut extends ZoomCommand {

		public ZoomOut(final QuPathGUI qupath) {
			super(qupath, 10);
		}
				
	}
	
}