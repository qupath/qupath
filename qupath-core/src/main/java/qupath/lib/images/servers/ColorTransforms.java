package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;

/**
 * Color transforms that may be used to extract single-channel images from BufferedImages.
 * These are JSON-serializable, and therefore can be used with pixel classifiers.
 * 
 * @author Pete Bankhead
 */
public class ColorTransforms {
	
	/**
	 * Interface defining a color transform that can extract a float values from a BufferedImage.
	 * <p>
	 * The simplest example of this is to extract a single channel (band) from an image.
	 */
	public interface ColorTransform {
		
		/**
		 * Extract a (row-wise) array containing the pixels extracted from a BufferedImage.
		 * @param server the server from which the image was read; can be necessary for some transforms (e.g. to request color deconvolution stains)
		 * @param img the image
		 * @param pixels optional preallocated array; will be used if it is long enough to hold the transformed pixels
		 * @return
		 */
		float[] extractChannel(ImageServer<BufferedImage> server, BufferedImage img, float[] pixels);
		
		/**
		 * Query whether this transform can be applied to the specified image.
		 * Reasons why it may not be include the type or channel number being incompatible.
		 * @param server
		 * @return
		 */
		boolean supportsImage(ImageServer<BufferedImage> server);
		
		/**
		 * Get a displayable name for the transform.
		 * @return
		 */
		String getName();
		
	}
	
	/**
	 * {@link TypeAdapter} to support serializing a {@link ColorTransform}.
	 */
	public static class ColorTransformTypeAdapter extends TypeAdapter<ColorTransform> {
		
		private static Gson gson = new GsonBuilder().setLenient().create();

		@Override
		public void write(JsonWriter out, ColorTransform value) throws IOException {
			gson.toJson(gson.toJsonTree(value), out);
		}

		@Override
		public ColorTransform read(JsonReader in) throws IOException {
			JsonObject obj = gson.fromJson(in, JsonObject.class);
			if (obj.has("channel"))
				return new ExtractChannel(obj.get("channel").getAsInt());
			if (obj.has("channelName"))
				return new ExtractChannelByName(obj.get("channelName").getAsString());
			if (obj.has("combineType")) {
				String combine = obj.get("combineType").getAsString();
				switch (CombineType.valueOf(combine)) {
				case MAXIMUM:
					return new MaxChannels();
				case MEAN:
					return new AverageChannels();
				case MINIMUM:
					return new MinChannels();
				default:
					break;
				}
			}
			throw new IOException("Unknown ColorTransform " + obj);
		}
		
	}
	
	
	/**
	 * Create ColorTransform to extract a channel based on its number (0-based index, although result of {@link ColorTransform#getName()} is 1-based).
	 * @param channel
	 * @return
	 */
	public static ColorTransform createChannelExtractor(int channel) {
		return new ExtractChannel(channel);
	}

	/**
	 * Create ColorTransform to extract a channel based on its name.
	 * @param channelName
	 * @return
	 */
	public static ColorTransform createChannelExtractor(String channelName) {
		return new ExtractChannelByName(channelName);
	}
	
	/**
	 * Create a ColorTransform that calculates the mean of all channels.
	 * @return
	 */
	public static ColorTransform createMeanChannelTransform() {
		return new AverageChannels();
	}
	
	/**
	 * Create a ColorTransform that applies color deconvolution.
	 * @param stains the stains (this will be 'fixed', and not adapted for each image)
	 * @param stainNumber number of the stain (1, 2 or 3)
	 * @return
	 */
	public static ColorTransform createColorDeconvolvedChannel(ColorDeconvolutionStains stains, int stainNumber) {
		return new ColorDeconvolvedChannel(stains, stainNumber);
	}
	
	/**
	 * Create a ColorTransform that calculates the maximum of all channels.
	 * @return
	 */
	public static ColorTransform createMaximumChannelTransform() {
		return new MaxChannels();
	}

	
	/**
	 * Create a ColorTransform that calculates the minimum of all channels.
	 * @return
	 */
	public static ColorTransform createMinimumChannelTransform() {
		return new MinChannels();
	}


	
	
	static float[] ensureArrayLength(BufferedImage img, float[] pixels) {
		int n = img.getWidth() * img.getHeight();
		if (pixels == null || pixels.length < n)
			return new float[n];
		return pixels;
	}
	
	
	static class ColorDeconvolvedChannel implements ColorTransform {
		
		private ColorDeconvolutionStains stains;
		private int stainNumber;
		private transient ColorTransformMethod method;
		
		ColorDeconvolvedChannel(ColorDeconvolutionStains stains, int stainNumber) {
			this.stains = stains;
			this.stainNumber = stainNumber;
		}

		@Override
		public float[] extractChannel(ImageServer<BufferedImage> server, BufferedImage img, float[] pixels) {
			int[] rgb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
			return ColorTransformer.getTransformedPixels(rgb, getMethod(), pixels, stains);
		}
		
