/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

/**
 * Helper class implementing simple 'single-method' commands related to projects.
 * 
 * @author Pete Bankhead
 *
 */
public class ProjectCommands {
	
	private final static Logger logger = LoggerFactory.getLogger(ProjectCommands.class);

	/**
	 * Check the URIs within a project, prompting the user to correct any broken links if required.
	 * @param project the project containing URIs to check
	 * @param onlyIfMissing if true, only display a prompt if some links are broken
	 * @return true if the dialog was shown and closed successfully, false if it was cancelled
	 * @throws IOException
	 */
	public static boolean promptToCheckURIs(Project<?> project, boolean onlyIfMissing) throws IOException {
		var manager = new ProjectCheckUris.ProjectUriManager(project);
		if (!onlyIfMissing || manager.countOriginalItems(ProjectCheckUris.UriStatus.MISSING) > 0) {
			return manager.showDialog();
		}
		return true;
	}
	
	/**
	 * Show prompt for the user to select images to import into the current project in QuPath, choosing a suitable {@link ImageServerBuilder}.
	 * @param qupath the QuPath instance
	 * @param defaultPaths image paths to include when the dialog is shown (useful when the dialog is shown with some paths already known)
	 * @return a list of project entries for all images that were successfully added to the project
	 */
	public static List<ProjectImageEntry<BufferedImage>> promptToImportImages(QuPathGUI qupath, String... defaultPaths) {
		return promptToImportImages(qupath, null, defaultPaths);
	}
	
	/**
	 * Show prompt for the user to select images to import into the current project in QuPath.
	 * @param qupath the QuPath instance
	 * @param builder if not null, this will be used to create the servers. If null, a combobox will be shown to choose an installed builder.
	 * @param defaultPaths image paths to include when the dialog is shown (useful when the dialog is shown with some paths already known)
	 * @return a list of project entries for all images that were successfully added to the project
	 */
	public static List<ProjectImageEntry<BufferedImage>> promptToImportImages(QuPathGUI qupath, ImageServerBuilder<BufferedImage> builder, String... defaultPaths) {
		return ProjectImportImagesCommand.promptToImportImages(qupath, builder, defaultPaths);
	}
	
	/**
	 * Add a single image to a project.
	 * @param project the project
	 * @param server the image to add
	 * @param type optional image type, if known
	 * @return an entry corresponding to the image that was added
	 */
	public static ProjectImageEntry<BufferedImage> addSingleImageToProject(Project<BufferedImage> project, ImageServer<BufferedImage> server, ImageType type) {
		return ProjectImportImagesCommand.addSingleImageToProject(project, server, type);
	}
	
	/**
	 * Get an RGB thumbnail for an image server, suitable for showing as a project thumbnail.
	 * @param server
	 * @return an RGB thumbnail for server
	 * @throws IOException
	 */
	public static BufferedImage getThumbnailRGB(ImageServer<BufferedImage> server) throws IOException {
		return ProjectImportImagesCommand.getThumbnailRGB(server, null);
	}
	
	/**
	 * Show the metadata editor for the specified project.
	 * @param project
	 */
	public static void showProjectMetadataEditor(Project<?> project) {
		ProjectMetadataEditorCommand.showProjectMetadataEditor(project);
	}
	
