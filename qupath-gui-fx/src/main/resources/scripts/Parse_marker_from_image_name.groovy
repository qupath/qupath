/*
 * Script to try to parse sample metadata from an image name.
 *
 * Here, it attempts to find a biomarker name and a scanner type.
 *
 * The code could be modified to parse different kinds of metadata.
 *
 * Note: by default, the code doesn't actually change the project -
 * if you want it to actually set the metadata values, it is necessary
 * to set dryRun = false (line 18)
 */


import qupath.lib.projects.ProjectIO
import qupath.lib.scripting.QPEx

// Some boolean variables to consider
def dryRun = true // If true, just output the changes that would be made - but don't apply them
def doScanner = true // If true, look at the file extension for a scanner name
def doMarker = true // If true, look at the image name contents to try to find a biomarker name

// Get current project
def project = QPEx.getProject()
if (project == null) {
    println("No project open!")
    return
}

// Some abbreviated biomarker names that might be of interest
// It's a good idea to rank in order with those likely to occur in 'normal' filenames
// (e.g. ER) at the bottom
// Since the comparison will be made lower case, these should all be given lower-case
def markers = [:]
markers["HER2"] = ["her2", "her-2"]
markers["Ki67"] = ["ki67", "ki-67", "mib1", "mib-1"]
markers["PD-L1"] = ["pdl1", "pd-l1", "pdl-1"]
markers["PD-1"] = ["pd1"]
markers["p53"] = ["p53"]
markers["p63"] = ["p63"]
markers["CD45"] = ["cd45"]
markers["CD3"] = ["cd3"]
markers["CD8"] = ["cd8"]
markers["CD4"] = ["cd4"]
markers["CK5-6"] = ["ck5-6", "ck 5-6"]
markers["CK14"] = ["ck14"]
markers["ER"] = ["er"]
markers["PR"] = ["pr", "pgr"]
markers["H&E"] = ["h&e", "h & e"]

// Some file formats associated with different scanners
// (See http://openslide.org for more info)
def scanners = [:]
scanners["Aperio"] = [".svs"]
scanners["Hamamastu"] = [".ndpi"]
scanners["TIFF"] = [".tiff", "tif"] // Not really a scanner...
scanners["Leica"] = [".scn"]
scanners["MIRAX"] = [".mrxs"]
scanners["Ventana"] = [".bif"]


// Loop through entries, setting metadata if needed
// Also, count the values for each
def markerCount = [:]
def scannerCount = [:]
for (def entry in project.getImageList()) {
    // Get lower case name
    def imageName = entry.getImageName()
    def name = imageName.toLowerCase()

    // Create a new metadata map
    def metadata = [:]

    // Check file extension for scanner
    if (doScanner) {
        scannerLoop:
        for (def scannerEntry in scanners.entrySet()) {
            for (def ext in scannerEntry.getValue()) {
                if (name.endsWith(ext)) {
                    def scannerName = scannerEntry.getKey()
                    metadata["Scanner"] = scannerName
                    scannerCount[scannerName] =  scannerCount.getOrDefault(scannerName, 0) + 1
                    break scannerLoop
                }
            }
        }
    }

    // Check name for biomarker
    if (doMarker) {
        markerLoop:
        for (def markerEntry in markers.entrySet()) {
            for (def markerAbbreviation in markerEntry.getValue()) {
                if (name.contains(markerAbbreviation)) {
                    def markerName = markerEntry.getKey()
                    metadata["Marker"] = markerName
                    markerCount[markerName] =  markerCount.getOrDefault(markerName, 0) + 1
                    break markerLoop
                }
            }
        }
    }

    // Print the changes that will be made
    if (metadata.isEmpty()) {
        print("No changes for " + imageName)
        continue
    }
    print("Parsed metadata for " + imageName + ": " + metadata)

    // Actually set the metadata values
    if (!dryRun) {
        for (def metadataEntry in metadata.entrySet())
            entry.putMetadataValue(metadataEntry.getKey(), metadataEntry.getValue())
    }

}

// Print out a summary
if (!scannerCount.isEmpty())
    print("Scanners: " + scannerCount.toMapString())
if (!markerCount.isEmpty())
    print("Markers: " + markerCount.toMapString())

// If we're doing this seriously, we need to save the project
if (!dryRun) {
    ProjectIO.writeProject(project)
    print("Project file updated!")
}
else
    print("No changes made to project! Run again with dryRun = false to make changes")