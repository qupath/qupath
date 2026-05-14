/*
 * Groovy script for QuPath's ImageJ script runner.
 * This writes out RGB images for a ROI, where pixels outside the ROI are
 * filled white.
 * Images are written to a subdirectory of the current project.
 */


import ij.IJ
import ij.ImagePlus
import qupath.lib.regions.ImageRegion
import qupath.lib.scripting.QP

import java.awt.*

// Get the image and Roi
var imp = IJ.getImage()
var roi = imp.getRoi()
if (roi == null || imp.getType() != ImagePlus.COLOR_RGB) {
    throw new IllegalArgumentException("The script only supports RGB images with a Roi set")
}

// Fill outside
var ip = imp.getProcessor()
ip.setColor(Color.WHITE)
ip.fillOutside(roi)

// Get rid of Roi & overlay so they aren't displayed
imp.killRoi()
imp.setOverlay(null)

// Export the image
var path = QP.createDirectoryInProject("export")
var imageName = QP.getCurrentImageNameWithoutExtension()
ImageRegion region = imp.getProperty("qupath.request")
var name = "$imageName-[$region.x, $region.y, $region.width, $region.height].png"
IJ.save(imp, path + name)
