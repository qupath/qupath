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

package qupath.lib.gui.commands;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ObservableValue;
import javafx.util.Duration;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Command to zoom in or out a selected viewer (with a little bit of animation).
 * 
 * @author Pete Bankhead
 *
 */
class ZoomCommand implements Runnable {
	
	private ObservableValue<? extends QuPathViewer> viewerValue;
	private int zoomAmount;

	public ZoomCommand(final ObservableValue<? extends QuPathViewer> viewerValue, final int zoomAmount) {
		this.viewerValue = viewerValue;
		this.zoomAmount = zoomAmount;
	}

	private Timeline timer;
	
	@Override
	public void run() {
		QuPathViewer viewer = viewerValue.getValue();
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
	}
	
	
	public static ZoomCommand createZoomInCommand(final ObservableValue<? extends QuPathViewer> viewerValue) {
		return new ZoomCommand(viewerValue, -10);
	}

	public static ZoomCommand createZoomOutCommand(final ObservableValue<? extends QuPathViewer> viewerValue) {
		return new ZoomCommand(viewerValue, 10);
	}

}