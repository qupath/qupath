package legacy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.objects.FeatureExtractor;

@Deprecated
class PixelFeatureExtractor extends FeatureExtractor {
	
	private List<String> measurements = new ArrayList<>();
	private ImageServer<BufferedImage> server;
	private double downsample;
	private int width, height;
	private float scale = 1.0f;
	
	private transient float[] pixels;
	
	PixelFeatureExtractor(final ImageServer<BufferedImage> server, final int width, final int height, final double requestedPixelSizeMicrons) {
		super(Collections.emptyList());
		this.server = server;
		PixelCalibration cal = server.getPixelCalibration();
		this.downsample = cal.hasPixelSizeMicrons() ? requestedPixelSizeMicrons / cal.getAveragedPixelSizeMicrons() : requestedPixelSizeMicrons;
		this.width = width;
		this.height = height;
		for (int c = 0; c < server.nChannels(); c++) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < height; x++) {
					this.measurements.add(String.format("Channel %d (%d,%d)", c, x, y));
				}			
			}
		}
		
		// Automatically rescale pixel values
		if (server.getPixelType() == PixelType.UINT8)
			scale = 1/255f;
		else if (server.getPixelType() == PixelType.UINT16)
			scale = 1/65535f;
	}
	
	@Override
	public void extractFeatures(final Collection<PathObject> pathObjects, FloatBuffer buffer) {
		List<BufferedImage> images = pathObjects.parallelStream().map(p -> extractImage(p)).collect(Collectors.toList());
		extractFeatures(images, buffer);
	}
	
	@Override
	public List<String> getFeatureNames() {
		return Collections.unmodifiableList(measurements);
	}
	
	@Override
	public int nFeatures() {
		return measurements.size();
	}
	
	protected BufferedImage extractImage(final PathObject pathObject) {
		int x = (int)(pathObject.getROI().getCentroidX() - width * downsample / 2.0);
		int y = (int)(pathObject.getROI().getCentroidY() - height * downsample / 2.0);
		var request = RegionRequest.createInstance(server.getPath(), downsample, x, y, (int)Math.ceil(width*downsample), (int)Math.ceil(height*downsample));
		try {
			return server.readBufferedImage(request);
		} catch (IOException e) {
			return null;
		}
	}
	
	@Override
	public void extractFeatures(final PathObject pathObject, FloatBuffer buffer) {
		extractFeatures(Collections.singletonList(extractImage(pathObject)), buffer);
	}
	
	protected void extractFeatures(final List<BufferedImage> images, final FloatBuffer buffer) {
		for (var img : images) {
			if (img == null) {
				for (int i = 0; i < width*height*server.nChannels(); i++)
					buffer.put(Float.NaN);
			}
			var data = img.getRaster().getDataBuffer();
			for (int c = 0; c < server.nChannels(); c++) {
				pixels = img.getSampleModel().getSamples(0, 0, width, height, c, pixels, data);
				if (scale == 1)
					buffer.put(pixels);
				else {
					for (float f : pixels)
						buffer.put(f * scale);
				}
			}
		}
	}
	
}