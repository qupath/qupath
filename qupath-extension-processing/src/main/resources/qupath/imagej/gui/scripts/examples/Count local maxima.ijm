/*
 * ImageJ macro to create a point selection using 'Find Maxima...'
 */

// Set the noise tolerance for maxima
prominence = 50

// Specify whether the background is light or dark
lightBackground = true

// Build the Find Maxima args string
args = "prominence=" + prominence;
if (lightBackground)
    args += " light";
args += " output=[Point Selection]";

// Find the local maxima
run("Find Maxima...", args);