		private ColorTransformMethod getMethod() {
			if (method == null) {
				switch (stainNumber) {
				case 1: return ColorTransformMethod.Stain_1;
				case 2: return ColorTransformMethod.Stain_2;
				case 3: return ColorTransformMethod.Stain_3;
				default: throw new IllegalArgumentException("Stain number is " + stainNumber + ", but must be between 1 and 3!");
				}
			}
			return method;
		}

		@Override
		public boolean supportsImage(ImageServer<BufferedImage> server) {
			return server.isRGB() && server.getPixelType() == PixelType.UINT8;
		}

		@Override
		public String getName() {
			return stains.getStain(stainNumber).getName();
		}
		
		@Override
		public String toString() {
			return getName();
		}
		
	}
	
	
	static class ExtractChannel implements ColorTransform {
		
		private int channel;
		
		ExtractChannel(int channel) {
			this.channel = channel;
		}
	
		@Override
		public float[] extractChannel(ImageServer<BufferedImage> server, BufferedImage img, float[] pixels) {
			pixels = ensureArrayLength(img, pixels);
			return img.getRaster().getSamples(0, 0, img.getWidth(), img.getHeight(), channel, pixels);
		}
		
		@Override
		public String getName() {
			return "Channel " + (channel + 1);
		}
		
		@Override
		public String toString() {
			return getName();
		}
		
		/**
		 * Get the channel number to extract (0-based index).
		 * @return
		 */
		public int getChannelNumber() {
			return channel;
		}

		@Override
		public boolean supportsImage(ImageServer<BufferedImage> server) {
			return channel < server.nChannels();
		}
		
	}
	
	static class ExtractChannelByName implements ColorTransform {
		
		private String channelName;
		
		ExtractChannelByName(String channel) {
			this.channelName = channel;
		}
	
		@Override
		public float[] extractChannel(ImageServer<BufferedImage> server, BufferedImage img, float[] pixels) {
			pixels = ensureArrayLength(img, pixels);
			int c = getChannelNumber(server);
			if (c >= 0) {
				return img.getRaster().getSamples(0, 0, img.getWidth(), img.getHeight(), c, pixels);
			}
			throw new IllegalArgumentException("No channel found with name " + channelName);
		}
		
		@Override
		public String getName() {
			return channelName;
		}
		
		/**
		 * Get the channel name to extract.
		 * @return
		 */
		public String getChannelName() {
			return channelName;
		}
	
		@Override
		public String toString() {
			return getName();
		}
		
		private int getChannelNumber(ImageServer<BufferedImage> server) {
			int i = 0;
			for (ImageChannel channel : server.getMetadata().getChannels()) {
				if (channelName.equals(channel.getName())) {
					return i;
				}
				i++;
			}
			return -1;
		}

		@Override
		public boolean supportsImage(ImageServer<BufferedImage> server) {
			return getChannelNumber(server) >= 0;
		}
		
	}
	
	
	static abstract class CombineChannels implements ColorTransform {
				
		@Override
		public float[] extractChannel(ImageServer<BufferedImage> server, BufferedImage img, float[] pixels) {
			pixels = ensureArrayLength(img, pixels);
			int w = img.getWidth();
			int h = img.getHeight();
			var raster = img.getRaster();
			double[] vals = null;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					vals = raster.getPixel(x, y, vals);
					pixels[y*w+x] = (float)computeValue(vals);
				}
			}
			return pixels;
		}
		
		abstract double computeValue(double[] values);

		@Override
		public boolean supportsImage(ImageServer<BufferedImage> server) {
			return true;
		}
		
		@Override
		public String toString() {
			return getName();
		}
		
	}
	
	/**
	 * Store the {@link CombineType}. This is really to add deserialization from JSON.
	 */
	private static enum CombineType {MEAN, MINIMUM, MAXIMUM}
	
	
	static class AverageChannels extends CombineChannels {
		
		@SuppressWarnings("unused")
		private CombineType combineType = CombineType.MEAN;
		
		@Override
		public double computeValue(double[] values) {
			int n = values.length;
			double mean = 0;
			for (double v : values)
				mean += v/n;
			return mean;
		}

		@Override
		public String getName() {
			return "Average channels";
		}
		
	}
	
	static class MaxChannels extends CombineChannels {
		
		@SuppressWarnings("unused")
		private CombineType combineType = CombineType.MAXIMUM;
		
		@Override
		public double computeValue(double[] values) {
			int n = values.length;
			if (n == 0)
				return Double.NaN;
			double max = Double.NEGATIVE_INFINITY;
			for (double v : values) {
				if (v > max)
					max = v;
			}
			return max;
		}

		@Override
		public String getName() {
			return "Max channels";
		}
		
	}
	
	static class MinChannels extends CombineChannels {
		
		@SuppressWarnings("unused")
		private CombineType combineType = CombineType.MINIMUM;
		
		@Override
		public double computeValue(double[] values) {
			int n = values.length;
			if (n == 0)
				return Double.NaN;
			double min = Double.POSITIVE_INFINITY;
			for (double v : values) {
				if (v < min)
					min = v;
			}
			return min;
		}

		@Override
		public String getName() {
			return "Min channels";
		}
		
	}
	
}