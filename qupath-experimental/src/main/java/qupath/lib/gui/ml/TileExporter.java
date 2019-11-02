package qupath.lib.gui.ml;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.regions.RegionRequest;

/**
 * Helper class for exporting image tiles, typically for further analysis elsewhere or for training up an AI algorithm.
 * 
 * @author Pete Bankhead
 */
public class TileExporter  {

	private final static Logger logger = LoggerFactory.getLogger(TileExporter.class);

	private ImageData<BufferedImage> imageData;

	private double downsample;
	private int tileWidth = 512, tileHeight = 512;
	private int overlapX = 0, overlapY = 0;

	private boolean includePartialTiles = false;
	private boolean annotatedTilesOnly = false;

	private String ext = ".tif";
	private String extLabeled = null;

	private ImageServer<BufferedImage> serverLabeled;

	/**
	 * Create a builder to export tiles.
	 * @param imageData
	 */
	public TileExporter(ImageData<BufferedImage> imageData) {
		this.imageData = imageData;
	}

	/**
	 * Define the tile size in pixel units at the export resolution.
	 * @param tileSize
	 * @return
	 */
	public TileExporter tileSize(int tileSize) {
		return tileSize(tileSize, tileSize);
	}

	/**
	 * Define the horizontal and vertical tile size in pixel units at the export resolution.
	 * @param tileWidth
	 * @param tileHeight
	 * @return
	 */
	public TileExporter tileSize(int tileWidth, int tileHeight) {
		this.tileWidth = tileWidth;
		this.tileHeight = tileHeight;
		return this;
	}

	/**
	 * Define tile overlap (both x and y) in pixel units at the export resolution.
	 * @param overlap
	 * @return
	 */
	public TileExporter overlap(int overlap) {
		return overlap(overlap, overlap);
	}

	/**
	 * Define tile overlap (x and y separately) in pixel units at the export resolution.
	 * @param overlapX
	 * @param overlapY
	 * @return
	 */
	public TileExporter overlap(int overlapX, int overlapY) {
		this.overlapX = overlapX;
		this.overlapY = overlapY;
		return this;
	}

	/**
	 * Define resolution as a downsample value.
	 * @param downsample
	 * @return
	 * @see #requestedPixelSize(double)
	 */
	public TileExporter downsample(double downsample) {
		this.downsample = downsample;
		return this;
	}

	/**
	 * Define resolution as a pixel size in calibrated units. The actual units depend upon those stored within the server.
	 * @param pixelSize
	 * @return
	 */
	public TileExporter requestedPixelSize(double pixelSize) {
		this.downsample = imageData.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue() / pixelSize;
		return this;
	}

	/**
	 * Specify whether incomplete tiles at image boundaries should be included. Default is false.
	 * @param includePartialTiles
	 * @return
	 */
	public TileExporter includePartialTiles(boolean includePartialTiles) {
		this.includePartialTiles = includePartialTiles;
		return this;
	}

	/**
	 * Specify whether tiles without any annotations should be included. Default is false.
	 * @param annotatedTilesOnly
	 * @return
	 */
	public TileExporter annotatedTilesOnly(boolean annotatedTilesOnly) {
		this.annotatedTilesOnly = annotatedTilesOnly;
		return this;
	}

	/**
	 * Specify a file extension for the original pixels, which determines the export file format.
	 * @param ext
	 * @return
	 */
	public TileExporter imageExtension(String ext) {
		if (!ext.startsWith("."))
			ext = "." + ext;
		this.ext = ext;
		return this;
	}

	/**
	 * Specify a file extension for the labelled image, which determines the export file format.
	 * @param ext
	 * @return
	 */
	public TileExporter labeledImageExtension(String ext) {
		if (!ext.startsWith("."))
			ext = "." + ext;
		this.extLabeled = ext;
		return this;
	}

	public TileExporter labeledServer(ImageServer<BufferedImage> server) {
		this.serverLabeled = server;
		return this;
	}


	public void writeTiles(String dirOutput) throws IOException {

		if (!new File(dirOutput).isDirectory())
			throw new IOException("Output directory " + dirOutput + " does not exist!");

		if (serverLabeled != null) {
			if (extLabeled == null)
				extLabeled = serverLabeled.getMetadata().getChannelType() == ChannelType.CLASSIFICATION ? ".png" : ".tif";
		}

		var pool = Executors.newWorkStealingPool(4);

		var server = imageData.getServer();
		var labeledServer = serverLabeled;
		var requests = getTiledRegionRequests(server,
				downsample, tileWidth, tileHeight, overlapX, overlapY, includePartialTiles);

		String imageName = GeneralTools.stripInvalidFilenameChars(
				GeneralTools.getNameWithoutExtension(server.getMetadata().getName())
				);

		int tileWidth = includePartialTiles ? -1 : this.tileWidth;
		int tileHeight = includePartialTiles ? -1 : this.tileHeight;

		for (var r : requests) {
			String name = String.format("%s [%s]%s", imageName, getRegionString(r), ext);
			File fileOutput = new File(dirOutput, name);
			ExportTask taskImage = new ExportTask(server, r, fileOutput.getAbsolutePath(), tileWidth, tileHeight);

			ExportTask taskLabels = null;
			if (labeledServer != null) {
				name = String.format("%s [%s]-labelled%s", imageName, getRegionString(r), extLabeled);
				fileOutput = new File(dirOutput, name);
				r = RegionRequest.createInstance(labeledServer.getPath(), r);
				if (annotatedTilesOnly && labeledServer.isEmptyRegion(r))
					taskImage = null;
				else
					taskLabels = new ExportTask(labeledServer, r, fileOutput.getAbsolutePath(), tileWidth, tileHeight);
			}

			if (taskImage != null)
				pool.submit(taskImage);
			if (taskLabels != null) {
				pool.submit(taskLabels);
			}
		}

		pool.shutdown();
	}


