/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.opencv.tools;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import qupath.imagej.tools.IJTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.opencv.ops.ImageOps;

/**
 * Category class for enhanced Groovy scripting with OpenCV.
 * <p>
 * See https://docs.groovy-lang.org/latest/html/api/groovy/lang/Category.html
 * <p>
 * Warning! This is experimental and likely to change between QuPath releases.
 * Use with caution and discuss on http://forum.image.sc/tag/qupath
 * 
 * @author Pete Bankhead
 */
public class GroovyCV {
	
	/**
	 * Calculate the mean of all elements in a Mat, ignoring NaNs.
	 * @param mat
	 * @return
	 */
	public static double mean(Mat mat) {
        return OpenCVTools.mean(mat);
    }
	
	/**
	 * Calculate the sum of all elements in a Mat, ignoring NaNs.
	 * @param mat
	 * @return
	 */
	public static double sum(Mat mat) {
        return OpenCVTools.sum(mat);
    }
	
	/**
	 * Calculate the standard deviation of all elements in a Mat, ignoring NaNs.
	 * @param mat
	 * @return
	 */
	public static double std(Mat mat) {
        return OpenCVTools.stdDev(mat);
    }

	/**
	 * Find the maximum of all elements in a Mat, ignoring NaNs.
	 * @param mat
	 * @return
	 */
	public static double max(Mat mat) {
		return OpenCVTools.maximum(mat);
    }

	/**
	 * Find the minimum of all elements in a Mat, ignoring NaNs.
	 * @param mat
	 * @return
	 */
	public static double min(Mat mat) {
		return OpenCVTools.minimum(mat);
    }
	
	/**
	 * Flatten a Mat to give a single column.
	 * @param mat
	 * @return
	 */
	public static Mat flatten(Mat mat) {
		return mat.reshape(1, mat.rows()*mat.cols()*mat.channels());
    }
	
	/**
	 * Get the shape of a multidimensional Mat.
	 * @param mat
	 * @return
	 */
	public static long[] shape(Mat mat) {
		try (var indexer = mat.createIndexer()) {
			return indexer.sizes();
		}
	}
	
	/**
	 * Clip values of a Mat to be within a specified minimum and maximum.
	 * @param mat
	 * @param min
	 * @param max
	 * @return
	 */
	public static Mat clip(Mat mat, double min, double max) {
		return maximum(minimum(mat, max), min);
	}

	/**
	 * Get the per-element maximum value for two arrays.
	 * @param m1
	 * @param m2
	 * @return
	 * @implNote This uses OpenCV's max function, which seems unreliable with NaNs (the input order matters).
	 *           This behavior may change in a future version.
	 */
    public static Mat maximum(Mat m1, Mat m2) {
        return opencv_core.max(m1, m2).asMat();
    }

	/**
	 * Get the per-element maximum value between an array and a scalar.
	 * @param m1
	 * @param d
	 * @return
	 * @implNote This uses OpenCV's max function, which seems unreliable with NaNs (the input order matters).
	 *           This behavior may change in a future version.
	 */
    public static Mat maximum(Mat m1, double d) {
        return opencv_core.max(m1, d).asMat();
    }

	/**
	 * Get the per-element minimum value between an array and a scalar.
	 * @param m1
	 * @param d
	 * @return
	 * @implNote This uses OpenCV's min function, which seems unreliable with NaNs (the input order matters).
	 *           This behavior may change in a future version.
	 */
    public static Mat minimum(Mat m1, double d) {
        return opencv_core.min(m1, d).asMat();
    }

    /**
	 * Get the per-element minimum value for two arrays.
	 * @param m1
	 * @param m2
	 * @return
	 * @implNote This uses OpenCV's min function, which seems unreliable with NaNs (the input order matters).
	 *           This behavior may change in a future version.
	 */
    public static Mat minimum(Mat m1, Mat m2) {
        return opencv_core.min(m1, m2).asMat();
    }

    /**
     * Add two arrays.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat plus(Mat m1, Mat m2) {
        return opencv_core.add(m1, m2).asMat();
    }

    /**
     * Add an array and a number.
     * @param m1
     * @param s
     * @return
     */
    public static Mat plus(Mat m1, Number s) {
    	int c = OpenCVTools.typeToChannels(m1.type());
        return plus(m1, OpenCVTools.scalarMatWithType(s.doubleValue(), opencv_core.CV_64FC(c)));
    }

