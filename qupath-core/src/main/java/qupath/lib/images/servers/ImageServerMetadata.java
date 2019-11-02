/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.images.servers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.objects.classes.PathClass;

/**
 * Class for storing primary ImageServer metadata fields.
 * <p>
 * Can be used when the metadata needs to be adjusted (e.g. to correct erroneous pixel sizes).
 * 
 * @author Pete Bankhead
 *
 */
public class ImageServerMetadata {
	
	private static Logger logger = LoggerFactory.getLogger(ImageServerMetadata.class);
	
	/**
	 * Default channel to use with images where the channel type is {@link ChannelType#CLASSIFICATION}.
	 */
	public static final ImageChannel DEFAULT_CLASSIFICATION_LABELS_CHANNEL = ImageChannel.getInstance("Labels", ColorTools.makeRGB(255, 255, 255));
	
	/**
	 * Enum representing possible channel (band) types for an image.
	 * The purpose of this is to support images where channels have different interpretations, 
	 * such as probabilities or classifications.
	 */
	public static enum ChannelType {
		/**
		 * Default channel interpretation. This is true for most 'normal' images.
		 */
		DEFAULT,
		/**
		 * Each channel represents a feature for a pixel classifier.
		 */
		FEATURE,
		/**
		 * Each channel represents a probability value, where it is assumed that there is only one true class per pixel.
		 */
		PROBABILITY,
		/**
		 * Each channel represents a probability value, where each pixel is potentially part of multiple classes.
		 */
		MULTICLASS_PROBABILITY,
		/**
		 * Each channel represents a classification, such as in a labelled image.
		 */
		CLASSIFICATION;
		
		@Override
		public String toString() {
			switch (this) {
			case DEFAULT:
				return "Channel";
			case FEATURE:
				return "Feature";
			case PROBABILITY:
				return "Probability";
			case MULTICLASS_PROBABILITY:
				return "Multiclass probability";
			case CLASSIFICATION:
				return "Classification";
			default:
				return super.toString();
			}
		}
	
	}
	
	private static int DEFAULT_TILE_SIZE = 256;

	private String name;
	
	private int width;
	private int height;
	
	private int sizeZ = 1;
	private int sizeT = 1;
	
	private ImageServerMetadata.ChannelType channelType = ImageServerMetadata.ChannelType.DEFAULT;
	
	private boolean isRGB = false;
	private PixelType pixelType = PixelType.UINT8;
	
	private ImageResolutionLevel[] levels;
	
	private Map<Integer, PathClass> classificationLabels;
	private List<ImageChannel> channels = new ArrayList<>();
	
	private Number minValue;
	private Number maxValue;
	
	private PixelCalibration pixelCalibration;
	
	private Double magnification = null;
	
	private int preferredTileWidth;
	private int preferredTileHeight;
	
	// Cached variables
	private transient List<ImageResolutionLevel> unmodifiableLevels;
	private transient double[] downsamples;
	
	/**
	 * Builder to create a new {@link ImageServerMetadata} object.
	 */
	public static class Builder {
		
		private ImageServerMetadata metadata;
		private PixelCalibration.Builder pixelCalibrationBuilder = new PixelCalibration.Builder();
		
		/**
		 * Builder for a new ImageServerMetadata object that takes an existing metadata object as a starting point, 
		 * but allows individual properties to be overridden.
		 * <p>
		 * The existing metadata will be duplicated, therefore later changes in one metadata object will not be 
		 * reflected in the other.
		 * 
		 * @param metadata
		 */
		public Builder(final ImageServerMetadata metadata) {
			this.metadata = metadata.duplicate();
			this.pixelCalibrationBuilder = new PixelCalibration.Builder(metadata.pixelCalibration);
		}
		
		/**
		 * Minimal builder for a new ImageServerMetadata; further properties must be set.
		 */
		public Builder() {
			metadata = new ImageServerMetadata();
//			metadata.path = path;
		}
		
//		/**
//		 * Minimal builder for a new ImageServerMetadata; further properties must be set.
//		 * 
//		 * @param serverClass
//		 * @param path
//		 */
//		public Builder(final Class<? extends ImageServer<?>> serverClass, final String path) {
//			metadata = new ImageServerMetadata();
//			metadata.serverClassName = serverClass.getName();
////			metadata.path = path;
//		}
		
