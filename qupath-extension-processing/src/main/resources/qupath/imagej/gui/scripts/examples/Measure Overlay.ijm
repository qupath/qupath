/*
 * ImageJ macro to add all available ImageJ measurements to all Rois on the current Overlay.
 * If this Overlay is then send back to QuPath, the measurements will be added to the original objects.
 *
 * WARNING! Cell objects can be troublesome because they can have two different ImageJ Rois.
 * Here, we assume that if 'nucleus' is in the Roi name then we should add 'Nucleus' to measurement
 * names - but this may not be a very robust strategy (e.g. if 'Nucleus' appears in other object names).
 */

// Make all measurements for the overlay
overlaySize = Overlay.size;
for (o = 0; o < overlaySize; o++) {
    Overlay.activateSelection(o);
    List.setMeasurements;
    size = List.size;
    List.toArrays(keys, values);
    prefix = "";
    name = Roi.getName();
    if (name.contains("-nucleus")) {
        prefix = "Nucleus_";
    }
    // Add measurements as properties
    for (i = 0; i < size; i++) {
        // Spaces not allowed in the name
        // Underscores will be replaced by spaces when passing measurements to QuPath
        Roi.setProperty("qupath.object.measurements.ImageJ_" + prefix + keys[i], values[i]);
    }
}