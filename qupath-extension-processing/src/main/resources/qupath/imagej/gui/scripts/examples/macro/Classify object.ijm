/*
 * ImageJ macro showing how a QuPath object can be classified.
 * You will need to send the current object as a Roi, and also
 * return the active Roi to see the results in QuPath.
 *
 * Note: for cells you should *not* return the overlay, unless you
 * set the new classification for both the cell boundary and nucleus ROIs.
 */

// Check we have a Roi
if (selectionType < 0) {
    exit;
}

// Remove the Roi to smooth & set a threshold
run("Select None");
setAutoThreshold("Triangle dark");

// The original Roi should be the first in the overlay
// We can't use "Restore Selection" if we have multiple threads
Overlay.activateSelection(0);

// Restore the Roi and get the percentage pixels > threshold
percentageArea = getValue("%Area");

// Threshold using a minimum percentage of pixels
if (percentageArea >= 50) {
    Roi.setProperty("qupath.object.classification", "Positive");
} else {
    Roi.setProperty("qupath.object.classification", "Negative");
}
