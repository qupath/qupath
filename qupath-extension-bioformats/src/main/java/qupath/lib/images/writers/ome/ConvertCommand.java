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
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.extensions.Subcommand;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.bioformats.BioFormatsServerBuilder;
import qupath.lib.images.writers.ome.OMEPyramidWriter.Builder;
import qupath.lib.images.writers.ome.OMEPyramidWriter.CompressionType;

/**
 * Allows command line option to convert an input image to OME-TIFF
 * 
 * @author Melvin Gelbard
 */
@Command(name = "convert-ome", description = "Converts an input image to OME-TIFF.", sortOptions = false)
public class ConvertCommand implements Runnable, Subcommand {
	
	private static final Logger logger = LoggerFactory.getLogger(ConvertCommand.class);
	
	@Parameters(index = "0", description="Path to the file to convert.", paramLabel = "input")
	private File inputFile;
	
	@Parameters(index = "1", description="Path of the output file.", paramLabel = "output")
	private File outputFile;
	
	@Option(names = {"-r", "--crop"}, defaultValue = "", description = {"Bounding box to crop the input image.",
			"Defined in terms of full-resolution pixel coordinates in the form x,y,w,h. ",
			"If empty (default), the full image will be exported."})
	private String crop;

	@Option(names = {"-z", "--zslices"}, defaultValue = "all", description = {"Request which z-slice(s) will be exported.",
			"Value may be \"all\" (the default), a single number (e.g. \"1\") or a range (e.g. \"1-5\"). Indices and 1-based and ranges are inclusive."})
	private String zSlices;

	@Option(names = {"-t", "--timepoints"}, defaultValue = "all", description = {"Request which timepoints will be exported.",
			"Value may be \"all\" (the default), a single number (e.g. \"1\") or a range (e.g. \"1-5\"). Indices and 1-based and ranges are inclusive."})
	private String timepoints;
	
	@Option(names = {"-d", "--downsample"}, defaultValue = "1.0", description = "Downsample the input image by the given factor (default=1).")
	private double downsample;
	
	@Option(names = {"-y", "--pyramid-scale"}, defaultValue = "1.0", description = {"Scale factor for pyramidal images.",
			"Each pyramidal level is scaled down by the specified factor (> 1)."})
	private double pyramid;
	
	@Option(names = {"--big-tiff"}, defaultValue = Option.NULL_VALUE, description = {"Request to write a big tiff, which is required when writing a TIFF images > 4GB.",
			"Default is to automatically decide based on image size. Choose --big-tiff=false to force a non-big-tiff to be written."})
	private Boolean bigTiff;
	
	@Option(names = {"--tile-size"}, defaultValue = "-1", description = "Set the tile size (of equal height and width).")
	private int tileSize;
	
	@Option(names = {"--tile-width"}, defaultValue = "512", description = "Set the tile width.")
	private int tileWidth;
	
	@Option(names = {"--tile-height"}, defaultValue = "512", description = "Set the tile height.")
	private int tileHeight;
	
	@Option(names = {"-c", "--compression"}, defaultValue = "DEFAULT", description = {"Type of compression to use for conversion.",
																				"Options: ${COMPLETION-CANDIDATES}"})
	private CompressionType compression;
	
	@Option(names = {"-p", "--parallelize"}, defaultValue = "false", description = "Parallelize tile export if possible.", paramLabel = "parallelization")
	private boolean parallelize;
	
	@Option(names = {"--overwrite"}, defaultValue = "false", description = "Overwrite any existing file with the same name as the output.")
	private boolean overwrite = false;
	
