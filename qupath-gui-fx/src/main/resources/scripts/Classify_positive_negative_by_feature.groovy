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


// Loop through all detections
def doAll = includeClassesWithName.contains("All")
def includeClasses = []
if (!doAll) {
    for (String n : includeClassesWithName)
        includeClasses.add(PathClassFactory.getPathClass(n))
}
for (def pathObject : QP.getDetectionObjects()) {

    // Get the base classification
    PathClass baseClass = pathObject.getPathClass()
    if (baseClass != null)
        baseClass = baseClass.getBaseClass()

    if (doAll || includeClasses.contains(baseClass)) {

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



println("Done!")