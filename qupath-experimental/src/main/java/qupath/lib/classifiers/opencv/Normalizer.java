package qupath.lib.classifiers.opencv;

import java.util.Arrays;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.classifiers.Normalization;

class Normalizer {
	
	private double[] offsets;
	private double[] scales;
	private double missingValue;
	private transient Boolean isIdentity;
	
	public static Normalizer createNormalizer(final Normalization normalization, final Mat samples, final double missingValue) {
		
		Mat features;
		if (samples.channels() == 1)
			features = samples;
		else
			features = samples.reshape(1, samples.rows() * samples.cols());
		
		int nSamples = features.rows();
		int nFeatures = features.cols();
		
		var offsets = new double[nFeatures];
		var scales = new double[nFeatures];
		Arrays.fill(scales, 1.0);
		
		if (normalization == Normalization.NONE) {
			return new Normalizer(offsets, scales, missingValue);
		}
		
		var indexer = samples.createIndexer();
		var inds = new long[2];
		for (int c = 0; c < nFeatures; c++) {
			var stats = new RunningStatistics();
			inds[1] = c;
			for (int r = 0; r < nSamples; r++) {
				inds[0] = r;
				var val = indexer.getDouble(inds);
				if (Double.isFinite(val))
					stats.addValue(val);
			}
			offsets[c] = 0.0;
			scales[c] = 1.0;
			if (stats.size() > 0) {
				if (normalization == Normalization.MEAN_VARIANCE) {
					offsets[c] = -stats.getMean();
					scales[c] = 1.0/stats.getStdDev();
				} else if (normalization == Normalization.MIN_MAX) {
					offsets[c] = -stats.getMin();
					scales[c] = 1.0/(stats.getMax() - stats.getMin());
				}
			}
		}
		indexer.release();
		
		if (features != samples)
			features.release();
		
		return new Normalizer(offsets, scales, missingValue);
	}
	
	
	public static void normalize(final Mat samples, final Normalizer normalizer) {
		if (normalizer == null || normalizer.isIdentity()) {
			// If we have an identify normalizer, 
			if (Double.isFinite(normalizer.missingValue)) {
				opencv_core.patchNaNs(samples, normalizer.missingValue);
			}
			return;
		}
		
		boolean doChannels = samples.channels() > 1 && samples.channels() == normalizer.nFeatures();
		
		// It makes a major difference to performance if these values are extracted rather than requested in the loops
		int nChannels = samples.channels();
		int nRows = samples.rows();
		int nCols = samples.cols();
		
//		FloatIndexer indexer = samples.createIndexer();
//		for (int channel = 0; channel < nChannels; channel++) {
//			for (int row = 0; row < nRows; row++) {
//				for (int col = 0; col < nCols; col++) {
//					int f = doChannels ? channel : col;
//					double val = indexer.get(row, col, channel);
//					val = normalizer.normalizeFeature(f, val);
//					indexer.put(row, col, channel, (float)val);
//				}				
//			}			
//		}
//		indexer.release();
		
		var indexer = samples.createIndexer();
		var inds = new long[3];
		
		for (int channel = 0; channel < nChannels; channel++) {
			inds[2] = channel;
			for (int row = 0; row < nRows; row++) {
				inds[0] = row;
				for (int col = 0; col < nCols; col++) {
					inds[1] = col;
					int f = doChannels ? channel : col;
					double val = indexer.getDouble(inds);
					val = normalizer.normalizeFeature(f, val);
					indexer.putDouble(inds, val);
				}				
			}			
		}
		indexer.release();	
	}
	
	
	Normalizer(double[] offsets, double[] scales, double missingValue) {
		if (offsets.length != scales.length)
			throw new IllegalArgumentException("Length of offsets and scales arrays do not match!");
		this.missingValue = missingValue;
		this.offsets = offsets.clone();
		this.scales = scales.clone();
	}
	
	double normalizeFeature(int idx, double originalValue) {
		double val = originalValue;
		if (!isIdentity())
			val = (originalValue + offsets[idx]) * scales[idx];
		if (Double.isFinite(val))
			return val;
		else
			return missingValue;
	}
	
	boolean allEqual(double[] array, double val) {
		for (double d : array) {
			if (d != val)
				return false;
		}
		return true;
	}
	
	boolean isIdentity() {
		if (isIdentity == null) {
			isIdentity = Boolean.valueOf(allEqual(offsets, 0) && allEqual(scales, 1));			
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