	static class ExportTask implements Runnable {

		private ImageServer<BufferedImage> server;
		private RegionRequest request;
		private String path;
		private int tileWidth, tileHeight;

		private ExportTask(ImageServer<BufferedImage> server, RegionRequest request, String path, int tileWidth, int tileHeight) {
			this.server = server;
			this.request = request;
			this.path = path;
			this.tileWidth = tileWidth;
			this.tileHeight = tileHeight;
		}

		@Override
		public void run() {
			try {
				var img = server.readBufferedImage(request);
				if ((tileWidth > 0 && tileHeight > 0) && (img.getWidth() != tileWidth || img.getHeight() != tileHeight)) {
					logger.warn("Resizing tile from {}x{} to {}x{}", img.getWidth(), img.getHeight(), tileWidth, tileHeight);
					img = BufferedImageTools.resize(img, tileWidth, tileHeight, false);
				}
				ImageWriterTools.writeImageRegion(server, request, path);
				//				ImageWriterTools.writeImage(img, path);
			} catch (Exception e) {
				logger.error("Error writing tile: " + e.getLocalizedMessage(), e);
			}
		}

	}

	static String getRegionString(RegionRequest request) {
		String s = "x="+request.getX()+",y="+request.getY()+",w="+request.getWidth()+"h="+request.getHeight();
		if (request.getZ() != 0)
			s += ",z="+request.getZ();
		if (request.getT() != 0)
			s += ",t="+request.getT();
		return s;
	}



	static Collection<RegionRequest> getTiledRegionRequests(
			ImageServer<?> server, double downsample, 
			int tileWidth, int tileHeight, int xOverlap, int yOverlap, boolean includePartialTiles) {
		List<RegionRequest> requests = new ArrayList<>();

		for (int t = 0; t < server.nTimepoints(); t++) {
			for (int z = 0; z < server.nZSlices(); z++) {
				requests.addAll(
						splitRegionRequests(RegionRequest.createInstance(server, downsample), tileWidth, tileHeight, xOverlap, yOverlap, includePartialTiles)
						);
			}
		}
		return requests;
	}

	/**
	 * Split a single RegionRequest into multiple requests, specifying a tile size and overlap at the output resolution.
	 * This is useful, for example, to export fixed-size image tiles using a particular downsample value.
	 * <p>
	 * Note: one should be careful in case rounding errors within specific servers result in some off-by-one tile sizes, 
	 * so that additional cropping/padding/scaling may be necessary in some cases.
	 * 
	 * @param request
	 * @param tileWidth
	 * @param tileHeight
	 * @param xOverlap
	 * @param yOverlap
	 * @param includePartialTiles
	 * @return
	 */
	static Collection<RegionRequest> splitRegionRequests(
			RegionRequest request, 
			int tileWidth, int tileHeight,
			int xOverlap, int yOverlap,
			boolean includePartialTiles) {

		var set = new LinkedHashSet<RegionRequest>();

		double downsample = request.getDownsample();
		String path = request.getPath();

		int fullWidth = request.getWidth();
		int fullHeight = request.getHeight();

		int maxX = (int)(request.getMaxX() / downsample);
		int maxY = (int)(request.getMaxY() / downsample);

		int z = request.getZ();
		int t = request.getT();

		for (int y = request.getY(); y < maxY; y += tileHeight-yOverlap) {
			int th = tileHeight;
			if (y + th > maxY)
				th = maxY - y;

			int yi = (int)Math.round(y * downsample);
			int y2i = (int)Math.round((y + tileHeight) * downsample);

			if (y2i > fullHeight) {
				if (!includePartialTiles)
					continue;
				y2i = fullHeight;
			} else if (y2i == yi)
				continue;

			for (int x = request.getX(); x < maxX; x += tileWidth-xOverlap) {
				int tw = tileWidth;
				if (x + tw > maxX)
					tw = maxX - x;

				int xi = (int)Math.round(x * downsample);
				int x2i = (int)Math.round((x + tileWidth) * downsample);

				if (x2i > fullWidth) {
					if (!includePartialTiles)
						continue;
					x2i = fullWidth;
				} else if (x2i == xi)
					continue;

				var tile = RegionRequest.createInstance(path, downsample,
						xi, yi, x2i-xi, y2i-yi, z, t
						);
				set.add(tile);
			}
		}					

		return set;
	}

}