	@Option(names = {"--series"}, description = "Series number. Setting this will ensure the image is opened using Bio-Formats and control which image is read from the file. "
			+ "If it is not specified, the default image will be read (typically series 0).")
	private int series = -1;
	
	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
	private boolean usageHelpRequested;
	
	
	@Override
	public void run() {
		
		long startTime = System.currentTimeMillis();
		
		try {
			if (inputFile == null || outputFile == null)
				throw new IOException("Incorrect given path(s)");
		} catch (IOException e) {
			logger.error(e.getLocalizedMessage());
			return;
		}
		
		// Change name if not ending with .ome.tif
		if (!outputFile.getAbsolutePath().toLowerCase().endsWith(".ome.tif"))
			outputFile = new File(outputFile.getParentFile(), GeneralTools.getNameWithoutExtension(outputFile) + ".ome.tif");
		if (outputFile.exists() && !overwrite) {
			logger.error("Output file " + outputFile + " exists!");
			return;
		}
		if (inputFile.equals(outputFile)) {
			logger.error("Input and output files are the same!");
			return;
		}
		
		String[] args;
		if (series >= 0)
			args = new String[]{"--classname", BioFormatsServerBuilder.class.getName(), "--series", Integer.toString(series)};
		else
			args = new String[0];
		
		createTileCache();
		
		try (ImageServer<BufferedImage> server = ImageServers.buildServer(inputFile.toURI(), args)) {
			
			// Get compression from user (or CompressionType.DEFAULT)
//			CompressionType compressionType = stringToCompressionType(compression);
			CompressionType compressionType = compression;

			// Check that compression is compatible with image
			if (!Arrays.stream(CompressionType.values()).filter(c -> c.supportsImage(server)).anyMatch(c -> c == compressionType)) {
				logger.error("Chosen compression " + compressionType.toString() + " is not compatible with the input image.");
			}
			
			// No longer needed because of a494568f - AbstractTileableImageServer now better handles simultaneous tile requests, 
			// and parallelization can still help when requesting regions from an ImageServer that contains an in-memory image
//			// Check if output will be a single tile
//			boolean singleTile = server.getTileRequestManager().getTileRequests(RegionRequest.createInstance(server)).size() == 1;
//			
//			if (singleTile)
//				parallelize = false;
			
			if (tileSize > -1) {
				tileWidth = tileSize;
				tileHeight = tileSize;
			}
			
			Builder builder = new OMEPyramidWriter.Builder(server)
					.compression(compressionType)
					.tileSize(tileWidth, tileHeight)
					.parallelize(parallelize);
			
			if (bigTiff != null)
				builder = builder.bigTiff(bigTiff.booleanValue());
			
			// Make pyramidal, if requested
			if (downsample < 1)
				downsample = server.getDownsampleForResolution(0);
			
			if (pyramid > 1)
				builder.scaledDownsampling(downsample, pyramid);
			else
				builder.downsamples(downsample);
			
			String patternRange = "(\\d+)-(\\d+)";
			String patternInteger = "\\d+";
			
			// Parse z-slices, remembering to convert from 1-based (inclusive) to 0-based (upper value exclusive) indexing
			if (zSlices == null || zSlices.isBlank() || "all".equals(zSlices)) {
				builder.allZSlices();
			} else if (zSlices.matches(patternRange)) {
				int zStart = Integer.parseInt(zSlices.substring(0, zSlices.indexOf("-")));
				int zEnd = Integer.parseInt(zSlices.substring(zSlices.indexOf("-")+1));
				if (zEnd == zStart)
					builder.zSlice(zStart-1);
				else if (zStart > zEnd) {
					logger.error("Invalid range of --zslices (must be ascending): " + zSlices);
					return;
				} else
					builder.zSlices(zStart-1, zEnd);
			} else if (zSlices.matches(patternInteger)) {
				int z = Integer.parseInt(zSlices);
				builder.zSlice(z-1);
			} else {
				logger.error("Unknown value for --zslices: " + zSlices);
				return;
			}
			
			// Parse timepoints, remembering to convert from 1-based (inclusive) to 0-based (upper value exclusive) indexing
			if ("all".equals(timepoints)) {
				builder.allTimePoints();
			} else if (timepoints.matches(patternRange)) {
				int tStart = Integer.parseInt(timepoints.substring(0, timepoints.indexOf("-")));
				int tEnd = Integer.parseInt(timepoints.substring(timepoints.indexOf("-")+1));
				if (tStart == tEnd)
					builder.timePoint(tStart-1);
				else if (tStart > tEnd) {
					logger.error("Invalid range of --timepoints (must be ascending): " + timepoints);
					return;
				} else
					builder.timePoints(tStart-1, tEnd);
			} else if (timepoints.matches(patternInteger)) {
				int t = Integer.parseInt(timepoints);
				builder.timePoint(t-1);
			} else {
				logger.error("Unknown value for --timepoints: " + timepoints);
				return;
			}
			
			// Parse the bounding box, if required
			if (crop != null && !crop.isBlank()) {
				var matcher = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+)").matcher(crop);
				if (matcher.matches()) {
					int x = Integer.parseInt(matcher.group(1));
					int y = Integer.parseInt(matcher.group(2));
					int w = Integer.parseInt(matcher.group(3));
					int h = Integer.parseInt(matcher.group(4));
					builder.region(x, y, w, h);
				} else {
					logger.error("Unknown value for --crop: " + crop);					
					return;
				}
			}
			
			builder.build().writeSeries(outputFile.getPath());
			
			long duration = System.currentTimeMillis() - startTime;
			logger.info(String.format("%s written in %.1f seconds", outputFile.getAbsolutePath(), duration/1000.0));

		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}
	
	
//	private CompressionType stringToCompressionType(String compressionParam) {
//		switch (compressionParam.toLowerCase()) {
//		case "default":
//			return CompressionType.DEFAULT;
//		
//		case "jpeg-2000":
//		case "jpeg2000":
//		case "j2k":
//			return CompressionType.J2K;
//		
//		case "jpeg-2000-lossy":
//		case "jpeg2000lossy":
//		case "j2k-lossy":
//			return CompressionType.J2K_LOSSY;
//		
//		case "jpeg":
//			return CompressionType.JPEG;
//		
//		case "lzw":
//			return CompressionType.LZW;
//			
//		case "uncompressed":
//			return CompressionType.UNCOMPRESSED;
//			
//		case "zlib":
//			return CompressionType.ZLIB;
//			
//		default:
//			return CompressionType.DEFAULT;
//		}
//	}
	
	
	/**
	 * The tile cache is usually set when initializing the GUI; here, we need to create one for performance
	 */
	private void createTileCache() {
		// TODO: Refactor this to avoid replicating logic from QuPathGUI private method
		Runtime rt = Runtime.getRuntime();
		long maxAvailable = rt.maxMemory(); // Max available memory
		if (maxAvailable == Long.MAX_VALUE) {
			logger.warn("No inherent maximum memory set - for caching purposes, will assume 64 GB");
			maxAvailable = 64L * 1024L * 1024L * 1024L;
		}
		double percentage = PathPrefs.tileCachePercentageProperty().get();
		if (percentage < 10) {
			percentage = 10;
		} else if (percentage > 90) {
			percentage = 90;			
		}
		long tileCacheSize = Math.round(maxAvailable * (percentage / 100.0));
		logger.info(String.format("Setting tile cache size to %.2f MB (%.1f%% max memory)", tileCacheSize/(1024.*1024.), percentage));
		
		var imageRegionStore = ImageRegionStoreFactory.createImageRegionStore(tileCacheSize);
		ImageServerProvider.setCache(imageRegionStore.getCache(), BufferedImage.class);
	}
	
}