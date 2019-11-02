package qupath.lib.images.servers;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferFloat;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.StainVector;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.regions.RegionRequest;

/**
 * An ImageServer that applies color deconvolution to extract one or more stains from a wrapped (brightfield, RGB) ImageServer.
 * <p>
 * Warning! This is incomplete and will be changed/removed in the future.
 * 
 * @author Pete Bankhead
 *
 */
class ColorDeconvolutionImageServer extends TransformingImageServer<BufferedImage> {
	
	private final static Logger logger = LoggerFactory.getLogger(ColorDeconvolutionImageServer.class);
	
	private ColorDeconvolutionStains stains;
	private List<ColorTransformMethod> methods;
	private List<StainVector> stainVectors;
	private ImageServerMetadata metadata;
	private ColorModel colorModel;

	public ColorDeconvolutionImageServer(ImageServer<BufferedImage> server, ColorDeconvolutionStains stains, int... stainNumbers) {
		super(server);
		this.stains = stains;
		
		this.methods = new ArrayList<>();
		if (stainNumbers.length == 0)
			stainNumbers = new int[] {1, 2, 3};
		List<ImageChannel> channels = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		for (int s : stainNumbers) {
			if (s < 1 || s > 3) {
				logger.warn("Invalid stain number {}, must be >= 1 and <= 3 (i.e. 'one-based')", s);
				continue;
			}
			if (sb.length() == 1)
				sb.append(s);
			else
				sb.append(",").append(s);
			StainVector stain = stains.getStain(s);
			stainVectors.add(stain);
			
			channels.add(ImageChannel.getInstance(stain.getName(), stain.getColor()));
			
//			channels.add(ImageChannel.getInstance(stain.getName(), ImageChannel.getDefaultChannelColor(i++)));
			switch (s) {
			case 1:
				methods.add(ColorTransformMethod.Stain_1);
				break;
			case 2:
				methods.add(ColorTransformMethod.Stain_2);
				break;
			case 3:
				methods.add(ColorTransformMethod.Stain_3);
				break;
			}
		}
		this.stainVectors = Collections.unmodifiableList(stainVectors);
		
		this.colorModel = ColorModelFactory.getProbabilityColorModel32Bit(channels);
		
		metadata = new ImageServerMetadata.Builder(server.getMetadata())
//				.path(String.format("%s, %s (%s)", server.getPath(), stains.toString(), sb.toString()))
				.pixelType(PixelType.FLOAT32)
				.rgb(false)
				.channels(channels)
				.name(String.format("%s (%s)", server.getMetadata().getName(), stains.toString()))
				.build();
	}
	
	/**
	 * Returns null (does not support ServerBuilders).
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return null;
	}
	
	/**
	 * Returns a UUID.
	 */
	@Override
	protected String createID() {
		return UUID.randomUUID().toString();
	}
	
	/**
	 * Get the stains applied to color deconvolve the wrapped image server.
	 * @return
	 */
	public ColorDeconvolutionStains getStains() {
		return stains;
	}
	
	/**
	 * Get the StainVectors actually used (possibly a subset of the StainVectors included in {@link #getStains()}).
	 * @return
	 */
	public List<StainVector> getStainVectors() {
		return stainVectors;
	}
	
	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
		BufferedImage img = getWrappedServer().readBufferedImage(request);
		
		int w = img.getWidth();
		int h = img.getHeight();
		int nChannels = methods.size();
		
		SampleModel model = new BandedSampleModel(DataBuffer.TYPE_FLOAT, w, h, nChannels);
		float[][] bytes = new float[nChannels][w*h];
		DataBufferFloat buffer = new DataBufferFloat(bytes, w*h);
		WritableRaster raster = Raster.createWritableRaster(model, buffer, null);
		
		int[] rgb = img.getRGB(0, 0, w, h, null, 0, img.getWidth());
		float[] pixels = new float[w * h];
		for (int b = 0; b < methods.size(); b++) {
			ColorTransformer.getTransformedPixels(rgb, methods.get(b), pixels, stains);			
			raster.setSamples(0, 0, img.getWidth(), img.getHeight(), b, pixels);
		}
		return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), false, null);
		
//		WritableRaster raster = WritableRaster.createInterleavedRaster(DataBuffer.TYPE_FLOAT, img.getWidth(), img.getHeight(), 1, null);
//		ColorTransformer.getTransformedPixels(rgb, method, pixels, stains);
//		raster.setSamples(0, 0, img.getWidth(), img.getHeight(), 0, pixels);
//		return new BufferedImage(colorModel, raster, false, null);
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	
	@Override
	public String getServerType() {
		return "Color deconvolution server";
	}

}
