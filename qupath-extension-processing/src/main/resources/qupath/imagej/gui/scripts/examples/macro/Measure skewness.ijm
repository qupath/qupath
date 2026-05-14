/*
 * ImageJ macro to measure skewness for all channels of an image.
 * If this Roi is then send back to QuPath, the measurements will be added to
 * the original object.
 */

// Check we have a Roi
if (selectionType < 0) {
    exit;
}

// Get dimensions of the image
getDimensions(width, height, channels, slices, frames);

// Loop through all channels
for (c = 1; c <= channels; c++) {
    // Update the active active
    Stack.setChannel(c);
    // Spaces not allowed in the name
    // Underscores will be replaced by spaces when passing measurements to QuPath
    slice = replace(Property.getSliceLabel(), " ", "_");
    // Measure skewness
    skewness = getValue("Skew");
    // Add to the ImageJ Roi
    Roi.setProperty("qupath.object.measurements.Skewness_" + slice, skewness);
}