    /**
     * Add an array and a scalar.
     * @param m1
     * @param s
     * @return
     */
    public static Mat plus(Mat m1, Scalar s) {
        return opencv_core.add(m1, s).asMat();
    }

    /**
     * Subtract one array from another.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat minus(Mat m1, Mat m2) {
        return opencv_core.subtract(m1, m2).asMat();
    }

    /**
     * Subtract a constant from an array.
     * @param m1
     * @param s
     * @return
     */
    public static Mat minus(Mat m1, Number s) {
    	int c = OpenCVTools.typeToChannels(m1.type());
        return minus(m1, OpenCVTools.scalarMatWithType(s.doubleValue(), opencv_core.CV_64FC(c)));
    }

    /**
     * Subtract a scalar from an array.
     * @param m1
     * @param s
     * @return
     */
    public static Mat minus(Mat m1, Scalar s) {
        return opencv_core.subtract(m1, s).asMat();
    }

    /**
     * Per-element multiplication of two arrays (not matrix multiplication).
     * @param m1
     * @param m2
     * @return
     */
    public static Mat multiply(Mat m1, Mat m2) {
        return m1.mul(m2).asMat();
    }

    /**
     * Multiply array elements by a constant.
     * @param m1
     * @param s
     * @return
     */
    public static Mat multiply(Mat m1, double s) {
    	int c = m1.channels();
    	if (c == 1)
    		return opencv_core.multiply(m1, s).asMat();
//    	return m1.mul(OpenCVTools.scalarMat(s, opencv_core.CV_64F)).asMat();
    	var m2 = new Mat();
    	m1.convertTo(m2, m1.type(), s, 0);
    	return m2;
    }

    /**
     * Per-element division of two arrays.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat div(Mat m1, Mat m2) {
        return opencv_core.divide(m1, m2).asMat();
    }

    /**
     * Divide array elements by a constant.
     * @param m1
     * @param s
     * @return
     */
    public static Mat div(Mat m1, double s) {
    	return multiply(m1, 1.0/s);
    }

    /**
     * Compute the absolute value of all elements in an array.
     * @param mat
     * @return
     */
    public static Mat abs(Mat mat) {
    	if (mat.channels() <= 4)
    		return opencv_core.abs(mat).asMat();
    	var m2 = mat.clone();
    	OpenCVTools.applyToChannels(m2, m -> m.put(opencv_core.abs(m)));
    	return m2;
    }

    /**
     * Multiply elements of an array by -1.
     * @param m1
     * @return
     */
    public static Mat negative(Mat m1) {
        return multiply(m1, -1.0);
    }

    /**
     * Compute the bitwise OR of two arrays.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat or(Mat m1, Mat m2) {
        var dest = new Mat();
        opencv_core.bitwise_or(m1, m2, dest);
        return dest;
    }

    /**
     * Compute the bitwise AND of two arrays.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat and(Mat m1, Mat m2) {
        var dest = new Mat();
        opencv_core.bitwise_and(m1, m2, dest);
        return dest;
    }

    /**
     * Compute the bitwise XOR of two arrays.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat xor(Mat m1, Mat m2) {
        var dest = new Mat();
        opencv_core.bitwise_xor(m1, m2, dest);
        return dest;
    }

    /**
     * Compute the bitwise NOT of an array.
     * @param m1
     * @return
     */
    public static Mat bitwiseNegate(Mat m1) {
        var dest = new Mat();
        opencv_core.bitwise_not(m1, dest);
        return dest;
    }

    /**
     * Raise elements of an array to a specified power.
     * @param m1
     * @param power
     * @return
     */
    public static Mat power(Mat m1, double power) {
        if (!Double.isFinite(power)) {
        	throw new IllegalArgumentException("power does not support non-finite values!");
        }
//        var dest = new Mat();
        return ImageOps.Core.power(power).apply(m1.clone());
//        opencv_core.pow(m1, power, dest);
//        return dest;
    }

    /**
     * Split channels of an array.
     * @param mat
     * @return
     */
    public static List<Mat> splitChannels(Mat mat) {
        return OpenCVTools.splitChannels(mat);
    }

