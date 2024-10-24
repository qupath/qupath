/*
 * ImageJ macro to create a point selection using 'Find Maxima...'
 */

// Set the noise tolerance for maxima
prominence = 50

// Check if the image has a property specifying a light background
// Override this by setting the value to true or false
if (Property.get("qupath.image.background")=="light")
    lightBackground = true;
else
    lightBackground = false;

// Build the Find Maxima args string
args = "prominence=" + prominence;
if (lightBackground)
    args += " light";
args += " output=[Point Selection]";

// Find the local maxima
run("Find Maxima...", args);
