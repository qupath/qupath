package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;


public class ColorTransforms {

	/**
	 * Interface defining a color transform that can extract a float values from a BufferedImage.
	 * <p>
	 * The simplest example of this is to extract a single channel (band) from an image.
	 */
	interface ColorTransform {
		
		float[] extractChannel(ImageData<BufferedImage> imageData, BufferedImage img, float[] pixels);
		
		String getName();
		
	}
	
	
	/**
	 * Create ColorTransform to extract a channel based on its number (0-based index).
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
		
	}
	
	static class ExtractChannelByName implements ColorTransform {
		
		private String channelName;
		
		ExtractChannelByName(String channel) {
			this.channelName = channel;
		}
	
		@Override
		public float[] extractChannel(ImageData<BufferedImage> imageData, BufferedImage img, float[] pixels) {
			pixels = ensureArrayLength(img, pixels);
			int i = 0;
			for (ImageChannel channel : imageData.getServer().getMetadata().getChannels()) {
				if (channelName.equals(channel.getName())) {
					return img.getRaster().getSamples(0, 0, img.getWidth(), img.getHeight(), i, pixels);
				}
				i++;
			}
			throw new IllegalArgumentException("No channel found with name " + channelName);
		}
		
		@Override
		public String getName() {
			return channelName;
		}
	
		
	}
	
}