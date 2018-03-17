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

import java.util.concurrent.TimeUnit;

/**
 * Class for storing primary ImageServer metadata fields.
 * Could be used when the metadata needs ot be adjusted (e.g. to correct erroneous pixel sizes).
 * 
 * TODO: Support metadata changes, taking into consideration the need for scriptability and persistence
 * 
 * @author Pete Bankhead
 *
 */
public class ImageServerMetadata {
	
	private String path;
	private int width;
	private int height;
	
	private int sizeC = 1;
	private int sizeZ = 1;
	private int sizeT = 1;
	
	private boolean isRGB = false;
	private int bitDepth = 8;
	
	private double[] downsamples = new double[] {1};
	
	private double pixelWidthMicrons = Double.NaN;
	private double pixelHeightMicrons = Double.NaN;
	private double zSpacingMicrons = Double.NaN;
	private TimeUnit timeUnit = TimeUnit.SECONDS;
	
	private double magnification = Double.NaN;
	
	private int preferredTileWidth;
	private int preferredTileHeight;
	
	
	public static class Builder {
		
		private ImageServerMetadata metadata;
		
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
		
		public Builder setSizeC(final int sizeC) {
			metadata.sizeC = sizeC;
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

		public Builder setPixelSizeMicrons(final double pixelWidthMicrons, final double pixelHeightMicrons) {
			metadata.pixelWidthMicrons = pixelWidthMicrons;
			metadata.pixelHeightMicrons = pixelHeightMicrons;
			return this;
		}

		public Builder setZSpacingMicrons(final double zSpacingMicrons) {
			metadata.zSpacingMicrons = zSpacingMicrons;
			return this;
		}

		public Builder setTimeUnit(final TimeUnit timeUnit) {
			metadata.timeUnit = timeUnit;
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
		
		public ImageServerMetadata build() {
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
		this.width = metadata.width;
		this.height = metadata.height;
		
		this.sizeC = metadata.sizeC;
		this.sizeZ = metadata.sizeZ;
		this.sizeT = metadata.sizeT;
		
		this.isRGB = metadata.isRGB;
		this.bitDepth = metadata.bitDepth;
		this.downsamples = metadata.downsamples.clone();
		
		this.pixelWidthMicrons = metadata.pixelWidthMicrons;
		this.pixelHeightMicrons = metadata.pixelHeightMicrons;
		this.zSpacingMicrons = metadata.zSpacingMicrons;
		this.timeUnit = metadata.timeUnit;
		
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
		return !Double.isNaN(pixelHeightMicrons + pixelWidthMicrons);
	}
	
	public boolean zSpacingCalibrated() {
		return !Double.isNaN(zSpacingMicrons);
	}
	
	public double getAveragedPixelSize() {
		return (pixelWidthMicrons + pixelHeightMicrons)/2;
	}
	
	public double getPixelWidthMicrons() {
		return pixelWidthMicrons;
	}

	public double getPixelHeightMicrons() {
		return pixelHeightMicrons;
	}
	
	public double getZSpacingMicrons() {
		return zSpacingMicrons;
	}
	
//	// TODO: Consider if mutability is permissible
//	public void setPixelSizeMicrons(final double pixelWidth, final double pixelHeight) {
//		this.pixelWidthMicrons = pixelWidth;
//		this.pixelHeightMicrons = pixelHeight;
//	}

	public TimeUnit getTimeUnit() {
		return timeUnit;
	}
	
	public int getSizeZ() {
		return sizeZ;
	}

	public int getSizeT() {
		return sizeT;
	}

	public int getSizeC() {
		return sizeC;
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
	
	
	/**
	 * Returns true if a specified ImageServerMetadata is compatible with this one, i.e. it has the same path and dimensions
	 * (but possibly different pixel sizes, magnifications etc.).
	 * @param metadata
	 * @return
	 */
	public boolean isCompatibleMetadata(final ImageServerMetadata metadata) {
		return path.equals(metadata.path) && width == metadata.width && height == metadata.height && sizeT == metadata.sizeT && sizeC == metadata.sizeC &&
				sizeZ == metadata.sizeZ;
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("{ ");
		sb.append("\"path\": \"").append(path).append("\", ");
		sb.append("\"width\": ").append(width).append(", ");
		sb.append("\"height\": ").append(height).append(", ");
		sb.append("\"sizeC\": ").append(sizeC);
		if (sizeZ != 1)
			sb.append(", ").append("\"sizeZ\": ").append(sizeZ);
		if (sizeT != 1) {
			sb.append(", ").append("\"sizeT\": ").append(sizeT);
			sb.append(", ").append("\"timeUnit\": ").append(timeUnit);
		}
		if (pixelSizeCalibrated()) {
			sb.append(", ").append("\"pixelWidthMicrons\": ").append(pixelWidthMicrons);
			sb.append(", ").append("\"pixelHeightMicrons\": ").append(pixelHeightMicrons);
		}
		sb.append(" }");
		return sb.toString();
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + height;
		long temp;
		temp = Double.doubleToLongBits(magnification);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		temp = Double.doubleToLongBits(pixelHeightMicrons);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(pixelWidthMicrons);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + preferredTileHeight;
		result = prime * result + preferredTileWidth;
		result = prime * result + sizeC;
		result = prime * result + sizeT;
		result = prime * result + sizeZ;
		result = prime * result + ((timeUnit == null) ? 0 : timeUnit.hashCode());
		result = prime * result + width;
		temp = Double.doubleToLongBits(zSpacingMicrons);
		result = prime * result + (int) (temp ^ (temp >>> 32));
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
		if (height != other.height)
			return false;
		if (Double.doubleToLongBits(magnification) != Double.doubleToLongBits(other.magnification))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (Double.doubleToLongBits(pixelHeightMicrons) != Double.doubleToLongBits(other.pixelHeightMicrons))
			return false;
		if (Double.doubleToLongBits(pixelWidthMicrons) != Double.doubleToLongBits(other.pixelWidthMicrons))
			return false;
		if (preferredTileHeight != other.preferredTileHeight)
			return false;
		if (preferredTileWidth != other.preferredTileWidth)
			return false;
		if (sizeC != other.sizeC)
			return false;
		if (sizeT != other.sizeT)
			return false;
		if (sizeZ != other.sizeZ)
			return false;
		if (timeUnit != other.timeUnit)
			return false;
		if (width != other.width)
			return false;
		if (Double.doubleToLongBits(zSpacingMicrons) != Double.doubleToLongBits(other.zSpacingMicrons))
			return false;
		return true;
	}

}
