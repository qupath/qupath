package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.ImageRegion;

public class SparseImageServerCommand implements PathCommand {
	
	private static Logger logger = LoggerFactory.getLogger(SparseImageServerCommand.class);

	private QuPathGUI qupath;

	public SparseImageServerCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var project = qupath.getProject();
		if (project == null) {
			DisplayHelpers.showErrorMessage("Sparse image server", "You need a project!");
			return;
		}

		try {
			var server = createSparseServer(project.getImageList(), p -> p.getPathClass() == PathClassFactory.getPathClass(StandardPathClasses.REGION));
			if (server.getManager().getRegions().isEmpty()) {
				DisplayHelpers.showErrorMessage("Sparse image server", "No 'Region' annotations found!");
				return;			
			}
			
			var entry = ProjectImportImagesCommand.addSingleImageToProject(project, server, null);
//			var entry = project.addImage(server.getBuilder());
//			var img = ProjectImportImagesCommand.getThumbnailRGB(server, null);
//			entry.setThumbnail(img);
			server.close();
			
			qupath.refreshProject();
			if (entry != null) {
				project.syncChanges();
				qupath.openImageEntry(entry);
			}
			
//			var file = qupath.getDialogHelper().promptToSaveFile("Save sparse server", null, null, "Sparse server", "server.json");
//			if (file == null)
//				return;
//		
//			try (var writer = Files.newBufferedWriter(file.toPath())) {
//				writer.write(ImageServers.toJson(server));
//			}
//			project.addImage(server);
		} catch (Exception e) {
			DisplayHelpers.showErrorMessage("Sparse image server", e);
		}


	}


	static SparseImageServer createSparseServer(Collection<ProjectImageEntry<BufferedImage>> entries, Predicate<PathObject> predicate) throws IOException {

		var builder = new SparseImageServer.Builder();

		int pad = 0;
		int x = 0;
		int y = 0;
		int rowHeight = 0;
		int maxX = 50000;

		ImageServerMetadata firstMetadata = null;

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
	
					rowHeight = Math.max(region.getHeight(), rowHeight);
					var regionOutput = ImageRegion.createInstance(x, y, region.getWidth(), region.getHeight(), 0, 0);
					for (double downsample : croppedServer.getPreferredDownsamples())
						builder.serverRegion(regionOutput, downsample, croppedServer);
	
					// Increment x
					x += region.getWidth() + pad;
	
					// Move to next row
					if (x >= maxX) {
						y += rowHeight + pad;
						rowHeight = 0;
						x = 0;
					}
				}
			} catch (Exception e) {
				logger.warn("Exception trying to read {}: {}", entry.getImageName(), e.getLocalizedMessage());
			}
		}
		return builder.build();

	}


}
