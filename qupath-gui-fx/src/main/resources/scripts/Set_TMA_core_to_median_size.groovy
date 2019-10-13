/*
 * A common (albeit minor) annoyance occurs whenever accidentally changing the size of a TMA core,
 * and being unable to resize it back to exactly the same size as the other cores.
 *
 * This script addresses this by resizing a selected core to have the median width & height of
 * all the cores (which is assumed to be the 'correct' size for the slide).
 *
 */


import qupath.lib.objects.TMACoreObject
import qupath.lib.roi.EllipseROI
import qupath.lib.roi.ROIs

import static qupath.lib.scripting.QP.*

// Get the currently-selected object
def core = getSelectedObject();

// Get the TMA core list
def cores = getTMACoreList()
if (cores.isEmpty()) {
    println("No TMA cores found!")
    return
}

// Check we have a TMA core
if (!(core instanceof TMACoreObject)) {
    println("Please select a TMA core to resize first!")
    return
}

// Calculate the widths & heights of each TMA core area
// (This will fail if somehow a core doesn't have a ROI... but presumably that doesn't happen)
double[] widths = cores.stream().mapToDouble({c -> (double)c.getROI().getBoundsWidth()}).toArray()
double[] heights = cores.stream().mapToDouble({c -> (double)c.getROI().getBoundsHeight()}).toArray()

// Here, I don't care quite enough to deal explicitly with even numbers of cores
int n = widths.length/2;
double medianWidth = widths[n]
double medianHeight = heights[n]

// Set the core ROI
double cx = core.getROI().getCentroidX()
double cy = core.getROI().getCentroidY()
def roi = ROIs.createEllipseROI(cx-medianWidth/2, cy-medianHeight/2, medianWidth, medianHeight,
        core.getROI().getImagePlane());
core.setROI(roi);

// Update the hierarchy
getCurrentHierarchy().fireHierarchyChangedEvent(this);

// Print something
print(sprintf("Resized %s to %.2f x %.2f pixels", core.toString(), medianWidth , medianHeight))