package qupath.lib.images.writers.ome;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.channels.ClosedByInterruptException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.writers.ome.OMEPyramidWriter.OMEPyramidSeries;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImageRegion;

/**
 * OME TIFF writer command capable of exporting image pyramids based on QuPath ImageServers.
 * <p>
 * Note this requires Bio-Formats v6.0.0 or later.
 * 
 * @author Pete Bankhead
 *
 */
public class OMEPyramidWriterCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(OMEPyramidWriterCommand.class);
	
	private static StringProperty defaultPyramidCompression = PathPrefs.createPersistentPreference(
			"ome-pyramid-default-compression", "Uncompressed");

	private static IntegerProperty defaultTileSize = PathPrefs.createPersistentPreference(
			"ome-pyramid-default-tile-size", 256);

	private static IntegerProperty minSizeForTiling = PathPrefs.createPersistentPreference(
			"ome-pyramid-default-min-size-for-tiling", 4096);
	
	private static BooleanProperty parallelizeTiling = PathPrefs.createPersistentPreference(
			"ome-pyramid-default-parallelize", true);

	/**
	 * Query the default compression type when writing OME-TIFF images.
	 * @return
	 */
	public static String getDefaultPyramidCompression() {
		return defaultPyramidCompression.get();
	}
	
	/**
	 * Query the default tile size when writing OME-TIFF images.
	 * @return
	 */
	public static int getDefaultTileSize() {
		return defaultTileSize.get();
	}

	/**
	 * Query the default minimum image size when writing OME-TIFF images.
	 * This is used as a hint to disable tiling for images with widths and heights smaller than this value.
	 * @return
	 */
	public static int getMinSizeForTiling() {
		return minSizeForTiling.get();
	}
	
	private static boolean initializedPreferences = false;
	
	private QuPathGUI qupath;
	
	private File lastDirectory = null;
	
	private ExecutorService pool;
	private Future<?> currentTask;
	
	/**
	 * Constructor.
	 * @param qupath current QuPath instance.
	 */
	public OMEPyramidWriterCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		
		if (qupath != null) {
			synchronized (qupath) {
				if (!initializedPreferences) {
					qupath.getPreferencePanel().addChoicePropertyPreference(
							defaultPyramidCompression,
							FXCollections.observableArrayList(OMEPyramidWriter.getAvailableCompressionTypes()),
							String.class,
							"OME TIFF compression",
							"Input/Output",
							"Specify default OME TIFF compression type (will only be used if compatible with the image being exported)");
					
					qupath.getPreferencePanel().addPropertyPreference(
							defaultTileSize, Integer.class,
							"OME TIFF tile size",
							"Input/Output",
							"Specify default OME TIFF tile size (only used for pyramidal TIFF images)");
					
					qupath.getPreferencePanel().addPropertyPreference(
							minSizeForTiling, Integer.class,
							"OME TIFF min size for tiling",
							"Input/Output",
							"Specify minimum image width or height used to determine whether to create a pyramid or export a standard OME TIFF");
					
					qupath.getPreferencePanel().addPropertyPreference(
							parallelizeTiling, Boolean.class,
							"OME TIFF use parallel tile export",
							"Input/Output",
							"Use multithreading when exporting image tiles - you probably want this on, but may to it off to help debug export problems");

				}
			}			
		}
	}

	@Override
	public void run() {
		
		if (currentTask != null && !currentTask.isDone()) {
			if (!DisplayHelpers.showConfirmDialog("OME Pyramid writer",
					"Do you want to stop the current export?"
					))
				// TODO: Delete exporting file?
				return;
			else {
				currentTask.cancel(true);
			}
		}
		
		if (pool == null) {
			pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("ome-pyramid-export", false));
		}
		QuPathViewer viewer = qupath.getViewer();
		ImageData<BufferedImage> imageData = viewer.getImageData();
		if (imageData == null) {
			DisplayHelpers.showErrorMessage("OME Pyramid writer", "There is no image open in the current viewer!");
			return;
		}
		ImageServer<BufferedImage> server = imageData.getServer();
		
		// Region
		PathObject selected = imageData.getHierarchy().getSelectionModel().getSelectedObject();
		ImageRegion region;
		if (selected == null || !selected.hasROI() || !selected.getROI().isArea()) {
			region = ImageRegion.createInstance(0, 0, server.getWidth(), server.getHeight(), viewer.getZPosition(), viewer.getTPosition());
		} else {
			region = ImageRegion.createInstance(selected.getROI());
		}
		
		// Prompt for file
		File fileOutput = qupath.getDialogHelper().promptToSaveFile("Write pyramid", lastDirectory, null, "OME TIFF pyramid", ".ome.tif");
		if (fileOutput == null)
			return;
		String name = fileOutput.getName();
		// We can have trouble with only the '.tif' part of the name being included
		if (name.endsWith(".tif") && !name.endsWith(".ome.tif"))
			fileOutput = new File(fileOutput.getParentFile(), name.substring(0, name.length()-4) + ".ome.tif");
		lastDirectory = fileOutput.getParentFile();
		
		OMEPyramidWriter.Builder builder = new OMEPyramidWriter.Builder(server)
				.region(region);
		
		// Set tile size; if we just have one tile, use the image width & height
		if (server.getTileRequestManager().getAllTileRequests().size() == 1)
			builder = builder.tileSize(region.getWidth(), region.getHeight());
		else if (getDefaultTileSize() > 0)
			builder = builder.tileSize(getDefaultTileSize());
		else {
			builder = builder.tileSize(server.getMetadata().getPreferredTileWidth(), server.getMetadata().getPreferredTileHeight());
		}

		// Decide whether to write pyramid or not based on image size
		if (Math.max(region.getWidth(), region.getHeight()) > getMinSizeForTiling())
			builder = builder.scaledDownsampling(4.0);
		else
			builder = builder.downsamples(1.0);
		
		// Set compression - with a sanity check for validity, defaulting to another comparable method if necessary
		String compression = getDefaultPyramidCompression();
		Set<String> compatibleCompression = OMEPyramidWriter.getCompatibleCompressionTypes(server);
		if (!compatibleCompression.contains(compression)) {
			String oldCompression = compression;
			compression = OMEPyramidWriter.isLossyCompressionType(compression) ? 
					OMEPyramidWriter.getDefaultLossyCompressionType(server) : 
					OMEPyramidWriter.getDefaultLosslessCompressionType(server);
			logger.warn("Requested compression type {} is not compatible with the current image - will use {} instead", oldCompression, compression);
		}
		builder = builder.compression(compression);
		
		if (parallelizeTiling.get())
			builder = builder.parallelize();
		
		OMEPyramidSeries writer = builder.build();
		
		currentTask = pool.submit(new WriterTask(OMEPyramidWriter.createWriter(writer), fileOutput.getAbsolutePath()));
	}
	
	
	static class WriterTask implements Runnable {
		
		private OMEPyramidWriter writer;
		private String path;
		
		WriterTask(final OMEPyramidWriter writer, final String path) {
			this.writer = writer;
			this.path = path;
		}

		@Override
		public void run() {
			try {
				DisplayHelpers.showInfoNotification("OME Pyramid writer", "Exporting to " + path);
				long startTime = System.currentTimeMillis();
				writer.writeImage(path);
				long endTime = System.currentTimeMillis();
				logger.info(String.format("OME TIFF export to {} complete in %.1f seconds", (endTime - startTime)/1000.0), path);
				DisplayHelpers.showInfoNotification("OME Pyramid writer", "OME TIFF export complete!");
			} catch (ClosedByInterruptException e) {
				logger.warn("OME Pyramid writer closed by interrupt (possibly due to user cancelling it)", e);
			} catch (Exception e) {
				DisplayHelpers.showErrorMessage("OME Pyramid writer", e);
			} finally {
				writer = null;
			}
		}
		
		public String getPath() {
			return path;
		}
		
	}
	

}
