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

package qupath.lib.gui;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.stage.Stage;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Launcher application to start QuPathGUI.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathApp extends Application {
	
	final static Logger logger = LoggerFactory.getLogger(QuPathApp.class);

	@Override
	public void start(Stage stage) throws Exception {
		// Handle any resets needed
		List<String> args = getParameters().getRaw();
		if (args.contains("reset")) {
			PathPrefs.resetPreferences();
			logger.info("Preferences have been reset");
			args = new ArrayList<>(args);
			args.remove("reset");
		}
		
		// Create main GUI
		QuPathGUI gui = new QuPathGUI(stage);
		logger.info("Starting QuPath with parameters: " + args);
		
		// Open an image, if required
		if (args != null && !args.isEmpty()) {
			gui.openImage(args.get(0), false, false, false);
		}
		
		gui.updateCursor();
		
	}

}
