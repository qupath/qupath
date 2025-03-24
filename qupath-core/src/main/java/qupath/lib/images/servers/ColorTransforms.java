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

package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.io.GsonTools;

/**
 * Color transforms that may be used to extract single-channel images from BufferedImages.
 * These are JSON-serializable, and therefore can be used with pixel classifiers.
 * 
 * @author Pete Bankhead
 */
public class ColorTransforms {

	private ColorTransforms() {
		throw new AssertionError("This class is not instantiable.");
	}
	
	/**
	 * Interface defining a color transform that can extract a float value from a BufferedImage.
	 * <p>
	 * The simplest example of this is to extract a single channel (band) from an image.
	 * <p>
	 * Note that only implementations of this interface present in this file will be correctly
	 * serialized/deserialized into JSON, and not custom implementations. As such, some features
	 * of QuPath (such as saving a ColorTransform in a project) won't work for custom implementations.
	 */
	public interface ColorTransform {

		/**
		 * Extract a (row-wise) array containing the pixels extracted from a BufferedImage.
		 *
		 * @param server the server from which the image was read; can be necessary for some transforms (e.g. to request color deconvolution stains)
		 * @param img the image
		 * @param pixels optional preallocated array; will be used if it is long enough to hold the transformed pixels
		 * @return a (row-wise) array containing the transformed pixels of the provided image
		 */
		float[] extractChannel(ImageServer<BufferedImage> server, BufferedImage img, float[] pixels);

		/**
		 * Query whether this transform can be applied to the specified image.
		 * Reasons why it may not be supported include the type or channel number being incompatible.
		 *
		 * @param server the server from which the image will be read
		 * @return whether this transform can be applied to the provided image
		 */
		boolean supportsImage(ImageServer<BufferedImage> server);
		
		/**
		 * Get a displayable name for the transform. Can be null
		 */
		String getName();
	}
	
	/**
	 * {@link TypeAdapter} to support serializing a {@link ColorTransform}.
	 */
	public static class ColorTransformTypeAdapter extends TypeAdapter<ColorTransform> {

		private static final Gson gson = new GsonBuilder().setStrictness(Strictness.LENIENT).create();

		@Override
		public void write(JsonWriter out, ColorTransform value) throws IOException {
			gson.toJson(gson.toJsonTree(value), out);
		}

		@Override
		public ColorTransform read(JsonReader in) throws IOException {
			JsonObject obj = gson.fromJson(in, JsonObject.class);

			if (obj.has("channel")) {
				return new ExtractChannel(obj.get("channel").getAsInt());
			} else if (obj.has("channelName")) {
				return new ExtractChannelByName(obj.get("channelName").getAsString());
			} else if (obj.has("channelNamesToCoefficients") || obj.has("channelIndicesToCoefficients")) {
				Map<String, Double> channelNamesToCoefficients = null;
				List<Double> channelIndicesToCoefficients = null;

				if (obj.get("channelNamesToCoefficients") != null) {
					channelNamesToCoefficients = gson.fromJson(obj.get("channelNamesToCoefficients"), new TypeToken<Map<String, Double>>() {}.getType());
				}
				if (obj.get("channelIndicesToCoefficients") != null) {
					channelIndicesToCoefficients = obj.get("channelIndicesToCoefficients").getAsJsonArray().asList().stream().map(JsonElement::getAsDouble).toList();
				}

				return new LinearCombinationChannel(channelNamesToCoefficients, channelIndicesToCoefficients);
			} else if (obj.has("stains")) {
				return new ColorDeconvolvedChannel(
						GsonTools.getInstance().fromJson(obj.get("stains"), ColorDeconvolutionStains.class),
						obj.get("stainNumber").getAsInt());
			} else if (obj.has("combineType")) {
				return switch (CombineType.valueOf(obj.get("combineType").getAsString())) {
					case MEAN -> new AverageChannels();
					case MAXIMUM -> new MaxChannels();
					case MINIMUM -> new MinChannels();
				};
			} else {
				throw new IOException("Unknown ColorTransform " + obj);
			}
		}
	}

