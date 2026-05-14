/*
 * ImageJ macro to measure circularity for a Roi sent from QuPath.
 * If the same Roi is then send back to QuPath, the measurements will be added to
 * the original object.
 */

// Measure circularity
circularity = getValue("Circ.");

// Add to the ImageJ Roi
Roi.setProperty("qupath.object.measurements.Circularity", circularity);