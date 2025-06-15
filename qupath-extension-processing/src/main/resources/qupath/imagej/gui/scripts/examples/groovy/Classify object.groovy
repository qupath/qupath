package qupath.imagej.gui.scripts.examples.hidden
/*
 * Groovy script showing how a QuPath object can be classified
 * by thresholding based on local intensities.
 * 
 * You will need to send the current object as a Roi.
 * Because this script classifies the object directly, you should 
 * *not* return any Roi or overlay to QuPath
 * (since this could result in the classification being reset).
 */

import ij.IJ

// Check we have a Roi
var imp = IJ.getImage()
var roi = imp.getRoi()
if (!roi) {
    return
}

// Determine a threshold from outside the Roi
var roiInverse = roi.getInverse(imp)
imp.setRoi(roiInverse)
var statsOutside = imp.getStatistics()
double threshold = statsOutside.mean + 3 * statsOutside.stdDev

// Determine some statistics from inside the Roi
imp.setRoi(roi)
var statsInside = imp.getStatistics()

// Get the object
var pathObject = imp.getProperty("qupath.pathObject")

// Assign a classification
if (statsInside.mean > threshold) {
    pathObject.classification = "Positive"
} else {
    pathObject.classification = "Negative"
}
