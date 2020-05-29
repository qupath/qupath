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
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
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
	
	private static final String KEY_PIXEL_LAYER = "PIXEL_LAYER";
	
	private static int DEFAULT_TILE_SIZE = 512;
	
	private ImageData<BufferedImage> imageData;
	private ImageServer<BufferedImage> server;
	
	private PixelClassifier classifier;
	
	private ImageServerMetadata originalMetadata;
	
	/**
	 * Set an ImageServer as a property in the ImageData.
	 * <p>
	 * Note that this method is subject to change (in location and behavior).
	 * 
	 * @param imageData
	 * @param layerServer server to return the pixel layer data; if null, the property will be removed
	 */
	public static void setPixelLayer(ImageData<BufferedImage> imageData, ImageServer<BufferedImage> layerServer) {
		if (layerServer == null)
			imageData.removeProperty(KEY_PIXEL_LAYER);
		else
			imageData.setProperty(KEY_PIXEL_LAYER, layerServer);			
	}
	
	/**
	 * Request the pixel layer from an ImageData.
	 * <p>
	 * Note that this method is subject to change (in location and behavior).
	 * 
	 * @param imageData
	 * @return
	 */
	public static ImageServer<BufferedImage> getPixelLayer(ImageData<?> imageData) {
		var layer = imageData.getProperty(KEY_PIXEL_LAYER);
		if (layer instanceof ImageServer)
			return (ImageServer<BufferedImage>)layer;
		return null;
	}
	

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
		super();
		this.classifier = classifier;
		this.imageData = imageData;
		this.server = imageData.getServer();
		
		var classifierMetadata = classifier.getMetadata();
				
		var pixelType = PixelType.UINT8;
		
		var tileWidth = classifierMetadata.getInputWidth();
		var tileHeight = classifierMetadata.getInputHeight();
		if (tileWidth <= 0)
			tileWidth = DEFAULT_TILE_SIZE;
		if (tileHeight <= 0)
			tileHeight = DEFAULT_TILE_SIZE;
		
		var inputResolution = classifierMetadata.getInputResolution();
		var cal = server.getPixelCalibration();
		if (!(cal.getPixelWidthUnit().equals(inputResolution.getPixelWidthUnit()) && cal.getPixelHeightUnit().equals(inputResolution.getPixelHeightUnit()))) {
			logger.warn("Image pixel units do not match the classifier pixel units! This may give unexpected results.");
			logger.warn("Server calibration: {}", cal);
			logger.warn("Classifier calibration: {}", inputResolution);
		}
		double downsample = inputResolution.getAveragedPixelSize().doubleValue() / cal.getAveragedPixelSize().doubleValue();
		
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
	 * Returns a random UUID.
	 */
	@Override
	protected String createID() {
		try {
			// If we can construct a path (however long) that includes the full serialization info, then cached tiles can be reused even if the server is recreated
			return getClass().getName() + ": " + server.getPath() + "::" + GsonTools.getInstance().toJson(classifier);
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
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		BufferedImage img;
		double fullResDownsample = getDownsampleForResolution(0);
		if (tileRequest.getDownsample() != fullResDownsample && Math.abs(tileRequest.getDownsample() - fullResDownsample) > 1e-6) {
			// If we're generating lower-resolution tiles, we need to request the higher-resolution data accordingly
			var request2 = RegionRequest.createInstance(getPath(), fullResDownsample, tileRequest.getRegionRequest());
			img = readBufferedImage(request2);
			img = BufferedImageTools.resize(img, tileRequest.getTileWidth(), tileRequest.getTileHeight(), allowSmoothInterpolation());
		} else {
			// Classify at this resolution if need be
			img = classifier.applyClassification(imageData, tileRequest.getRegionRequest());
			img = BufferedImageTools.resize(img, tileRequest.getTileWidth(), tileRequest.getTileHeight(), allowSmoothInterpolation());
		}
		return img;
	}
	
	/**
	 * Returns null (does not support ServerBuilders).
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return null;
	}
	

}