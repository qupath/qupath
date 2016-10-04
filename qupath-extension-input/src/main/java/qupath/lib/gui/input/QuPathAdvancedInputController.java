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

package qupath.lib.gui.input;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * 
 * Advanced input controller for browsing whole slide images in digital pathology, using
 * JInput - https://java.net/projects/jinput
 * 
 * Currently, this has been designed for (and only tested with) SpaceNavigator from 3D Connexion:
 *  http://www.3dconnexion.co.uk/products/spacemouse/spacenavigator.html
 * However, it does not make use of most of the 3D features, and therefore *may* work with other
 * similar input controllers, including joysticks (and it may not).
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathAdvancedInputController {

		private Controller controller;
		private QuPathGUI qupath;
		
		private double scrollScale = 10;
		
		private boolean zoomInPressed = false;
		private boolean zoomOutPressed = false;
		
//		private long lastTimestamp = 0;
		
		transient int MAX_SKIP = 5;
		transient int skipCount = 0;
		
		public QuPathAdvancedInputController(final Controller controller, final QuPathGUI qupath, final int heartbeat) {
			this.controller = controller;
			this.qupath = qupath;
//			lastTimestamp = System.currentTimeMillis();
		}
		
		public String getControllerName() {
			return controller.getName();
		}
		
		double getHigherMagnification(double mag) {
			if (mag >= 10 - 0.0001)
				return 40;
			else if (mag >= 4 - 0.0001)
				return 10;
			else if (mag >= 1 - 0.0001)
				return 4;
			else if (mag >= 0.25 - 0.0001)
				return 1;
			return 0.25;
		}

		double getLowerMagnification(double mag) {
			if (mag > 40 + 0.0001)
				return 40;
			if (mag > 10 + 0.0001)
				return 10;
			else if (mag > 4 + 0.0001)
				return 4;
			else if (mag > 1 + 0.0001)
				return 1;
			return 0.25;
		}

		
		/**
		 * Return true if the update is successful and the controller remains in a valid state, false otherwise.
		 * 
		 * If false is returned, then the controller may be stopped.
		 * 
		 * @return
		 */
		public boolean updateViewer() {
			
//			// If we seem to be falling behind, skip this event
//			long timestamp = System.currentTimeMillis();
//			if (timestamp - lastTimestamp > heartbeat * 1.75 && skipCount < MAX_SKIP) {
//				System.out.println("3D input event: Skipping update - timestamp difference is " + (timestamp - lastTimestamp) + " ms");
//				skipCount++;
//				lastTimestamp = timestamp;
//				return;
//			}
//			skipCount = 0;
//			lastTimestamp = timestamp;
			
			// Check the device
			if (!controller.poll())
				return false;
				
			// Check we have a viewer & server
			QuPathViewer viewer = qupath.getViewer();
			if (viewer == null || viewer.getServer() == null)
				return true;
			
			double serverMag = viewer.getServer().getMagnification();
			double magnification = viewer.getMagnification();
			double downsample = viewer.getDownsampleFactor();
			// Assume x40 if no other info...
			if (Double.isNaN(serverMag)) {
				serverMag = 40;
				magnification = serverMag / downsample;
			}
			
			if (magnification >= 40-0.0001)
				scrollScale = 25;
			else if (magnification >= 4)
				scrollScale = 50;
			else
				scrollScale = 100;

			
			double x = 0;
			double y = 0;
			double rz = 0;
			
			// Zooming in or out
			int zoom = 0;
			for (Component c : controller.getComponents()) {
				String name = c.getName();
				if ("x".equals(name)) {
					x = c.getPollData() * scrollScale;
				} else if ("y".equals(name)) {
					y = c.getPollData() * scrollScale;
				} else if ("z".equals(name)) {
					c.getPollData();
				} else if ("rx".equals(name)) {
					c.getPollData();
				} else if ("ry".equals(name)) {
					c.getPollData();
				} else if ("rz".equals(name)) {
					rz = c.getPollData();
				} else if ("0".equals(name)) {
					if (c.getPollData() != 0) {
						if (!zoomInPressed) // Don't zoom again if the button was already pressed
							zoom -= 1;						
						zoomInPressed = true;
				} else
						zoomInPressed = false;
				} else if ("1".equals(name)) {
					if (c.getPollData() != 0) {
						if (!zoomOutPressed) // Don't zoom again if the button was already pressed
							zoom += 1;	
						zoomOutPressed = true;
					} else
						zoomOutPressed = false;
				}
			}
			
			
			if (zoom != 0) {
				if (zoom > 0)
					downsample = serverMag / getHigherMagnification(magnification);
				else
					downsample = serverMag / getLowerMagnification(magnification);					
				viewer.setDownsampleFactor(downsample, -1, -1);
			} else if (Math.abs(rz * 20) >= 1) {
				if (rz < 0)
					viewer.zoomIn((int)(Math.abs(rz * 20)));
				else
					viewer.zoomOut((int)(Math.abs(rz * 20)));
				// If we're zooming this way, we're done - ignore other small x,y adjustments
				return true;
				//					System.out.println("rx: " + rx + ", ry: " + ry + ", rz: " + rz);
			}
			
			
			if (x != 0 || y != 0) {
				viewer.setCenterPixelLocation(
						viewer.getCenterPixelX() + x * scrollScale,
						viewer.getCenterPixelY() + y * scrollScale);
			}
			
			return true;
		}

	}