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

import qupath.lib.extension.svg.SvgTools.SvgBuilder.ImageIncludeType;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;

/**
 * Command to export viewer images as SVG.
 * 
 * @author Pete Bankhead
 *
 */
class SvgExportCommand implements Runnable {
	
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
	private double downsample = 1.0;
	private ImageIncludeType imageType = ImageIncludeType.NONE;
	private boolean highlightSelected = false;
	private boolean compress = false;
	
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
			Dialogs.showNoImageError(title);
			return;
		}
				
		var builder = new SvgTools.SvgBuilder(viewer);
		var server = imageData.getServer();
		String description = "SVG image";
		String ext = ".svg";
		String name = null;

		// Prompt for more options if we are exporting a selected region
		if (type == SvgExportType.SELECTED_REGION) {
			var selected = viewer.getSelectedObject();
			
			var params = new ParameterList()
					.addDoubleParameter("downsample", "Downsample factor", downsample, null, "Downsample factor for export resolution (default: current viewer downsample)")
					.addChoiceParameter("includeImage", "Raster image", imageType, Arrays.asList(ImageIncludeType.values()), "Export associated raster image")
					.addBooleanParameter("highlightSelected", "Highlight selected objects", highlightSelected, "Highlight selected objects to distinguish these from unselected objects, as they are shown in the viewer")
					.addBooleanParameter("compress", "Compress SVGZ", compress, "Write compressed SVGZ file, rather than standard SVG (default: no compression, for improved compatibility with other software)")
					;
			
			if (!Dialogs.showParameterDialog(title, params))
				return;
			
			downsample = params.getDoubleParameterValue("downsample");
			imageType = (ImageIncludeType)params.getChoiceParameterValue("includeImage");
			highlightSelected = params.getBooleanParameterValue("highlightSelected");
			compress = params.getBooleanParameterValue("compress");
			
			if (downsample <= 0) {
				Dialogs.showErrorMessage(title, "Downsample factor must be > 0!");
				return;
			}
			
			RegionRequest request;
			if (selected != null && selected.hasROI()) {
				request = RegionRequest.createInstance(server.getPath(), downsample, selected.getROI());
			} else {
				request = RegionRequest.createInstance(server, downsample);
			}
			
			int width = (int)(request.getWidth() / downsample);
			int height = (int)(request.getHeight() / downsample);
			if ((width > 8192 || height > 8192)) {
				if (!Dialogs.showYesNoDialog(title,
						String.format("The requested image size (approx. %d x %d pixels) is very big -\n"
								+ "are you sure you want to try to export at this resolution?", width, height)))
						return;
			}
			
			builder
				.images(imageType)
				.region(request)
				.downsample(request.getDownsample())
				.showSelection(highlightSelected);
			
			if (compress) {
				description = "SVGZ image";
				ext = ".svgz";
			}
		}
		
		var file = Dialogs.promptToSaveFile(title, null, name, description, ext);
		if (file == null)
			return;
		
		try {
			builder.writeSVG(file);
		} catch (IOException e) {
			Dialogs.showErrorMessage(title, e);
		}
	}

}