/*
 * ImageJ macro to add all available ImageJ measurements to all Rois on the current Overlay.
 * If this Overlay is then send back to QuPath, the measurements will be added to the original objects.
 */

// Make all measurements for the overlay
overlaySize = Overlay.size;
for (o = 0; o < overlaySize; o++) {
    Overlay.activateSelection(o);
    List.setMeasurements;
    size = List.size;
    List.toArrays(keys, values);
    // Add measurements as properties
    for (i = 0; i < size; i++) {
        // Spaces not allowed in the name
        // Underscores will be replaced by spaces when passing measurements to QuPath
        Roi.setProperty("qupath.object.measurements.ImageJ_" + keys[i], values[i]);
    }
}