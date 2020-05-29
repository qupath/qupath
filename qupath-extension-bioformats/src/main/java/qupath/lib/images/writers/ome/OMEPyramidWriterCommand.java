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

package qupath.lib.images.writers.ome;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.channels.ClosedByInterruptException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.writers.ome.OMEPyramidWriter.CompressionType;
import qupath.lib.images.writers.ome.OMEPyramidWriter.OMEPyramidSeries;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * OME TIFF writer command capable of exporting image pyramids based on QuPath ImageServers.
 * <p>
 * Note this requires Bio-Formats v6.0.0 or later.
 * 
 * @author Pete Bankhead
 *
 */
public class OMEPyramidWriterCommand implements Runnable {

	private final static Logger logger = LoggerFactory.getLogger(OMEPyramidWriterCommand.class);
	
	private static ObjectProperty<CompressionType> defaultPyramidCompression = PathPrefs.createPersistentPreference(
			"ome-pyramid-compression", CompressionType.DEFAULT, CompressionType.class);

	private static IntegerProperty defaultTileSize = PathPrefs.createPersistentPreference(
			"ome-pyramid-tile-size", 256);

	private static IntegerProperty minSizeForTiling = PathPrefs.createPersistentPreference(
			"ome-pyramid-min-size-for-tiling", 4096);
	
	private static IntegerProperty scaledDownsample = PathPrefs.createPersistentPreference(
			"ome-pyramid-scaled-downsample", 4);
	
	private static BooleanProperty parallelizeTiling = PathPrefs.createPersistentPreference(
			"ome-pyramid-parallelize", true);
	
	private static BooleanProperty allZ = PathPrefs.createPersistentPreference(
			"ome-pyramid-all-z", true);
	
	private static BooleanProperty allT = PathPrefs.createPersistentPreference(
			"ome-pyramid-all-t", true);

	/**
	 * Query the default compression type when writing OME-TIFF images.
	 * @return
	 */
	public static CompressionType getDefaultPyramidCompression() {
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
	
	private QuPathGUI qupath;
	
	private ExecutorService pool;
	private Future<?> currentTask;
	
	/**
	 * Constructor.
	 * @param qupath current QuPath instance.
	 */
	public OMEPyramidWriterCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		if (currentTask != null && !currentTask.isDone()) {
			if (!Dialogs.showConfirmDialog("OME Pyramid writer",
					"Do you want to stop the current export?"
					))
				// TODO: Delete exporting file?
				return;
			else {
				currentTask.cancel(true);
			}
		}
		
		QuPathViewer viewer = qupath.getViewer();
		int zPos = viewer.getZPosition();
		int tPos = viewer.getTPosition();
		
		ImageData<BufferedImage> imageData = viewer.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError("OME Pyramid writer");
			return;
		}
		ImageServer<BufferedImage> server = imageData.getServer();
		
		// Region
		PathObject selected = imageData.getHierarchy().getSelectionModel().getSelectedObject();
		ImageRegion region = null;
		int width, height;
		if (selected == null || !selected.hasROI() || !selected.getROI().isArea()) {
			width = server.getWidth();
			height = server.getHeight();
		} else {
			region = ImageRegion.createInstance(selected.getROI());
			width = region.getWidth();
			height = region.getHeight();
		}
		
		// Set compression - with a sanity check for validity, defaulting to another comparable method if necessary
		CompressionType compression = getDefaultPyramidCompression();
		List<String> compatibleCompression = Arrays.stream(CompressionType.values()).filter(c -> c.supportsImage(server)).map(c -> c.toFriendlyString()).collect(Collectors.toList());
		if (!compatibleCompression.contains(compression.toFriendlyString()))
			compression = CompressionType.DEFAULT;
		
		
		var params = new ParameterList()
				.addChoiceParameter("compression", "Compression type", compression.toFriendlyString(), compatibleCompression)
				.addIntParameter("scaledDownsample", "Pyramidal downsample", scaledDownsample.get(), "", 1, 8,
						"Amount to downsample each consecutive pyramidal level; use 1 to indicate the image should not be pyramidal")
				.addIntParameter("tileSize", "Tile size", getDefaultTileSize(), "px", "Tile size for export (should be between 128 and 8192)")
				.addBooleanParameter("parallelize", "Parallelize export", parallelizeTiling.get(), "Export image tiles in parallel - " +
						"this should be faster, best keep it on unless you encounter export problems")
				.addBooleanParameter("allZ", "All z-slices", allZ.get(), "Include all z-slices in the stack")
				.addBooleanParameter("allT", "All timepoints", allT.get(), "Include all timepoints in the time-series")
				;
		