		/**
		 * Builder for a new ImageServerMetadata; further properties must be set.
		 * 
		 * @param serverClass
		 * @param path
		 * @param width
		 * @param height
		 */
		public Builder(final Class<? extends ImageServer<?>> serverClass, final String path, final int width, final int height) {
			metadata = new ImageServerMetadata();
//			metadata.path = path;
			metadata.width = width;
			metadata.height = height;
		}
		
		/**
		 * Specify the full-resolution image width.
		 * @param width
		 * @return
		 */
		public Builder width(final int width) {
			metadata.width = width;
			return this;
		}
		
		/**
		 * Specify the full-resolution image height.
		 * @param height
		 * @return
		 */
		public Builder height(final int height) {
			metadata.height = height;
			return this;
		}
		
		/**
		 * Specify the minimum value supported by this image.
		 * If not provided, this will be taken from the {@link PixelType}.
		 * @param val
		 * @return
		 * @see #pixelType(PixelType)
		 */
		public Builder minValue(final Number val) {
			metadata.minValue = val;
			return this;
		}
		
		/**
		 * Specify the maximum value supported by this image.
		 * If not provided, this will be taken from the {@link PixelType}.
		 * This purpose of this method is to support other effective bit-depths (e.g. 12-bit, 14-bit).
		 * @param val
		 * @return
		 * @see #pixelType(PixelType)
		 */
		public Builder maxValue(final Number val) {
			metadata.maxValue = val;
			return this;
		}

		
		/**
		 * Specify the interpretation of channels. If this is {@link ChannelType#CLASSIFICATION} it will also 
		 * set the channels to be the default for this kind of server, i.e. a single channel with {@link ImageServerMetadata#DEFAULT_CLASSIFICATION_LABELS_CHANNEL}.
		 * @param type
		 * @return
		 */
		public Builder channelType(final ImageServerMetadata.ChannelType type) {
			this.metadata.channelType = type;
			if (type == ChannelType.CLASSIFICATION)
				this.metadata.channels = Collections.singletonList(DEFAULT_CLASSIFICATION_LABELS_CHANNEL);
			return this;
		}
		
		/**
		 * Specify the interpretation of classification labels. Only relevant for {@link ChannelType#CLASSIFICATION}.
		 * @param classificationLabels
		 * @return
		 */
		public Builder classificationLabels(final Map<Integer, PathClass> classificationLabels) {
			this.metadata.classificationLabels = Collections.unmodifiableMap(new LinkedHashMap<>(classificationLabels));
			return this;
		}
		
		/**
		 * Specify that the image stores pixels in (A)RGB form.
		 * @param isRGB
		 * @return
		 */
		public Builder rgb(boolean isRGB) {
			metadata.isRGB = isRGB;
			return this;
		}
		
		/**
		 * Specify the bit-depth of the image.
		 * @param pixelType
		 * @return
		 */
		public Builder pixelType(PixelType pixelType) {
			metadata.pixelType = pixelType;
			return this;
		}
		
		/**
		 * Specify downsample values for pyramidal levels.
		 * The appropriate image sizes will be computed based upon these.
		 * @param downsamples
		 * @return
		 * 
		 * @see #levels(Collection)
		 */
		public Builder levelsFromDownsamples(double... downsamples) {
			var levelBuilder = new ImageResolutionLevel.Builder(metadata.width, metadata.height);
			for (double d : downsamples)
				levelBuilder.addLevelByDownsample(d);
			return this.levels(levelBuilder.build());
		}
		
