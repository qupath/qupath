package qupath.lib.images.writers.ome;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import qupath.lib.gui.extensions.Subcommand;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.writers.ome.OMEPyramidWriter.Builder;
import qupath.lib.images.writers.ome.OMEPyramidWriter.CompressionType;
import qupath.lib.regions.RegionRequest;

/**
 * Allows command line option to convert an input image to OME-TIFF
 * 
 * @author Melvin Gelbard
 */
@Command(name = "convert-ome", description = "Converts an input image to OME-TIFF.")
public class ConvertCommand implements Runnable, Subcommand {
	
	private final static Logger logger = LoggerFactory.getLogger(ConvertCommand.class);
	
	@Parameters(index = "0", description="Path to the file to convert.", paramLabel = "input")
	private File inputFile;
	
	@Parameters(index = "1", description="Path of the output file.", paramLabel = "output")
	private File outputFile;
	
	@Option(names = {"-d", "--downsample"}, defaultValue = "1.0", description = "Downsample the input image by the given factor.")
	private double downsample;
	
	@Option(names = {"-c", "--compression"}, defaultValue = "default", description = "Type of compression to use for conversion.")
	private String compression;
	
	// TODO
	//@Option(names = {"-r", "--crop"}, defaultValue = "(0, 0)", description = "Crop the input image to fit the given size. (To do!)")
	//private String crop;
	
	@Option(names = {"--tile-size"}, defaultValue = "-1", description = "Set the tile size (of equal height and width).")
	private int tileSize;
	
	@Option(names = {"--tile-width"}, defaultValue = "256", description = "Set the tile width.")
	private int tileWidth;
	
	@Option(names = {"--tile-height"}, defaultValue = "256", description = "Set the tile height.")
	private int tileHeight;
	
	@Option(names = {"-z"}, arity = "1..2", description = {"Request which z-slice(s) is/are exported. ",
															"Default will export all z-slices.",
															"If specifying one value, the specified z-slice will be exported.",
															"If specifying two values, all the z-slices between them (inclusive) will be exported."})
	private int[] zSlices;
	
	@Option(names = {"-t"}, arity = "1..2", description = {"Request which timepoints of a time series are exported.",
															"Default will export all timepoints.",
															"If specifying one value, the specified timepoints will be exported.",
															"If specifying two values, all the timepoints between them (inclusive) will be exported."})
	private int[] timepoints;
	
	@Option(names = {"-p", "--paralellize"}, description = "Specify if tile export should be parallelized if possible.", paramLabel = "parallelization")
	private boolean parallelize;
	
	
	@Override
	public void run() {
		try {
			if (inputFile == null || outputFile == null)
				throw new IOException("Incorrect given path(s)");
		} catch (IOException e) {
			logger.error(e.getLocalizedMessage());
			return;
		}
		
		// Change name if not ending with .ome.tif
		if (outputFile.getPath().endsWith(".tif") && !outputFile.getPath().endsWith(".ome.tif"))
			outputFile = new File(outputFile.getParentFile(), outputFile.getPath().substring(0, outputFile.getPath().length()-4) + ".ome.tif");

		
		try (ImageServer<BufferedImage> server = ImageServerProvider.buildServer(inputFile.getPath(), BufferedImage.class)) {
			
			// Get compression from user (or CompressionType.DEFAULT)
			CompressionType compressionType = stringToCompressionType(compression);

			// Check that compression is compatible with image
			if (!Arrays.stream(CompressionType.values()).filter(c -> c.supportsImage(server)).anyMatch(c -> c == compressionType))
				throw new Exception("Compression chosen: " + compressionType.toString() + " is not compatible with image.");
			
			// Check if output will be a single tile
			boolean singleTile = server.getTileRequestManager().getTileRequests(RegionRequest.createInstance(server)).size() == 1;
			
			if (singleTile)
				parallelize = false;
			
			if (tileSize > -1) {
				tileWidth = tileSize;
				tileHeight = tileSize;
			}
			
			Builder builder = new OMEPyramidWriter.Builder(server)
					.compression(compressionType)
					.tileSize(tileWidth, tileHeight)
					.parallelize(parallelize);
			
			int width = server.getWidth();
			int height = server.getHeight();
			if (downsample <= 1 || Math.max(width, height)/server.getDownsampleForResolution(0) < 4096)
				builder.downsamples(server.getDownsampleForResolution(0));
			else
				builder.scaledDownsampling(server.getDownsampleForResolution(0), downsample);
			
			
			if (zSlices != null) {
				if (zSlices.length == 1)
					builder.zSlice(zSlices[0]);
				else if (zSlices.length == 2)
					builder.zSlices(zSlices[0], zSlices[1]);
			}
			
			if (timepoints != null) {
				if (timepoints.length == 1)
					builder.timePoint(timepoints[0]);
				else if (timepoints.length == 2)
					builder.timePoints(timepoints[0], timepoints[1]);
			}
			
			builder.build().writePyramid(outputFile.getPath());

		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		}
	}
	
	
	private CompressionType stringToCompressionType(String compressionParam) {
		switch (compressionParam.toLowerCase()) {
		case "default":
			return CompressionType.DEFAULT;
		
		case "jpeg-2000":
		case "jpeg2000":
		case "j2k":
			return CompressionType.J2K;
		
		case "jpeg-2000-lossy":
		case "jpeg2000lossy":
		case "j2k-lossy":
			return CompressionType.J2K_LOSSY;
		
		case "jpeg":
			return CompressionType.JPEG;
		
		case "lzw":
			return CompressionType.LZW;
			
		case "uncompressed":
			return CompressionType.UNCOMPRESSED;
			
		case "zlib":
			return CompressionType.ZLIB;
			
		default:
			return CompressionType.DEFAULT;
		}
	}
}