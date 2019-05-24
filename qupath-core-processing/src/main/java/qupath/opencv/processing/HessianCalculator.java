package qupath.opencv.processing;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;

import ij.CompositeImage;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.RegionRequest;

/**
 * Calculate eigenvalues, eigenvectors and determinants of Hessian matrices calculated from images in 2D and 3D.
 * 
 * @author Pete Bankhead
 *
 */
public class HessianCalculator {
	
	/**
	 * Default border strategy when filtering.
	 */
	private static final int BORDER_DEFAULT = opencv_core.BORDER_REPLICATE;
	
	public static void testMe(ImageData<BufferedImage> imageData) throws IOException {
				
		var server = imageData.getServer();
		var hierarchy = imageData.getHierarchy();
		var selectedROI = hierarchy.getSelectionModel().getSelectedROI();
		RegionRequest request = RegionRequest.createInstance(server);
		if (selectedROI != null)
			request = RegionRequest.createInstance(server.getPath(), 1.0, selectedROI);
		
		var stack = OpenCVTools.extractZStack(server, request);
		
		int sizeZ = stack.size();
		
		List<HessianResults> results = Collections.synchronizedList(new ArrayList<>());
		
		int[] channels = new int[] {0};
//		double[] sigmas = new double[] {0.5, 1.0};
		double[] sigmas = new double[] {1.0, 2.0, 4.0, 8.0};
		
		var pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		List<Future<List<HessianResults>>> futures = new ArrayList<>();
		for (int channel : channels) {
		
			Mat[] mats = new Mat[stack.size()];
			for (int i = 0; i < stack.size(); i++) {
				Mat mat = stack.get(i);
				Mat temp = new Mat();
				opencv_core.extractChannel(mat, temp, channel);
				
				temp.convertTo(temp, opencv_core.CV_32F);
				temp.put(opencv_core.multiply(temp, -1.0));
				mats[i] = temp;
			}
			
			// Do local normalization
			double sigmaNormalizeMicrons = 5.0;		
//			normalize3D(
//					Arrays.asList(mats), 
//					sigmaNormalizeMicrons/server.getPixelWidthMicrons(), 
//					sigmaNormalizeMicrons/server.getPixelHeightMicrons(),
//					sigmaNormalizeMicrons/server.getZSpacingMicrons());
			
//			OpenCVTools.matToImagePlus("Normalized", mats).show();
			
			for (double sigmaMicrons : sigmas) {
				futures.add(pool.submit(() -> {
				
					long startTime = System.currentTimeMillis();
			
					List<HessianResults> resultsTemp = new HessianResultsBuilder()
						.sigmaX(sigmaMicrons / server.getPixelWidthMicrons())
						.sigmaY(sigmaMicrons / server.getPixelHeightMicrons())
						.sigmaZ(sigmaMicrons / server.getZSpacingMicrons())
			//			.skipDeterminant()
			//			.ind3D(5)
						.skipEigenvectors()
						.retainSmoothed()
			//			.skipEigenvalues()
//						.do3D()
						.build(mats);
					
					long endTime = System.currentTimeMillis();
					System.err.println("Hessian calculation time: " + (endTime - startTime) + " ms");

					return resultsTemp;
				}));
			}
		}
		pool.shutdown();
		try {
			for (var future : futures)
				results.addAll(future.get());
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		List<Mat> output = new ArrayList<>();
		int n = 1;
		for (var r : results) {
			n = (int)r.getEigenvalues().size();
			output.add(r.getSmoothed());
			output.add(r.getDeterminant());
//			for (var m : r.getEigenvalues().get())
//				output.add(m);
//			for (var m : r.getEigenvectors().get())
//				output.add(m);
		}
		
		var imp = OpenCVTools.matToImagePlus("My stack", output.toArray(Mat[]::new));
		imp = new CompositeImage(imp);
		imp.setDimensions(output.size() / sizeZ, sizeZ, 1);
		((CompositeImage)imp).resetDisplayRanges();
		imp.show();
	}
	
	
	/**
	 * 
	 * TODO: Consider alternative 2x2 eigenvalue calculation
	 * TODO: Calculate determinant by multiplying eigenvalues
	 * TODO: Calculate Laplacian as Lxx + Lyy (+ Lzz)
	 * 
	 * @author Pete Bankhead
	 *
	 */
	public static class HessianResults {
		
		private HessianResultsBuilder builder;
		
		private Mat matDerivs;
		
		private Mat matSmoothed;
		
		private MatVector matvecEigenvalues;
		private MatVector matvecEigenvectors;
		private Mat matDeterminant;
		private Mat matLaplacian;
		
		private HessianResults(HessianResultsBuilder builder, Mat matSmooth, Mat matDerivs) {
			this.builder = new HessianResultsBuilder(builder);
			if (this.builder.retainSmoothed)
				this.matSmoothed = matSmooth;
			initialize(matDerivs);
		}
		
		public Mat getPartialDerivatives() {
			return matDerivs;
		}
		
		public Mat getLaplacian() {
			if (matLaplacian == null)
				throw new UnsupportedOperationException("Laplacian image was not calculated - remember to specify this in the builder!");
			return matLaplacian;
		}
		
		/**
		 * Get the Gaussian-smoothed image, or throws an {@code UnsupportedOperationException} if these were not calculated.
		 * @return
		 */
		public Mat getSmoothed() {
			if (matSmoothed == null)
				throw new UnsupportedOperationException("Smoothed image was not calculated - remember to specify this in the builder!");
			return matSmoothed;
		}
		
		/**
		 * Get the determinant of the Hessian matrix per pixel, or throws an {@code UnsupportedOperationException} if this was not calculated.
		 * @return
		 */
		public Mat getDeterminant() {
			if (matDeterminant == null)
				throw new UnsupportedOperationException("Determinants were not calculated - remember to specify them in the builder!");
			return matDeterminant;
		}
		
		/**
		 * Get the eigenvalues of the Hessian matrix per pixel, or throws an {@code UnsupportedOperationException} if these were not calculated.
		 * <p>
		 * Eigenvalues are ordered from highest to lowest.
		 * @return
		 */
		public MatVector getEigenvalues() {
			if (matvecEigenvalues == null)
				throw new UnsupportedOperationException("Eigenvalues were not calculated - remember to specify them in the builder!");
			return matvecEigenvalues;
		}
		
		/**
		 * Get the eigenvectors of the Hessian matrix per pixel, or throws an {@code UnsupportedOperationException} if these were not calculated.
		 * <p>
		 * Eigenvectors are ordered according to the corresponding eigenvalue (from highest to lowest eigenvalue).
		 * @return
		 */
		public MatVector getEigenvectors() {
			if (matvecEigenvectors == null)
				throw new UnsupportedOperationException("Eigenvectors were not calculated - remember to specify them in the builder!");
			return matvecEigenvectors;
		}

			
		void initialize(Mat matDerivs) {
			if (builder.retainHessian)
				this.matDerivs = matDerivs;
			
			if (builder.skipDeterminant && builder.skipEigenvalues && builder.skipEigenvectors)
				return;
			
			boolean doDeterminant = !builder.skipDeterminant;
			boolean doEigenvalues = !builder.skipEigenvalues;
			boolean doEigenvectors = !builder.skipEigenvectors;
			boolean doLaplacian = !builder.skipLaplacian;
			
			int width = matDerivs.cols();
			int height = matDerivs.rows();
			
			FloatIndexer indexerDerivs = matDerivs.createIndexer();
			int nChannels = (int)indexerDerivs.channels();
			int vecLength;
			if (nChannels == 4)
				vecLength = 2;
			else if (nChannels == 9)
				vecLength = 3;
			else
				throw new IllegalArgumentException("Input to Hessian calculation should have 4 of 9 channels!");
			
			if (doDeterminant) {
				matDeterminant = new Mat(height, width, opencv_core.CV_32F);
			} 
			if (doEigenvalues) {
				Mat[] array = new Mat[vecLength];
				for (int i = 0; i < vecLength; i++)
					array[i] = new Mat(height, width, opencv_core.CV_32F);
				matvecEigenvalues = new MatVector(array);
			}
			if (doEigenvectors) {
				Mat[] array = new Mat[vecLength];
				for (int i = 0; i < vecLength; i++)
					array[i] = new Mat(height, width, opencv_core.CV_32FC(vecLength));
				matvecEigenvectors = new MatVector(array);
			}
			if (doLaplacian) {
				Mat temp = new Mat();
				opencv_core.extractChannel(matDerivs, temp, 0);
				matLaplacian = temp.clone();
				for (int c = vecLength+1; c < nChannels; c += vecLength+1) {
					opencv_core.extractChannel(matDerivs, temp, c);
					opencv_core.add(matLaplacian, temp, matLaplacian);					
				}
				temp.release();
			}
			
//			try (PointerScope scope = new PointerScope()) {

				Mat matInput = new Mat(vecLength, vecLength, opencv_core.CV_32F);
				Mat matEigenvalues = doEigenvalues || doEigenvectors ? new Mat(vecLength, 1, opencv_core.CV_32F, Scalar.ZERO) : null;
				Mat matEigenvectors = doEigenvectors ? new Mat(vecLength, vecLength, opencv_core.CV_32F, Scalar.ZERO) : null;

				float[] eigenvalueData = new float[vecLength];
				float[] eigenvectorData = new float[vecLength*vecLength];

				FloatIndexer indexer = matInput.createIndexer(true);
				FloatIndexer indexerEigen = matEigenvalues == null ? null : matEigenvalues.createIndexer(true);
				FloatIndexer indexerEigenvectors = matEigenvectors == null ? null : matEigenvectors.createIndexer(true);
				
				
				FloatIndexer indexerDeterminant = matDeterminant == null ? null : matDeterminant.createIndexer();
				List<FloatIndexer> indexersEigenvalues = matvecEigenvalues == null ? null : Arrays.stream(matvecEigenvalues.get()).map(m -> (FloatIndexer)m.createIndexer()).collect(Collectors.toList());
				List<FloatIndexer> indexersEigenvectors = matvecEigenvectors == null ? null : Arrays.stream(matvecEigenvectors.get()).map(m -> (FloatIndexer)m.createIndexer()).collect(Collectors.toList());

				long[] inds = new long[3];

				float det = Float.NaN;
				for (long y = 0; y < height; y++) {
					inds[0] = y;
					for (long x = 0; x < width; x++) {
						inds[1] = x;
						
						// Populate the Hessian matrix
						for (long c = 0; c < nChannels; c++) {
							inds[2] = c;
							float v = indexerDerivs.get(inds);
							indexer.put(c, v);
						}
						
						// Calculate required values
						if (doEigenvectors) {
							opencv_core.eigen(matInput, matEigenvalues, matEigenvectors);
						} else if (doEigenvalues) {
							opencv_core.eigen(matInput, matEigenvalues);						
						}
						if (doDeterminant) {
							det = (float)opencv_core.determinant(matInput);
						}
						
						// Populate output Mats
						if (doEigenvectors) {
							indexerEigenvectors.get(0L, eigenvectorData);
							for (int c = 0; c < vecLength; c++) {
								FloatIndexer temp = indexersEigenvectors.get(c);
								for (int k = 0; k < vecLength; k++) {
									temp.put(y, x, k, eigenvectorData[c * vecLength + k]); // TODO: Check ordering!									
								}
							}
						}
						if (doEigenvalues) {
							indexerEigen.get(0L, eigenvalueData);
							for (int c = 0; c < vecLength; c++) {
								indexersEigenvalues.get(c).put(y, x, eigenvalueData[c]);
							}
						}
						if (doDeterminant) {
							indexerDeterminant.put(y, x, det);
						}
					}
				}
				if (indexersEigenvalues != null) {
					for (var idx : indexersEigenvalues)
						idx.release();
				}
				if (indexersEigenvectors != null) {
					for (var idx : indexersEigenvectors)
						idx.release();
				}
//			}
		}
		
	}
	
	public static class HessianResultsBuilder {
		
		private PixelCalibration pixelCalibration = PixelCalibration.getDefaultInstance();
		
		private double sigmaX = 1.0, sigmaY = 1.0, sigmaZ = 0.0;
		private boolean skipDeterminant = false;
		private boolean skipEigenvalues = false;
		private boolean skipEigenvectors = false;
		private boolean skipLaplacian = false;
		private boolean retainHessian = false;
		private boolean retainSmoothed = false;
		
		private boolean do3D = false;
		
		private int ind3D = -1;
		
		private int border = BORDER_DEFAULT;
		
		public HessianResultsBuilder() {}

		public HessianResultsBuilder(HessianResultsBuilder builder) {
			this.pixelCalibration = builder.pixelCalibration;
			this.sigmaX = builder.sigmaX;
			this.sigmaY = builder.sigmaY;
			this.sigmaZ = builder.sigmaZ;
			this.skipDeterminant = builder.skipDeterminant;
			this.skipEigenvalues = builder.skipEigenvalues;
			this.skipEigenvectors = builder.skipEigenvectors;
			this.skipLaplacian = builder.skipLaplacian;
			this.retainHessian = builder.retainHessian;
			this.retainSmoothed = builder.retainSmoothed;
			this.border = builder.border;
			this.ind3D = builder.ind3D;
			this.do3D = builder.do3D;
		}

		public HessianResultsBuilder retainHessian() {
			this.retainHessian = true;
			return this;
		}
		
		public HessianResultsBuilder do3D() {
			this.do3D = true;
			return this;
		}
		
		public HessianResultsBuilder skipEigenvalues() {
			this.skipEigenvalues = true;
			return this;
		}
		
		public HessianResultsBuilder skipLaplacian() {
			this.skipLaplacian = true;
			return this;
		}
		
		public HessianResultsBuilder skipEigenvectors() {
			this.skipEigenvectors = true;
			return this;
		}
		
		public HessianResultsBuilder skipDeterminant() {
			this.skipDeterminant = true;
			return this;
		}
		
		public HessianResultsBuilder retainSmoothed() {
			this.retainSmoothed = true;
			return this;
		}

		/**
		 * Set the pixel calibration.
		 * <p>
		 * If available, this will convert the units in which Gaussian sigma values are applied.
		 * This helps apply an isotropic filtering more easily, by specifying a single sigma value 
		 * and supplying the pixel calibration so that any differences in pixel dimensions is 
		 * automatically adjusted for.
		 * 
		 * @param cal
		 * @return
		 * 
		 * @see #sigma(double)
		 * @see #sigmaX(double)
		 * @see #sigmaY(double)
		 * @see #sigmaZ(double)
		 */
		public HessianResultsBuilder pixelCalibration(PixelCalibration cal) {
			this.pixelCalibration = cal;
			return this;
		}
		
		/**
		 * For 3D calculations, restrict to calculating results for only a single plane.
		 * <p>
		 * Default is to calculate results for all planes.
		 * 
		 * @param ind
		 * @return
		 */
		public HessianResultsBuilder ind3D(int ind) {
			this.ind3D = ind;
			return this;
		}
		
		/**
		 * Set all Gaussian sigma values (x, y and z) to the same value.
		 * <p>
		 * Note that this value is in pixels by default, or may be microns is supported 
		 * by setting the pixel calibration.
		 * 
		 * @param sigma
		 * @return
		 * 
		 * @see #pixelCalibration(PixelCalibration)
		 * @see #sigmaX(double)
		 * @see #sigmaY(double)
		 * @see #sigmaZ(double)
		 */
		public HessianResultsBuilder sigma(double sigma) {
			this.sigmaX = sigma;
			this.sigmaY = sigma;
			this.sigmaZ = sigma;
			return this;
		}
		
		/**
		 * Set all Gaussian sigma values for the horizontal filter.
		 * <p>
		 * Note that this value is in pixels by default, or may be microns is supported 
		 * by setting the pixel calibration.
		 * 
		 * @param sigma
		 * @return
		 * 
		 * @see #pixelCalibration(PixelCalibration)
		 * @see #sigma(double)
		 * @see #sigmaY(double)
		 * @see #sigmaZ(double)
		 */
		public HessianResultsBuilder sigmaX(double sigmaX) {
			this.sigmaX = sigmaX;
			return this;
		}

		/**
		 * Set all Gaussian sigma values for the vertical filter.
		 * <p>
		 * Note that this value is in pixels by default, or may be microns is supported 
		 * by setting the pixel calibration.
		 * 
		 * @param sigma
		 * @return
		 * 
		 * @see #pixelCalibration(PixelCalibration)
		 * @see #sigma(double)
		 * @see #sigmaX(double)
		 * @see #sigmaZ(double)
		 */
		public HessianResultsBuilder sigmaY(double sigmaY) {
			this.sigmaY = sigmaY;
			return this;
		}
			
		/**
		 * Set all Gaussian sigma values for the z-dimension filter.
		 * <p>
		 * Note that this value is in pixels by default, or may be microns is supported 
		 * by setting the pixel calibration.
		 * 
		 * @param sigma
		 * @return
		 * 
		 * @see #pixelCalibration(PixelCalibration)
		 * @see #sigma(double)
		 * @see #sigmaX(double)
		 * @see #sigmaY(double)
		 */
		public HessianResultsBuilder sigmaZ(double sigmaZ) {
			this.sigmaZ = sigmaZ;
			return this;
		}
		
		/**
		 * Calculate results for one or more Mats.
		 * @param mats
		 * @return
		 */
		public List<HessianResults> build(Mat... mats) {
			return build(Arrays.asList(mats));
		}
		
		/**
		 * Calculate results for a list of Mats.
		 * @param mats
		 * @return
		 */
		public List<HessianResults> build(List<Mat> mats) {
			if (do3D && mats.size() > 1)
				return build3D(mats);
			return build2D(mats);
		}
		
		private List<HessianResults> build2D(List<Mat> mats) {
			
			double sigmaX = this.sigmaX;
			double sigmaY = this.sigmaY;
			if (pixelCalibration.hasPixelSizeMicrons()) {
				sigmaX /= pixelCalibration.getPixelWidthMicrons();
				sigmaY /= pixelCalibration.getPixelHeightMicrons();
			}
			
			Mat kx0 = OpenCVTools.getGaussianDerivKernel(sigmaX, 0, false);
			Mat kx1 = OpenCVTools.getGaussianDerivKernel(sigmaX, 1, false);
			Mat kx2 = OpenCVTools.getGaussianDerivKernel(sigmaX, 2, false);
			
			Mat ky0 = OpenCVTools.getGaussianDerivKernel(sigmaY, 0, true);
			Mat ky1 = OpenCVTools.getGaussianDerivKernel(sigmaY, 1, true);
			Mat ky2 = OpenCVTools.getGaussianDerivKernel(sigmaY, 2, true);
			
			// Calculate image derivatives
			Mat dxx = new Mat();
			Mat dxy = new Mat();
			Mat dyy = new Mat();
			
			List<HessianResults> results = new ArrayList<>();
			for (Mat mat : mats) {
				Mat matSmooth = null;
				if (retainSmoothed) {
					matSmooth = new Mat();
					opencv_imgproc.sepFilter2D(mat, matSmooth, opencv_core.CV_32F, kx0, kx0, null, 0.0, border);
				}
				opencv_imgproc.sepFilter2D(mat, dxx, opencv_core.CV_32F, kx2, ky0, null, 0.0, border);
				opencv_imgproc.sepFilter2D(mat, dyy, opencv_core.CV_32F, kx0, ky2, null, 0.0, border);
				opencv_imgproc.sepFilter2D(mat, dxy, opencv_core.CV_32F, kx1, ky1, null, 0.0, border);
				
				Mat matDerivs = new Mat();
				opencv_core.merge(new MatVector(
						dxx, dxy,
						dxy, dyy), matDerivs);
				
				results.add(new HessianResults(this, matSmooth, matDerivs));
			}

			kx0.release();
			kx1.release();
			kx2.release();
			ky0.release();
			ky1.release();
			ky2.release();
			
			return results;
		}
		
		private List<HessianResults> build3D(List<Mat> mats) {
			if (mats.size() == 0)
				return Collections.emptyList();
			
			double sigmaX = this.sigmaX;
			double sigmaY = this.sigmaY;
			double sigmaZ = this.sigmaZ;
			if (pixelCalibration.hasPixelSizeMicrons()) {
				sigmaX /= pixelCalibration.getPixelWidthMicrons();
				sigmaY /= pixelCalibration.getPixelHeightMicrons();
			}
			if (pixelCalibration.hasZSpacingMicrons()) {
				sigmaZ /= pixelCalibration.getZSpacingMicrons();
			}
			
			// Get all the kernels we will need
			Mat kx0 = OpenCVTools.getGaussianDerivKernel(sigmaX, 0, false);
			Mat kx1 = OpenCVTools.getGaussianDerivKernel(sigmaX, 1, false);
			Mat kx2 = OpenCVTools.getGaussianDerivKernel(sigmaX, 2, false);
			
			Mat ky0 = OpenCVTools.getGaussianDerivKernel(sigmaY, 0, true);
			Mat ky1 = OpenCVTools.getGaussianDerivKernel(sigmaY, 1, true);
			Mat ky2 = OpenCVTools.getGaussianDerivKernel(sigmaY, 2, true);
			
			Mat kz0 = OpenCVTools.getGaussianDerivKernel(sigmaZ, 0, true);
			Mat kz1 = OpenCVTools.getGaussianDerivKernel(sigmaZ, 1, true);
			Mat kz2 = OpenCVTools.getGaussianDerivKernel(sigmaZ, 2, true);
			
			// Apply the awkward filtering along the z-dimension first
			List<Mat> matsZ0 = OpenCVTools.filterZ(mats, kz0, ind3D, border);
			List<Mat> matsZ1 = OpenCVTools.filterZ(mats, kz1, ind3D, border);
			List<Mat> matsZ2 = OpenCVTools.filterZ(mats, kz2, ind3D, border);
			
			// We need some Mats for each plane, but we can reuse them
			Mat dxx = new Mat();
			Mat dxy = new Mat();
			Mat dxz = new Mat();
			
			Mat dyy = new Mat();
			Mat dyz = new Mat();
			
			Mat dzz = new Mat();
			
			Mat matDerivs = new Mat();
			
			
			// Loop through and handle the remaining 2D filtering
			// We do this 1 plane at a time so that we don't need to retain all filtered images in memory
			List<HessianResults> output = new ArrayList<>();
			for (int i = 0; i < matsZ0.size(); i++) {
				
				Mat z0 = matsZ0.get(i);
				Mat z1 = matsZ1.get(i);
				Mat z2 = matsZ2.get(i);
				
				Mat matSmooth = null;
				if (retainSmoothed) {
					matSmooth = new Mat();
					opencv_imgproc.sepFilter2D(z0, matSmooth, opencv_core.CV_32F, kx0, ky0, null, 0.0, border);
				}
				
				opencv_imgproc.sepFilter2D(z0, dxx, opencv_core.CV_32F, kx2, ky0, null, 0.0, border);
				opencv_imgproc.sepFilter2D(z0, dxy, opencv_core.CV_32F, kx1, ky1, null, 0.0, border);
				opencv_imgproc.sepFilter2D(z1, dxz, opencv_core.CV_32F, kx1, ky0, null, 0.0, border);
				
				opencv_imgproc.sepFilter2D(z0, dyy, opencv_core.CV_32F, kx0, ky2, null, 0.0, border);
				opencv_imgproc.sepFilter2D(z1, dyz, opencv_core.CV_32F, kx0, ky1, null, 0.0, border);
				
				opencv_imgproc.sepFilter2D(z2, dzz, opencv_core.CV_32F, kx0, ky0, null, 0.0, border);
				
				opencv_core.merge(new MatVector(
						dxx, dxy, dxz,
						dxy, dyy, dyz,
						dxz, dyz, dzz
						), matDerivs);
				
				if (retainHessian)
					matDerivs = matDerivs.clone();
				
				output.add(new HessianResults(this, matSmooth, matDerivs));
			}
			return output;
		}
		
	}

}