		/**
		 * Specify resolution levels, where the largest image should come first.
		 * <p>
		 * Normally {@code level[0].width == width && level[0].height == height}, but this is <i>not</i> 
		 * strictly required; for example, it is permissible for the server to supply only resolutions lower than 
		 * the full image if these ought to be upsampled elsewhere.
		 * <p>
		 * In other words, the {@code width} and {@code height} encode the size of the image as it should be 
		 * interpreted, while the {@code levels} refer to the size of the rasters actually available here.
		 * 
		 * @param levels
		 * @return
		 */
		public Builder levels(Collection<ImageResolutionLevel> levels) {
			metadata.levels = levels.toArray(ImageResolutionLevel[]::new);
			return this;
		}
		
		/**
		 * Specify the number of z-slices.
		 * @param sizeZ
		 * @return
		 */
		public Builder sizeZ(final int sizeZ) {
			metadata.sizeZ = sizeZ;
			return this;
		}

		/**
		 * Specify the number of time points.
		 * @param sizeT
		 * @return
		 */
		public Builder sizeT(final int sizeT) {
			metadata.sizeT = sizeT;
			return this;
		}

		/**
		 * Specify the pixel sizes, in microns.
		 * @param pixelWidthMicrons
		 * @param pixelHeightMicrons
		 * @return
		 */
		public Builder pixelSizeMicrons(final Number pixelWidthMicrons, final Number pixelHeightMicrons) {
			pixelCalibrationBuilder.pixelSizeMicrons(pixelWidthMicrons, pixelHeightMicrons);
			return this;
		}

		/**
		 * Specify the spacing between z-slices, in microns.
		 * @param zSpacingMicrons
		 * @return
		 */
		public Builder zSpacingMicrons(final Number zSpacingMicrons) {
			pixelCalibrationBuilder.zSpacingMicrons(zSpacingMicrons);
			return this;
		}

		/**
		 * Specify the time unit and individual time points.
		 * @param timeUnit
		 * @param timepoints time points, defined in terms of timeUnits.
		 * @return
		 */
		public Builder timepoints(final TimeUnit timeUnit, double... timepoints) {
			pixelCalibrationBuilder.timepoints(timeUnit, timepoints);
			return this;
		}
		
		/**
		 * Specify a magnification value for the highest-resolution image.
		 * @param magnification
		 * @return
		 */
		public Builder magnification(final double magnification) {
			metadata.magnification = Double.isFinite(magnification) ? magnification : null;
			return this;
		}
		
		/**
		 * Specify the preferred tile height and width.
		 * @param tileWidth
		 * @param tileHeight
		 * @return
		 */
		public Builder preferredTileSize(final int tileWidth, final int tileHeight) {
			metadata.preferredTileWidth = tileWidth;
			metadata.preferredTileHeight = tileHeight;
			return this;
		}
		
		/**
		 * Specify the image channels.
		 * @param channels
		 * @return
		 */
		public Builder channels(Collection<ImageChannel> channels) {
			metadata.channels = Collections.unmodifiableList(new ArrayList<>(channels));
			return this;
		}

		/**
		 * Specify the image name.
		 * @param name
		 * @return
		 */
		public Builder name(final String name) {
			metadata.name = name;
			return this;
		}
		
		/**
		 * Build an {@link ImageServerMetadata}.
		 * Note that the builder should only be used once. If a second builder is required, a new one should be 
		 * initialized from an existing ImageServerMetadata object.
		 * @return
		 */
		public ImageServerMetadata build() {
			metadata.pixelCalibration = pixelCalibrationBuilder.build();
			
			if (metadata.levels == null)
				metadata.levels = new ImageResolutionLevel[] {new ImageResolutionLevel(1, metadata.width, metadata.height)};
			
			if (metadata.width <= 0 && metadata.height <= 0)
				throw new IllegalArgumentException("Invalid metadata - width & height must be > 0");

//			if (metadata.path == null || metadata.path.isBlank())
//				throw new IllegalArgumentException("Invalid metadata - path must be set (and not be blank)");
						
			// Set sensible tile sizes, if required
			if (metadata.preferredTileWidth <= 0) {
				if (metadata.levels.length == 1)
					metadata.preferredTileWidth = metadata.width;
				else
					metadata.preferredTileWidth = Math.min(metadata.width, DEFAULT_TILE_SIZE);
			}
			if (metadata.preferredTileHeight <= 0) {
				if (metadata.levels.length == 1)
					metadata.preferredTileHeight = metadata.height;
				else
					metadata.preferredTileHeight = Math.min(metadata.height, DEFAULT_TILE_SIZE);
			}
			// Return a duplicate so that we can reuse the builder if necessary
			return new ImageServerMetadata(metadata);
		}

	}
	
