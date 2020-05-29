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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import qupath.lib.common.GeneralTools;

/**
 * Class used to represent pixel sizes.
 * <p>
 * Currently only 'pixel' and 'µm' units are supported.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelCalibration {

	/**
	 * String to represent 'pixel' units. This is the default when no pixel size calibration is known.
	 */
	public final static String PIXEL = "px";

	/**
	 * String to represent 'micrometer' units.
	 */
	public final static String MICROMETER = GeneralTools.micrometerSymbol();

	/**
	 * String to represent 'z-slice' units.
	 */
	public final static String Z_SLICE = "z-slice";
	
	private SimpleQuantity pixelWidth = SimpleQuantity.DEFAULT_PIXEL_SIZE;
	private SimpleQuantity pixelHeight = SimpleQuantity.DEFAULT_PIXEL_SIZE;
	
	private SimpleQuantity zSpacing = SimpleQuantity.DEFAULT_Z_SPACING;
	
	private TimeUnit timeUnit = TimeUnit.SECONDS;
	private double[] timepoints = new double[0];
	
	private static PixelCalibration DEFAULT_INSTANCE = new Builder()
			.build();
	
	private PixelCalibration() {}
		
	private PixelCalibration duplicate() {
		var cal = new PixelCalibration();
		cal.pixelWidth = new SimpleQuantity(pixelWidth.value, pixelWidth.unit);
		cal.pixelHeight = new SimpleQuantity(pixelHeight.value, pixelHeight.unit);
		cal.zSpacing = new SimpleQuantity(zSpacing.value, zSpacing.unit);
		cal.timeUnit = timeUnit;
		cal.timepoints = timepoints == null ? null : timepoints.clone();
		return cal;
	}

