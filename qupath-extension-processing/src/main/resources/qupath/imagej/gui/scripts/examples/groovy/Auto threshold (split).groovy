/*
 * ImageJ macro to apply an automated threshold to detect multiple regions.
 * This generates separate Rois for different connected components,
 * and adds them to the image overlay.
 * You will need to return the active overlay to see the results in QuPath.
 */

import ij.IJ
import ij.process.AutoThresholder
import ij.process.ColorProcessor
import qupath.imagej.processing.IJProcessing
import qupath.imagej.tools.IJProperties

// Select the thresholding method
var method = AutoThresholder.Method.Otsu

// Optional Gaussan smoothing
double sigma = 1.0

// Get the current image
var imp = IJ.getImage()

// Check if the image has a property specifying a dark background
boolean darkBackground = IJProperties.getImageBackground(imp) == IJProperties.BACKGROUND_DARK

// Ensure single-channel grayscale
var ip = imp.getProcessor()
ip.resetMinAndMax()
if (ip instanceof ColorProcessor)
    ip = ip.convertToByteProcessor()

// Smooth if needed
if (sigma > 0)
    ip.blurGaussian(sigma)

// Create Rois from threshold
ip.setAutoThreshold(method, darkBackground)
var rois = IJProcessing.thresholdToSplitRois(ip, IJProcessing.CONNECTIVITY_4)
var overlay = IJProcessing.createOverlay(rois)
imp.setOverlay(overlay)