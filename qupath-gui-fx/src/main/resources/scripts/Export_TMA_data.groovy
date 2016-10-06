/*
 * Script to export TMA data in a standardized way.
 *
 * Can be applied to a project.
 */

import qupath.lib.scripting.QPEx

// Downsample value for exported TMA cores
// If images shouldn't be included, set downsampleFactor = -1
double downsampleFactor = 4;

// Metadata value to use to set the directory name - or null, if no metadata value should be used
String metadataKey = "Marker"

// Get the metadata value, if required
String metadataValue = metadataKey == null || QPEx.getProjectEntry() == null ? null : QPEx.getProjectEntry().getMetadataValue(metadataKey)

// Build a suitable export path
String path = QPEx.PROJECT_BASE_DIR;
if (metadataValue == null)
    path = QPEx.buildFilePath(path, "results", metadataValue)
else
    path = QPEx.buildFilePath(path, "results", metadataValue)

// Ensure we have the directory
QPEx.mkdirs(path)

// Do the actual export
QPEx.exportTMAData(path, downsampleFactor)

// Print an update
println("Exported data to " + path)