	/**
	 * Create a ColorTransform that extracts a channel based on its index.
	 *
	 * @param channel the index of the channel to extract. It must be 0-based, although
	 *                the result of {@link ColorTransform#getName()} will be 1-based
	 * @return a ColorTransform extracting the provided channel
	 */
	public static ColorTransform createChannelExtractor(int channel) {
		return new ExtractChannel(channel);
	}

	/**
	 * Create a ColorTransform that extracts a channel based on its name.
	 *
	 * @param channelName the name of the channel to extract
	 * @return a ColorTransform extracting the provided channel
	 */
	public static ColorTransform createChannelExtractor(String channelName) {
		return new ExtractChannelByName(channelName);
	}

	/**
	 * Create a ColorTransform that apply a linear combination to the channels.
	 * For example, calling this function with the Map {"c1": 0.5, "c3": 0.2}
	 * will create a new channel with values "0.5*c1 + 0.2*c3".
	 *
	 * @param coefficients the channel names mapped to coefficients
	 * @return a ColorTransform computing the provided linear combination
	 */
	public static ColorTransform createLinearCombinationChannelTransform(Map<String, ? extends Number> coefficients) {
		return new LinearCombinationChannel(coefficients);
	}

	/**
	 * Create a ColorTransform that apply a linear combination to the channels.
	 * For example, calling this function with the list [0.5, 0.9]
	 * will create a new channel with values "0.5*firstChannel + 0.9*secondChannel".
	 *
	 * @param coefficients the list of coefficients to apply to each channel
	 * @return a ColorTransform computing the provided linear combination
	 */
	public static ColorTransform createLinearCombinationChannelTransform(List<? extends Number> coefficients) {
		return new LinearCombinationChannel(coefficients);
	}

	/**
	 * Create a ColorTransform that apply a linear combination to the channels.
	 * For example, calling this function with the coefficients (0.5, 0.9)
	 * will create a new channel with values "0.5*firstChannel + 0.9*secondChannel".
	 *
	 * @param coefficients the coefficients to apply to each channel
	 * @return a ColorTransform computing the provided linear combination
	 */
	public static ColorTransform createLinearCombinationChannelTransform(double... coefficients) {
		return createLinearCombinationChannelTransform(Arrays.stream(coefficients).boxed().toList());
	}
	
	/**
	 * Create a ColorTransform that calculates the mean of all channels.
	 */
	public static ColorTransform createMeanChannelTransform() {
		return new AverageChannels();
	}

	/**
	 * Create a ColorTransform that calculates the maximum of all channels.
	 */
	public static ColorTransform createMaximumChannelTransform() {
		return new MaxChannels();
	}


	/**
	 * Create a ColorTransform that calculates the minimum of all channels.
	 */
	public static ColorTransform createMinimumChannelTransform() {
		return new MinChannels();
	}

	/**
	 * Create a ColorTransform that applies color deconvolution. It only works on RGB images.
	 *
	 * @param stains the stains (this will be 'fixed', and not adapted for each image)
	 * @param stainNumber number of the stain (1, 2 or 3)
	 * @return a ColorTransform applying color deconvolution with the provided parameters
	 * @throws IllegalArgumentException when the stain number is incorrect
	 */
	public static ColorTransform createColorDeconvolvedChannel(ColorDeconvolutionStains stains, int stainNumber) {
		return new ColorDeconvolvedChannel(stains, stainNumber);
	}

	static class ExtractChannel implements ColorTransform {

		private final int channel;

		public ExtractChannel(int channel) {
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
		public boolean supportsImage(ImageServer<BufferedImage> server) {
			return channel < server.nChannels();
		}

		@Override
		public String toString() {
			return getName();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + channel;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof ExtractChannel extractChannel))
				return false;
			return channel == extractChannel.channel;
		}

