/*
 * ImageJ macro to add all available ImageJ measurements to the current Roi.
 * If this Roi is then send back to QuPath, the measurements will be added to
 * the original object.
 */

// Check we have a Roi
if (selectionType < 0) {
    exit;
}

// Make all measurements for the Roi
List.setMeasurements;
size = List.size;
List.toArrays(keys, values);
for (i = 0; i < size; i++) {
    // Spaces not allowed in the name
    // Underscores will be replaced by spaces when passing measurements to QuPath
    Roi.setProperty("qupath.object.measurements.ImageJ_" + keys[i], values[i]);
}