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
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.DoubleStream;

import org.apache.commons.io.FileUtils;
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
import qupath.lib.images.writers.ome.zarr.OMEZarrWriter;
import qupath.lib.regions.ImageRegion;

/**
 * Allows command line option to convert an input image to OME-TIFF or OME-Zarr.
 * 
 * @author Melvin Gelbard
 */
@Command(name = "convert-ome", description = "Converts an input image to OME-TIFF or OME-Zarr.", sortOptions = false)
public class ConvertCommand implements Runnable, Subcommand {
	
	private static final Logger logger = LoggerFactory.getLogger(ConvertCommand.class);
	
	@Parameters(index = "0", paramLabel = "input", description="Path of the file to convert.")
	private File inputFile;
	
	@Parameters(index = "1", paramLabel = "output", description={
			"Path of the output file.",
			"The extension of the file must be .ome.zarr to create a Zarr file",
			"or .ome.tiff to create an OME TIFF file."
	})
	private File outputFile;
	
	@Option(names = {"-r", "--crop"}, defaultValue = "", description = {
			"Bounding box to crop the input image.",
			"Defined in terms of full-resolution pixel coordinates in the form x,y,w,h.",
			"If empty (default), the full image will be exported."
	})
	private String crop;

	@Option(names = {"-z", "--zslices"}, defaultValue = "all", description = {
			"Request which z-slice(s) will be exported.",
			"Value may be \"all\" (the default), a single number (e.g. \"1\") or a range (e.g. \"1-5\"). Indices are 1-based and ranges are inclusive."
	})
	private String zSlices;

	@Option(names = {"-t", "--timepoints"}, defaultValue = "all", description = {
			"Request which timepoints will be exported.",
			"Value may be \"all\" (the default), a single number (e.g. \"1\") or a range (e.g. \"1-5\"). Indices are 1-based and ranges are inclusive."
	})
	private String timepoints;
	
	@Option(names = {"-d", "--downsample"}, defaultValue = "1.0", description = {
			"Downsample the input image by the given factor (default=1)."
	})
	private double downsample;
	
	@Option(names = {"-y", "--pyramid-scale"}, defaultValue = "1.0", description = {
			"Scale factor for pyramidal images.",
			"Each pyramidal level is scaled down by the specified factor (> 1).",
			"The downsamples of the original image are used if <= 1."
	})
	private double pyramid;
	
	@Option(names = {"--big-tiff"}, defaultValue = Option.NULL_VALUE, description = {
			"Request to write a big tiff, which is required when writing a TIFF images > 4GB.",
			"Default is to automatically decide based on image size. Choose --big-tiff=false to force a non-big-tiff to be written.",
			"Only relevant for TIFF files."
	})
	private Boolean bigTiff;
	
	@Option(names = {"--tile-size"}, defaultValue = "-1", description = "Set the tile size (of equal height and width).")
	private int tileSize;
	
	@Option(names = {"--tile-width"}, defaultValue = "512", description = "Set the tile width (default=512).")
	private int tileWidth;
	
	@Option(names = {"--tile-height"}, defaultValue = "512", description = "Set the tile height (default=512).")
	private int tileHeight;
	
	@Option(names = {"-c", "--compression"}, defaultValue = "DEFAULT", description = {
			"Type of compression to use for writing TIFF files.",
			"Only relevant for TIFF files",
			"Options: ${COMPLETION-CANDIDATES}"
	})
	private OMEPyramidWriter.CompressionType compression;
	
	@Option(names = {"-p", "--parallelize"}, defaultValue = "true", paramLabel = "parallelization",
			description = "Parallelize tile export if possible (default=true).",
			negatable = true)
	private boolean parallelize;
	
	@Option(names = {"--overwrite"}, defaultValue = "false",
			description = "Overwrite any existing file with the same name as the output (default=false).")
	private boolean overwrite = false;
	
	@Option(names = {"--series"}, description = {
			"Series number.",
			"Setting this will ensure the image is opened using Bio-Formats and control which image is read from the file.",
			"If it is not specified, the default image will be read (typically series 0)."
	})
	private int series = -1;
	
	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
	private boolean usageHelpRequested;