	/**
	 * Prompt to export a text file containing a list of image paths for a project.
	 * @param project
	 */
	public static void promptToExportImageList(Project<?> project) {
		var title = "Export image list";
		if (project == null) {
			Dialogs.showNoProjectError(title);
			return;
		}
		// Try to get a project directory
		File dirBase = Projects.getBaseDirectory(project);
		
		// Prompt for where to save
		File fileOutput = Dialogs.promptToSaveFile(title, dirBase, null, "Text files", ".txt");
		if (fileOutput == null)
			return;
		
		// Write out image paths, along with metadata values
		Set<String> keys = new TreeSet<>();
		for (ProjectImageEntry<?> entry : project.getImageList()) {
			keys.addAll(entry.getMetadataKeys());
		}
		
		String delim = PathPrefs.tableDelimiterProperty().get();
		long startTime = System.currentTimeMillis();
		int n = 0;
		try (PrintWriter writer = new PrintWriter(fileOutput, StandardCharsets.UTF_8)) {
			writer.print("Name");
			writer.print(delim);
			writer.print("ID");
			writer.print(delim);
			writer.print("URIs");
			for (String key : keys) {
				writer.print(delim);
				writer.print(key);
			}
			writer.println();
			
			for (ProjectImageEntry<?> entry : project.getImageList()) {
				try {
					Collection<URI> uris = entry.getServerURIs();
					String path = String.join(" ", uris.stream().map(u -> u.toString()).collect(Collectors.toList()));
	//				String path = entry.getServerPath();
					writer.print(entry.getImageName());
					writer.print(delim);				
					writer.print(entry.getID());
					writer.print(delim);				
					writer.print(path);
					for (String key : keys) {
						writer.print(delim);
						String value = entry.getMetadataValue(key);
						if (value != null)
							writer.print(value);
					}
					writer.println();
					n++;
					logger.debug(path);
				} catch (IOException e) {
					logger.error("Error reading URIs from " + entry, e);
				}
			}		
		} catch (IOException e) {
			Dialogs.showErrorMessage(title, fileOutput.getAbsolutePath() + " not found!");
		}
		long endTime = System.currentTimeMillis();
		logger.debug("Exported {} images in {} ms", n, endTime - startTime);
		
	}

	
	/**
	 * Prompt the user to select a legacy project file, and then import the images into 
	 * the current project.
	 * <p>
	 * In this case 'legacy' means QuPath v0.1.2 or earlier.
	 * Such projects contain a "data" subdirectory, containing data files in the form "name.qpdata".
	 * <p>
	 * Note that the paths to the images need to be correct, because it is necessary to 
	 * open each image during import.
	 * 
	 * @param qupath the current instance of QuPath
	 * @return true if changes were made to the current project, false otherwise
	 */
	public static boolean promptToImportLegacyProject(QuPathGUI qupath) {
		var project = qupath.getProject();
		String title = "Import legacy project";
		if (project == null) {
			Dialogs.showNoProjectError(title);
			return false;
		}
		
		// Prompt for the old project
		var file = Dialogs.promptForFile(title, null, "Project (v0.1.2)", ".qpproj");
		if (file == null)
		    return false;
		
		
		// Read the entries
		LegacyProject oldProject;
		try (var reader = new FileReader(file)) {
			oldProject = GsonTools.getInstance().fromJson(reader, LegacyProject.class);			
		} catch (IOException e) {
			logger.error("Error reading file: " + e.getLocalizedMessage(), e);
			return false;
		}
		if (oldProject.getEntries().isEmpty()) {
			logger.warn("No images found in project {}", file.getAbsolutePath());
			return false;
		}
		
		var dirData = new File(file.getParent(), "data");
		if (!dirData.exists()) {
		    Dialogs.showErrorMessage(title, "No data directory found for the legacy project!");
		    return false;
		}
		
		var task = new LegacyProjectTask(project, oldProject.getEntries(), file.getParentFile());
		int nImages = oldProject.getEntries().size();
		var dialog = new ProgressDialog(task);
		dialog.setTitle(title);
		if (nImages == 1)
			dialog.setContentText("Importing 1 image...");
		else
			dialog.setContentText("Importing " + nImages + " images...");

		qupath.submitShortTask(task);
		dialog.showAndWait();
		Integer nCompleted = task.getValue();
		if (nCompleted == null)
			nCompleted = 0;
		if (nCompleted < nImages)
			Dialogs.showWarningNotification(title, nCompleted + "/" + nImages + " imported successfully");
		
		try {
			project.syncChanges();
		} catch (Exception e) {
			logger.error("Error syncing project: " + e.getLocalizedMessage(), e);
		}
		qupath.refreshProject();
		return true;
	}
	
	
	
	private static class LegacyProjectTask extends Task<Integer> {
		
		private Project<BufferedImage> project;
		
		private File dirLegacy;
		private List<LegacyProjectEntry> legacyEntries;
		
		LegacyProjectTask(Project<BufferedImage> project, List<LegacyProjectEntry> legacyEntries, File dirLegacy) {
			this.project = project;
			this.legacyEntries = legacyEntries;
			this.dirLegacy = dirLegacy;
		}

		@Override
		protected Integer call() throws Exception {
			var dirThumbnails = new File(dirLegacy, "thumbnails");
			var dirData = new File(dirLegacy, "data");
			int count = 0;
			int max = legacyEntries.size();
			List<LegacyProjectEntry> failed = new ArrayList<>();
			for (var oldEntry : legacyEntries) {
			    try {
			        // Check for a data file
			        var name = oldEntry.getName();
			        var fileData = new File(dirData, name + ".qpdata");
			        var split = oldEntry.getPath().split("::");
			        String path = split[0];
			        ImageServer<BufferedImage> server;
			        if (split.length > 1)
			        	server = ImageServerProvider.buildServer(path, BufferedImage.class, "--name", split[1]);
			        else
			        	server = ImageServerProvider.buildServer(path, BufferedImage.class);
			        var entry = project.addImage(server.getBuilder());
			        entry.setImageName(name);
			        // Save the data if needed
			        if (fileData.exists()) {
			            logger.debug("Reading image data found for {}", name);
			            var imageData = PathIO.readImageData(fileData, null, server, BufferedImage.class);
			            entry.saveImageData(imageData);
			        } else {
			            logger.warn("No image data found for {}", name);
			        }
			        // Read or generate a thumbnail
			        var fileThumbnail = new File(dirThumbnails, "thumbnails");
			        BufferedImage imgThumbnail = null;
			        if (fileThumbnail.exists())
			            imgThumbnail = ImageIO.read(fileThumbnail);
			        if (imgThumbnail == null)
			            imgThumbnail = ProjectCommands.getThumbnailRGB(server);
			        entry.setThumbnail(imgThumbnail);
			        count++;
			    } catch (Exception e) {
			    	failed.add(oldEntry);
			        logger.error("Error adding entry: {}", e.getLocalizedMessage());
			    } finally {
			        updateProgress(count, max);
			    }
			}
			if (!failed.isEmpty()) {
				logger.error("Unable to import the following image(s):\n  {}", 
						failed.stream().map(LegacyProjectEntry::toString).collect(Collectors.joining("\n  "))
						);
			}
			return count;
		}
		
	}
	
	
	private static class LegacyProject {

		private List<LegacyProjectEntry> images;
	    
	    List<LegacyProjectEntry> getEntries() {
	    	return images;
	    }

	}


	private class LegacyProjectEntry {

	    private String path;
	    private String name;
	    
	    String getPath() {
	    	return path;
	    }
	    
	    String getName() {
	    	return name;
	    }
	    
	    @Override
	    public String toString() {
	    	return name + " (" + path + ")";
	    }

	}
	
}