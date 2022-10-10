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

package qupath.lib.classifiers.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.ImageServerMetadata.ImageResolutionLevel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer that delivers pixels derived from applying a PixelClassifier to another ImageServer.
 *
 * @author Pete Bankhead
 *
 */
public class PixelClassificationImageServer extends AbstractTileableImageServer {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationImageServer.class);
	
	private static int DEFAULT_TILE_SIZE = 512;
	
	private ImageData<BufferedImage> imageData;
	private ImageServer<BufferedImage> server;
	
	private String customID;
	
	private PixelClassifier classifier;
	private ColorModel colorModel;
	
	private ImageServerMetadata originalMetadata;
	
	private static Map<String, String> idCache = new HashMap<>();
	
	/**
	 *  Some classifiers cache all their tiles.
	 *  This is useful for classifiers that depend upon {@link ImageData} that may change.
	 */
	private Map<TileRequest, BufferedImage> tileMap;
	

	/**
	 * Constructor.
	 * <p>
	 * An {@link ImageData} is required because some forms of classification may required additional image properties 
	 * (e.g. image type, stains), not simply an {@link ImageServer}.
	 * 
	 * @param imageData
	 * @param classifier
	 */
	public PixelClassificationImageServer(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		this(imageData, classifier, null, null);
	}
		
	/**
	 * Constructor.
	 * @param imageData
	 * @param classifier
	 * @param customID optionally provide a custom ID (path). This is when the default (based upon the {@link ImageData} and {@link PixelClassifier} isn't sufficient), 
	 *                 e.g. because the classifier can change output based upon {@link ImageData} status.
	 * @param colorModel optional colormodel
	 */
	public PixelClassificationImageServer(ImageData<BufferedImage> imageData, PixelClassifier classifier, String customID, ColorModel colorModel) {
		super();
		this.classifier = classifier;
		this.imageData = imageData;
		this.customID = customID;
		this.server = imageData.getServer();
		this.colorModel = colorModel;
		
		var classifierMetadata = classifier.getMetadata();
				
		var pixelType = classifierMetadata.getOutputPixelType();
		if (pixelType == null) {
			logger.debug("PixelType is unknown - will use default of UINT8");
			pixelType = PixelType.UINT8;
		}
		
		var tileWidth = classifierMetadata.getInputWidth();
		var tileHeight = classifierMetadata.getInputHeight();
		if (tileWidth <= 0)
			tileWidth = DEFAULT_TILE_SIZE;
		if (tileHeight <= 0)
			tileHeight = DEFAULT_TILE_SIZE;
		
		var inputResolution = classifierMetadata.getInputResolution();
		var cal = server.getPixelCalibration();
		if (inputResolution == null) {
			logger.warn("Input resolution not specified in PixelClassifier metadata - will assume full resolution");
			inputResolution = cal;
		}
		double downsample = inputResolution.getAveragedPixelSize().doubleValue() / cal.getAveragedPixelSize().doubleValue();
		if (!(cal.getPixelWidthUnit().equals(inputResolution.getPixelWidthUnit()) && cal.getPixelHeightUnit().equals(inputResolution.getPixelHeightUnit()))) {
			if (inputResolution.unitsMatch2D() && PixelCalibration.PIXEL.equals(inputResolution.getPixelWidthUnit())) {
				downsample = inputResolution.getAveragedPixelSize().doubleValue();
				logger.warn("Input resolution is requested in uncalibrated units - will use downsample of {}", downsample);
			} else {
				logger.warn("Image pixel units do not match the classifier pixel units! This may give unexpected results.");
				logger.warn("Server calibration: {}", cal);
				logger.warn("Classifier calibration: {}", inputResolution);
			}
		}
		
		int width = server.getWidth();
		int height = server.getHeight();
		
		var levels = new ImageResolutionLevel.Builder(width, height)
						.addLevelByDownsample(downsample)
						.build();
		
//		int pad = classifierMetadata.strictInputSize() ? classifierMetadata.getInputPadding() : 0;
		int pad = classifierMetadata.getInputPadding();
		
		var builder = new ImageServerMetadata.Builder(server.getMetadata())
				.width(width)
				.height(height)
				.channelType(classifierMetadata.getOutputType())
				.preferredTileSize(tileWidth-pad*2, tileHeight-pad*2)
				.levels(levels)
				.pixelType(pixelType)
				.classificationLabels(classifierMetadata.getClassificationLabels())
				.rgb(false);
		
		if (classifierMetadata.getOutputType() != ChannelType.CLASSIFICATION)
			builder.channels(classifierMetadata.getOutputChannels());
		
//		if (classifierMetadata.getOutputType() == ChannelType.PROBABILITY)
//			.channels(classifierMetadata.getOutputChannels())

		
		originalMetadata = builder.build();
		
		
	}
	
	/**
	 * Read all the tiles.
	 * This is useful for a classifier that can be applied in full to an image without causing memory issues 
	 * (e.g. a density map), particularly if it is is dependent upon a changing property of the image 
	 * (e.g. its object hierarchy).
	 * After calling this method, tiles will be returned from an internal cache rather than being computed anew.
	 */
	public synchronized void readAllTiles() {
		if (tileMap != null)
			return;
		
		var tempTileMap = getTileRequestManager()
					.getAllTileRequests()
					.parallelStream()
					.filter(t -> !isEmptyRegion(t.getRegionRequest()))
					.collect(Collectors.toMap(t -> t, t -> tryToReadTile(t)));
		
		var iter = tempTileMap.entrySet().iterator();
		while (iter.hasNext()) {
			var next = iter.next();
			if (next.getValue() == null)
				iter.remove();
		}
		tileMap = tempTileMap;
	}
	
	
	private BufferedImage tryToReadTile(TileRequest tile) {
		try {
			return readTile(tile);
		} catch (IOException e) {
			logger.warn("Unable to read tile: " + e.getLocalizedMessage(), e);
//			logger.debug("Unable to read tile: " + e.getLocalizedMessage(), e);
			return null;
		}
	}
	
	
	@Override
	protected ColorModel getDefaultColorModel() throws IOException {
		if (colorModel == null)
			return super.getDefaultColorModel();
		return colorModel;
	}
	
	/**
	 * Returns a random UUID.
	 */
	@Override
	protected String createID() {
		if (customID != null)
			return customID;
		try {
			// If we can construct a path (however long) that includes the full serialization info, then cached tiles can be reused even if the server is recreated.
			// However, because a serialized classifier might be many MB in size (resulting in performance issues with RegionRequest), 
			// we truncate astronomical ones and add a UUID for uniqueness.
			String json = GsonTools.getInstance().toJson(classifier);
			String suffix;
			if (json.length() < 1000)
				suffix = json;
			else {
				suffix = idCache.computeIfAbsent(json, j -> json.substring(0, 1000) + "... (" + UUID.randomUUID() + ")");
			}
			return getClass().getName() + ": " + server.getPath() + "::" + suffix;
		} catch (Exception e) {
			logger.debug("Unable to serialize pixel classifier to JSON: {}", e.getLocalizedMessage());
			return getClass().getName() + ": " + server.getPath() + "::" + UUID.randomUUID().toString();
		}
	}
	
	@Override
	public Collection<URI> getURIs() {
		return Collections.emptyList();
	}
	
	/**
	 * Get the underlying ImageData used for classification.
	 * @return
	 */
	public ImageData<BufferedImage> getImageData() {
		return imageData;
	}
	
	/**
	 * Get the PixelClassifier performing the classification.
	 * @return
	 */
	public PixelClassifier getClassifier() {
		return classifier;
	}

	@Override
	public String getServerType() {
		return "Pixel classification server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}
	
	/**
	 * Not allowed - throws an {@link UnsupportedOperationException}.
	 */
	@Override
	public void setMetadata(ImageServerMetadata metadata) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("Setting metadata is not allowed!");
	}
	
	
	@Override
	public BufferedImage getCachedTile(TileRequest tile) {
		if (tileMap != null && tileMap.containsKey(tile))
			return tileMap.get(tile);
		return super.getCachedTile(tile);
	}


	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		try {
			BufferedImage img;
			double fullResDownsample = getDownsampleForResolution(0);
			if (tileRequest.getDownsample() != fullResDownsample && Math.abs(tileRequest.getDownsample() - fullResDownsample) > 1e-6) {
				// If we're generating lower-resolution tiles, we need to request the higher-resolution data accordingly
				var request2 = RegionRequest.createInstance(getPath(), fullResDownsample, tileRequest.getRegionRequest());
				img = readRegion(request2);
				img = BufferedImageTools.resize(img, tileRequest.getTileWidth(), tileRequest.getTileHeight(), allowSmoothInterpolation());
			} else {
				// Classify at this resolution if need be
				img = classifier.applyClassification(imageData, tileRequest.getRegionRequest());
				img = BufferedImageTools.resize(img, tileRequest.getTileWidth(), tileRequest.getTileHeight(), allowSmoothInterpolation());
			}
			// If we have specified a color model, apply it now
			if (colorModel != null && colorModel != img.getColorModel() && colorModel.isCompatibleRaster(img.getRaster())) {
				img = new BufferedImage(colorModel, img.getRaster(), img.isAlphaPremultiplied(), null);
			}
			return img;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		} catch (Error e) {
			// Because sometimes we have library loading problems (e.g. OpenCV) and need to report this somehow, 
			// even if called within a stream
			throw new IOException(e);
		}
	}
	
	/**
	 * Returns null (does not support ServerBuilders).
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return null;
	}
	

}