/*
 * Classify all detections as positive/negative by thresholding on a specified feature value.
 */

import qupath.lib.objects.classes.PathClass
import qupath.lib.objects.classes.PathClassFactory
import qupath.lib.scripting.QP

// Parameters to modify
List<String> includeClassesWithName = ["Tumor", "Stroma"] as List<String>
def feature = "DAB Mean"
def threshold = 0.1

// Check what base classifications we should be worried about
// It's possible to specify 'All', or select specific classes and exclude others
def doAll = includeClassesWithName.contains("All")
def includeClasses = []
if (!doAll) {
    for (String n : includeClassesWithName)
        includeClasses.add(PathClassFactory.getPathClass(n))
}

// Loop through all detections
for (def pathObject : QP.getDetectionObjects()) {

    // Get the base classification
    PathClass baseClass = pathObject.getPathClass()
    if (baseClass != null)
        baseClass = baseClass.getBaseClass()
    else if (PathClassFactory.isPositiveClass(baseClass) || PathClassFactory.isNegativeClass(baseClass))
        // In the event that we have a positive or negative classification that lacks a base class,
        // this implies that the base class should be null
        baseClass = null;

    // Apply classification, if required
    if (doAll || includeClasses.contains(baseClass)) {

        // Check if we have a measurement - if not, assign the base class
        double val = pathObject.getMeasurementList().getMeasurementValue(feature)
        if (Double.isNaN(val)) {
            pathObject.setPathClass(baseClass)
            continue
        }

        // Set positive or negative class
        if (val >= threshold)
            pathObject.setPathClass(PathClassFactory.getPositive(baseClass, null))
        else
            pathObject.setPathClass(PathClassFactory.getNegative(baseClass, null))

    }


}

// Fire update event
QP.getCurrentHierarchy().fireHierarchyChangedEvent(this)

// Make sure we know we're done
println("Done!")