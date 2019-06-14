package qupath.lib.images.servers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
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

	private final static String Z_SLICE = "z-slice";
	
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
	
	/**
	 * Get the average of the pixel width and height in microns if possible, or Double.NaN if the pixel size is not available.
	 * @param pixelCalibration
	 * @return
	 */
	public static double getAveragePixelSizeMicrons(PixelCalibration pixelCalibration) {
		return (pixelCalibration.getPixelWidthMicrons() + pixelCalibration.getPixelHeightMicrons()) / 2.0;
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
	public PixelCalibration getScaledInstance(double scaleX, double scaleY) {
		return getScaledInstance(scaleX, scaleY, 1);
	}
	
	/**
	 * Get a scaled instance of this PixelCalibration, multiplying pixel sizes for x, y and z by the specified scale values.
	 * Units are kept the same.
	 * @param scaleX
	 * @param scaleY
	 * @param scaleZ
	 * @return
	 */
	public PixelCalibration getScaledInstance(double scaleX, double scaleY, double scaleZ) {
		PixelCalibration cal2 = duplicate();
		cal2.pixelWidth.value = scale(cal2.pixelWidth.value, scaleX);
		cal2.pixelHeight.value = scale(cal2.pixelHeight.value, scaleY);
		cal2.zSpacing.value = scale(cal2.zSpacing.value, scaleZ);
		return cal2;
	}
	
	private static Number scale(Number n1, double scale) {
		if (n1 instanceof BigInteger)
			n1 = new BigDecimal((BigInteger)n1);
		if (n1 instanceof BigDecimal)
			return ((BigDecimal)n1).multiply(new BigDecimal(scale));
		return n1.doubleValue() * scale;
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

	@Override
	public String toString() {
		return String.format("Pixel calibration: x=%s, y=%s, z=%s, t=%s",
				pixelWidth.toString(), pixelHeight.toString(), zSpacing.toString(), timeUnit.toString());
	}
	
	static class SimpleQuantity {
		
		private Number value;
		private String unit;
		
		private static SimpleQuantity DEFAULT_PIXEL_SIZE = new SimpleQuantity(1, PIXEL);
		private static SimpleQuantity DEFAULT_Z_SPACING = new SimpleQuantity(1, Z_SLICE);
		
		static SimpleQuantity getLengthMicrometers(Number value) {
			return new SimpleQuantity(value, MICROMETER);
		}
				
		private SimpleQuantity(Number value, String unit) {
			this.value = value;
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
		
	}
	
	
	public static class Builder {
		
		PixelCalibration cal = new PixelCalibration();
		
		public Builder() {}
		
		public Builder(PixelCalibration cal) {
			this.cal = cal.duplicate();
		}
		
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
		
		public Builder timepoints(TimeUnit timeUnit, double... timepoints) {
			cal.timeUnit = timeUnit;
			cal.timepoints = timepoints.clone();
			return this;
		}
				
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