	ImageServerMetadata() {};
	
	
	ImageServerMetadata(final String path) {
//		this.path = path;
	};

	ImageServerMetadata(final ImageServerMetadata metadata) {
		this.name = metadata.name;
		this.levels = metadata.levels.clone();
		
		this.width = metadata.width;
		this.height = metadata.height;
		
		this.sizeZ = metadata.sizeZ;
		this.sizeT = metadata.sizeT;
				
		this.isRGB = metadata.isRGB;
		this.pixelType = metadata.pixelType;
		
		this.pixelCalibration = metadata.pixelCalibration;
		if (metadata.classificationLabels != null) {
			this.classificationLabels = Collections.unmodifiableMap(new LinkedHashMap<>(metadata.classificationLabels));
		} else if (metadata.channelType == ChannelType.CLASSIFICATION) {
			throw new IllegalArgumentException("Classification labels are required whenever the channel type is CLASSIFICATION!");
		}
		
		this.channels = new ArrayList<>(metadata.getChannels());
		
		this.magnification = metadata.magnification;
		
		this.minValue = metadata.minValue;
		this.maxValue = metadata.maxValue;
		this.channelType = metadata.channelType;
		
		this.preferredTileWidth = metadata.preferredTileWidth;
		this.preferredTileHeight = metadata.preferredTileHeight;
	};
	
	/**
	 * Request the preferred downsamples from the image metadata.
	 * <p>
	 * Note that this makes a defensive copy, and so should not be called often; it is generally preferably 
	 * to request downsample values individually.
	 * 
	 * @return
	 */
	public double[] getPreferredDownsamplesArray() {
		if (downsamples == null) {
			downsamples = new double[nLevels()];
			for (int i = 0; i < downsamples.length; i++) {
				downsamples[i] = getDownsampleForLevel(i);
			}
		}
		return downsamples.clone();
	}
	
	
	/**
	 * Get an unmodifiable list containing the resolution levels
	 * @return
	 */
	public List<ImageResolutionLevel> getLevels() {
		if (unmodifiableLevels == null)
			unmodifiableLevels = Collections.unmodifiableList(Arrays.asList(levels));
		return unmodifiableLevels;
	}
	
	/**
	 * Get the full-resolution image width.
	 * @return
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * Get the full-resolution image height.
	 * @return
	 */
	public int getHeight() {
		return height;
	}
	
	/**
	 * Get the number of resolution levels. For a non-pyramidal image, this is 1.
	 * @return
	 */
	public int nLevels() {
		return levels.length;
	}
	
	/**
	 * Get a {@link PixelCalibration} object representing the pixel size information for this metadata object.
	 * @return
	 */
	public PixelCalibration getPixelCalibration() {
		return pixelCalibration;
	}
	
	/**
	 * Get the downsample factor for a specific resolution level.
	 * @param level
	 * @return
	 */
	public double getDownsampleForLevel(int level) {
		return levels[level].getDownsample();
	}
	
	/**
	 * Get resolution information for a specified pyramidal level.
	 * @param level
	 * @return
	 */
	public ImageResolutionLevel getLevel(int level) {
		return levels[level];
	}
	
	/**
	 * Returns true if the pixels are stored in (A)RGB form.
	 * @return
	 */
	public boolean isRGB() {
		return isRGB;
	}
	
	/**
	 * Returns the bit-depth for individual pixels in the image.
	 * @return
	 */
	public PixelType getPixelType() {
		return pixelType;
	}
	
	/**
	 * Get the minimum value supported by this image.
	 * By default, this is the lower bound of the {@link PixelType} unless otherwise specified.
	 * @return
	 * @see #getPixelType()
	 */
	public Number getMinValue() {
		return minValue == null ? getPixelType().getLowerBound() : minValue;
	}
	