	private enum OutputType {
		TIFF(".ome.tif"),
		ZARR(".ome.zarr");
		private final String extension;

		OutputType(String extension) {
			this.extension = extension;
		}

		public String getExtension() {
			return extension;
		}
	}

	private record Range(int start, int end) {}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();

		if (inputFile == null || outputFile == null) {
			logger.error("Incorrect given path(s)");
			System.exit(-1);
		}

		// Set output type to TIFF if output extension not recognized
		OutputType outputType = outputFile.getAbsolutePath().toLowerCase().endsWith(OutputType.ZARR.getExtension()) ? OutputType.ZARR : OutputType.TIFF;

		// Change name if not ending with correct extension
		if (!outputFile.getAbsolutePath().toLowerCase().endsWith(outputType.getExtension())) {
			outputFile = new File(outputFile.getParentFile(), GeneralTools.getNameWithoutExtension(outputFile) + outputType.getExtension());
		}

		if (outputFile.exists() && !overwrite) {
			logger.error("Output file " + outputFile + " exists!");
			System.exit(-1);
		}

		if (inputFile.equals(outputFile)) {
			logger.error("Input and output files are the same!");
			System.exit(-1);
		}

		try {
			if (overwrite && outputFile.exists()) {
				if (outputFile.isDirectory()) {
					FileUtils.deleteDirectory(outputFile);
				} else {
					Files.delete(outputFile.toPath());
				}
			}
		} catch (IOException e) {
			logger.error("Error while deleting existing image", e);
		}
		
		String[] args;
		if (series >= 0)
			args = new String[]{"--classname", BioFormatsServerBuilder.class.getName(), "--series", Integer.toString(series)};
		else
			args = new String[0];
		
		createTileCache();
		
