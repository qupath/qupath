/*
 * Example script to output the number of detections with each classification.
 */

import static qupath.lib.scripting.QP.*;

// Create a map, linking PathClasses for each detections object with counts
def countMap = [:]
for (pathObject in getDetectionObjects()) {
    def pathClass = pathObject.getPathClass()
    def count = countMap.get(pathClass)
    if (count == null)
        countMap.put(pathClass, 1)
    else
        countMap.put(pathClass, count + 1)
}

// Print the map
println("Output of count map:");
for (entry in countMap.entrySet()) {
    def output = sprintf("  %s:\t%s", entry.getKey(), entry.getValue());
    println(output);
}