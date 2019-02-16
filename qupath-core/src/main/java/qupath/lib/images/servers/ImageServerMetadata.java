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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;

/**
 * Class for storing primary ImageServer metadata fields.
 * Could be used when the metadata needs to be adjusted (e.g. to correct erroneous pixel sizes).
 * 
 * TODO: Support metadata changes, taking into consideration the need for scriptability and persistence
 * 
 * @author Pete Bankhead
 *
 */
public class ImageServerMetadata {
	
	private static int DEFAULT_TILE_SIZE = 256;

	private String path;
	private String name;
	
	private int width;
	private int height;
	
	private int sizeZ = 1;
	private int sizeT = 1;
	
	private boolean isRGB = false;
	private int bitDepth = 8;
	
	private ImageResolutionLevel[] levels;
	
	private List<ImageChannel> channels = new ArrayList<>();
	
	private PixelCalibration pixelCalibration;
	
	private double magnification = Double.NaN;
	
	private int preferredTileWidth;
	private int preferredTileHeight;
	
	
	public static class Builder {
		
		private ImageServerMetadata metadata;
		private PixelCalibration.Builder pixelCalibrationBuilder = new PixelCalibration.Builder();
		
		public Builder(final ImageServerMetadata metadata) {
			this.metadata = metadata.duplicate();
			this.pixelCalibrationBuilder = new PixelCalibration.Builder(metadata.pixelCalibration);
		}
		
		public Builder(final String path) {
			metadata = new ImageServerMetadata();
			metadata.path = path;
		}
		
		public Builder(final String path, final int width, final int height) {
			metadata = new ImageServerMetadata();
			metadata.path = path;
			metadata.width = width;
			metadata.height = height;
		}
		
		public Builder width(final int width) {
			metadata.width = width;
			return this;
		}
		
		public Builder height(final int height) {
			metadata.height = height;
			return this;
		}
		
		public Builder path(final String path) {
			this.metadata.path = path;
			return this;
		}
		
		public Builder rgb(boolean isRGB) {
			metadata.isRGB = isRGB;
			return this;
		}
		
		public Builder bitDepth(int bitDepth) {
			metadata.bitDepth = bitDepth;
			return this;
		}
		
		public Builder levelsFromDownsamples(double... downsamples) {
			var levelBuilder = new ImageResolutionLevel.Builder(metadata.width, metadata.height);
			for (double d : downsamples)
				levelBuilder.addLevelByDownsample(d);
			return this.levels(levelBuilder.build());
		}
		
		public Builder levels(Collection<ImageResolutionLevel> levels) {
			return levels(levels.toArray(ImageResolutionLevel[]::new));
		}
		
