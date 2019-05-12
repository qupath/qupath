package qupath.lib.classifiers.opencv.pixel.features;

import java.util.ArrayList;
import java.util.List;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;

import com.google.gson.Gson;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import qupath.lib.common.GeneralTools;

/**
 * Static methods to generate FeatureFilters.
 * 
 * @author Pete Bankhead
 *
 */
public class FeatureFilters {
	
	public static final String ORIGINAL_PIXELS = "Original pixels";
	public static final String GAUSSIAN_FILTER = "Gaussian filter";
	public static final String STANDARD_DEVIATION_FILTER = "Standard deviation filter";
	public static final String MEDIAN_FILTER = "Median filter";
	public static final String MORPHOLOGICAL_ERODE_FILTER = "Erosion filter";
	public static final String MORPHOLOGICAL_DILATE_FILTER = "Dilation filter";
	public static final String MORPHOLOGICAL_OPEN_FILTER = "Opening filter";
	public static final String MORPHOLOGICAL_CLOSE_FILTER = "Closing filter";
	public static final String SOBEL_FILTER = "Sobel filter";
	public static final String LAPLACIAN_OF_GAUSSIAN_FILTER = "Laplacian of Gaussian filter";
	public static final String COHERENCE_FILTER = "Coherence filter";
	
	public static final String NORMALIZED_INTENSITY_FILTER = "Normalized intensity filter";
	public static final String PEAK_DENSITY_FILTER = "Peak density filter";
	public static final String VALLEY_DENSITY_FILTER = "Valley density filter";
	
	public static FeatureFilter getFeatureFilter(String type, double size) {
		switch(type) {
		case ORIGINAL_PIXELS:
			return new OriginalPixels();
		case GAUSSIAN_FILTER:
			return new GaussianFeatureFilter(size);
		case STANDARD_DEVIATION_FILTER:
			return new StdDevFeatureFilter((int)Math.round(size));
		case MEDIAN_FILTER:
			return new MedianFeatureFilter((int)Math.round(size));
		case SOBEL_FILTER:
			return new SobelFeatureFilter(size);
		case LAPLACIAN_OF_GAUSSIAN_FILTER:
			return new LoGFeatureFilter(size);
		case MORPHOLOGICAL_ERODE_FILTER:
			return new MorphFilter(opencv_imgproc.MORPH_ERODE, (int)Math.round(size));
		case MORPHOLOGICAL_DILATE_FILTER:
			return new MorphFilter(opencv_imgproc.MORPH_DILATE, (int)Math.round(size));
		case MORPHOLOGICAL_OPEN_FILTER:
			return new MorphFilter(opencv_imgproc.MORPH_OPEN, (int)Math.round(size));
		case MORPHOLOGICAL_CLOSE_FILTER:
			return new MorphFilter(opencv_imgproc.MORPH_CLOSE, (int)Math.round(size));
		case NORMALIZED_INTENSITY_FILTER:
			return new NormalizedIntensityFilter(size);
		case PEAK_DENSITY_FILTER:
			return new PeakDensityFilter(1.0, (int)Math.round(size), true);
		case VALLEY_DENSITY_FILTER:
			return new PeakDensityFilter(1.0, (int)Math.round(size), false);
		case COHERENCE_FILTER:
			return new CoherenceFeatureFilter(size);
		default:
			throw new IllegalArgumentException("Unknown feature filter '" + type + "'");
		}
	}
	
	
	static class FeatureFilterTypeAdapterFactory implements TypeAdapterFactory {

		public FeatureFilterTypeAdapterFactory() {}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return featureFilterTypeAdapter.create(gson, type);
		}
		
	}

	private final static RuntimeTypeAdapterFactory<FeatureFilter> featureFilterTypeAdapter = 
			RuntimeTypeAdapterFactory.of(FeatureFilter.class)
			.registerSubtype(OriginalPixels.class)
			.registerSubtype(GaussianFeatureFilter.class)
			.registerSubtype(StdDevFeatureFilter.class)
			.registerSubtype(CoherenceFeatureFilter.class)
			.registerSubtype(MedianFeatureFilter.class)
			.registerSubtype(SobelFeatureFilter.class)
			.registerSubtype(LoGFeatureFilter.class)
			.registerSubtype(MorphFilter.class)
			.registerSubtype(PeakDensityFilter.class)
			.registerSubtype(NormalizedIntensityFilter.class);
	