//	public SimpleQuantity getPixelWidth() {
//		return pixelWidth;
//	}
//	
//	public SimpleQuantity getPixelHeight() {
//		return pixelHeight;
//	}
//
//	public SimpleQuantity getZSpacing() {
//		return zSpacing;
//	}
	
	/**
	 * Get a scaled instance of this PixelCalibration, multiplying pixel sizes for x and y by the specified scale values.
	 * Units are kept the same.
	 * @param scaleX
	 * @param scaleY
	 * @return
	 */
	public PixelCalibration createScaledInstance(double scaleX, double scaleY) {
		return createScaledInstance(scaleX, scaleY, 1);
	}
	
	/**
	 * Get a scaled instance of this PixelCalibration, multiplying pixel sizes for x, y and z by the specified scale values.
	 * Units are kept the same.
	 * @param scaleX
	 * @param scaleY
	 * @param scaleZ
	 * @return
	 */
	public PixelCalibration createScaledInstance(double scaleX, double scaleY, double scaleZ) {
		PixelCalibration cal2 = duplicate();
		cal2.pixelWidth = pixelWidth.scale(scaleX);
		cal2.pixelHeight = pixelHeight.scale(scaleY);
		cal2.zSpacing = zSpacing.scale(scaleZ);
		return cal2;
	}
	
	/**
	 * Multiply one number by another, handling BigDecimals if necessary.
	 * @param n1
	 * @param scale
	 * @return
	 */
	private static Number multiply(Number n1, double scale) {
		if (n1 instanceof BigInteger)
			n1 = new BigDecimal((BigInteger)n1);
		if (n1 instanceof BigDecimal)
			return ((BigDecimal)n1).multiply(BigDecimal.valueOf(scale));
		return n1.doubleValue() * scale;
	}
	
	private static Number average(Number n1, Number n2) {
		Number half1 = multiply(n1, 0.5);
		Number half2 = multiply(n2, 0.5);
		return add(half1, half2);
	}
	
	private static Number add(Number n1, Number n2) {
		if (n1 instanceof BigInteger)
			n1 = new BigDecimal((BigInteger)n2);
		if (n2 instanceof BigInteger)
			n2 = new BigDecimal((BigInteger)n2);
		
		if (n1 instanceof BigDecimal && n2 instanceof BigDecimal)
			return ((BigDecimal)n1).add((BigDecimal)n2);
		return n1.doubleValue() + n2.doubleValue();
	}
	
	/**
	 * Get the time unit for a time series.
	 * @return
	 */
	public TimeUnit getTimeUnit() {
		return timeUnit;
	}
	
	/**
	 * Get the number of known time points.
	 * @return
	 */
	public int nTimepoints() {
		return timepoints.length;
	}
	
	/**
	 * Get the time for the specified time point, or Double.NaN if this is unknown.
	 * @param ind
	 * @return
	 */
	public double getTimepoint(int ind) {
		return ind >= timepoints.length ? Double.NaN : timepoints[ind];
	}
	
	/**
	 * Returns true if the pixel width and height information in microns is known.
	 * @return
	 */
	public boolean hasPixelSizeMicrons() {
		return MICROMETER.equals(pixelWidth.unit) && MICROMETER.equals(pixelHeight.unit);
	}
	
	/**
	 * Returns true if the z-spacing is known in microns.
	 * @return
	 */
	public boolean hasZSpacingMicrons() {
		return MICROMETER.equals(zSpacing.unit);
	}
	
	/**
	 * Get the average of the pixel width and height in microns if possible, or Double.NaN if the pixel size is not available.
	 * @return
	 */
	public double getAveragedPixelSizeMicrons() {
		return (getPixelWidthMicrons() + getPixelHeightMicrons()) / 2.0;
	}
	
	/**
	 * Get the z-spacing in microns, or Double.NaN if this is unknown.
	 * @return
	 */
	public double getZSpacingMicrons() {
		if (hasZSpacingMicrons())
			return zSpacing.value.doubleValue();
		return Double.NaN;
	}
	
	/**
	 * Get the pixel width in microns, or Double.NaN if this is unknown.
	 * @return
	 */
	public double getPixelWidthMicrons() {
		if (hasPixelSizeMicrons())
			return pixelWidth.value.doubleValue();
		return Double.NaN;
	}
	
	/**
	 * Get the pixel height in microns, or Double.NaN if this is unknown.
	 * @return
	 */
	public double getPixelHeightMicrons() {
		if (hasPixelSizeMicrons())
			return pixelHeight.value.doubleValue();
		return Double.NaN;
	}
	
	/**
	 * Get a String representation of the preferred pixel width unit.
	 * @return
	 * 
	 * @see #getPixelWidthMicrons()
	 * @see #PIXEL
	 * @see #MICROMETER
	 */
	public String getPixelWidthUnit() {
		return pixelWidth.unit;
	}
	
	/**
	 * Returns true if the units for pixel width and height are the same.
	 * @return
	 */
	public boolean unitsMatch2D() {
		return Objects.equals(getPixelHeightUnit(), getPixelHeightUnit());
	}
	
	/**
	 * Returns true if the units for pixel width, height and z-spacing are the same.
	 * @return
	 */
	public boolean unitsMatch3D() {
		return unitsMatch2D() && Objects.equals(getPixelHeightUnit(), getZSpacingUnit());
	}

	/**
	 * Get a String representation of the preferred pixel height unit.
	 * @return
	 * 
	 * @see #getPixelHeightMicrons()
	 * @see #PIXEL
	 * @see #MICROMETER
	 */
	public String getPixelHeightUnit() {
		return pixelHeight.unit;
	}
	
	/**
	 * Get a String representation of the preferred z-spacing unit.
	 * @return
	 * 
	 * @see #getZSpacing()
	 * @see #PIXEL
	 * @see #MICROMETER
	 */
	public String getZSpacingUnit() {
		return zSpacing.unit;
	}

	/**
	 * Get an average of {@link #getPixelWidth()} and {@link #getPixelHeight()}.
	 * No check is made to ensure that these are returned in the same units; rather, the numbers are simply averaged.
	 * @return
	 */
	public Number getAveragedPixelSize() {
		return average(getPixelWidth(), getPixelHeight());
	}
	
	/**
	 * Get the numeric value representing the pixel width, in the stored units.
	 * @return
	 * 
	 * @see #getPixelWidthUnit()
	 */
	public Number getPixelWidth() {
		return pixelWidth.value;
	}
	
	/**
	 * Get the numeric value representing the pixel height, in the stored units.
	 * @return
	 * 
	 * @see #getPixelHeightUnit()
	 */
	public Number getPixelHeight() {
		return pixelHeight.value;
	}
	
	/**
	 * Get the numeric value representing the z-spacing, in the stored units.
	 * @return
	 * 
	 * @see #getZSpacingUnit()
	 */
	public Number getZSpacing() {
		return zSpacing.value;
	}

	@Override
	public String toString() {
		return String.format("Pixel calibration: x=%s, y=%s, z=%s, t=%s",
				pixelWidth.toString(), pixelHeight.toString(), zSpacing.toString(), timeUnit.toString());
	}
	
	static class SimpleQuantity {
		
		private Double value; // Note that we could use Number, but then this causes serialization complications with Gson
		private String unit;
		
		private static SimpleQuantity DEFAULT_PIXEL_SIZE = new SimpleQuantity(1, PIXEL);
		private static SimpleQuantity DEFAULT_Z_SPACING = new SimpleQuantity(1, Z_SLICE);
		
		static SimpleQuantity getLengthMicrometers(Number value) {
			return new SimpleQuantity(value, MICROMETER);
		}
				
		private SimpleQuantity(Number value, String unit) {
			this.value = value.doubleValue();
			this.unit = unit;
		}
		
//		public Number getValue() {
//			return value;
//		}
//		
//		public String getUnit() {
//			return unit;
//		}

		@Override
		public String toString() {
			return value + " " + unit;
		}
		
		private SimpleQuantity scale(double scale) {
			return new SimpleQuantity(multiply(value, scale), unit);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((unit == null) ? 0 : unit.hashCode());
			result = prime * result + ((value == null) ? 0 : value.hashCode());
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
			SimpleQuantity other = (SimpleQuantity) obj;
			if (unit == null) {
				if (other.unit != null)
					return false;
			} else if (!unit.equals(other.unit))
				return false;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}
		
		
		
	}
	
	/**
	 * Builder class for {@link PixelCalibration} objects.
	 */
	public static class Builder {
		
		PixelCalibration cal = new PixelCalibration();
		
		/**
		 * Create a new builder with default (uncalibrated) values.
		 */
		public Builder() {}
		
		/**
		 * Create a new builder, initialized values of an existing {@link PixelCalibration}.
		 * @param cal
		 */
		public Builder(PixelCalibration cal) {
			this.cal = cal.duplicate();
		}
		
		/**
		 * Specify the pixel width and height in microns (the most common calibration value).
		 * @param pixelWidthMicrons
		 * @param pixelHeightMicrons
		 * @return
		 */
		public Builder pixelSizeMicrons(Number pixelWidthMicrons, Number pixelHeightMicrons) {
			// Support resetting both pixel sizes to default
			if ((pixelWidthMicrons == null || Double.isNaN(pixelWidthMicrons.doubleValue())) && 
					(pixelHeightMicrons == null || Double.isNaN(pixelHeightMicrons.doubleValue()))) {
				cal.pixelWidth = SimpleQuantity.DEFAULT_PIXEL_SIZE;
				cal.pixelHeight = SimpleQuantity.DEFAULT_PIXEL_SIZE;
				return this;
			}
				
			if (!Double.isFinite(pixelWidthMicrons.doubleValue()) || pixelWidthMicrons.doubleValue() <= 0)
				throw new IllegalArgumentException("Pixel width must be a finite number > 0, not " + pixelWidthMicrons);
			
			if (!Double.isFinite(pixelHeightMicrons.doubleValue()) || pixelHeightMicrons.doubleValue() <= 0)
				throw new IllegalArgumentException("Pixel height must be a finite number > 0, not " + pixelHeightMicrons);

			cal.pixelWidth = SimpleQuantity.getLengthMicrometers(pixelWidthMicrons);
			cal.pixelHeight = SimpleQuantity.getLengthMicrometers(pixelHeightMicrons);
			
			return this;
		}
		
		/**
		 * Specify timepoints and units.
		 * @param timeUnit
		 * @param timepoints
		 * @return
		 */
		public Builder timepoints(TimeUnit timeUnit, double... timepoints) {
			cal.timeUnit = timeUnit;
			cal.timepoints = timepoints.clone();
			return this;
		}
				
		/**
		 * Specify spacing between z-slices, in microns.
		 * @param zSpacingMicrons
		 * @return
		 */
		public Builder zSpacingMicrons(Number zSpacingMicrons) {
			if (zSpacingMicrons == null || Double.isNaN(zSpacingMicrons.doubleValue())) {
				cal.zSpacing = SimpleQuantity.DEFAULT_Z_SPACING;
				return this;
			}
			
			if (!Double.isFinite(zSpacingMicrons.doubleValue()) || zSpacingMicrons.doubleValue() <= 0)
				throw new IllegalArgumentException("Z-spacing must be a finite number > 0, not " + zSpacingMicrons);
			
			cal.zSpacing = SimpleQuantity.getLengthMicrometers(zSpacingMicrons);
			return this;
		}
		
		/**
		 * Build {@link PixelCalibration} object.
		 * @return
		 */
		public PixelCalibration build() {
			return cal;
		}
		
	}
	
	/**
	 * Get the default PixelCalibration.
	 * This isn't terribly informative, giving pixel sizes in pixel units.
	 * @return
	 */
	public static PixelCalibration getDefaultInstance() {
		return DEFAULT_INSTANCE;
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((pixelHeight == null) ? 0 : pixelHeight.hashCode());
		result = prime * result + ((pixelWidth == null) ? 0 : pixelWidth.hashCode());
		result = prime * result + ((timeUnit == null) ? 0 : timeUnit.hashCode());
		result = prime * result + Arrays.hashCode(timepoints);
		result = prime * result + ((zSpacing == null) ? 0 : zSpacing.hashCode());
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
		PixelCalibration other = (PixelCalibration) obj;
		if (pixelHeight == null) {
			if (other.pixelHeight != null)
				return false;
		} else if (!pixelHeight.equals(other.pixelHeight))
			return false;
		if (pixelWidth == null) {
			if (other.pixelWidth != null)
				return false;
		} else if (!pixelWidth.equals(other.pixelWidth))
			return false;
		if (timeUnit != other.timeUnit)
			return false;
		if (!Arrays.equals(timepoints, other.timepoints))
			return false;
		if (zSpacing == null) {
			if (other.zSpacing != null)
				return false;
		} else if (!zSpacing.equals(other.zSpacing))
			return false;
		return true;
	}
	

}