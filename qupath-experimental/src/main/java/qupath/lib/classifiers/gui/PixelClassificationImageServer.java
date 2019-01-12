package qupath.lib.classifiers.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.images.servers.BufferedImagePlusServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.regions.RegionRequest;

public class PixelClassificationImageServer extends AbstractTileableImageServer {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationImageServer.class);
	
	private static int DEFAULT_TILE_SIZE = 512;
	
	private String cacheDirectory;
	
	private ImageServer<BufferedImage> server;
	private PixelClassifier classifier;
	private ImageServerMetadata metadata;

	protected PixelClassificationImageServer(String cacheDirectory, Map<RegionRequest, BufferedImage> cache, ImageServer<BufferedImage> server, PixelClassifier classifier) {
		super(cache);
		this.classifier = classifier;
		this.cacheDirectory = cacheDirectory;
		this.server = server;
		
		var classifierMetadata = classifier.getMetadata();
		var path = server.getPath() + "::" + classifier.toString();
		
		var bitDepth = 8;
		
		var tileWidth = classifierMetadata.getInputWidth();
		var tileHeight = classifierMetadata.getInputHeight();
		if (tileWidth <= 0)
			tileWidth = DEFAULT_TILE_SIZE;
		if (tileHeight <= 0)
			tileHeight = DEFAULT_TILE_SIZE;
		
		double inputSizeMicrons = classifierMetadata.getInputPixelSizeMicrons();
		double downsample = inputSizeMicrons / server.getAveragedPixelSizeMicrons();
		
		int width = server.getWidth();
		int height = server.getHeight();
		
		metadata = new ImageServerMetadata.Builder(path, width, height)
				.setPreferredTileSize(tileWidth, tileHeight)
				.setPreferredDownsamples(downsample)
				.setPixelSizeMicrons(server.getPixelWidthMicrons(), server.getPixelHeightMicrons())
				.setSizeC(classifierMetadata.nOutputChannels())
				.setSizeT(server.nTimepoints())
				.setSizeZ(server.nZSlices())
				.setMagnification(server.getMagnification())
				.setBitDepth(bitDepth)
				.setRGB(false)
				.build();
				
//		var classifierMetadata = classifier.getMetadata();
//		var path = server.getPath() + "::" + classifier.toString();
//		
//		var bitDepth = 8;
//		
//		var tileWidth = classifierMetadata.getInputWidth();
//		var tileHeight = classifierMetadata.getInputHeight();
//		if (tileWidth <= 0)
//			tileWidth = DEFAULT_TILE_SIZE;
//		if (tileHeight <= 0)
//			tileHeight = DEFAULT_TILE_SIZE;
//		
//		double inputSizeMicrons = classifierMetadata.getInputPixelSizeMicrons();
//		double downsample = inputSizeMicrons / server.getAveragedPixelSizeMicrons();
//		double preferredDownsample = 1.0;
//		
//		int width = (int)(server.getWidth() / downsample);
//		int height = (int)(server.getHeight() / downsample);
//		
//		metadata = new ImageServerMetadata.Builder(path, width, height)
//				.setPreferredTileSize(tileWidth, tileHeight)
//				.setPreferredDownsamples(preferredDownsample)
//				.setPixelSizeMicrons(server.getPixelWidthMicrons() * downsample, server.getPixelHeightMicrons() * downsample)
//				.setSizeC(classifierMetadata.nOutputChannels())
//				.setSizeT(server.nTimepoints())
//				.setSizeZ(server.nZSlices())
//				.setMagnification(server.getMagnification() / downsample)
//				.setBitDepth(bitDepth)
//				.setRGB(false)
//				.build();
	}

	@Override
	public String getServerType() {
		return "Pixel classification server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	/**
	 * Not allowed - throws an {@link UnsupportedOperationException}.
	 */
	@Override
	public void setMetadata(ImageServerMetadata metadata) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("Setting metadata is not allowed!");
	}
	
	@Override
	public String getChannelName(int channel) {
		return classifier.getMetadata().getChannels().get(channel).getName();
	}
	
	@Override
	public Integer getDefaultChannelColor(int channel) {
		return classifier.getMetadata().getChannels().get(channel).getColor();
	}


	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		var path = getCachedPath(tileRequest);
		var img = path == null ? null : readFromCache(path);
		if (img == null) {
//			double downsample = classifier.getMetadata().getInputPixelSizeMicrons() / server.getAveragedPixelSizeMicrons();
//			var regionRequest = RegionRequest.createInstance(server.getPath(), downsample,
//					(int)Math.round(tileRequest.getImageX() * downsample),
//					(int)Math.round(tileRequest.getImageY() * downsample),
//					(int)Math.round(tileRequest.getImageWidth() * downsample),
//					(int)Math.round(tileRequest.getImageHeight() * downsample),
//					tileRequest.getZ(),
//					tileRequest.getT()
//					);
			img = classifier.applyClassification(server, tileRequest.getRegionRequest());
			if (path != null) {
				try {
					saveToCache(path, tileRequest.getRegionRequest(), img);
				} catch (IOException e) {
					logger.warn("Error attempting to save tile to cache: {}", e.getLocalizedMessage());
				}
			} 
		}
		return img;
	}
	
	String getCachedPath(TileRequest tileRequest) {
		if (cacheDirectory == null)
			return null;
		var name = String.format(
				"tile(x=%d,y=%d,w=%d,h=%d,z=%d,t=%d).tif",
				tileRequest.getImageX(),
				tileRequest.getImageY(),
				tileRequest.getImageWidth(),
				tileRequest.getImageHeight(),
				tileRequest.getZ(),
				tileRequest.getT());
		return new File(cacheDirectory, name).getAbsolutePath();
	}
	
	
	BufferedImage readFromCache(String path) throws IOException {
		var file = new File(path);
		if (file.exists()) {
			synchronized(this) {
				try {
					return ImageIO.read(file);
				} catch (IOException e) {
					logger.warn("Exception when reading tile from cache", e);				
				}
			}
		}
		return null;
	}
	
	/**
	 * Request the classification for a specific pixel.
	 * 
	 * @param x
	 * @param y
	 * @param c
	 * @param z
	 * @param t
	 * @return
	 * @throws IOException
	 */
	public int getClassification(int x, int y, int z, int t) throws IOException {
		var tile = getTileRequestManager().getTile(0, x, y, z, t);
		if (tile == null)
			return -1;
		int xx = (int)Math.round(x / tile.getDownsample() - tile.getTileX());
		int yy = (int)Math.round(y / tile.getDownsample() - tile.getTileY());
		var img = readBufferedImage(tile.getRegionRequest());
		
		if (xx >= img.getWidth())
			xx = img.getWidth() - 1;
		if (xx < 0)
			xx = 0;

		if (yy >= img.getHeight())
			yy = img.getHeight() - 1;
		if (yy < 0)
			yy = 0;

		int nBands = img.getRaster().getNumBands();
		if (nBands == 1) {
			try {
				return img.getRaster().getSample(xx, yy, 0);
			} catch (Exception e) {
				e.printStackTrace();
				return -1;
			}
		} else {
			int maxInd = -1;
			double maxVal = Double.NEGATIVE_INFINITY;
			for (int b = 0; b < nBands; b++) {
				double temp = img.getRaster().getSampleDouble(xx, yy, b);
				if (temp > maxVal) {
					maxInd = b;
					maxVal = temp;
				}
			}
			return maxInd;
		}
	}
	
	
	public String getCacheDirectory() {
		return cacheDirectory;
	}
	
	/**
	 * Save file.  We use ImageJ for writing
	 * 
	 * @param url
	 * @param request
	 * @param img
	 * @throws IOException
	 */
	void saveToCache(String path, RegionRequest request, BufferedImage img) throws IOException {
		var imp = BufferedImagePlusServer.convertToImagePlus("Tile", this, img, request).getImage();
//		new FileSaver(imp).
//		new Writer()
		synchronized(this) {
			ij.IJ.save(imp, path);
		}
	}

}