	/**
	 * Get the minimum value supported by this image.
	 * By default, this is the lower bound of the {@link PixelType} unless otherwise specified.
	 * This purpose of this method is to support other effective bit-depths (e.g. 12-bit, 14-bit).
	 * @return
	 * @see #getPixelType()
	 */
	public Number getMaxValue() {
		return maxValue == null ? getPixelType().getUpperBound() : maxValue;
	}
	
	/**
	 * Returns true if pixel width and height calibration information is available for the image.
	 * @return
	 */
	public boolean pixelSizeCalibrated() {
		return pixelCalibration.hasPixelSizeMicrons();
	}
	
	/**
	 * Returns true if z-spacing calibration information is available for the image.
	 * @return
	 */
	public boolean zSpacingCalibrated() {
		return pixelCalibration.hasZSpacingMicrons();
	}
	
	/**
	 * Get the averaged pixel size in microns, if available - or Double.NaN otherwise.
	 * @return
	 */
	public double getAveragedPixelSize() {
		return (getPixelWidthMicrons() + getPixelHeightMicrons())/2;
	}
	
	/**
	 * Get the pixel width in microns, if available - or Double.NaN otherwise.
	 * @return
	 */
	public double getPixelWidthMicrons() {
		return pixelCalibration.getPixelWidthMicrons();
	}

	/**
	 * Get the pixel height in microns, if available - or Double.NaN otherwise.
	 * @return
	 */
	public double getPixelHeightMicrons() {
		return pixelCalibration.getPixelHeightMicrons();
	}
	
	/**
	 * Get the z-spacing in microns, if available - or Double.NaN otherwise.
	 * @return
	 */
	public double getZSpacingMicrons() {
		return pixelCalibration.getZSpacingMicrons();
	}
	
//	// TODO: Consider if mutability is permissible
//	public void setPixelSizeMicrons(final double pixelWidth, final double pixelHeight) {
//		this.pixelWidthMicrons = pixelWidth;
//		this.pixelHeightMicrons = pixelHeight;
//	}

	/**
	 * Get the time unit for a time series.
	 * @return
	 */
	public TimeUnit getTimeUnit() {
		return pixelCalibration.getTimeUnit();
	}
	
	/**
	 * Get the time point, defined in {@link #getTimeUnit()}, or Double.NaN if this is unknown.
	 * @param ind
	 * @return
	 */
	public double getTimepoint(int ind) {
		return pixelCalibration.getTimepoint(ind);
	}
	
	/**
	 * Get the number of z-slices.
	 * @return
	 */
	public int getSizeZ() {
		return sizeZ;
	}

	/**
	 * Get the number of time points.
	 * @return
	 */
	public int getSizeT() {
		return sizeT;
	}

	/**
	 * Get the number of image channels.
	 * @return
	 */
	public int getSizeC() {
		return channels.size();
	}
	
	/**
	 * Get the magnification value, or Double.NaN if this is unavailable.
	 * @return
	 */
	public double getMagnification() {
		return magnification == null ? Double.NaN : magnification;
	}
	
	/**
	 * Get the preferred tile width, which can be used to optimize pixel requests for large images.
	 * @return
	 */
	public int getPreferredTileWidth() {
		return preferredTileWidth;
	}

	/**
	 * Get the preferred tile height, which can be used to optimize pixel requests for large images.
	 * @return
	 */
	public int getPreferredTileHeight() {
		return preferredTileHeight;
	}

	/**
	 * Duplicate this metatadata.
	 * @return
	 */
	public ImageServerMetadata duplicate() {
		return new ImageServerMetadata(this);
	}
	
	/**
	 * Get the image name.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Get the specified channel.
	 * @param n channel index, starting at 0.
	 * @return
	 */
	public ImageChannel getChannel(int n) {
		return channels.get(n);
	}
	
	/**
	 * Get an unmodifiable list of all channels.
	 * @return
	 */
	public List<ImageChannel> getChannels() {
		return channels;
	}
	
