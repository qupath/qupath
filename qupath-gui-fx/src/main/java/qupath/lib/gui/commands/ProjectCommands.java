package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
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
	 * Show prompt for the user to select images to import into the current project in QuPath.
	 * @param qupath the QuPath instance
	 * @param defaultPaths image paths to include when the dialog is shown (useful when the dialog is shown with some paths already known)
	 * @return a list of project entries for all images that were successfully added to the project
	 */
	public static List<ProjectImageEntry<BufferedImage>> promptToImportImages(QuPathGUI qupath, String... defaultPaths) {
		return ProjectImportImagesCommand.promptToImportImages(qupath, defaultPaths);
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

}
