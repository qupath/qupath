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
import javafx.application.Platform;
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
		List<String> args = new ArrayList<>(getParameters().getRaw());
		if (args.contains("reset")) {
			PathPrefs.resetPreferences();
			logger.info("Preferences have been reset");
			args.remove("reset");
		}
		boolean skipSetup = false;
		if (args.contains("skip-setup")) {
			skipSetup = true;
			args.remove("skip-setup");
		}
		
		// Create main GUI
		QuPathGUI gui = new QuPathGUI(getHostServices(), stage);
		logger.info("Starting QuPath with parameters: " + args);
		
		// Try to open an image, if required
		if (args != null && !args.isEmpty()) {
			String path = args.get(0);
			gui.openImage(path, false, false, false);
//			if (path != null && !path.isEmpty())
//				Platform.runLater(() -> gui.openImage(path, false, false, false));
		}
		
		gui.updateCursor();
		
		// Show setup if required, and if we haven't an argument specifying to skip
		// Store a value indicating the setup version... this means we can enforce running 
		// setup at a later date with a later version if new and necessary options are added
		int currentSetup = 1; 
		int lastSetup = PathPrefs.getUserPreferences().getInt("qupathSetupValue", -1);
		if (!skipSetup && lastSetup != currentSetup) {
			Platform.runLater(() -> {
				if (gui.showSetupDialog()) {
					PathPrefs.getUserPreferences().putInt("qupathSetupValue", currentSetup);
					PathPrefs.savePreferences();
				}
			});
		}
		
	}

}
