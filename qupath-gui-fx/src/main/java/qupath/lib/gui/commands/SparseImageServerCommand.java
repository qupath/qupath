package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.servers.CroppedImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.SparseImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.RectangleROI;

/**
 * Command to generate a {@link SparseImageServer} from multiple image regions across a project.
 * This can be useful as a training image for a pixel classifier, for example.
 * 
 * @author Pete Bankhead
 */
public class SparseImageServerCommand implements PathCommand {
	
	private static Logger logger = LoggerFactory.getLogger(SparseImageServerCommand.class);
	
	private static String NAME = "Create training image";

	private QuPathGUI qupath;
	
	private PathClass pathClass = PathClassFactory.getPathClass(StandardPathClasses.REGION);
	private int maxWidth = 50000;
	private boolean doZ = false;
	private boolean rectanglesOnly = false;

	public SparseImageServerCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var project = qupath.getProject();
		if (project == null) {
			DisplayHelpers.showErrorMessage(NAME, "You need a project!");
			return;
		}
		List<PathClass> pathClasses = qupath.getAvailablePathClasses();
		if (pathClasses.isEmpty()) {
			DisplayHelpers.showErrorMessage(NAME, "Please ensure classifications are available in QuPath!");
			return;			
		}
		
		if (!pathClasses.contains(pathClass))
			pathClass = pathClasses.get(0);
		
		var params = new ParameterList()
				.addEmptyParameter("Generates a single image from regions extracted from the project.")
				.addEmptyParameter("Before running this command, add classified rectangle annotations to select the regions.")
				.addChoiceParameter("pathClass", "Classification", pathClass, pathClasses, "Select classification for annotated regions")
				.addIntParameter("maxWidth", "Preferred image width", maxWidth, "px", "Preferred maximum width of the training image, in pixels")
				.addBooleanParameter("doZ", "Do z-stacks", doZ, "Take all slices of a z-stack, where possible")
				.addBooleanParameter("rectanglesOnly", "Rectangles only", rectanglesOnly, 
						"Only extract regions annotated with rectangles. Otherwise, the bounding box of all regions with the classification will be taken.")
				.addEmptyParameter("Note this command requires images to have similar bit-depths/channels/pixel sizes for compatibility.")
				;
		
		if (!DisplayHelpers.showParameterDialog(NAME, params))
			return;
		
		pathClass = (PathClass)params.getChoiceParameterValue("pathClass");
		maxWidth = params.getIntParameterValue("maxWidth");
		doZ = params.getBooleanParameterValue("doZ");
		rectanglesOnly = params.getBooleanParameterValue("rectanglesOnly");

		var task = new Task<SparseImageServer>() {

			@Override
			protected SparseImageServer call() throws Exception {
				return createSparseServer(project, pathClass, maxWidth, doZ, rectanglesOnly);
			}
		};
		
		var dialog = new ProgressDialog(task);
		dialog.setTitle(NAME);
		dialog.setHeaderText("Creating training image...");
				
		Executors.newSingleThreadExecutor().submit(task);
		
		dialog.showAndWait();
		
		try {
			var server = task.get();
//			var server = createSparseServer(project, pathClass, maxWidth, doZ, rectanglesOnly);
			if (server == null || server.getManager().getRegions().isEmpty()) {
				DisplayHelpers.showErrorMessage("Sparse image server", "No suitable annotations found in the current project!");
				return;			
			}
			
			var entry = ProjectImportImagesCommand.addSingleImageToProject(project, server, null);
			server.close();
			
			qupath.refreshProject();
			if (entry != null) {
				project.syncChanges();
				qupath.openImageEntry(entry);
			}
			
		} catch (Exception e) {
			DisplayHelpers.showErrorMessage("Sparse image server", e);
		}
	}

	static Predicate<PathObject> createPredicate(PathClass pathClass, boolean rectanglesOnly) {
		if (rectanglesOnly)
			return (PathObject p) -> p.isAnnotation() && p.getPathClass() == pathClass && p.getROI() instanceof RectangleROI;
		else
			return (PathObject p) -> p.isAnnotation() && p.getPathClass() == pathClass;
	}
	
	
	static SparseImageServer createSparseServer(Project<BufferedImage> project, PathClass pathClass, int maxX, boolean doZ, boolean rectanglesOnly) throws IOException {
		return createSparseServer(project.getImageList(), createPredicate(pathClass, rectanglesOnly), maxX, doZ);
	}
	
	/**
	 * Create a {@link SparseImageServer} from a collection of images.
	 * @param entries project entries that may be added to the image
	 * @param predicate predicate used to select annotated regions
	 * @param maxWidth the preferred width of the generated image, in pixels (used to determine whether to append a region horizontally or vertically)
	 * @param doZ if true, take all slices of a z-stack for each region where available; if false, take the region and create a 2D image
	 * @return
	 * @throws IOException
	 */
	static SparseImageServer createSparseServer(Collection<ProjectImageEntry<BufferedImage>> entries, Predicate<PathObject> predicate, int maxWidth, boolean doZ) throws IOException {

		var builder = new SparseImageServer.Builder();
		
		boolean doT = false;

		int pad = 0;
		int x = 0;
		int y = 0;
		int rowHeight = 0;

		ImageServerMetadata firstMetadata = null;

		int n = 0;
		for (var entry : entries) {
			if (!entry.hasImageData())
				continue;
			var hierarchy = entry.readHierarchy();
			if (hierarchy == null)
				continue;
			var annotations = hierarchy.getAnnotationObjects();
			if (annotations.isEmpty())
				continue;
			try {
				ImageServer<BufferedImage> server = null;
				for (var annotation : annotations) {
					if (!predicate.test(annotation))
						continue;
					
					var roi = annotation.getROI();
					var region = ImageRegion.createInstance(roi);
					
					if (server == null) {
						server = entry.getServerBuilder().build();
						if (firstMetadata == null)
							firstMetadata = server.getMetadata();
						else {
							if (firstMetadata.getPixelType() != server.getPixelType()) {
								logger.warn("Incompatible pixel types {} and {} - will skip regions from {}",
										server.getPixelType(), firstMetadata.getPixelType(), entry.getImageName());
								break;
							}
							if (firstMetadata.getSizeC() != server.nChannels()) {
								logger.warn("Incompatible channel counts {} and {} - will skip regions from {}",
										server.nChannels(), firstMetadata.getSizeC() , entry.getImageName());
								break;
							}
						}
					}
					var croppedServer = new CroppedImageServer(server, region);
					
					int[] zArray = doZ ? IntStream.range(0, croppedServer.nZSlices()).toArray() : new int[] {0};
					int[] tArray = doT ? IntStream.range(0, croppedServer.nTimepoints()).toArray() : new int[] {0};
					
					for (int t : tArray) {
						for (int z : zArray) {
							rowHeight = Math.max(region.getHeight(), rowHeight);
							var regionOutput = ImageRegion.createInstance(x, y, region.getWidth(), region.getHeight(), z, t);
							for (double downsample : croppedServer.getPreferredDownsamples()) {
								builder.serverRegion(regionOutput, downsample, croppedServer);
								n++;
							}
						}
					}
	
					// Increment x
					x += region.getWidth() + pad;
	
					// Move to next row
					if (x >= maxWidth) {
						y += rowHeight + pad;
						rowHeight = 0;
						x = 0;
					}
				}
			} catch (Exception e) {
				logger.warn("Exception trying to read {}: {}", entry.getImageName(), e.getLocalizedMessage());
			}
		}
		if (n == 0) {
			return null;
		} else
			return builder.build();

	}


}
