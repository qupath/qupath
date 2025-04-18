/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
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

package qupath.imagej.processing;

import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.DoublePredicate;

/**
 * Helper class for working with ImageJ.
 * <p>
 * This focuses especially on common processing operations involving {@link ImageProcessor}, including the creation
 * of labeled images and Rois.
 *
 * @since v0.6.0
 */
public class IJProcessing {

    /**
     * Constant for 4-connectivity when tracing Rois.
     */
    public static int CONNECTIVITY_4 = 4;

    /**
     * Constant for 8-connectivity when tracing Rois.
     */
    public static int CONNECTIVITY_8 = 8;

    /**
     * Apply an auto-threshold to an image, and generate a Roi from the result.
     * <p>
     * The threshold of the image will be set temporarily and then removed afterwards.
     *
     * @param ip the input image
     * @param method a method string, as accepted by {@link ImageProcessor#setAutoThreshold(String)}
     * @return the threshold Roi, or null if no thresholded pixels were found
     */
    public static Roi autoThresholdToRoi(ImageProcessor ip, String method) {
        ip.setAutoThreshold(method);
        var roi = thresholdToRoi(ip);
        ip.resetThreshold();
        return roi;
    }

    /**
     * Apply an auto-threshold to an image, and generate a zero or more Rois from the result based on connected
     * components.
     * <p>
     * The threshold of the image will be set temporarily and then removed afterwards.
     *
     * @param ip the input image
     * @param method a method string, as accepted by {@link ImageProcessor#setAutoThreshold(String)}
     * @param connectivity either 4 or 8
     * @return a list containing a Roi for each connected component
     */
    public static List<Roi> autoThresholdToSplitRois(ImageProcessor ip, String method, int connectivity) {
        ip.setAutoThreshold(method);
        var rois = thresholdToSplitRois(ip, connectivity);
        ip.resetThreshold();
        return rois;
    }

    /**
     * Create an ImageJ ROI from a thresholded image.
     * <p>
     * This makes use of {@link ThresholdToSelection}, and returns null if no Roi is found.
     * @param ip the image, with min and/or max thresholds already set.
     * @return a Roi generated by applying the threshold, or null if there are no thresholded pixels
     */
    public static Roi thresholdToRoi(ImageProcessor ip) {
        if (!ip.isThreshold())
            return null;

        // Need to check we have any above-threshold pixels at all
        int n = ip.getWidth() * ip.getHeight();
        boolean noPixels = true;
        double min = ip.getMinThreshold();
        double max = ip.getMaxThreshold();
        if (min <= max) {
            for (int i = 0; i < n; i++) {
                double val = ip.getf(i);
                if (val >= min && val <= max) {
                    noPixels = false;
                    break;
                }
            }
        }
        if (noPixels)
            return null;

        return new ThresholdToSelection().convert(ip);
    }

    /**
     * Create a list of ImageJ ROIs by tracing connected components in a thresholded image.
     * <p>
     * This makes use of {@link ThresholdToSelection}, and returns null if no Roi is found.
     * @param ip the image, with min and/or max thresholds already set
     * @param connectivity may be 4 or 8
     * @return a Roi generated by applying the threshold, or null if there are no thresholded pixels
     */
    public static List<Roi> thresholdToSplitRois(ImageProcessor ip, int connectivity) {
        var ipLabels = labelImage(ip, connectivity);
        return labelsToRois(ipLabels);
    }


    /**
     * Create a labeled image using the thresholds set on an {@link ImageProcessor} (inclusive).
     * @param ip the input image
     * @param connectivity may be 4 or 8
     * @return labelled image, as a ShortProcessor (if possible) or FloatProcessor (if necessary)
     */
    public static ImageProcessor labelImage(ImageProcessor ip, int connectivity) {
        var predicate = createThresholdPredicate(ip);
        return labelImage(ip, connectivity, predicate);
    }

