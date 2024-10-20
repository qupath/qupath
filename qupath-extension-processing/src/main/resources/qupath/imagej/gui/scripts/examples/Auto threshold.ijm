/*
 * ImageJ macro to apply an automated threshold to detect a single region.
 * You will need to return the active Roi to see the results in QuPath.
 */

// Define method (e.g. "Triangle", "Otsu"...)
method = "Triangle";

// Check if the image has a property specifying a dark background
// Override this by setting the value to true or false
if (Property.get("qupath.image.background")=="dark")
    darkBackground = true;
else
    darkBackground = false;

// Ensure 8-bit grayscale
resetMinAndMax();
run("8-bit");

// Create Roi from threshold
if (darkBackground)
    setAutoThreshold(method + " dark");
else
    setAutoThreshold(method);
run("Create Selection");
