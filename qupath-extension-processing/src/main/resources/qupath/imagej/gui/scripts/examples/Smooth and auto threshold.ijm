/*
 * Macro to apply a Gaussian filter and then an automated threshold to
 * detect a single region.
 * You will need to return the active Roi to see the results in QuPath.
 */

// Define method (e.g. "Triangle", "Otsu"...)
method = "Otsu";

// Check if the image has a property specifying a dark background
// Override this by setting the value to true or false
if (Property.get("qupath.image.background")=="dark")
    darkBackground = true;
else
    darkBackground = false;

// Define smoothing sigma for Gaussian filter
sigma = 1;

// Do smoothing
run("Gaussian Blur...", "sigma=" + sigma);

// Ensure 8-bit grayscale
resetMinAndMax();
run("8-bit");

// Create Roi from threshold
if (darkBackground)
    setAutoThreshold(method + " dark");
else
    setAutoThreshold(method);
run("Create Selection");
