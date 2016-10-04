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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.Timeline;
import javafx.animation.Animation.Status;
import javafx.animation.KeyFrame;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.util.Duration;
import qupath.lib.gui.QuPathGUI;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Controller.Type;


/**
 * Starting point for installing an advanced input controller.
 * 
 * @author Pete Bankhead
 *
 */
public class AdvancedControllerActionFactory {

	final private static Logger logger = LoggerFactory.getLogger(AdvancedControllerActionFactory.class);
	
	/**
	 * Attempt to turn on advanced controller, if any can be found.
	 * 
	 * Note: This only searches for controllers the first time it is run...
	 * therefore any controller needs to be plugged in before this is called.
	 * 
	 * What's worse, if the controller is unplugged and then plugged in again it
	 * won't be picked up.
	 * 
	 * @param qupath
	 * @return
	 */
	public static boolean tryToTurnOnAdvancedController(final QuPathGUI qupath) {
		if (hasAdvancedControllers())
			return new ControllerChangeListener(qupath).turnOnController();
		return false;
	}
	
	
	/**
	 * Returns true if there are advanced controllers present, so it's worth adding a menu item.
	 * 
	 * @return
	 */
	public static boolean hasAdvancedControllers() {
		return !getCompatibleControllers().isEmpty();
	}
	
	
	
	/**
	 * Get controllers from JInput that would be suitable
	 * 
	 * @return
	 */
	private static List<Controller> getCompatibleControllers() {
		ControllerEnvironment controllerEnvironment = ControllerEnvironment.getDefaultEnvironment(); 
//		controllerEnvironment.addControllerListener(new ControllerListener() {
//			public void controllerRemoved(ControllerEvent ev) {
//				System.err.println("ADDED: " + ev);
//			}
//
//		    /**
//		     * Invoked when a controller has been added.
//		     */
//		    public void controllerAdded(ControllerEvent ev) {
//				System.err.println("REMOVED: " + ev);
//		    }
//		});
		Controller[] controllers = controllerEnvironment.getControllers();
		List<Controller> advancedControllers = new ArrayList<>();
		for (Controller controller : controllers) {
			// For now, we only support 'Space Navigator' controllers... being more permissive can cause strange things to happen
			// (in particular, things go very badly wrong with VirtualBox)
			if (controller.getType() == Type.STICK && controller.getName().toLowerCase().equals("spacenavigator")) {
//			if (controller.getType() == Type.STICK || !controller.getName().toLowerCase().contains("virtualbox")) {
				logger.info("Registering controller: " + controller.getName() + ", " + controller.getType() ); 
				advancedControllers.add(controller);
			} else
				logger.trace("Skipping controller: " + controller.getName() + ", " + controller.getType() ); 
		}
		return advancedControllers;
	}
	
	
	
	static class ControllerChangeListener implements ChangeListener<Boolean> {
		
		private QuPathGUI qupath;
		private int heartbeat = 25;
		
		private List<QuPathAdvancedInputController> advancedControllers = new ArrayList<>();
		private Timeline timeline;
		
		private BooleanProperty controllerOn = new SimpleBooleanProperty();
		
		ControllerChangeListener(final QuPathGUI qupath) {
			this.qupath = qupath;
		}
		
		
		@Override
		public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
			if (newValue) {
				turnOnController();
			}
			else
				turnOffController();
		}
		
		Timeline getTimeline() {
			if (timeline == null) {
				timeline = new Timeline(
						new KeyFrame(
								Duration.ZERO,
								actionEvent -> {
									Iterator<QuPathAdvancedInputController> iter = advancedControllers.iterator();
									while (iter.hasNext()) {
										QuPathAdvancedInputController controller = iter.next();
										if (!controller.updateViewer()) {
											iter.remove();
											logger.error("Controller {} has been unplugged!", controller.getControllerName());
										}
									}
									if (advancedControllers.isEmpty())
										turnOffController();
								}
								),
						new KeyFrame(
								Duration.millis(heartbeat)
								)
						);
				timeline.setCycleCount(Timeline.INDEFINITE);
			}
			return timeline;
		}
		
		private boolean isControllerOn() {
			return timeline != null && timeline.getStatus() == Status.RUNNING;
		}
		
		
		ObservableBooleanValue controllerOnProperty() {
			return controllerOn;
		}
		
		
		void turnOffController() {
			if (timeline != null) {
				timeline.stop();
				advancedControllers.clear();
				controllerOn.set(true);
			}
		}
		
		
		boolean turnOnController() {
			if (isControllerOn())
				return true;
			
			advancedControllers.clear(); // Just to be sure...
			for (Controller controller : getCompatibleControllers()) {
				advancedControllers.add(new QuPathAdvancedInputController(controller, qupath, heartbeat));
			}
			if (advancedControllers.isEmpty()) {
				logger.error("No advanced controller found!");
				return false;
			}
			
			if (advancedControllers.size() > 1)
				logger.warn("Number of controllers registered: " + advancedControllers.size()); 			
			timeline = getTimeline();
			timeline.play();			
			controllerOn.set(true);
			return true;
		}
		
		
	}
	

}
