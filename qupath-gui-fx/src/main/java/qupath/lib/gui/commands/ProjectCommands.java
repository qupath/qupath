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
	
	public static List<ProjectImageEntry<BufferedImage>> promptToImportImages(QuPathGUI qupath, String... defaultPaths) {
		return ProjectImportImagesCommand.promptToImportImages(qupath);
	}
	
	public static ProjectImageEntry<BufferedImage> addSingleImageToProject(Project<BufferedImage> project, ImageServer<BufferedImage> server, ImageType type) {
		return ProjectImportImagesCommand.addSingleImageToProject(project, server, type);
	}
	
	public static BufferedImage getThumbnailRGB(ImageServer<BufferedImage> server) throws IOException {
		return ProjectImportImagesCommand.getThumbnailRGB(server, null);
	}
	
	public static void showProjectMetadataEditor(QuPathGUI qupath) {
		ProjectMetadataEditorCommand.showProjectMetadataEditor(qupath);
	}
	
	
	public static void promptToExportImageList(Project<?> project) {
		var title = "Export image list";
		if (project == null) {
			Dialogs.showNoProjectError(title);
			return;
		}
		// Try to get a project directory
		File dirBase = Projects.getBaseDirectory(project);
		
		// Prompt for where to save
		File fileOutput = QuPathGUI.getSharedDialogHelper().promptToSaveFile(title, dirBase, null, "Text files", ".txt");
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
