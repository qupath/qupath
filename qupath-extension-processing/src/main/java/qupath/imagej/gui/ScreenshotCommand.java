/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.imagej.gui;

import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.macro.Interpreter;
import ij.measure.Calibration;

import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;

/**
 * Create a snapshot of the QuPath viewer, and send the image directly to ImageJ.
 * 
 * @author Pete Bankhead
 *
 */
class ScreenshotCommand implements Runnable {

	private QuPathGUI qupath;

	/**
	 * Constructor.
	 * @param qupath QuPath instance where the command should be installed.
	 */
	public ScreenshotCommand(QuPathGUI qupath) {
//		super("Make screenshot", PathIconFactory.createIcon(QuPathGUI.iconSize, QuPathGUI.iconSize, PathIconFactory.PathIcons.SCREENSHOT));
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		QuPathViewer viewer = qupath.getViewer();
		if (viewer == null)
			return;
		
		BufferedImage img = GuiTools.makeSnapshot(qupath, GuiTools.SnapshotType.VIEWER);

		// Try to start up & display ImageJ
		ImageJ ij = IJExtension.getImageJInstance();
		if (ij != null)
			ij.setVisible(true);
		String name = "QuPath screenshot";
		if (viewer.getServer() != null)
			name = WindowManager.getUniqueName("Screenshot - " + ServerTools.getDisplayableImageName(viewer.getServer()));
		ImagePlus imp = new ImagePlus(name, img);
		double pixelWidth = getDisplayedPixelWidthMicrons(viewer);
		double pixelHeight = getDisplayedPixelHeightMicrons(viewer);
		if (!Double.isNaN(pixelWidth + pixelHeight)) {
			Calibration cal = imp.getCalibration();
			cal.pixelWidth = pixelWidth;
			cal.pixelHeight = pixelHeight;
			cal.setUnit("um");
//			// TODO: Set the screenshot origin, if no rotations have been applied
//			cal.xOrigin = -viewer.componentXtoImageX(0) / viewer.getDownsampleFactor();
//			cal.yOrigin = -viewer.componentYtoImageY(0) / viewer.getDownsampleFactor();
		}
		// TODO: Put image path into screenshot images - so they can also be used as whole slide images later
		Interpreter.batchMode = false; // Make sure we aren't in batch mode, so that image will display
		
		SwingUtilities.invokeLater(() -> imp.show());
	}
	
	
	private double getDisplayedPixelWidthMicrons(QuPathViewer viewer) {
		ImageServer<BufferedImage> server = viewer.getServer();
		if (server == null)
			return Double.NaN;
		return server.getPixelCalibration().getPixelWidthMicrons() * viewer.getDownsampleFactor();
	}

	private double getDisplayedPixelHeightMicrons(QuPathViewer viewer) {
		ImageServer<BufferedImage> server = viewer.getServer();
		if (server == null)
			return Double.NaN;
		return server.getPixelCalibration().getPixelHeightMicrons() * viewer.getDownsampleFactor();
	}
	
}