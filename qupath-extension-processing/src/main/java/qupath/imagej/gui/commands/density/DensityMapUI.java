/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.imagej.gui.commands.density;

import java.awt.image.BufferedImage;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.StringProperty;
import javafx.scene.layout.Pane;
import qupath.imagej.gui.commands.ui.SaveResourcePaneBuilder;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.projects.Project;

/**
 * UI elements associated with density maps.
 * 
 * @author Pete Bankhead
 */
public class DensityMapUI {

	public static Pane createSaveDensityMapPane(ObjectExpression<Project<BufferedImage>> project, ObjectExpression<DensityMapBuilder> densityMap, StringProperty savedName) {
		var tooltipTextYes = "Save density map in the current project - this is required to use the density map later (e.g. to create objects, measurements)";
		var tooltipTextNo = "Cannot save a density map outside a project. Please create a project to save the classifier.";
		var tooltipText = Bindings
				.when(project.isNull())
				.then(Bindings.createStringBinding(() -> tooltipTextNo, project))
				.otherwise(Bindings.createStringBinding(() -> tooltipTextYes, project));
		
		return new SaveResourcePaneBuilder<>(DensityMapBuilder.class, densityMap)
				.project(project)
				.labelText("Density map name")
				.textFieldPrompt("Enter name")
				.savedName(savedName)
				.tooltip(tooltipText)
				.title("Density maps")
				.build();
	}

	
}