	/**
	 * Get map between labels and classification names. This is relevant only where the channel type is {@link ChannelType#CLASSIFICATION}, 
	 * otherwise it returns an empty map.
	 * @return
	 */
	public Map<Integer, PathClass> getClassificationLabels() {
		return classificationLabels == null ? Collections.emptyMap() : classificationLabels;
	}
	
	/**
	 * Get the channel type, which can be used to interpret the channels.
	 * @return
	 */
	public ImageServerMetadata.ChannelType getChannelType() {
		return channelType;
	}
	
	/**
	 * Returns true if a specified ImageServerMetadata is compatible with this one, that is it has the same path and dimensions
	 * (but possibly different pixel sizes, magnifications etc.).
	 * 
	 * @param metadata
	 * @return
	 */
	public boolean isCompatibleMetadata(final ImageServerMetadata metadata) {
//		if (!path.equals(metadata.path)) {
//			logger.warn("Metadata paths are not compatible: \n{}\n{}", path, metadata.path);
//			return false;
//		}
		if (pixelType != metadata.pixelType) {
			logger.warn("Pixel types are not compatible: {} vs {}", pixelType, metadata.pixelType);
			return false;
		}
		if (sizeT != metadata.sizeT ||
				getSizeC() != metadata.getSizeC() ||
				sizeZ != metadata.sizeZ) {
			logger.warn(String.format(
					"Metadata image dimensions (czt) are not the same! %dx%dx%d for original vs %dx%dx%d",
					getSizeC(), getSizeZ(), getSizeT(), metadata.getSizeC(), metadata.getSizeZ(), metadata.getSizeT()));
			return false;			
		}
		return true;
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("{ ");
//		sb.append("\"path\": \"").append(path).append("\", ");
		sb.append("\"name\": \"").append(name).append("\", ");
		sb.append("\"width\": ").append(getWidth()).append(", ");
		sb.append("\"height\": ").append(getHeight()).append(", ");
		sb.append("\"resolutions\": ").append(nLevels()).append(", ");
		sb.append("\"sizeC\": ").append(getSizeC());
		if (sizeZ != 1)
			sb.append(", ").append("\"sizeZ\": ").append(sizeZ);
		if (sizeT != 1) {
			sb.append(", ").append("\"sizeT\": ").append(sizeT);
			sb.append(", ").append("\"timeUnit\": ").append(getTimeUnit());
		}
		if (pixelSizeCalibrated()) {
			sb.append(", ").append("\"pixelWidthMicrons\": ").append(getPixelWidthMicrons());
			sb.append(", ").append("\"pixelHeightMicrons\": ").append(getPixelHeightMicrons());
		}
		sb.append(" }");
		return sb.toString();
	}

	


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((channelType == null) ? 0 : channelType.hashCode());
		result = prime * result + ((channels == null) ? 0 : channels.hashCode());
		result = prime * result + height;
		result = prime * result + (isRGB ? 1231 : 1237);
		result = prime * result + Arrays.hashCode(levels);
		result = prime * result + ((magnification == null) ? 0 : magnification.hashCode());
		result = prime * result + ((maxValue == null) ? 0 : maxValue.hashCode());
		result = prime * result + ((minValue == null) ? 0 : minValue.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((pixelCalibration == null) ? 0 : pixelCalibration.hashCode());
		result = prime * result + ((pixelType == null) ? 0 : pixelType.hashCode());
		result = prime * result + preferredTileHeight;
		result = prime * result + preferredTileWidth;
		result = prime * result + sizeT;
		result = prime * result + sizeZ;
		result = prime * result + width;
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ImageServerMetadata other = (ImageServerMetadata) obj;
		if (channelType != other.channelType)
			return false;
		if (channels == null) {
			if (other.channels != null)
				return false;
		} else if (!channels.equals(other.channels))
			return false;
		if (height != other.height)
			return false;
		if (isRGB != other.isRGB)
			return false;
		if (!Arrays.equals(levels, other.levels))
			return false;
		if (magnification == null) {
			if (other.magnification != null)
				return false;
		} else if (!magnification.equals(other.magnification))
			return false;
		if (maxValue == null) {
			if (other.maxValue != null)
				return false;
		} else if (!maxValue.equals(other.maxValue))
			return false;
		if (minValue == null) {
			if (other.minValue != null)
				return false;
		} else if (!minValue.equals(other.minValue))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (pixelCalibration == null) {
			if (other.pixelCalibration != null)
				return false;
		} else if (!pixelCalibration.equals(other.pixelCalibration))
			return false;
		if (pixelType != other.pixelType)
			return false;
		if (preferredTileHeight != other.preferredTileHeight)
			return false;
		if (preferredTileWidth != other.preferredTileWidth)
			return false;
		if (sizeT != other.sizeT)
			return false;
		if (sizeZ != other.sizeZ)
			return false;
		if (width != other.width)
			return false;
		return true;
	}





