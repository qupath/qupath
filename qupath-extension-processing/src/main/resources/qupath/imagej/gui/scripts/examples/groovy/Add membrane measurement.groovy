/*
 * Groovy script for QuPath's ImageJ script runner.
 * This can make intensity measurements around a ROI boundary.
 *
 * It is intended to help calculate the cell membrane intensity values,
 * giving control over the membrane thickness.
 *
 * Here, it will loop through all the channels that are provided to calculate
 * a value for each channel.
 */

import ij.IJ
import ij.process.ByteProcessor
import ij.process.ImageProcessor
import ij.process.ImageStatistics
import qupath.imagej.processing.IJProcessing
import qupath.lib.objects.PathObject

// Define the membrane thickness
int thickness = 3

// Access the image and original QuPath object
var imp = IJ.getImage()
var roi = imp.getRoi()
PathObject pathObject = imp.getProperty("qupath.pathObject")

// Create a Roi for the membrane
var bp = new ByteProcessor(imp.getWidth(), imp.getHeight())
bp.setValue(255)
bp.setLineWidth(thickness)
bp.draw(roi)
bp.setThreshold(127, Double.MAX_VALUE, ImageProcessor.NO_LUT_UPDATE)
var roiMembrane = IJProcessing.thresholdToRoi(bp)
imp.setRoi(roiMembrane)

// Loop through the channels
for (int c = 1; c <= imp.getNChannels(); c++) {
    imp.setPositionWithoutUpdate(c, imp.getSlice(), imp.getFrame())
    var label = imp.getStack().getSliceLabel(imp.getCurrentSlice())
    var name = label ? "Membrane - $label" : "Membrane - Channel $c"
    // Get the mean value - other metrics could be calculated here
    var stats = imp.getStatistics(ImageStatistics.MEAN)
    pathObject.measurements[name] = stats.mean
}

// Reset the original Roi (to avoid returning the new one)
// Comment out this line for a test run to see the membrane Roi
imp.setRoi(roi)
