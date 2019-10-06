package qupath.opencv.ml;

/**
 * Class to help with simple feature normalization, by adding an offset and then multiplying by a scaling factor.
 * 
 * @author Pete Bankhead
 *
 */
public class Normalizer {
	
	private double[] offsets;
	private double[] scales;
	private double missingValue;
	private transient Boolean isIdentity;
	
	private Normalizer(double[] offsets, double[] scales, double missingValue) {
		if (offsets.length != scales.length)
			throw new IllegalArgumentException("Length of offsets and scales arrays do not match!");
		this.missingValue = missingValue;
		this.offsets = offsets.clone();
		this.scales = scales.clone();
	}
	
	public static Normalizer createNormalizer(double[] offsets, double[] scales, double missingValue) {
		return new Normalizer(offsets, scales, missingValue);
	}
	
	public double normalizeFeature(int idx, double originalValue) {
		double val = originalValue;
		if (!isIdentity())
			val = (originalValue + offsets[idx]) * scales[idx];
		if (Double.isFinite(val))
			return val;
		else
			return missingValue;
	}
	
	/**
	 * Test is all entries of an array are identical to a specified value.
	 * @param array
	 * @param val
	 * @return true if {@code array[i] == val} for all i within the array, false otherwise.
	 */
	private static boolean allEqual(double[] array, double val) {
		for (double d : array) {
			if (d != val)
				return false;
		}
		return true;
	}
	
	/**
	 * Returns true if this normalizer does not actually do anything.
	 * This is the case if all offsets are zero and all scales are 1.
	 * @return
	 */
	public boolean isIdentity() {
		if (isIdentity == null) {
			isIdentity = Boolean.valueOf(allEqual(offsets, 0) && allEqual(scales, 1));			
		}
		return isIdentity.booleanValue();
	}
	
	/**
	 * Return the value that will be output after normalization if the computed value is not finite.
	 * @return
	 */
	public double getMissingValue() {
		return missingValue;
	}
	
	public int nFeatures() {
		return scales.length;
	}
	
	public double getOffset(int ind) {
		return offsets[ind];
	}

	public double getScale(int ind) {
		return scales[ind];
	}
	
}