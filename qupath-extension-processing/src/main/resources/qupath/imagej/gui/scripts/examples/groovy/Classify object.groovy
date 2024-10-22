package qupath.imagej.gui.scripts.examples.hidden
/*
 * Groovy script showing how a QuPath object can be classified.
 * You will need to send the current object as a Roi, and also
 * return the active Roi to see the results in QuPath.
 */

import ij.IJ
import qupath.imagej.tools.IJProperties

// Check we have a Roi
var imp = IJ.getImage()
var roi = imp == null ? null : imp.getRoi()
if (!roi) {
    return
}

// Make all measurements for the Roi
var stats = imp.getProcessor().getStatistics()
if (stats.max - stats.mean > stats.mean - stats.min) {
    IJProperties.setClassification(roi, "dark")
} else {
    IJProperties.setClassification(roi, "light")
}