	/**
	 * Width and height of each resolution in a multi-level image pyramid.
	 */
	public static class ImageResolutionLevel {
		
		private static final Logger logger = LoggerFactory.getLogger(ImageResolutionLevel.class);

		private double downsample;
		private final int width, height;
		
		private ImageResolutionLevel(final double downsample, final int width, final int height) {
			this.downsample = downsample;
			this.width = width;
			this.height = height;
		}
		
		/**
		 * Get the downsample factor for this level.
		 * @return
		 */
		public double getDownsample() {
			return downsample;
		}
		
		/**
		 * Get the image width at this level.
		 * @return
		 */
		public int getWidth() {
			return width;
		}
		
		/**
		 * Get the image height at this level.
		 * @return
		 */
		public int getHeight() {
			return height;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			long temp;
			temp = Double.doubleToLongBits(downsample);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			result = prime * result + height;
			result = prime * result + width;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ImageResolutionLevel other = (ImageResolutionLevel) obj;
			if (Double.doubleToLongBits(downsample) != Double.doubleToLongBits(other.downsample))
				return false;
			if (height != other.height)
				return false;
			if (width != other.width)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Level: " + width + "x" + height + " (" + GeneralTools.formatNumber(downsample, 5) + ")";
		}
		
		/**
		 * Builder to create a list of {@link ImageResolutionLevel} to represent pyramidal resolutions.
		 */
		public static class Builder {
			
			private int fullWidth, fullHeight;
			private List<ImageResolutionLevel> levels = new ArrayList<>();
			
			/**
			 * Constructor to help build a list of {@link ImageResolutionLevel} objects to represent pyramidal resolutions.
			 * 
			 * @param fullWidth full-resolution image width
			 * @param fullHeight full-resolution image height
			 */
			public Builder(int fullWidth, int fullHeight) {
				this.fullWidth = fullWidth;
				this.fullHeight = fullHeight;
			}
			
			/**
			 * Add a new level, calculating dimensions using a downsample factor applied to the full-resolution image.
			 * @param downsample
			 * @return
			 */
			public Builder addLevelByDownsample(double downsample) {
				int levelWidth = (int)(fullWidth / downsample);
				int levelHeight = (int)(fullHeight / downsample);
				return addLevel(downsample, levelWidth, levelHeight);
			}
			
			/**
			 * Add the full-resolution image as a level of the pyramid.
			 * It is not required that this form part of the pyramid in cases where this image pyramid might 
			 * be used to provide a smaller overlay of a larger image, and not itself contain 
			 * pixels at the highest resolution.
			 * @return
			 */
			public Builder addFullResolutionLevel() {
				return addLevel(1, fullWidth, fullHeight);
			}
			
			/**
			 * Add a new level by providing a downsample value, width and height.
			 * This avoids relying on any rounding decisions made when specifying the dimensions or downsample value only.
			 * @param downsample
			 * @param levelWidth
			 * @param levelHeight
			 * @return
			 */
			public Builder addLevel(double downsample, int levelWidth, int levelHeight) {
				levels.add(new ImageResolutionLevel(downsample, levelWidth, levelHeight));
				return this;
			}
			
