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

package qupath.lib.gui.tma;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;


/**
 * Standalone viewer for looking at TMA summary results.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathTMAViewer extends Application {
	
	final private static Logger logger = LoggerFactory.getLogger(QuPathTMAViewer.class);
	
	@Override
	public void start(Stage primaryStage) throws Exception {
		TMASummaryViewer tmaSummary = new TMASummaryViewer(primaryStage);
		
		Stage stage = tmaSummary.getStage();
		try {
			Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
			stage.setWidth(bounds.getWidth() * 0.75);
			stage.setHeight(bounds.getHeight() * 0.75);
		} catch (Exception e) {
			logger.error("Problem determining screen size: {}", e);
			stage.setWidth(600);
		}
		stage.show();
		
		String file = getParameters().getNamed().get("file");
		if (file != null)
			tmaSummary.setInputFile(new File(file));
	}
	

}