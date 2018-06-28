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

package qupath.imagej.gui.commands;

import ij.ImageJ;
import ij.ImagePlus;
import ij.macro.Interpreter;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import qupath.imagej.gui.IJExtension;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;

/**
 * Extract a region from QuPath, and display it within ImageJ.
 * 
 * This command also attempts to set properties appropriately so that it's possible to determine from 
 * within ImageJ exactly where in the image was selected.  This is needed to be able to transfer back 
 * Rois from ImageJ as QuPath objects.
 * 
 * @author Pete Bankhead
 *
 */
public class ExtractRegionCommand implements PathCommand {
	
	private QuPathGUI qupath;
	private double downsample;
	private boolean prompt;
	
	public ExtractRegionCommand(QuPathGUI qupath, double downsample, boolean doPrompt) {
//		super("Extract region (custom)", PathIconFactory.createIcon(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.EXTRACT_REGION));
		this.qupath = qupath;
//		if (!doPrompt)
//			this.putValue(NAME, String.format("Extract region (%.1fx)", downsample));
		this.downsample = downsample;
		this.prompt = doPrompt;
	}
	
	public String getName() {
		if (prompt)
			return "Extract region (custom)";
		else
			return String.format("Extract region (%.1fx)", downsample);
	}
	

	@Override
	public void run() {
		QuPathViewer viewer = qupath.getViewer();
		if (viewer == null || viewer.getServer() == null)
			return;
		
		if (prompt) {
			double downsample2 = downsample;
			String input = DisplayHelpers.showInputDialog("Extract image region", "Downsample factor for region extraction (a number >= 1)", "4");
			if (input == null)
				return;
			try {
				downsample2 = Double.parseDouble(input);
				if (downsample2 < 1) {
					DisplayHelpers.showPlainMessage("Extract image region", "Sorry, the downsample factor must be >= 1 - you can rescale later with ImageJ if needed");
//					JOptionPane.showMessageDialog(null, "Sorry, the downsample factor must be >= 1 - you can rescale later with ImageJ if needed", "Extract image region", JOptionPane.PLAIN_MESSAGE, null);
					return;
				}
				downsample = downsample2;
			} catch (NumberFormatException e) {
				return;
			}
		}
		
		// Get the selected object & determine if its size seems to be ok
		PathObject pathObject = viewer.getSelectedObject();
		ImageServer<BufferedImage> server = viewer.getServer();
		int width, height;
		if (pathObject == null || !pathObject.hasROI()) {
			width = server.getWidth();
			height = server.getHeight();
		} else {
			Rectangle bounds = AwtTools.getBounds(pathObject.getROI());
			width = bounds.width;
			height = bounds.height;
		}
		int bytesPerPixel = server.isRGB() ? 4 : server.getBitsPerPixel() * server.nChannels() / 8;
		double memory = ((long)width * height * bytesPerPixel) / (downsample * downsample);
		// TODO: Perform calculation based on actual amount of available memory
		if (memory >= Runtime.getRuntime().totalMemory()) {
			DisplayHelpers.showErrorMessage("Extract region error", "Selected region is too large to extract - please selected a smaller region or use a higher downsample factor");
//			JOptionPane.showMessageDialog(viewer, 
//					"Selected region is too large to extract - please selected a smaller region or use a higher downsample factor",
//					"Extract region error", JOptionPane.ERROR_MESSAGE, null);
			return;
		}
		if (memory / 1024 / 1024 > 100) {
			if (!DisplayHelpers.showYesNoDialog("Extract ROI", String.format("Attempting to extract this region is likely to require > %.2f MB - are you sure you want to continue?", memory/1024/1024)))
				return;
		}
		
		
		//		PathImage<ImagePlus> pathImage = ImageJWholeSlideViewerGUI.extractROI(viewer.getImageServer(), viewer.getCurrentROI(), downsample, viewer.getZPosition(), true);
		RegionRequest region;
		if (pathObject == null || !pathObject.hasROI() || pathObject.isPoint()) {
			region = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight(), viewer.getZPosition(), viewer.getTPosition());
		} else
			region = RegionRequest.createInstance(server.getPath(), downsample, pathObject.getROI());
		//					region = RegionRequest.createInstance(server.getPath(), downsample, pathObject.getROI(), viewer.getZPosition(), viewer.getTPosition());

		if (region.getWidth() / downsample < 10 || region.getHeight() / downsample < 10) {
			DisplayHelpers.showErrorMessage("Image region to small", "The width & height of the extracted image must both be >= 10 pixels");
			return;
		}
		
		// Color transforms are (currently) only applied for brightfield images - for fluorescence we always provide everything as unchanged as possible
		ImageDisplay imageDisplay = viewer.getImageData().isBrightfield() ? viewer.getImageDisplay() : null;

		
		// We should switch to the event dispatch thread when interacting with ImageJ
		PathImage<ImagePlus> pathImage = IJExtension.extractROIWithOverlay(server, pathObject, viewer.getHierarchy(), region, true, viewer.getOverlayOptions(), imageDisplay);
		if (pathImage != null) {
			SwingUtilities.invokeLater(() -> {

				// Try to start an ImageJ instance, and return if this fails
				ImageJ ij = IJExtension.getImageJInstance();
				if (ij == null)
					return;
				ij.setVisible(true);


				Interpreter.batchMode = false; // Make sure we aren't in batch mode, so that image will display
				pathImage.getImage().show();
			});
		}
	}
	
	
}