    /**
     * Create a predicate based on the thresholds set on an image.
     * @param ip
     * @return
     */
    private static DoublePredicate createThresholdPredicate(ImageProcessor ip) {
        if (!ip.isThreshold()) {
            return v -> false;
        }
        double minThreshold = ip.getMinThreshold();
        double maxThreshold = ip.getMaxThreshold();
        return v -> v >= minThreshold && v <= maxThreshold;
    }

    /**
     * Create a labeled image using the thresholds set on an {@link ImageProcessor}.
     * @param ip the input image
     * @param connectivity may be 4 or 8
     * @param predicate the predicate to determine if a pixel should be labeled based on its value
     * @return labelled image, as a ShortProcessor (if possible) or FloatProcessor (if necessary)
     * @since v0.6.0
     */
    public static ImageProcessor labelImage(ImageProcessor ip, int connectivity, DoublePredicate predicate) {
        int w = ip.getWidth();
        int h = ip.getHeight();
        short shortMax = (short)65535;
        ImageProcessor ipLabels = new ShortProcessor(w, h);
        short[] pxShort = (short[])ipLabels.getPixels();
        for (int i = 0; i < w*h; i++) {
            double val = ip.getf(i);
            if (predicate.test(val))
                pxShort[i] = shortMax;
        }
        // Loop through and flood fill
        FloodFiller ff = new FloodFiller(ipLabels);
        double label = 0;
        double maxSupported = 65535;
        for (int i = 0; i < pxShort.length; i++) {
            if (pxShort[i] == shortMax) {
                label++;
                // We would overflow the max int value for a ShortProcessor - convert now to 32-bit
                if (label == maxSupported) {
                    ipLabels = ipLabels.convertToFloatProcessor();
                    ff = new FloodFiller(ipLabels);
                    maxSupported = -1;
                }
                ipLabels.setValue(label);
                if (connectivity == CONNECTIVITY_8)
                    ff.fill8(i % w, i / w);
                else if (connectivity == CONNECTIVITY_4)
                    ff.fill(i % w, i / w);
                else
                    throw new IllegalArgumentException("Connectivity must be 4 or 8, but found " + connectivity);
            }
        }
        ipLabels.setMinAndMax(0, label);
        return ipLabels;
    }


    /**
     * Create ImageJ Rois from labels in an image.
     * <p>
     * Note that this is intended to handle disconnected Rois and Rois that contain holes, such that the Roi
     * contains all pixels with the given label and no other pixels.
     *
     * @param ipLabels the labeled image; generally this should be a ByteProcessor or ShortProcessor
     * @return an array of length n; output[i] is the ROI for label i+1, or null if no Roi is found
     *         with that label.
     */
    public static Roi[] labelsToRoisArray(ImageProcessor ipLabels) {
        int n = (int)Math.ceil(ipLabels.getStatistics().max);
        return labelsToRoisImpl(ipLabels, n);
    }

    /**
     * Create ImageJ Rois from labels in an image.
     * <p>
     * This is similar to {@link #labelsToRoisArray(ImageProcessor)} but omits any null Rois.
     * Consequently, when labels can be missing it is not possible to relate the index from the list to the
     * original label.
     *
     * @param ipLabels the labeled image; generally this should be a ByteProcessor or ShortProcessor
     * @return a list of Rois that were found
     */
    public static List<Roi> labelsToRois(ImageProcessor ipLabels) {
        var rois = labelsToRoisArray(ipLabels);
        var list = new ArrayList<Roi>(rois.length);
        for (var r : rois) {
            if (r != null)
                list.add(r);
        }
        return list;
    }