    /**
     * Apply a greater than threshold. The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param threshold
     * @return
     */
    public static Mat gt(Mat m1, double threshold) {
        return opencv_core.greaterThan(m1, threshold).asMat();
    }

    /**
     * Apply a greater than threshold between two arrays. The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat gt(Mat m1, Mat m2) {
    	return opencv_core.greaterThan(m1, m2).asMat();
    }
    
    /**
     * Create a binary image showing where an array has a specific value. The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param value
     * @return
     */
    public static Mat eq(Mat m1, double value) {
        return opencv_core.equals(m1, value).asMat();
    }

    /**
     * Create a binary image showing where two arrays have matching values.
     * The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat eq(Mat m1, Mat m2) {
    	return opencv_core.equals(m1, m2).asMat();
    }
    
    /**
     * Apply a greater than or equal to threshold. The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param threshold
     * @return
     */
    public static Mat geq(Mat m1, double threshold) {
        return opencv_core.greaterThanEquals(m1, threshold).asMat();
    }

    /**
     * Apply a greater than or equal to threshold between two arrays. The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat geq(Mat m1, Mat m2) {
    	return opencv_core.greaterThanEquals(m1, m2).asMat();
    }

    /**
     * Apply a less than threshold. The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param threshold
     * @return
     */
    public static Mat lt(Mat m1, double threshold) {
    	return opencv_core.lessThan(m1, threshold).asMat();
    }

    /**
     * Apply a less than or equal to threshold between two arrays. The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat leq(Mat m1, Mat m2) {
    	return opencv_core.lessThanEquals(m1, m2).asMat();
    }
    
    /**
     * Apply a less than or equal to threshold. The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param threshold
     * @return
     */
    public static Mat leq(Mat m1, double threshold) {
    	return opencv_core.lessThanEquals(m1, threshold).asMat();
    }

    /**
     * Apply a less than threshold between two arrays. The output is an 8-bit unsigned array with values 0 and 255.
     * @param m1
     * @param m2
     * @return
     */
    public static Mat lt(Mat m1, Mat m2) {
    	return opencv_core.lessThan(m1, m2).asMat();
    }

    /**
     * Helper method to convert a {@link Mat} to a {@link BufferedImage}, {@link ImagePlus}, {@link ImageProcessor}, 
     * double or float array using Groovy's 'as' syntax.
     * @param <T>
     * @param mat
     * @param cls
     * @return
     */
    public static <T> T asType(Mat mat, Class<T> cls) {
    	if (Mat.class.isAssignableFrom(cls))
        	return (T)mat;
        if (BufferedImage.class.isAssignableFrom(cls))
            return (T)OpenCVTools.matToBufferedImage(mat);
        if (ImageProcessor.class.isAssignableFrom(cls)) {
            if (mat.channels() == 3 && mat.depth() == opencv_core.CV_8U)
                return (T)(new ColorProcessor(OpenCVTools.matToBufferedImage(mat)));
            return (T)OpenCVTools.matToImageProcessor(mat);
        }
        if (ImagePlus.class.isAssignableFrom(cls)) {
            var imp = OpenCVTools.matToImagePlus(mat, null);
            if (CompositeImage.class.isAssignableFrom(cls))
                return (T)ensureComposite(imp);
            return (T)imp;
        }
        if (double[].class.isAssignableFrom(cls))
        	return (T)OpenCVTools.extractDoubles(mat);
        if (float[].class.isAssignableFrom(cls))
        	return (T)OpenCVTools.extractFloats(mat);
        return null; //mat.metaClass.asType(cls);
    }

    private static CompositeImage ensureComposite(ImagePlus imp) {
        return imp instanceof CompositeImage ? (CompositeImage)imp : new CompositeImage(imp);
    }