		/**
		 * Get the channel number to extract (0-based index).
		 */
		public int getChannelNumber() {
			return channel;
		}
	}

	static class ExtractChannelByName implements ColorTransform {

		private final String channelName;

		public ExtractChannelByName(String channel) {
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

		@Override
		public boolean supportsImage(ImageServer<BufferedImage> server) {
			return getChannelNumber(server) >= 0;
		}

		@Override
		public String toString() {
			return getName();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((channelName == null) ? 0 : channelName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof ExtractChannelByName extractChannel))
				return false;
			return Objects.equals(channelName, extractChannel.channelName);
		}

		/**
		 * Get the channel name to extract. Can be null
		 */
		public String getChannelName() {
			return channelName;
		}

		private int getChannelNumber(ImageServer<BufferedImage> server) {
			int i = 0;
			for (ImageChannel channel : server.getMetadata().getChannels()) {
				if (channel.getName().equals(channelName)) {
					return i;
				}
				i++;
			}
			return -1;
		}
	}

	static class LinearCombinationChannel implements ColorTransform {

		private final Map<String, Double> channelNamesToCoefficients;
		private final List<Double> channelIndicesToCoefficients;
		private transient String name;

		private LinearCombinationChannel(Map<String, Double> channelNamesToCoefficients, List<Double> channelIndicesToCoefficients) {
			this.channelNamesToCoefficients = channelNamesToCoefficients;
			this.channelIndicesToCoefficients = channelIndicesToCoefficients;
		}

		public LinearCombinationChannel(Map<String, ? extends Number> coefficients) {
			this(
					coefficients.entrySet().stream().collect(Collectors.toMap(
							Map.Entry::getKey,
							entry -> entry.getValue().doubleValue()
					)),
					null
			);
		}

		public LinearCombinationChannel(List<? extends Number> coefficients) {
			this(
					null,
					coefficients.stream().map(Number::doubleValue).toList()
			);
		}

		@Override
		public float[] extractChannel(ImageServer<BufferedImage> server, BufferedImage img, float[] pixels) {
			pixels = ensureArrayLength(img, pixels);
			int w = img.getWidth();
			int h = img.getHeight();
			var raster = img.getRaster();
			double[] coefficients = getCoefficients(server);

			double[] values = null;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					values = raster.getPixel(x, y, values);

					double result = 0;
					for (int i = 0; i < values.length; i++) {
						result += values[i] * coefficients[i];
					}
					pixels[y*w+x] = (float) result;
				}
			}
			return pixels;
		}

		@Override
		public String getName() {
			if (name == null) {
				if (channelNamesToCoefficients != null) {
					name = channelNamesToCoefficients.entrySet().stream()
							.map(entry -> entry.getValue() + "*" + entry.getKey())
							.collect(Collectors.joining(" + "));
				} else if (channelIndicesToCoefficients != null) {
					name = IntStream.range(0, channelIndicesToCoefficients.size())
							.mapToObj(i -> channelIndicesToCoefficients.get(i) + "*channel" + i)
							.collect(Collectors.joining(" + "));
				} else {
					name = "Linear combination channels";
				}
			}
			return name;
		}

		@Override
		public boolean supportsImage(ImageServer<BufferedImage> server) {
			if (channelNamesToCoefficients != null) {
				return server.getMetadata().getChannels().stream()
						.map(ImageChannel::getName)
						.collect(Collectors.toSet())
						.containsAll(channelNamesToCoefficients.keySet());
			} else if (channelIndicesToCoefficients != null) {
				return server.nChannels() >= channelIndicesToCoefficients.size();
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			return getName();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((channelNamesToCoefficients == null) ? 0 : channelNamesToCoefficients.hashCode());
			result = prime * result + ((channelIndicesToCoefficients == null) ? 0 : channelIndicesToCoefficients.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof LinearCombinationChannel linearCombinationChannel))
				return false;
			return Objects.equals(channelNamesToCoefficients, linearCombinationChannel.channelNamesToCoefficients) &&
					Objects.equals(channelIndicesToCoefficients, linearCombinationChannel.channelIndicesToCoefficients);
		}

		private double[] getCoefficients(ImageServer<BufferedImage> server) {
			double[] coefficients = new double[server.nChannels()];

			if (channelNamesToCoefficients != null) {
				for (int i=0; i<server.nChannels(); i++) {
					coefficients[i] = channelNamesToCoefficients.getOrDefault(server.getChannel(i).getName(), 0d);
				}
			} else if (channelIndicesToCoefficients != null) {
				for (int i=0; i<channelIndicesToCoefficients.size(); i++) {
					coefficients[i] = channelIndicesToCoefficients.get(i);
				}
			}

			return coefficients;
		}
	}

	abstract static class CombineChannels implements ColorTransform {

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
					pixels[y*w+x] = (float) computeValue(vals);
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

	static class AverageChannels extends CombineChannels {

		@SuppressWarnings("unused")		// used for JSON serialization
		private final CombineType combineType = CombineType.MEAN;

		@Override
		public double computeValue(double[] values) {
			return Arrays.stream(values).average().orElse(Double.NaN);
		}

		@Override
		public String getName() {
			return "Average channels";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + combineType.hashCode();
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof AverageChannels;
		}
	}

	static class MaxChannels extends CombineChannels {

		@SuppressWarnings("unused")		// used for JSON serialization
		private final CombineType combineType = CombineType.MAXIMUM;

		@Override
		public double computeValue(double[] values) {
			return Arrays.stream(values).max().orElse(Double.NaN);
		}

		@Override
		public String getName() {
			return "Max channels";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + combineType.hashCode();
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof MaxChannels;
		}
	}

	static class MinChannels extends CombineChannels {

		@SuppressWarnings("unused")		// used for JSON serialization
		private final CombineType combineType = CombineType.MINIMUM;

		@Override
		public double computeValue(double[] values) {
			return Arrays.stream(values).min().orElse(Double.NaN);
		}

		@Override
		public String getName() {
			return "Min channels";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + combineType.hashCode();
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof MinChannels;
		}
	}
	
	static class ColorDeconvolvedChannel implements ColorTransform {
		
		private final ColorDeconvolutionStains stains;
		private final int stainNumber;
		private final transient ColorTransformMethod method;
		
		public ColorDeconvolvedChannel(ColorDeconvolutionStains stains, int stainNumber) {
			this.stains = stains;
			this.stainNumber = stainNumber;
			this.method = switch (stainNumber) {
				case 1 -> ColorTransformMethod.Stain_1;
				case 2 -> ColorTransformMethod.Stain_2;
				case 3 -> ColorTransformMethod.Stain_3;
				default ->
						throw new IllegalArgumentException("Stain number is " + stainNumber + ", but must be between 1 and 3!");
			};
		}

		@Override
		public float[] extractChannel(ImageServer<BufferedImage> server, BufferedImage img, float[] pixels) {
			if (BufferedImageTools.is8bitColorType(img.getType())) {
				int[] rgb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
				return ColorTransformer.getTransformedPixels(rgb, method, pixels, stains);
			} else {
				return ColorDeconvolutionHelper.colorDeconvolve(img, stains, stainNumber-1, null);
			}
		}

		@Override
		public boolean supportsImage(ImageServer<BufferedImage> server) {
			return server.nChannels() == 3;
		}

		@Override
		public String getName() {
			return stains == null ? null : stains.getStain(stainNumber).getName();
		}

		@Override
		public String toString() {
			return getName();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + stainNumber;
			result = prime * result + ((stains == null) ? 0 : stains.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof ColorDeconvolvedChannel colorDeconvolvedChannel))
				return false;
			return Objects.equals(stains, colorDeconvolvedChannel.stains) && stainNumber == colorDeconvolvedChannel.stainNumber;
		}
	}
	
	/**
	 * Store the {@link CombineType}. This is really to add deserialization from JSON.
	 */
	private enum CombineType {
		MEAN,
		MINIMUM,
		MAXIMUM
	}

	private static float[] ensureArrayLength(BufferedImage img, float[] pixels) {
		int n = img.getWidth() * img.getHeight();
		if (pixels == null || pixels.length < n)
			return new float[n];
		return pixels;
	}
}