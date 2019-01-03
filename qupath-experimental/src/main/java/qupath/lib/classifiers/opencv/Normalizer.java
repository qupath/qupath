package qupath.lib.classifiers.opencv;

class Normalizer {
	
	private double[] offsets;
	private double[] scales;
	private transient Boolean isIdentity;
	
	Normalizer(double[] offsets, double[] scales) {
		if (offsets.length != scales.length)
			throw new IllegalArgumentException("Length of offsets and scales arrays do not match!");
		this.offsets = offsets.clone();
		this.scales = scales.clone();
		isIdentity = allOne(offsets) && allOne(scales);
	}
	
	double normalizeFeature(int idx, double originalValue) {
		if (isIdentity)
			return originalValue;
		return (originalValue + offsets[idx]) * scales[idx];
	}
	
	boolean allOne(double[] array) {
		for (double d : array) {
			if (d != 1.0)
				return false;
		}
		return true;
	}
	
	boolean isIdentity() {
		if (isIdentity == null) {
			isIdentity = Boolean.valueOf(allOne(offsets) && allOne(scales));
		}
		return isIdentity.booleanValue();
	}
	
	int nFeatures() {
		return scales.length;
	}
	
	double getOffset(int ind) {
		return offsets[ind];
	}

	double getScale(int ind) {
		return scales[ind];
	}
	
}