		try (ImageServer<BufferedImage> server = ImageServers.buildServer(inputFile.toURI(), args)) {
			if (tileSize > -1) {
				tileWidth = tileSize;
				tileHeight = tileSize;
			}

			if (downsample < 1) {
				downsample = server.getDownsampleForResolution(0);
			}

			Range zSlicesRange = getRange(zSlices, server.nZSlices(), "zslices");
			if (!isValidRange(zSlicesRange, server.nZSlices())) {
				logger.error("Invalid range of --zslices: {}, image supports {}-{}", zSlices, 1, server.nZSlices());
				System.exit(-1);
			}

			Range timepointsRange = getRange(timepoints, server.nTimepoints(), "timepoints");
			if (!isValidRange(timepointsRange, server.nTimepoints())) {
				logger.error("Invalid range of --timepoints: {}, image supports {}-{}", timepoints, 1, server.nTimepoints());
				System.exit(-1);
			}
			Optional<ImageRegion> boundingBox = getBoundingBox(crop);

			switch (outputType) {
				case TIFF -> {
					if (!compression.supportsImage(server)) {
						logger.error("Chosen compression " + compression.toString() + " is not compatible with the input image.");
						System.exit(-1);
					}

					OMEPyramidWriter.Builder builder = new OMEPyramidWriter.Builder(server)
							.compression(compression)
							.tileSize(tileWidth, tileHeight)
							.parallelize(parallelize)
							.zSlices(zSlicesRange.start(), zSlicesRange.end())
							.timePoints(timepointsRange.start(), timepointsRange.end());

					if (bigTiff != null) {
						builder = builder.bigTiff(bigTiff);
					}

					if (pyramid > 1) {
						builder.scaledDownsampling(downsample, pyramid);
					} else {
						builder.downsamples(DoubleStream.concat(
								DoubleStream.of(downsample),
								Arrays.stream(server.getPreferredDownsamples()).filter(d -> d > downsample)
						).toArray());
					}

					if (boundingBox.isPresent()) {
						builder.region(
								boundingBox.get().getX(),
								boundingBox.get().getY(),
								boundingBox.get().getWidth(),
								boundingBox.get().getHeight()
						);
					}

					builder.build().writeSeries(outputFile.getAbsolutePath());
				}
				case ZARR -> {
					OMEZarrWriter.Builder builder = new OMEZarrWriter.Builder(server)
							.tileSize(tileWidth, tileHeight)
							.region(boundingBox.orElse(null))
							.zSlices(zSlicesRange.start(), zSlicesRange.end())
							.timePoints(timepointsRange.start(), timepointsRange.end());

					if (!parallelize) {
						builder.parallelize(1);
					}

					if (pyramid > 1) {
						builder.downsamples(DoubleStream.iterate(
								downsample,
								d -> (int) (server.getWidth() / d) > tileWidth &&
										(int) (server.getHeight() / d) > tileHeight,
								d -> d * pyramid
						).toArray());
					} else {
						builder.downsamples(DoubleStream.concat(
								DoubleStream.of(downsample),
								Arrays.stream(server.getPreferredDownsamples()).filter(d -> d > downsample)
						).toArray());
					}

					try (OMEZarrWriter writer = builder.build(outputFile.getAbsolutePath())) {
						writer.writeImage();
					}
				}
			}
			long duration = System.currentTimeMillis() - startTime;
			logger.info(String.format("%s written in %.1f seconds", outputFile.getAbsolutePath(), duration/1000.0));
		} catch (Exception e) {
			logger.error("Error while creating image", e);
			System.exit(-1);
		}
	}
	
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

	/**
	 * Parse the provided range text and return the 0-based
	 * start (inclusive) and end (exclusive) indices contained in this text.
	 *
	 * @param rangeText the text containing the indices to parse. It can be "all",
	 *                  a single number (e.g. "1") or a range (e.g. "1-5").
	 *                  Indices are 1-based and ranges are inclusive.
	 * @param maxRange the maximum value the exclusive end index can have
	 * @param rangeLabel a text indicating what this range represent
	 * @return the 0-based start (inclusive) and end (exclusive) indices contained in the provided range text
	 */
	private static Range getRange(String rangeText, int maxRange, String rangeLabel) {
		String patternRange = "(\\d+)-(\\d+)";
		String patternInteger = "\\d+";

		if (rangeText == null || rangeText.isBlank() || "all".equals(rangeText)) {
			return new Range(0, maxRange);
		} else if (rangeText.matches(patternRange)) {
			int start = Integer.parseInt(rangeText.substring(0, rangeText.indexOf("-")));
			int end = Integer.parseInt(rangeText.substring(rangeText.indexOf("-")+1));

			if (start == end) {
				return new Range(start-1, start);
			} else if (start > end) {
				logger.error(String.format("Invalid range of --%s (must be ascending): %s", rangeLabel, rangeText));
				System.exit(-1);
				return null;
			} else {
				return new Range(start-1, end);
			}
		} else if (rangeText.matches(patternInteger)) {
			int v = Integer.parseInt(rangeText);
			return new Range(v-1, v);
		} else {
			logger.error(String.format("Unknown value for --%s: %s", rangeLabel, rangeText));
			System.exit(-1);
			return null;
		}
	}

	private static boolean isValidRange(Range range, int maxRange) {
		return range.start() >= 0 && range.end() <= maxRange && range.start() < range.end();
	}

	/**
	 * Parse the provided bounding box text and return an ImageRegion
	 * corresponding to it.
	 *
	 * @param crop the bounding box text to parse
	 * @return an ImageRegion corresponding to the input text, or an empty
	 * Optional if the region couldn't be created. Only the x, y, width, and height
	 * of the returned region should be considered
	 */
	private static Optional<ImageRegion> getBoundingBox(String crop) {
		if (crop != null && !crop.isBlank()) {
			var matcher = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+)").matcher(crop);
			if (matcher.matches()) {
				int x = Integer.parseInt(matcher.group(1));
				int y = Integer.parseInt(matcher.group(2));
				int w = Integer.parseInt(matcher.group(3));
				int h = Integer.parseInt(matcher.group(4));
				return Optional.of(ImageRegion.createInstance(x, y, w, h, 0, 0));
			} else {
				logger.error("Unknown value for --crop: " + crop);
				System.exit(-1);
				return Optional.empty();
			}
		} else {
			return Optional.empty();
		}
	}

}