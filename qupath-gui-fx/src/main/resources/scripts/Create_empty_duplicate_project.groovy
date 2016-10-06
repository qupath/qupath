/*
 * Duplicate an existing project, putting the new files into a subdirectory of the current project.
 *
 * All image analysis is reset, *except* for any TMA cores.  These are kept, along with any metadata.
 *
 * This is particularly useful for generating a new TMA project with the same dearrayed cores & metadata,
 * but otherwise everything kept the same.
 */

import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.panels.ProjectBrowser
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageServerProvider
import qupath.lib.io.PathIO
import qupath.lib.projects.Project
import qupath.lib.projects.ProjectIO

import java.awt.image.BufferedImage

// Specify the directory of the duplicated project
// If this doesn't contain any file separators, it is used as a subdirectory of the current project
def newProjectDirectory = "duplicated project"

// Get the running QuPath instance
def qupath = QuPathGUI.getInstance()

// Get the current project
def project = qupath.getProject()
if (project == null) {
    println("No project open!")
    return
}

// Determine output directory
File dirNewProject;
if (newProjectDirectory.contains(File.separator))
    dirNewProject = new File(newProjectDirectory)
else
    dirNewProject = new File(project.getBaseDirectory(), newProjectDirectory)

// Check for the existence of the new directory - don't do anything if it exists!
if (dirNewProject.exists()) {
    println("Directory already exists! " + dirNewProject.getAbsolutePath())
    println("Please specify another directory, or delete the specified directory to reuse")
    return
}
println("New project directory: " + dirNewProject.getAbsolutePath())

// Create a new project & data directory
def projectNew = new Project<BufferedImage>(new File(dirNewProject, "project.qpproj"), BufferedImage.class)

// Ensure we have all the directories we need
dirNewProject.mkdirs()

// Check for a duplicated project directory
for (def entry in project.getImageList()) {

    // Add entry to the new project
    projectNew.addImage(entry)

    // Get the current data file
    File fileData = ProjectBrowser.getImageDataPath(project, entry)
    if (fileData == null)
        continue

    // Read the hierarchy
    def hierarchy = PathIO.readHierarchy(fileData)
    if (hierarchy == null) {
        println("No hierarchy found for " + entry.getImageName())
        continue
    }

    // If we have no TMA cores, nothing to do
    if (hierarchy.getTMAGrid() == null)
        continue

    // Remove all child objects from cores
    for (def core in hierarchy.getTMAGrid().getTMACoreList())
        core.clearPathObjects()

    // If we *do* have TMA cores, then retain these
    def imageData = new ImageData<BufferedImage>(ImageServerProvider.buildServer(entry.getServerPath(), BufferedImage.class))
    imageData.getHierarchy().setTMAGrid(hierarchy.getTMAGrid())

    // Save into new data directory
    File fileDataNew = ProjectBrowser.getImageDataPath(projectNew, entry)
    if (!fileDataNew.getParentFile().exists())
        fileDataNew.getParentFile().mkdir()
    PathIO.writeImageData(fileDataNew, imageData)

}

// Write the new project itself
ProjectIO.writeProject(projectNew)

print("Done! Project written to " + projectNew.getFile().getAbsolutePath())