		boolean singleTile = server.getTileRequestManager().getTileRequests(RegionRequest.createInstance(server)).size() == 1;
		
		params.setHiddenParameters(server.nZSlices() == 1, "allZ");
		params.setHiddenParameters(server.nTimepoints() == 1, "allT");
		params.setHiddenParameters(singleTile, "tileSize", "parallelize");
		
		if (!Dialogs.showParameterDialog("Export OME-TIFF", params))
			return;
		
		compression = CompressionType.fromFriendlyString((String)params.getChoiceParameterValue("compression"));
		defaultPyramidCompression.set(compression);
		
		int downsampleScale = params.getIntParameterValue("scaledDownsample");
		scaledDownsample.set(downsampleScale);
		
		int tileSize = params.getIntParameterValue("tileSize");
		boolean parallelize = params.getBooleanParameterValue("parallelize");
		if (!singleTile) {
			tileSize = GeneralTools.clipValue(tileSize, 128, 8192);
			defaultTileSize.set(tileSize);
			parallelizeTiling.set(parallelize);
		}
		
		boolean doAllZ = false;
		boolean doAllT = false;
		if (server.nZSlices() > 1) {
			doAllZ = params.getBooleanParameterValue("allZ");		
			allZ.set(doAllZ);
		}
		if (server.nTimepoints() > 1) {
			doAllT = params.getBooleanParameterValue("allT");			
			allT.set(doAllT);
		}

		OMEPyramidWriter.Builder builder = new OMEPyramidWriter.Builder(server);
		if (region != null) {
			builder = builder.region(region);
		} else {
			if (server.nZSlices() > 1 && !doAllZ)
				builder.zSlice(zPos);
			if (server.nTimepoints() > 1 && !doAllT)
				builder.timePoint(tPos);
		}
		
		builder.compression(compression);
		
		if (downsampleScale <= 1 || Math.max(width, height)/server.getDownsampleForResolution(0) < minSizeForTiling.get())
			builder.downsamples(server.getDownsampleForResolution(0));
		else
			builder.scaledDownsampling(server.getDownsampleForResolution(0), downsampleScale);
		
		// Set tile size; if we just have one tile, use the image width & height
		if (singleTile)
			builder = builder.tileSize(width, height);
		else
			builder = builder.tileSize(tileSize)
							 .parallelize(parallelize);
		
		if (server.nZSlices() > 1 && doAllZ)
			builder.allZSlices();
		
		if (server.nTimepoints() > 1 && doAllT)
			builder.allTimePoints();
		
		
		// Prompt for file
		File fileOutput = Dialogs.promptToSaveFile("Write pyramid", null, null, "OME TIFF pyramid", ".ome.tif");
		if (fileOutput == null)
			return;
		String name = fileOutput.getName();
		
		// We can have trouble with only the '.tif' part of the name being included
		if (name.endsWith(".tif") && !name.endsWith(".ome.tif"))
			fileOutput = new File(fileOutput.getParentFile(), name.substring(0, name.length()-4) + ".ome.tif");

		OMEPyramidSeries writer = builder.build();
		
		if (pool == null) {
			pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("ome-pyramid-export", false));
		}
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
				Dialogs.showInfoNotification("OME Pyramid writer", "Exporting to " + path + " - \nplease keep QuPath running until export is complete!");
				long startTime = System.currentTimeMillis();
				writer.writeImage(path);
				long endTime = System.currentTimeMillis();
				logger.info(String.format("OME TIFF export to {} complete in %.1f seconds", (endTime - startTime)/1000.0), path);
				Dialogs.showInfoNotification("OME Pyramid writer", "OME TIFF export complete!");
			} catch (ClosedByInterruptException e) {
				logger.warn("OME Pyramid writer closed by interrupt (possibly due to user cancelling it)", e);
			} catch (Exception e) {
				Dialogs.showErrorMessage("OME Pyramid writer", e);
			} finally {
				writer = null;
			}
		}
		
		public String getPath() {
			return path;
		}
		
	}
	

}