    private static Roi[] labelsToRoisImpl(ImageProcessor ipLabels, int n) {
        Roi[] rois = new Roi[n];
        int w = ipLabels.getWidth();
        int h = ipLabels.getHeight();
        ByteProcessor bpCompleted = new ByteProcessor(w, h);
        bpCompleted.setValue(255);
        ThresholdToSelection tts = new ThresholdToSelection();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (bpCompleted.get(x, y) != 0)
                    continue;
                float val = ipLabels.getf(x, y);
                if (val > 0 && val <= n) {
                    Wand wand = new Wand(ipLabels);
                    ipLabels.resetThreshold();
                    wand.autoOutline(x, y, val, val, Wand.EIGHT_CONNECTED);
                    Roi roi = wandToRoi(wand);

                    // Check if ROI contains holes, and create if necessary
                    ipLabels.setRoi(roi);
                    ImageStatistics stats = ipLabels.getStatistics();
                    if (stats.max != stats.min || rois[(int)val-1] != null) {
                        ipLabels.setThreshold(val-0.25, val+0.25, ImageProcessor.NO_LUT_UPDATE);
                        roi = tts.convert(ipLabels);
                    }

                    rois[(int)val-1] = roi;
                    bpCompleted.fill(roi);
                }
            }
        }
        return rois;
    }

    /**
     * Convert a traced outline from the ImageJ Wand into a PolygonRoi.
     * @param wand
     * @return
     */
    public static PolygonRoi wandToRoi(Wand wand) {
        // The Wand can have far too many points (1000, when fewer are needed) - so used trimmed arrays where this is the case
        int n = wand.npoints;
        var x = Arrays.copyOf(wand.xpoints, n);
        var y = Arrays.copyOf(wand.ypoints, n);
        return new PolygonRoi(x, y, n, Roi.TRACED_ROI);
    }

    /**
     * Create an overlay containing all the Rois in the provided collection.
     * @param rois
     * @return
     */
    public static Overlay createOverlay(Collection<? extends Roi> rois) {
        var overlay = new Overlay();
        for (var r : rois) {
            overlay.add(r);
        }
        return overlay;
    }

    /**
     * Pixelwise subtraction of one or more images from the first image passed as a parameter.
     * The input images are unchanged.
     * @param ip the first image
     * @param ipOthers additional images
     * @return a new image representing the result of the subtraction.
     */
    public static <T extends ImageProcessor> T subtract(T ip, ImageProcessor... ipOthers) {
        return blitter(Blitter.SUBTRACT, ip, ipOthers);
    }

    /**
     * Pixelwise sum of input images.
     * The input images are unchanged.
     * @param ip the first image
     * @param ipOthers additional images
     * @return a new image representing the result of the addition.
     */
    public static <T extends ImageProcessor> T add(T ip, ImageProcessor... ipOthers) {
        return blitter(Blitter.ADD, ip, ipOthers);
    }

    /**
     * Pixelwise multiplication of the input images.
     * The input images are unchanged.
     * @param ip the first image
     * @param ipOthers additional images
     * @return a new image representing the result of the multiplication.
     */
    public static <T extends ImageProcessor> T multiply(T ip, ImageProcessor... ipOthers) {
        return blitter(Blitter.MULTIPLY, ip, ipOthers);
    }

    /**
     * Pixelwise division of the input images.
     * The input images are unchanged.
     * @param ip the first image
     * @param ipOthers additional images
     * @return a new image representing the result of the division.
     */
    public static <T extends ImageProcessor> T divide(T ip, ImageProcessor... ipOthers) {
        return blitter(Blitter.DIVIDE, ip, ipOthers);
    }

    /**
     * Pixelwise maximum of the input images.
     * The input images are unchanged.
     * @param ip the first image
     * @param ipOthers additional images
     * @return a new image representing the result of the max operation.
     */
    public static <T extends ImageProcessor> T max(T ip, ImageProcessor... ipOthers) {
        return blitter(Blitter.MAX, ip, ipOthers);
    }

    /**
     * Pixelwise minimum of the input images.
     * The input images are unchanged.
     * @param ip the first image
     * @param ipOthers additional images
     * @return a new image representing the result of the min operation.
     */
    public static <T extends ImageProcessor> T min(T ip, ImageProcessor... ipOthers) {
        return blitter(Blitter.MIN, ip, ipOthers);
    }

    private static <T extends ImageProcessor> T blitter(int operation, T ip, ImageProcessor... ipOthers) {
        T ipResult = (T)ip.duplicate();
        for (var ip2 : ipOthers) {
            ipResult.copyBits(ip2, 0, 0, operation);
        }
        return ipResult;
    }


}
