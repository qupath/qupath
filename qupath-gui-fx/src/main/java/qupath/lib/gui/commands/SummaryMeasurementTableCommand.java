/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

import java.awt.image.BufferedImage;
import java.util.function.Predicate;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.measure.ui.SummaryMeasurementTable;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.projects.ProjectImageEntry;


/**
 * Show a summary table for an object of a particular type (annotation, detection, TMA core...)
 * 
 * @author Pete Bankhead
 */
public class SummaryMeasurementTableCommand {

	private static final Logger logger = LoggerFactory.getLogger(SummaryMeasurementTableCommand.class);

	private final QuPathGUI qupath;

	/**
	 * Command to show a summary measurement table, for PathObjects of a specified type (e.g. annotation, detection).
	 * @param qupath
	 */
	public SummaryMeasurementTableCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
	}

	/**
	 * Show a measurement table for the specified image data.
	 * @param imageData the image data
	 * @param filter the filter to select which objects to include
	 * @see PathObjectFilter
	 */
	public void showTable(ImageData<BufferedImage> imageData, Predicate<PathObject> filter) {
		if (imageData == null) {
			logger.debug("Show table called with no image");
			GuiTools.showNoImageError("Show measurement table");
			return;
		}
		logger.debug("Show table called for {} and object filter {}", imageData, filter);
		showForFilter(imageData, filter);
	}


	private Stage showForFilter(ImageData<BufferedImage> imageData, Predicate<PathObject> filter) {
		var table = new SummaryMeasurementTable(imageData, filter);

		Stage stage = new Stage();
		stage.initOwner(qupath.getStage());

		String name = null;
		if (filter instanceof PathObjectFilter f)
			name = filterToName(f);
		var prefix = name == null || name.isBlank() ? "Measurements" : name;
		var title = createImageNameBinding(prefix + ": ", imageData);
		stage.titleProperty().bind(title);

		var pane = table.getPane();
		var screen = Screen.getPrimary();
		Scene scene = new Scene(pane,
				Math.min(screen.getBounds().getWidth(), 800),
				Math.min(screen.getBounds().getHeight(), 600));
		stage.setScene(scene);
		stage.show();
		return stage;
	}

	private static String filterToName(PathObjectFilter filter) {
		return switch (filter) {
			case TILES -> "Tiles";
			case CELLS -> "Cells";
			case DETECTIONS_ALL -> "Detections";
			case ANNOTATIONS -> "Annotations";
			case TMA_CORES -> "TMA cores";
			default -> null;
		};
	}

	private StringBinding createImageNameBinding(String prefix, ImageData<BufferedImage> imageData) {
		var entry = getEntry(imageData);
		if (entry == null) {
			return Bindings.createStringBinding(() -> prefix + imageData.getServerMetadata().getName());
		}
		return Bindings.createStringBinding(() -> prefix + entry.getImageName(), PathPrefs.maskImageNamesProperty());
	}

	private ProjectImageEntry<BufferedImage> getEntry(ImageData<BufferedImage> imageData) {
		var project = qupath == null ? null : qupath.getProject();
		if (project == null)
			return null;
		return project.getEntry(imageData);
	}
	

}
