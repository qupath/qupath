package qupath.lib.classifiers.gui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import ij.io.FileSaver;
import qupath.imagej.images.servers.BufferedImagePlusServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata.OutputType;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.RegionRequest;

public class PixelClassificationImageServer extends AbstractTileableImageServer {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationImageServer.class);
	
	private static int DEFAULT_TILE_SIZE = 512;
	
	private String cacheDirectory;
	private transient PersistentTileCache persistentTileCache;
	
	private ImageData<BufferedImage> imageData;
	private ImageServer<BufferedImage> server;
	private PixelClassifier classifier;
	private ImageServerMetadata metadata;

	protected PixelClassificationImageServer(String cacheDirectory, Map<RegionRequest, BufferedImage> cache, ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		super(cache);
		this.classifier = classifier;
		this.cacheDirectory = cacheDirectory;
		this.imageData = imageData;
		this.server = imageData.getServer();
		
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
		
		// This code makes it possible for the classification server to return downsampled values
		// The idea is that this might help performance... but it raises questions around interpolating 
		// classifications and can result in the appearance not matching expectations.
//		List<Double> downsampleValues = new ArrayList<>();
//		double factor = 1;
//		do {
//			downsampleValues.add(downsample * factor);
//			factor *= 4;
//		} while (Math.min(tileWidth, tileHeight) / factor > 16);
//		double[] downsamples = downsampleValues.stream().mapToDouble(d -> d).toArray();
		double[] downsamples = new double[] {downsample};
		
		int width = server.getWidth();
		int height = server.getHeight();
		
		metadata = new ImageServerMetadata.Builder(path, width, height)
				.setPreferredTileSize(tileWidth, tileHeight)
				.setPreferredDownsamples(downsamples)
				.setPixelSizeMicrons(server.getPixelWidthMicrons(), server.getPixelHeightMicrons())
				.channels(classifierMetadata.getChannels())
				.setSizeT(server.nTimepoints())
				.setSizeZ(server.nZSlices())
				.setMagnification(server.getMagnification())
				.setBitDepth(bitDepth)
				.setRGB(false)
				.build();
		
		if (cacheDirectory != null) {
			try {
				persistentTileCache = new FileSystemPersistentTileCache(Paths.get(cacheDirectory));
				persistentTileCache.writeJSON("metadata.json", getMetadata());
				persistentTileCache.writeJSON("classifier.json", classifier);
			} catch (Exception e) {
				logger.error("Unable to create persistent tile cache", e);
			}
		}
				
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
	
	
	public PixelClassifier getClassifier() {
		return classifier;
	}
	
	
	public void close() throws Exception {
		if (persistentTileCache != null)
			persistentTileCache.close();
		super.close();
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
		var img = tryReadFromCache(tileRequest.getRegionRequest());
		if (img != null && img.getRaster().getNumBands() != nChannels())
			img = null;
		if (img == null) {
			double fullResDownsample = getDownsampleForResolution(0);
			if (tileRequest.getDownsample() != fullResDownsample && Math.abs(tileRequest.getDownsample() - fullResDownsample) > 1e-6) {
				// If we're generating lower-resolution tiles, we need to request the higher-resolution data accordingly
				var request2 = RegionRequest.createInstance(getPath(), fullResDownsample, tileRequest.getRegionRequest());
				img = readBufferedImage(request2);
				img = resize(img, tileRequest.getImageWidth(), tileRequest.getTileHeight());
			} else {
				// Classify at this resolution if need be
				img = classifier.applyClassification(imageData, tileRequest.getRegionRequest());
			}
			// Save to cache in a background thread
			// It helps to return fast so that the tile can be cached locally as soon as possible
			var imgClassified = img;
			CompletableFuture.runAsync(() -> trySaveToCache(tileRequest.getRegionRequest(), imgClassified));
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
	
	
	BufferedImage tryReadFromCache(RegionRequest request) {
		if (persistentTileCache != null) {
			try {
				return persistentTileCache.readFromCache(request);
			} catch (IOException e) {
				logger.warn("Exception when reading tile from cache", e);				
			}
		}
		return null;
	}
	
	boolean trySaveToCache(RegionRequest request, BufferedImage img) {
		if (persistentTileCache != null) {
			try {
				persistentTileCache.saveToCache(request, img);
				return true;
			} catch (IOException e) {
				logger.warn("Exception when writing tile to cache", e);				
			}
		}
		return false;
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
		
		var type = classifier.getMetadata().getOutputType();
		if (type != OutputType.Classification && type != OutputType.Probability)
			return -1;
		
		var tile = getTile(0, x, y, z, t);
		if (tile == null)
			return -1;
		
		int xx = (int)Math.floor(x / tile.getDownsample() - tile.getTileX());
		int yy = (int)Math.floor(y / tile.getDownsample() - tile.getTileY());
		var img = getTile(tile);
		
		if (xx >= img.getWidth())
			xx = img.getWidth() - 1;
		if (xx < 0)
			xx = 0;

		if (yy >= img.getHeight())
			yy = img.getHeight() - 1;
		if (yy < 0)
			yy = 0;

		int nBands = img.getRaster().getNumBands();
		if (nBands == 1 && type == OutputType.Classification) {
			try {
				return img.getRaster().getSample(xx, yy, 0);
			} catch (Exception e) {
				logger.error("Error requesting classification", e);
				return -1;
			}
		} else if (type == OutputType.Probability) {
			int maxInd = -1;
			double maxVal = Double.NEGATIVE_INFINITY;
			var raster = img.getRaster();
			for (int b = 0; b < nBands; b++) {
				double temp = raster.getSampleDouble(xx, yy, b);
				if (temp > maxVal) {
					maxInd = b;
					maxVal = temp;
				}
			}
			return maxInd;
		}
		return -1;
	}
	
	
	public String getCacheDirectory() {
		return cacheDirectory;
	}
	
	
	
	
	interface PersistentTileCache extends AutoCloseable {
		
		default public String getCachedName(RegionRequest request) {
			return String.format(
					"tile(x=%d,y=%d,w=%d,h=%d,z=%d,t=%d).tif",
					request.getX(),
					request.getY(),
					request.getWidth(),
					request.getHeight(),
					request.getZ(),
					request.getT());
		}
		
		public void writeJSON(String name, Object o) throws IOException;
		
		public BufferedImage readFromCache(RegionRequest request) throws IOException;
		
		public void saveToCache(RegionRequest request, BufferedImage img) throws IOException;
		
	}

	
	class FileSystemPersistentTileCache implements PersistentTileCache {
		
		private Path path;
		private FileSystem fileSystem;
		private String root;
		
		FileSystemPersistentTileCache(Path path) throws IOException, URISyntaxException {
			this.path = path;
			initializeFileSystem();
		}
		
		private void initializeFileSystem() throws IOException {
			if (path.toString().toLowerCase().endsWith(".zip")) {
				var fileURI = path.toUri();
				try {
					var uri = new URI("jar:" + fileURI.getScheme(), fileURI.getPath(), null);
					this.fileSystem = FileSystems.newFileSystem(uri, Collections.singletonMap("create", String.valueOf(Files.notExists(path))));
					this.root = "/";
				} catch (URISyntaxException e) {
					logger.error("Problem constructing file system", e);
				}
			}
			if (this.fileSystem == null) {
				this.fileSystem = FileSystems.getDefault();
				this.root = path.toString();
			}
		}
		
		
		
		
		public void close() throws Exception {
			if (this.fileSystem != FileSystems.getDefault())
				this.fileSystem.close();
		}
		
		
		public void writeJSON(String name, Object o) throws IOException {
			var gson = new GsonBuilder()
					.setLenient()
					.serializeSpecialFloatingPointValues()
					.setPrettyPrinting()
					.create();
			var json = gson.toJson(o);
			synchronized (fileSystem) {
				var path = fileSystem.getPath(root, name);
				Files.writeString(path, json);
			}
		}
		
		
		
		@Override
		public BufferedImage readFromCache(RegionRequest request) throws IOException {
			synchronized (fileSystem) {
				var path = fileSystem.getPath(root, getCachedName(request));
				if (Files.exists(path)) {
					try (var stream = Files.newInputStream(path)) {
						// TODO: Read using ImageJ
//						var imp = new Opener().openTiff(stream, "Anything");
						var img = ImageIO.read(stream);
						if (img.getColorModel() instanceof IndexColorModel)
							return new BufferedImage(
									ClassificationColorModelFactory.geClassificationColorModel(classifier.getMetadata().getChannels()),
									img.getRaster(),
									img.isAlphaPremultiplied(),
									null
									);
						else if (img.getRaster().getDataBuffer() instanceof DataBufferByte)
							return new BufferedImage(
									ClassificationColorModelFactory.geProbabilityColorModel8Bit(classifier.getMetadata().getChannels()),
									img.getRaster(),
									img.isAlphaPremultiplied(),
									null
									);
						else
							return new BufferedImage(
									ClassificationColorModelFactory.geProbabilityColorModel32Bit(classifier.getMetadata().getChannels()),
									img.getRaster(),
									img.isAlphaPremultiplied(),
									null
									);
					}
				}
			}
			return null;
		}
		
		@Override
		public void saveToCache(RegionRequest request, BufferedImage img) throws IOException {
			var imp = BufferedImagePlusServer.convertToImagePlus("Tile", PixelClassificationImageServer.this, img, request).getImage();
			var bytes = new FileSaver(imp).serialize();
			synchronized (fileSystem) {
				var path = fileSystem.getPath(root, getCachedName(request));
				Files.write(path, bytes);				
			}
		}
		
	}
	

}
