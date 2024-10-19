/*
 * ImageJ macro that sequentially applies several automatic thresholds
 * to an image, adding the results to an Overlay.
 *
 * You will need to create objects from the Overlay to return the results
 * to QuPath.
 */

// Choose threshold methods to apply
methods = newArray("IJ_IsoData", "Otsu", "Triangle");

// Uncomment the following line if you want to try *all* methods
// methods = getList("threshold.methods");

// Check if the image has a property specifying a dark background
// Override this by setting the value to true or false
if (Property.get("qupath.image.background")=="dark")
    darkBackground = true;
else
    darkBackground = false;

// Ensure we have an 8-bit image
run("8-bit");

// Loop through thresholds
for (i=0; i < methods.length; i++) {
    // Reset the Roi so it isn't influencing the threshold
    run("Select None");

    // Set the threshold
    method = methods[i];
    if (darkBackground) {
        setAutoThreshold(method + " dark");
    } else {
        setAutoThreshold(method);
    }
    // Create the Roi
    run("Create Selection");
    // Assign group (classification) & add Roi to the overlay
    if (selectionType != -1) {
        Roi.setGroup(i+1);
        Overlay.addSelection();
    } else {
        print("No Roi for " + method);
    }
}

// Set the Roi group names, which are used for classifications
Roi.setGroupNames(String.join(methods));

// Reset the Roi so the last Roi isn't returned twice
run("Select None");