		/**
		 * Resolution levels; largest image should come first.
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
		public Builder levels(ImageResolutionLevel... levels) {
			metadata.levels = levels.clone();
			return this;
		}
		
		public Builder sizeZ(final int sizeZ) {
			metadata.sizeZ = sizeZ;
			return this;
		}

		public Builder sizeT(final int sizeT) {
			metadata.sizeT = sizeT;
			return this;
		}

		public Builder pixelSizeMicrons(final Number pixelWidthMicrons, final Number pixelHeightMicrons) {
			pixelCalibrationBuilder.pixelSizeMicrons(pixelWidthMicrons, pixelHeightMicrons);
			return this;
		}

		public Builder zSpacingMicrons(final Number zSpacingMicrons) {
			pixelCalibrationBuilder.zSpacingMicrons(zSpacingMicrons);
			return this;
		}

		public Builder timepoints(final TimeUnit timeUnit, double... timepoints) {
			pixelCalibrationBuilder.timepoints(timeUnit, timepoints);
			return this;
		}
		
		public Builder magnification(final double magnification) {
			metadata.magnification = magnification;
			return this;
		}
		
		public Builder preferredTileSize(final int tileWidth, final int tileHeight) {
			metadata.preferredTileWidth = tileWidth;
			metadata.preferredTileHeight = tileHeight;
			return this;
		}
		
		public Builder channels(ImageChannel... channels) {
			return this.channels(Arrays.asList(channels));
		}

		public Builder channels(Collection<ImageChannel> channels) {
			metadata.channels = Collections.unmodifiableList(new ArrayList<>(channels));
			return this;
		}

		public Builder name(final String name) {
			metadata.name = name;
			return this;
		}
		
		public ImageServerMetadata build() {
			metadata.pixelCalibration = pixelCalibrationBuilder.build();
			
			if (metadata.levels == null)
				metadata.levels = new ImageResolutionLevel[] {new ImageResolutionLevel(1, metadata.width, metadata.height)};
			
			if (metadata.width <= 0 && metadata.height <= 0)
				throw new IllegalArgumentException("Invalid metadata - width & height must be > 0");

			if (metadata.path == null || metadata.path.isBlank())
				throw new IllegalArgumentException("Invalid metadata - path must be set (and not be blank)");
						
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
			return metadata;
		}

	}
	
	ImageServerMetadata() {};
	
	
	ImageServerMetadata(final String path) {
		this.path = path;
	};

	ImageServerMetadata(final ImageServerMetadata metadata) {
		this.path = metadata.path;
		this.name = metadata.name;
		this.levels = metadata.levels.clone();
		
		this.width = metadata.width;
		this.height = metadata.height;
		
		this.sizeZ = metadata.sizeZ;
		this.sizeT = metadata.sizeT;
				
		this.isRGB = metadata.isRGB;
		this.bitDepth = metadata.bitDepth;
		
		this.pixelCalibration = metadata.pixelCalibration;
		
		this.channels = new ArrayList<>(metadata.getChannels());
		
		this.magnification = metadata.magnification;
		
		this.preferredTileWidth = metadata.preferredTileWidth;
		this.preferredTileHeight = metadata.preferredTileHeight;
	};

	public String getPath() {
		return path;
	}
	
	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}
	
	public int nLevels() {
		return levels.length;
	}
	
	public double getDownsampleForLevel(int level) {
		return levels[level].getDownsample();
	}
	
	public ImageResolutionLevel getLevel(int level) {
		return levels[level];
	}
	
	public boolean isRGB() {
		return isRGB;
	}
	
	public int getBitDepth() {
		return bitDepth;
	}
	
	public boolean pixelSizeCalibrated() {
		return pixelCalibration.hasPixelSizeMicrons();
	}
	
	public boolean zSpacingCalibrated() {
		return pixelCalibration.hasZSpacingMicrons();
	}
	
	public double getAveragedPixelSize() {
		return (getPixelWidthMicrons() + getPixelHeightMicrons())/2;
	}
	
	public double getPixelWidthMicrons() {
		return pixelCalibration.getPixelWidthMicrons();
	}

	public double getPixelHeightMicrons() {
		return pixelCalibration.getPixelHeightMicrons();
	}
	
	public double getZSpacingMicrons() {
		return pixelCalibration.getZSpacingMicrons();
	}
	
//	// TODO: Consider if mutability is permissible
//	public void setPixelSizeMicrons(final double pixelWidth, final double pixelHeight) {
//		this.pixelWidthMicrons = pixelWidth;
//		this.pixelHeightMicrons = pixelHeight;
//	}

	public TimeUnit getTimeUnit() {
		return pixelCalibration.getTimeUnit();
	}
	
	public double getTimepoint(int ind) {
		return pixelCalibration.getTimepoint(ind);
	}
	
	public int getSizeZ() {
		return sizeZ;
	}

	public int getSizeT() {
		return sizeT;
	}

	public int getSizeC() {
		return channels.size();
	}
	
	public double getMagnification() {
		return magnification;
	}
	
	public int getPreferredTileWidth() {
		return preferredTileWidth;
	}

	public int getPreferredTileHeight() {
		return preferredTileHeight;
	}

	public ImageServerMetadata duplicate() {
		return new ImageServerMetadata(this);
	}
	
	public String getName() {
		return name;
	}
	
	public ImageChannel getChannel(int n) {
		return channels.get(n);
	}
	
	public List<ImageChannel> getChannels() {
		return channels;
	}
	
	/**
	 * Returns true if a specified ImageServerMetadata is compatible with this one, i.e. it has the same path and dimensions
	 * (but possibly different pixel sizes, magnifications etc.).
	 * 
	 * @param metadata
	 * @return
	 */
	public boolean isCompatibleMetadata(final ImageServerMetadata metadata) {
		return path.equals(metadata.path) && 
				bitDepth == metadata.bitDepth &&
				Arrays.equals(levels, metadata.levels) && 
				sizeT == metadata.sizeT && 
				getSizeC() == metadata.getSizeC() &&
				sizeZ == metadata.sizeZ;
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("{ ");
		sb.append("\"path\": \"").append(path).append("\", ");
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
		result = prime * result + bitDepth;
		result = prime * result + ((channels == null) ? 0 : channels.hashCode());
		result = prime * result + (isRGB ? 1231 : 1237);
		result = prime * result + Arrays.hashCode(levels);
		long temp;
		temp = Double.doubleToLongBits(magnification);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((pixelCalibration == null) ? 0 : pixelCalibration.hashCode());
		result = prime * result + preferredTileHeight;
		result = prime * result + preferredTileWidth;
		result = prime * result + sizeT;
		result = prime * result + sizeZ;
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
		if (bitDepth != other.bitDepth)
			return false;
		if (channels == null) {
			if (other.channels != null)
				return false;
		} else if (!channels.equals(other.channels))
			return false;
		if (isRGB != other.isRGB)
			return false;
		if (!Arrays.equals(levels, other.levels))
			return false;
		if (Double.doubleToLongBits(magnification) != Double.doubleToLongBits(other.magnification))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (pixelCalibration == null) {
			if (other.pixelCalibration != null)
				return false;
		} else if (!pixelCalibration.equals(other.pixelCalibration))
			return false;
		if (preferredTileHeight != other.preferredTileHeight)
			return false;
		if (preferredTileWidth != other.preferredTileWidth)
			return false;
		if (sizeT != other.sizeT)
			return false;
		if (sizeZ != other.sizeZ)
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
		
		public double getDownsample() {
			return downsample;
		}
		
		public int getWidth() {
			return width;
		}
		
		public int getHeight() {
			return height;
		}
		
		
		
		@Override
		public String toString() {
			return "Level: " + width + "x" + height + " (" + GeneralTools.formatNumber(downsample, 5) + ")";
		}
		
		public static class Builder {
			
			private int fullWidth, fullHeight;
			private List<ImageResolutionLevel> levels = new ArrayList<>();
			
			public Builder(int fullWidth, int fullHeight) {
				this.fullWidth = fullWidth;
				this.fullHeight = fullHeight;
			}
			
			public Builder addLevelByDownsample(double downsample) {
				int levelWidth = (int)(fullWidth / downsample);
				int levelHeight = (int)(fullHeight / downsample);
				return addLevel(downsample, levelWidth, levelHeight);
			}
			
			public Builder addFullResolutionLevel() {
				return addLevel(1, fullWidth, fullHeight);
			}
			
			public Builder addLevel(double downsample, int levelWidth, int levelHeight) {
				levels.add(new ImageResolutionLevel(downsample, levelWidth, levelHeight));
				return this;
			}
			
			public Builder addLevel(int levelWidth, int levelHeight) {
				double downsample = estimateDownsample(fullWidth, fullHeight, levelWidth, levelHeight, levels.size());
				return addLevel(downsample, levelWidth, levelHeight);
			}
			
			public Builder addLevel(ImageResolutionLevel level) {
				return addLevel(level.downsample, level.width, level.height);
			}
			
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
			private double estimateDownsample(final int fullWidth, final int fullHeight, final int levelWidth, final int levelHeight, final int level) {
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
