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
import ij.WindowManager;
import ij.macro.Interpreter;
import ij.measure.Calibration;

import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import qupath.imagej.gui.IJExtension;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.DisplayHelpers.SnapshotType;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ServerTools;

/**
 * Create a snapshot of the QuPath viewer, and send the image directly to ImageJ.
 * 
 * @author Pete Bankhead
 *
 */
public class ScreenshotCommand implements PathCommand {

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
		
		BufferedImage img = DisplayHelpers.makeSnapshot(qupath, SnapshotType.CURRENT_VIEWER);
//		Graphics2D g2d;
//		Border border = viewer.getBorder();
//		if (border == null) {
//			img = new BufferedImage(viewer.getWidth(), viewer.getHeight(), BufferedImage.TYPE_INT_RGB);
//			g2d = img.createGraphics();
//		} else {
//			// Shift according to insets to avoid painting the panel border
//			Insets insets = border.getBorderInsets(viewer);
//			img = new BufferedImage(viewer.getWidth()-insets.left-insets.right, viewer.getHeight()-insets.top-insets.bottom, BufferedImage.TYPE_INT_RGB);
//			g2d = img.createGraphics();
//			g2d.translate(-insets.left, -insets.top);
//		}
//		viewer.paintAll(g2d);
		// Try to start up & display ImageJ
		ImageJ ij = IJExtension.getImageJInstance();
		if (ij != null)
			ij.setVisible(true);
		String name = "QuPath screenshot";
		if (viewer.getServer() != null)
			name = WindowManager.getUniqueName("Screenshot - " + ServerTools.getDisplayableImageName(viewer.getServer()));
		ImagePlus imp = new ImagePlus(name, img);
		double pixelWidth = viewer.getDisplayedPixelWidthMicrons();
		double pixelHeight = viewer.getDisplayedPixelHeightMicrons();
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
		
//		new PathImagePlus(viewer.getWholeSlideImageServer().getServerPath(), imp, viewer.getDisplayedImageBounds()).getImage().show();
	}
	
}