//	static class FeatureFilterTypeAdapter extends TypeAdapter<FeatureFilter> {
//
//		@Override
//		public void write(JsonWriter out, FeatureFilter value) throws IOException {
//			boolean lenient = out.isLenient();
//			try {
//				out.beginObject();
//				out.name("type");
//				out.value(value.getClass().getSimpleName());
//				var tree = toJsonTree(value);
//				Streams.write(tree, out);
//				out.endObject();
//			} finally {
//				out.setLenient(lenient);
//			}
//		}
//
//		@Override
//		public FeatureFilter read(JsonReader in) throws IOException {
//			boolean lenient = in.isLenient();
//			try {
//				var element = Streams.parse(in);
//				
//				from
//				
//				var obj = element.getAsJsonObject();
//				var inputString = obj.toString();//obj.get("mat").toString();
//				try (var fs = new FileStorage()) {
//					fs.open(inputString, FileStorage.FORMAT_JSON + FileStorage.READ + FileStorage.MEMORY);
//					return read(fs);
//				}
//			} finally {
//				in.setLenient(lenient);
//			}
//		}
//		
//	}
	
	
	static void gaussianFilter(Mat matInput, double sigma, Mat matOutput) {
    	int s = (int)Math.ceil(sigma * 3) * 2 + 1;
    	opencv_imgproc.GaussianBlur(matInput, matOutput, new Size(s, s), sigma);
    }
    
    /**
     * Clone the input image without further modification.
     */
    public static class OriginalPixels extends FeatureFilter {

		@Override
		public String getName() {
			return "Original pixels";
		}

		@Override
		public int getPadding() {
			return 0;
		}

		@Override
		public void calculate(Mat matInput, List<Mat> output) {
			output.add(matInput.clone());
		}
    	
    	
    	
    }
    
    
    static Mat getSumFilter(int radius) {
    	int s = radius*2 + 1;
		var kernel = opencv_imgproc.getStructuringElement(
				opencv_imgproc.MORPH_ELLIPSE, new Size(s, s));
		kernel.convertTo(kernel, opencv_core.CV_32F);
		return kernel;
    }
    
    static Mat getMeanFilter(int radius) {
		var kernel = getSumFilter(radius);
		opencv_core.dividePut(kernel, opencv_core.countNonZero(kernel));
		return kernel;
    }
    
    
    public static class StdDevFeatureFilter extends FeatureFilter {
    	
//    	private boolean includeMean = false;
    	private int radius;
    	
    	private transient Mat kernel;
    	
    	/**
    	 * Median filter.  Note that only size of 3 or 5 is supported in general 
    	 * (other filter sizes require 8-bit images for OpenCV).
    	 */
    	public StdDevFeatureFilter(final int radius) {
//    		this.includeMean = includeMean;
    		this.radius = radius;
    	}

		@Override
		public String getName() {
			return "Std dev filter (sigma=" + radius + ")";
		}

		@Override
		public int getPadding() {
			return radius;
		}
		
		private synchronized Mat getKernel() {
			if (kernel == null) {
				kernel = getMeanFilter(radius);
			}
			return kernel;
		}

		@Override
		public void calculate(Mat matInput, List<Mat> output) {
//			var matX = new Mat();
//			var kernel = getKernel();
//			gaussianFilter(matInput, radius, matX);
//			matX.put(matX.mul(matX));
//			
//			var matX2 = matInput.mul(matInput).asMat();
//			gaussianFilter(matX2, radius, matX2);
//			
//			opencv_core.subtractPut(matX2, matX);
//			opencv_core.sqrt(matX2, matX2);
//			matX.release();
//			output.add(matX2);
			
			var matX = new Mat();
			var kernel = getKernel();
			opencv_imgproc.filter2D(matInput, matX, opencv_core.CV_32F, kernel);
			matX.put(matX.mul(matX));
			
			var matX2 = matInput.mul(matInput).asMat();
			opencv_imgproc.filter2D(matX2, matX2, opencv_core.CV_32F, kernel);
			
			opencv_core.subtractPut(matX2, matX);
			opencv_core.sqrt(matX2, matX2);
			matX.release();
			
			// TODO: Consider applying Gaussian filter afterwards
			
			output.add(matX2);
		}
    	
    }
    
    
    
    public static class NormalizedIntensityFilter extends AbstractGaussianFeatureFilter {

		public NormalizedIntensityFilter(double sigma) {
			super(sigma);
		}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			
			var kernel = getMeanFilter((int)Math.round(getSigma() * 2));

			// Mean of X^2
			var matXSq = matInput.mul(matInput).asMat();
			opencv_imgproc.filter2D(matXSq, matXSq, -1, kernel);
			
			// Mean of 2X*y
			var matX = new Mat();
			opencv_imgproc.filter2D(matInput, matX, -1, kernel);
			var mat2XY = opencv_core.multiply(2.0, matX.mul(matGaussian));
			
			// Mean of y^2 (constant)
			var matYSq = matGaussian.mul(matGaussian);
//			var n = opencv_core.countNonZero(kernel);
//			matYSq = opencv_core.multiply(matYSq, n);
			
			// X^2 + y^2 - 2Xy
			var localStdDev = opencv_core.subtract(opencv_core.add(matXSq, matYSq), mat2XY).asMat();
			opencv_core.sqrt(localStdDev, localStdDev);
			// setTo doesn't appear to work?
//			var mask = opencv_core.lessThan(localStdDev, 1).asMat();
//			var one = new Mat(1, 1, localStdDev.type(), Scalar.ONE);
//			localStdDev.setTo(one, mask);
			localStdDev.put(opencv_core.max(localStdDev, 1.0));
			
			var localSubtracted = opencv_core.subtract(matInput, matGaussian);
			var localNormalized = opencv_core.divide(localSubtracted, localStdDev);
			
			matXSq.put(localNormalized);
			
//			matX.release();
//			mask.release();
//			localStdDev.release();
			
			output.add(matXSq);
		}

		@Override
		public String getName() {
			return "Normalized intensity" + sigmaString();
		}
    	
    }
    
    
    
    public static class PeakDensityFilter extends AbstractGaussianFeatureFilter {

    	private transient Mat kernel = opencv_imgproc.getStructuringElement(
    			opencv_imgproc.MORPH_RECT, new Size(3, 3));
    	
    	private boolean highPeaks;
    	private int radius;
    	private transient Mat sumFilter;
    	
		public PeakDensityFilter(double sigma, int radius, boolean highPeaks) {
			super(sigma);
			this.radius = radius;
			this.sumFilter = getSumFilter(radius);
			this.highPeaks = highPeaks;
		}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			var matTemp = new Mat();
			var matGaussian2 = new Mat();
			gaussianFilter(matInput, getSigma(), matGaussian2);
			if (highPeaks)
				opencv_imgproc.dilate(matGaussian2, matTemp, kernel);
			else
				opencv_imgproc.erode(matGaussian2, matTemp, kernel);
			
			opencv_core.subtractPut(matTemp, matGaussian2);
			matTemp.put(opencv_core.abs(matTemp));
			matTemp.put(opencv_core.lessThan(matTemp, 1e-6));
//			matTemp.put(opencv_core.equals(matTemp, matGaussian));
			
			opencv_imgproc.filter2D(matTemp, matTemp, opencv_core.CV_32F, sumFilter);
			
			matGaussian2.release();
			output.add(matTemp);
		}

		@Override
		public String getName() {
			if (highPeaks)
				return "High peak density" + sigmaString() + " (radius=" + radius + ")";
			else
				return "Low peak density" + sigmaString() + " (radius=" + radius + ")";
		}
    	
    }
    
    
    
    public static class MedianFeatureFilter extends FeatureFilter {
    	
    	private int size;
    	
    	/**
    	 * Median filter.  Note that only size of 3 or 5 is supported in general 
    	 * (other filter sizes require 8-bit images for OpenCV).
    	 */
    	public MedianFeatureFilter(final int size) {
    		this.size = size;
    	}

		@Override
		public String getName() {
			return "Median filter (" + size + "x" + size + ")";
		}

		@Override
		public int getPadding() {
			return size;
		}

		@Override
		public void calculate(Mat matInput, List<Mat> output) {
			var matOutput = new Mat();
			opencv_imgproc.medianBlur(matInput, matOutput, size);
			output.add(matOutput);
		}
    	
    }
    
    
    public static class MorphFilter extends FeatureFilter {
    	
    	private final int radius;
    	private final int op;
    	
    	private transient String opName;
    	private transient Mat kernel;
    	
    	/**
    	 * Median filter.  Note that only size of 3 or 5 is supported in general 
    	 * (other filter sizes require 8-bit images for OpenCV).
    	 */
    	public MorphFilter(final int op, final int radius) {
    		this.op = op;
    		this.radius = radius;
    	}
    	
    	String getOpName() {
    		if (opName == null) {
    			switch (op) {
    			case opencv_imgproc.MORPH_BLACKHAT:
    				return "Morphological blackhat";
    			case opencv_imgproc.MORPH_CLOSE:
    				return "Morphological closing";
    			case opencv_imgproc.MORPH_DILATE:
    				return "Morphological dilation";
    			case opencv_imgproc.MORPH_ERODE:
    				return "Morphological erosion";
    			case opencv_imgproc.MORPH_GRADIENT:
    				return "Morphological gradient";
    			case opencv_imgproc.MORPH_HITMISS:
    				return "Morphological hit-miss";
    			case opencv_imgproc.MORPH_OPEN:
    				return "Morphological opening";
    			case opencv_imgproc.MORPH_TOPHAT:
    				return "Morphological tophat";
    			default:
    				return "Unknown morphological filter (" + op + ")";
    			}
    		}
    		return opName;
    	}

		@Override
		public String getName() {
			return getOpName() + " (radius=" + radius + ")";
		}

		@Override
		public int getPadding() {
			return radius + 1;
		}
		
		Mat getKernel() {
			if (kernel == null) {
				kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, new Size(radius*2+1, radius*2+1));
			}
			return kernel;
		}

		@Override
		public void calculate(Mat matInput, List<Mat> output) {
			var matOutput = new Mat();
			opencv_imgproc.morphologyEx(matInput, matOutput, op, getKernel());
			output.add(matOutput);
		}
    	
    }
    

    private static abstract class AbstractGaussianFeatureFilter extends FeatureFilter {
    	
    	private double sigma;
    	
    	AbstractGaussianFeatureFilter(final double sigma) {
    		this.sigma = sigma;
    	}
    	
        String sigmaString() {
        	return " (\u03C3="+ GeneralTools.formatNumber(sigma, 1) + ")";
//        	return " (sigma=" + GeneralTools.formatNumber(sigma, 1) + ")";
        }
    	
    	public double getSigma() {
    		return sigma;
    	}
    	
    	@Override
		public int getPadding() {
    		return (int)Math.ceil(sigma * 4);
    	}
    	
    	@Override
		public void calculate(Mat matInput, List<Mat> output) {
    		var matGaussian = new Mat();
    		gaussianFilter(matInput, sigma, matGaussian);
    		calculate(matInput, matGaussian, output);
    		matGaussian.release();
    	}
    	
    	/**
    	 * Alternative calculate method, suitable whenever the Gaussian filtering has already been 
    	 * precomputed (so that it is not necessary to do this again).
    	 * 
    	 * @param matInput
    	 * @param matGaussian
    	 * @param output
    	 */
    	public abstract void calculate(Mat matInput, Mat matGaussian, List<Mat> output);
    	
    	@Override
    	public String toString() {
    		return getName();
    	}
    	
    }
    
    public static class GaussianFeatureFilter extends AbstractGaussianFeatureFilter {
    	
    	public GaussianFeatureFilter(double sigma) {
    		super(sigma);
    	}
    	
    	@Override
		public String getName() {
    		return "Gaussian" + sigmaString();
    	}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			output.add(matGaussian.clone());
		}    	
    	
    }
    
    public static class SobelFeatureFilter extends AbstractGaussianFeatureFilter {
    	
    	public SobelFeatureFilter(double sigma) {
    		super(sigma);
    	}
    	
    	@Override
		public String getName() {
    		return "Gradient magnitude" + sigmaString();
    	}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			var matOutput = new Mat();
			var matTemp = new Mat();
			opencv_imgproc.Sobel(matGaussian, matOutput, -1, 1, 0);
			opencv_imgproc.Sobel(matGaussian, matTemp, -1, 0, 1);
			opencv_core.magnitude(matOutput, matTemp, matOutput);
			output.add(matOutput);
			matTemp.release();
		}    	
    	
    }
    
    public static class LoGFeatureFilter extends AbstractGaussianFeatureFilter {
    	
    	public LoGFeatureFilter(double sigma) {
    		super(sigma);
    	}
    	
    	@Override
		public String getName() {
    		return "LoG" + sigmaString();
    	}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			var matOutput = new Mat();
			opencv_imgproc.Laplacian(matGaussian, matOutput, -1);
			output.add(matOutput);
		}    	
    	
    }
    
    
    public static class GaborFeatureFilter extends FeatureFilter {
    	
    	private transient List<Mat> kernels;
    	
    	private double sigma;
    	private double gamma = 0.5;
		private double lamda = 5.0;
		private int nAngles = 4;
    	
    	public GaborFeatureFilter(final double sigma, double gamma, double lamda, int nAngles) {
    		super();
    		this.sigma = sigma;
    		this.gamma = gamma;
    		this.lamda = lamda;
    		this.nAngles = nAngles;
    	}

		@Override
		public String getName() {
			return String.format("Gabor (\u03C3=%.2f, \u03B3=%.2f, \u03BB=%.2f)", sigma, gamma, lamda);
		}

		@Override
		public int getPadding() {
			return (int)Math.ceil(sigma * 4) * 2 + 1;
		}
		
		private synchronized void initializeKernels() {
			if (kernels != null)
				return;
			
//			gamma = 1.0;
			kernels = new ArrayList<>();
//			nAngles = 6;
//			lamda = 3 * sigma;
			
			int w = (int)Math.ceil(sigma * 4) * 2 + 1;
			var size = new Size(w, w);
			
			for (int angle = 0; angle < nAngles; angle++) {
				double theta = angle * (Math.PI / nAngles);
				var kernel = opencv_imgproc.getGaborKernel(size, sigma, theta, lamda * sigma, gamma);
				kernels.add(kernel);
			}
			
		}

		@Override
		public void calculate(Mat matInput, List<Mat> output) {
			kernels = null;
			if (kernels == null)
				initializeKernels();
			
//			Mat matSum = null;
//			for (var kernel : kernels) {
//				var matTemp = new Mat();
//				opencv_imgproc.filter2D(matInput, matTemp, -1, kernel);
//				matTemp.put(opencv_core.abs(matTemp));
//				if (matSum == null)
//					matSum = matTemp;
//				else {
//					opencv_core.max(matSum, matTemp, matSum);
//					matTemp.release();
//				}
//			}
//			output.add(matSum);
			
			Mat matSum = null;
			for (var kernel : kernels) {
				var matTemp = new Mat();
				opencv_imgproc.filter2D(matInput, matTemp, -1, kernel);
				opencv_core.multiply(matTemp, matTemp, matTemp);
				if (matSum == null)
					matSum = matTemp;
				else {
					opencv_core.addPut(matSum, matTemp);
					matTemp.release();
				}
			}
			opencv_core.sqrt(matSum, matSum);
			output.add(matSum);
			
//			Mat matSum = null;
//			for (var kernel : kernels) {
//				var matTemp = new Mat();
//				opencv_imgproc.filter2D(matInput, matTemp, -1, kernel);
//				matTemp.put(opencv_core.abs(matTemp));
//				if (matSum == null)
//					matSum = matTemp;
//				else {
//					opencv_core.addPut(matSum, matTemp);
//					matTemp.release();
//				}
//			}
//			output.add(matSum);
			
//			for (var kernel : kernels) {
//				var matTemp = new Mat();
//				opencv_imgproc.filter2D(matInput, matTemp, -1, kernel);
//				output.add(matTemp);
//			}
		}
    	
    }
    
    /**
     * See http://bigwww.epfl.ch/publications/puespoeki1603.html
     */
    public static class CoherenceFeatureFilter extends AbstractGaussianFeatureFilter {
    	
    	public CoherenceFeatureFilter(double sigma) {
    		super(sigma);
    	}
    	
    	@Override
		public String getName() {
    		return "Coherence" + sigmaString();
    	}

		@Override
		public void calculate(Mat matInput, Mat matGaussian, List<Mat> output) {
			var matDX = new Mat();
			var matDY = new Mat();
			opencv_imgproc.Sobel(matInput, matDX, -1, 1, 0);
			opencv_imgproc.Sobel(matInput, matDY, -1, 0, 1);
			
			var matDXY = new Mat();
			opencv_core.multiply(matDX, matDY, matDXY);
			opencv_core.multiply(matDX, matDX, matDX);
			opencv_core.multiply(matDY, matDY, matDY);
			
			double sigma = getSigma();
			gaussianFilter(matDX, sigma, matDX);
			gaussianFilter(matDY, sigma, matDY);
			gaussianFilter(matDXY, sigma, matDXY);
			
			FloatIndexer idxDX = matDX.createIndexer();
			FloatIndexer idxDY = matDY.createIndexer();
			FloatIndexer idxDXY = matDXY.createIndexer();

			// Reuse one mat for the output
			var matOutput = matDXY;
			FloatIndexer idxOutput = idxDXY;

			long cols = matOutput.cols();
			long rows = matOutput.rows();
			for (long y = 0; y < rows; y++) {
				for (long x = 0; x < cols; x++) {
					float fxx = idxDX.get(y, x);
					float fyy = idxDY.get(y, x);
					float fxy = idxDXY.get(y, x);
					double coherence = Math.sqrt(
							(fxx - fyy) * (fxx - fyy) + 4 * fxy * fxy
							) / (fxx + fyy);
					idxOutput.put(y, x, (float)coherence);
				}
			}
			output.add(matOutput);
			
			idxDX.release();
			idxDY.release();
			idxDXY.release();
			
			matDX.release();		
			matDY.release();		
		}
		
    	
    }

}
