/*
 * Example script to output the number of objects of different types,
 * identified using two different methods.
 */

import static qupath.lib.scripting.QP.*;

// Method #1: Use static helper methods from QP class

// Output counts for the main object types
println("Counts for main object types:")
if (getTMACoreList() != null)
    println("  Number of TMA cores:\t" + getTMACoreList().size())
println("  Number of annotations:\t" + getAnnotationObjects().size())
println("  Number of detections:\t" + getDetectionObjects().size())

//----------------------------------------
// Method #2: Loop through all objects & count

// Get a reference to the current object hierarchy
def hierarchy = getCurrentHierarchy();

// Create a map, linking Java classes for each object with counts
def countMap = [:]
for (pathObject in hierarchy.getFlattenedObjectList(null)) {
    // Note: getClass() returns the Java class, *not* the PathClass (i.e. classification)
	def cls = pathObject.getClass()
    def count = countMap.get(cls)
    if (count == null)
        countMap.put(cls, 1)
    else
        countMap.put(cls, count + 1)
}

// Print the map
println("");
println("Output of count map:");
for (entry in countMap.entrySet()) {
    def output = sprintf("  %s:\t%s", entry.getKey(), entry.getValue());
    println(output);
}