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

	private String path;
	private String name;
	
	private int width;
	private int height;
	
	private int sizeZ = 1;
	private int sizeT = 1;
	
	private boolean isRGB = false;
	private int bitDepth = 8;
	
	private double[] downsamples = new double[] {1};
	
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
		}
		
		public Builder(final String path, final int width, final int height) {
			metadata = new ImageServerMetadata(path, width, height);
		}
		
		public Builder setRGB(boolean isRGB) {
			metadata.isRGB = isRGB;
			return this;
		}
		
		public Builder setBitDepth(int bitDepth) {
			metadata.bitDepth = bitDepth;
			return this;
		}
		
		public Builder setPreferredDownsamples(double... downsamples) {
			metadata.downsamples = downsamples.clone();
			return this;
		}
		
		public Builder setSizeZ(final int sizeZ) {
			metadata.sizeZ = sizeZ;
			return this;
		}

		public Builder setSizeT(final int sizeT) {
			metadata.sizeT = sizeT;
			return this;
		}

		public Builder setPixelSizeMicrons(final Number pixelWidthMicrons, final Number pixelHeightMicrons) {
			pixelCalibrationBuilder.pixelSizeMicrons(pixelWidthMicrons, pixelHeightMicrons);
			return this;
		}

		public Builder setZSpacingMicrons(final Number zSpacingMicrons) {
			pixelCalibrationBuilder.zSpacingMicrons(zSpacingMicrons);
			return this;
		}

		public Builder setTimeUnit(final TimeUnit timeUnit) {
			pixelCalibrationBuilder.timeUnit(timeUnit);
			return this;
		}
		
		public Builder setMagnification(final double magnification) {
			metadata.magnification = magnification;
			return this;
		}
		
		public Builder setPreferredTileSize(final int tileWidth, final int tileHeight) {
			metadata.preferredTileWidth = tileWidth;
			metadata.preferredTileHeight = tileHeight;
			return this;
		}
		
		public Builder channels(ImageChannel... channels) {
			return this.channels(Arrays.asList(channels));
		}

		public Builder channels(Collection<ImageChannel> channels) {
			metadata.channels.clear();
			metadata.channels.addAll(channels);
			return this;
		}

		public Builder setName(final String name) {
			metadata.name = name;
			return this;
		}
		
		public ImageServerMetadata build() {
			metadata.pixelCalibration = pixelCalibrationBuilder.build();
			return metadata;
		}

	}
	
	ImageServerMetadata() {};
	
	
	ImageServerMetadata(final String path, final int width, final int height) {
		this.path = path;
		this.width = width;
		this.height = height;
		this.preferredTileWidth = width;
		this.preferredTileHeight = height;
	};

	ImageServerMetadata(final ImageServerMetadata metadata) {
		this.path = metadata.path;
		this.name = metadata.name;
		this.width = metadata.width;
		this.height = metadata.height;
		
		this.sizeZ = metadata.sizeZ;
		this.sizeT = metadata.sizeT;
		
		this.isRGB = metadata.isRGB;
		this.bitDepth = metadata.bitDepth;
		this.downsamples = metadata.downsamples.clone();
		
		this.pixelCalibration = metadata.pixelCalibration;
		
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
	
	public double[] getPreferredDownsamples() {
		return downsamples;
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
		return Collections.unmodifiableList(channels);
	}
	
	/**
	 * Returns true if a specified ImageServerMetadata is compatible with this one, i.e. it has the same path and dimensions
	 * (but possibly different pixel sizes, magnifications etc.).
	 * @param metadata
	 * @return
	 */
	public boolean isCompatibleMetadata(final ImageServerMetadata metadata) {
		return path.equals(metadata.path) && width == metadata.width && height == metadata.height && sizeT == metadata.sizeT && getSizeC() == metadata.getSizeC() &&
				sizeZ == metadata.sizeZ;
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("{ ");
		sb.append("\"path\": \"").append(path).append("\", ");
		sb.append("\"name\": \"").append(name).append("\", ");
		sb.append("\"width\": ").append(width).append(", ");
		sb.append("\"height\": ").append(height).append(", ");
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
		result = prime * result + Arrays.hashCode(downsamples);
		result = prime * result + height;
		result = prime * result + (isRGB ? 1231 : 1237);
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
		if (bitDepth != other.bitDepth)
			return false;
		if (channels == null) {
			if (other.channels != null)
				return false;
		} else if (!channels.equals(other.channels))
			return false;
		if (!Arrays.equals(downsamples, other.downsamples))
			return false;
		if (height != other.height)
			return false;
		if (isRGB != other.isRGB)
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
		if (width != other.width)
			return false;
		return true;
	}



	
	

}
