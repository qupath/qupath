package qupath.lib.images.servers;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import qupath.lib.common.GeneralTools;

class PixelCalibration {

	private static String PIXEL = "px";
	private static String Z_SLICE = "z-slice";
	private static String MICROMETER = GeneralTools.micrometerSymbol();
	
	private SimpleQuantity pixelWidth = SimpleQuantity.DEFAULT_PIXEL_SIZE;
	private SimpleQuantity pixelHeight = SimpleQuantity.DEFAULT_PIXEL_SIZE;
	
	private SimpleQuantity zSpacing = SimpleQuantity.DEFAULT_Z_SPACING;
	
	private TimeUnit timeUnit = TimeUnit.SECONDS;
	private double[] timepoints;
	
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
	
	public boolean hasPixelSizeMicrons() {
		return MICROMETER.equals(pixelWidth.unit) && MICROMETER.endsWith(pixelHeight.unit);
	}
	
	public boolean hasZSpacingMicrons() {
		return MICROMETER.equals(zSpacing.unit);
	}
	
	public TimeUnit getTimeUnit() {
		return timeUnit;
	}
	
	public int nTimepoints() {
		return timepoints.length;
	}
	
	public double getTimepoint(int ind) {
		return timepoints[ind];
	}
	
	public double getZSpacingMicrons() {
		if (hasZSpacingMicrons())
			return zSpacing.value.doubleValue();
		return Double.NaN;
	}
	
	public double getPixelWidthMicrons() {
		if (hasPixelSizeMicrons())
			return pixelWidth.value.doubleValue();
		return Double.NaN;
	}
	
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
		
		@Override
		public String toString() {
			return value + " " + unit;
		}
		
	}
	
	
	public static class Builder {
		
		PixelCalibration cal = new PixelCalibration();
		
		public Builder() {}
		
		Builder(PixelCalibration cal) {
			this.cal = cal.duplicate();
		}
		
		public Builder pixelSizeMicrons(Number pixelWidthMicrons, Number pixelHeightMicrons) {
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
			if (!Double.isFinite(zSpacingMicrons.doubleValue()) || zSpacingMicrons.doubleValue() <= 0)
				throw new IllegalArgumentException("Z-spacing must be a finite number > 0, not " + zSpacingMicrons);
			
			cal.zSpacing = SimpleQuantity.getLengthMicrometers(zSpacingMicrons);
			return this;
		}
		
		public PixelCalibration build() {
			return cal;
		}
		
	}
	
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
