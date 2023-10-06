/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.extension.svg;

import java.io.IOException;
import java.util.Arrays;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.extension.svg.SvgTools.SvgBuilder.ImageIncludeType;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;

/**
 * Command to export viewer images as SVG.
 * 
 * @author Pete Bankhead
 *
 */
class SvgExportCommand implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(SvgExportCommand.class);

	/**
	 * Export methods for SVG.
	 */
	public static enum SvgExportType {
		/**
		 * Snapshot of the contents of the current viewer, at the same resolution.
		 */
		VIEWER_SNAPSHOT,
		/**
		 * Custom export of the current selected ROI or entire image (if there is no ROI object selected).
		 */
		SELECTED_REGION
		}
	
	private static String title = "SVG export";
	
	private QuPathGUI qupath;
	private SvgExportType type;
	
	// Region export parameters
	private DoubleProperty downsample =
			PathPrefs.createPersistentPreference("svg.export.downsample", 1.0);

	private ObjectProperty<ImageIncludeType> imageType =
			PathPrefs.createPersistentPreference("svg.export.imageType", ImageIncludeType.EMBED, ImageIncludeType.class);

	private BooleanProperty highlightSelected =
			PathPrefs.createPersistentPreference("svg.export.highlightSelected", false);

	private BooleanProperty compress =
			PathPrefs.createPersistentPreference("svg.export.compress", false);
	
	/**
	 * Constructor.
	 * @param qupath QuPath instance, used to identify the ROI
	 * @param type specify export type (viewer snapshot, or region corresponding to the selected ROI
	 */
	public SvgExportCommand(QuPathGUI qupath, SvgExportType type) {
		this.qupath = qupath;
		this.type = type;
	}

	@Override
	public void run() {
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();
		if (imageData == null) {
			GuiTools.showNoImageError(title);
			return;
		}
				
		var builder = new SvgTools.SvgBuilder(viewer);
		var server = imageData.getServer();
		String description = "SVG image";
		String ext = ".svg";

		// Prompt for more options if we are exporting a selected region
		if (type == SvgExportType.SELECTED_REGION) {
			var selected = viewer.getSelectedObject();
			
			var params = new ParameterList()
					.addDoubleParameter("downsample", "Downsample factor", downsample.get(), null, "Downsample factor for export resolution (default: current viewer downsample)")
					.addChoiceParameter("includeImage", "Raster image", imageType.get(), Arrays.asList(ImageIncludeType.values()), "Export associated raster image")
					.addBooleanParameter("highlightSelected", "Highlight selected objects", highlightSelected.get(), "Highlight selected objects to distinguish these from unselected objects, as they are shown in the viewer")
					.addBooleanParameter("compress", "Compress SVGZ", compress.get(), "Write compressed SVGZ file, rather than standard SVG (default: no compression, for improved compatibility with other software)")
					;
			
			if (!GuiTools.showParameterDialog(title, params))
				return;
			
			downsample.set(params.getDoubleParameterValue("downsample"));
			imageType.set((ImageIncludeType)params.getChoiceParameterValue("includeImage"));
			highlightSelected.set(params.getBooleanParameterValue("highlightSelected"));
			compress.set(params.getBooleanParameterValue("compress"));
			
			if (downsample.get() <= 0) {
				Dialogs.showErrorMessage(title, "Downsample factor must be > 0!");
				return;
			}
			
			RegionRequest request;
			if (selected != null && selected.hasROI()) {
				request = RegionRequest.createInstance(server.getPath(), downsample.get(), selected.getROI());
			} else {
				request = RegionRequest.createInstance(server, downsample.get());
			}
			
			int width = (int)(request.getWidth() / downsample.get());
			int height = (int)(request.getHeight() / downsample.get());
			if ((width > 8192 || height > 8192)) {
				if (!Dialogs.showYesNoDialog(title,
						String.format("The requested image size (approx. %d x %d pixels) is very big -\n"
								+ "are you sure you want to try to export at this resolution?", width, height)))
						return;
			}
			
			builder
				.images(imageType.get())
				.region(request)
				.downsample(request.getDownsample())
				.showSelection(highlightSelected.get());
			
			if (compress.get()) {
				description = "SVGZ image";
				ext = ".svgz";
			}
		}
		
		var file = FileChoosers.promptToSaveFile(title, null,
				FileChoosers.createExtensionFilter(description, ext));
		if (file == null)
			return;
		
		try {
			builder.writeSVG(file);
		} catch (IOException e) {
			Dialogs.showErrorMessage(title, e);
			logger.error(e.getMessage(), e);
		}
	}

}