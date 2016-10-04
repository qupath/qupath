import qupath.lib.classifiers.PathClassificationLabellingHelper
import qupath.lib.scripting.QP

/*
 * Remove specified measurements from detection objects.
 */

enum SearchType {STARTS_WITH, ENDS_WITH, CONTAINS, EQUALS}

String searchText = "Delaunay";
SearchType searchType = SearchType.STARTS_WITH;
boolean ignoreCase = true;

// Determine *all* the measurement names first - faster than checking all measurement lists individually
def detections = QP.getDetectionObjects()
def measurementNames = PathClassificationLabellingHelper.getAvailableFeatures(detections)

// Create a filter to find what will need to be removed
if (ignoreCase)
    searchText = searchText.toLowerCase()

def filter = {String m ->
    if (ignoreCase)
        m = m.toLowerCase()
    switch (searchType) {
        case SearchType.STARTS_WITH:
            return m.startsWith(searchText)
        case SearchType.ENDS_WITH:
            return m.endsWith(searchText)
        case SearchType.CONTAINS:
            return m.contains(searchText)
        case SearchType.EQUALS:
            return m.equals(searchText)
        default:
            return true // Accept otherwise?  Shouldn't happen...
    }
}

// Identify measurements to remove
String[] measurementsToRemove = measurementNames.stream().filter(filter).toArray({n -> new String[n]})

// Check if we've anything to do
if (measurementsToRemove.length == 0) {
    println("No measurements to remove!")
    return
}

// Print what we will remove
println("Will remove measurements: " + String.join(", ", measurementsToRemove))

// Loop through objects and filter measurements accordingly
for (def pathObject in QP.getDetectionObjects()) {
    def ml = pathObject.getMeasurementList()
    ml.removeMeasurements(measurementsToRemove)
    ml.closeList()
}

// Fire update event
QP.getCurrentHierarchy().fireHierarchyChangedEvent(this)
println("Done!")