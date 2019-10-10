package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;

public class ColorTransforms {
	
	/**
	 * Interface defining a color transform that can extract a float values from a BufferedImage.
	 * <p>
	 * The simplest example of this is to extract a single channel (band) from an image.
	 */
	public interface ColorTransform {
		
		/**
		 * Extract a (row-wise) array containing the pixels extracted from a BufferedImage.
		 * @param imageData the ImageData from which the image was read; can be necessary for some transforms (e.g. to request color deconvolution stains)
		 * @param img the image
		 * @param pixels optional preallocated array; will be used if it is long enough to hold the transformed pixels
		 * @return
		 */
		float[] extractChannel(ImageData<BufferedImage> imageData, BufferedImage img, float[] pixels);
		
		/**
		 * Query whether this transform can be applied to the specified image.
		 * Reasons why it may not be include the type or channel number being incompatible.
		 * @param imageData
		 * @return
		 */
		boolean supportsImage(ImageData<BufferedImage> imageData);
		
		/**
		 * Get a displayable name for the transform.
		 * @return
		 */
		String getName();
		
	}
	
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

	
	
	static float[] ensureArrayLength(BufferedImage img, float[] pixels) {
		int n = img.getWidth() * img.getHeight();
		if (pixels == null || pixels.length < n)
			return new float[n];
		return pixels;
	}
	
	static class ExtractChannel implements ColorTransform {
		
		private int channel;
		
		ExtractChannel(int channel) {
			this.channel = channel;
		}
	
		@Override
		public float[] extractChannel(ImageData<BufferedImage> imageData, BufferedImage img, float[] pixels) {
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

		@Override
		public boolean supportsImage(ImageData<BufferedImage> imageData) {
			return channel < imageData.getServer().nChannels();
		}
		
	}
	
	static class ExtractChannelByName implements ColorTransform {
		
		private String channelName;
		
		ExtractChannelByName(String channel) {
			this.channelName = channel;
		}
	
		@Override
		public float[] extractChannel(ImageData<BufferedImage> imageData, BufferedImage img, float[] pixels) {
			pixels = ensureArrayLength(img, pixels);
			int c = getChannelNumber(imageData.getServer());
			if (c >= 0) {
				return img.getRaster().getSamples(0, 0, img.getWidth(), img.getHeight(), c, pixels);
			}
			throw new IllegalArgumentException("No channel found with name " + channelName);
		}
		
		@Override
		public String getName() {
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
		public boolean supportsImage(ImageData<BufferedImage> imageData) {
			return getChannelNumber(imageData.getServer()) >= 0;
		}
		
	}
	
}