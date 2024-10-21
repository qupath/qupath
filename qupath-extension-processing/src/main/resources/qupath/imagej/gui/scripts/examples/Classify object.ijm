/*
 * ImageJ macro showing how a QuPath object can be classified.
 * You will need to send the current object as a Roi, and also
 * return the active Roi to see the results in QuPath.
 */

// Check we have a Roi
if (selectionType < 0) {
    exit;
}

// Make all measurements for the Roi
getStatistics(area, mean, min, max, std, histogram);
if (max - mean > mean - min) {
    Roi.setProperty("qupath.object.classification", "dark");
} else {
    Roi.setProperty("qupath.object.classification", "light");
}