			/**
			 * Add a new level based on level dimensions, estimating the corresponding downsample value as required.
			 * @param levelWidth
			 * @param levelHeight
			 * @return
			 */
			public Builder addLevel(int levelWidth, int levelHeight) {
				double downsample = estimateDownsample(fullWidth, fullHeight, levelWidth, levelHeight, levels.size());
				return addLevel(downsample, levelWidth, levelHeight);
			}
			
			/**
			 * Add a new level directly.
			 * @param level
			 * @return
			 */
			public Builder addLevel(ImageResolutionLevel level) {
				return addLevel(level.downsample, level.width, level.height);
			}
			
			/**
			 * Build a list of ImageResolutionLevels, which can be used with an {@link ImageServerMetadata} object.
			 * @return
			 */
			public List<ImageResolutionLevel> build() {
				return levels;
			}
			
			
			
			private static double LOG2 = Math.log10(2);
			
			/**
			 * Estimate the downsample value for a specific level based on the full resolution image dimensions 
			 * and the level dimensions.
			 * <p>
			 * This method is provides so that different ImageServer implementations can potentially use the same logic.
			 * 
			 * @param fullWidth width of the full resolution image
			 * @param fullHeight height of the full resolution image
			 * @param levelWidth width of the pyramid level of interest
			 * @param levelHeight height of the pyramid level of interest
			 * @param level Resolution level.  Not required for the calculation, but if &geq; 0 and the computed x & y downsamples are very different a warning will be logged.
			 * @return
			 */
			private static double estimateDownsample(final int fullWidth, final int fullHeight, final int levelWidth, final int levelHeight, final int level) {
				// Calculate estimated downsamples for width & height independently
				double downsampleX = (double)fullWidth / levelWidth;
				double downsampleY = (double)fullHeight / levelHeight;
				
				// Check if the nearest power of 2 is within 2 pixel - since 2^n is the most common downsampling factor
				double downsampleAverage = (downsampleX + downsampleY) / 2.0;
				double closestPow2 = Math.pow(2, Math.round(Math.log10(downsampleAverage)/LOG2));
				if (Math.abs(fullHeight / closestPow2 - levelHeight) < 2 && Math.abs(fullWidth / closestPow2 - levelWidth) < 2)
					return closestPow2;
				
				
				// If the difference is less than 1 pixel from what we'd get by downsampling by closest integer, 
				// adjust the downsample factors - we're probably aiming at integer downsampling
				if (Math.abs(fullWidth / (double)Math.round(downsampleX)  - levelWidth) <= 1) {
					downsampleX = Math.round(downsampleX);
				}
				if (Math.abs(fullHeight / (double)Math.round(downsampleY) - levelHeight) <= 1) {
					downsampleY = Math.round(downsampleY);	
				}
				// If downsamples are equal, use that
				if (downsampleX == downsampleY)
					return downsampleX;
				
				// If one of these is a power of two, use it - this is usually the case
				if (downsampleX == closestPow2 || downsampleY == closestPow2)
					return closestPow2;
				
				/*
				 * Average the calculated downsamples for x & y, warning if they are substantially different.
				 * 
				 * The 'right' way to do this is a bit unclear... 
				 * * OpenSlide also seems to use averaging: https://github.com/openslide/openslide/blob/7b99a8604f38280d14a34db6bda7a916563f96e1/src/openslide.c#L272
				 * * OMERO's rendering may use the 'lower' ratio: https://github.com/openmicroscopy/openmicroscopy/blob/v5.4.6/components/insight/SRC/org/openmicroscopy/shoola/env/rnd/data/ResolutionLevel.java#L96
				 * 
				 * However, because in the majority of cases the rounding checks above will have resolved discrepancies, it is less critical.
				 */
				
				// Average the calculated downsamples for x & y
				double downsample = (downsampleX + downsampleY) / 2;
				
				// Give a warning if the downsamples differ substantially
				if (level >= 0 && !GeneralTools.almostTheSame(downsampleX, downsampleY, 0.001))
					logger.warn("Calculated downsample values differ for x & y for level {}: x={} and y={} - will use value {}", level, downsampleX, downsampleY, downsample);
				return downsample;
			}
			
		}
		
	}
	

}