    /**
     * Helper method to convert an {@link ImageProcessor} to a {@link Mat} using Groovy's 'as' syntax.
     * @param <T>
     * @param ip
     * @param cls
     * @return
     */
    public static <T> T asType(ImageProcessor ip, Class<T> cls) {
        Mat mat = null;
        if (Mat.class.isAssignableFrom(cls)) {
            if (ip instanceof ByteProcessor) {
                mat = new Mat(ip.getHeight(), ip.getWidth(), opencv_core.CV_8UC1);
                ByteBuffer buf = mat.createBuffer();
                buf.put((byte[])ip.getPixels());
            }
            else if (ip instanceof ShortProcessor) {
                mat = new Mat(ip.getHeight(), ip.getWidth(), opencv_core.CV_16UC1);
                ShortBuffer buf = mat.createBuffer();
                buf.put((short[])ip.getPixels());
            }
            else if (ip instanceof FloatProcessor) {
                mat = new Mat(ip.getHeight(), ip.getWidth(), opencv_core.CV_32FC1);
                FloatBuffer buf = mat.createBuffer();
                buf.put((float[])ip.getPixels());
            }
            else if (ip instanceof ColorProcessor) {
                var cp = (ColorProcessor)ip;
                var r = new byte[cp.getWidth() * cp.getHeight()];
                var g = new byte[cp.getWidth() * cp.getHeight()];
                var b = new byte[cp.getWidth() * cp.getHeight()];
                cp.getRGB(r, g, b);
                mat = new Mat(ip.getHeight(), ip.getWidth(), opencv_core.CV_8UC3);
                ByteBuffer buf = mat.createBuffer();
                for (int i = 0; i < r.length; i++) {
                    buf.put(r[i]);
                    buf.put(g[i]);
                    buf.put(b[i]);
                }
            } else
            	throw new IllegalArgumentException("Unknown ImageProcessor class " + ip.getClass());
            return (T)mat;
        }
        if (Roi.class.isAssignableFrom(cls)) {
            if (ip.getMinThreshold() == ImageProcessor.NO_THRESHOLD) {
                ip.setBinaryThreshold();
            }
            return (T)new ThresholdToSelection().convert(ip);
        }
        return null;
        // TODO: This doesn't work! Ideally we'd like to call the base implementation... somehow
//        return ip.metaClass.asType(cls)
    }

    /**
     * Helper method to convert an {@link ImagePlus} to a {@link Mat} or {@link ImageProcessor}
     * using Groovy's 'as' syntax.
     * @param <T>
     * @param imp
     * @param cls
     * @return
     */
    public static <T> T asType(ImagePlus imp, Class<T> cls) {
        if (ImageProcessor.class.isAssignableFrom(cls))
            return (T)imp.getProcessor();
        if (Roi.class.isAssignableFrom(cls))
            return (T)asType(imp.getProcessor(), Roi.class);
        if (Mat.class.isAssignableFrom(cls))
            // Note: this doesn't handle multichannel!
            return (T)asType(imp.getProcessor(), Mat.class);
        return null;
    }

    /**
     * Helper method to convert a {@link BufferedImage} to a {@link Mat}, {@link ImageProcessor} or {@link ImagePlus} 
     * using Groovy's 'as' syntax.
     * @param <T>
     * @param img
     * @param cls
     * @return
     */
    public static <T> T asType(BufferedImage img, Class<T> cls) {
        if (Mat.class.isAssignableFrom(cls))
            return (T) OpenCVTools.imageToMat(img);
        if (ImageProcessor.class.isAssignableFrom(cls)) {
            if (BufferedImageTools.is8bitColorType(img.getType()))
                return (T) new ColorProcessor(img);
            return (T) IJTools.convertToImageProcessor(img, 0);
        }
        if (ImagePlus.class.isAssignableFrom(cls)) {
            var imp = IJTools.convertToUncalibratedImagePlus("", img);
            if (CompositeImage.class.isAssignableFrom(cls))
                return (T) ensureComposite(imp);
            return (T) imp;
        }
        return null;
//        return img.metaClass.asType(cls)
    }

    /**
     * Helper function to convert a {@link Number} to a {@link Mat} or {@link Scalar} using Groovy's 'as' syntax.
     * @param <T>
     * @param n
     * @param cls
     * @return
     */
    public static <T> T asType(Number n, Class<T> cls) {
        if (Scalar.class.isAssignableFrom(cls))
            return (T)Scalar.all(n.doubleValue());
        if (Mat.class.isAssignableFrom(cls))
            return (T)OpenCVTools.scalarMat(n.doubleValue(), opencv_core.CV_64F);
        return null;
//        return n.metaClass.asType(cls)
    }

}
