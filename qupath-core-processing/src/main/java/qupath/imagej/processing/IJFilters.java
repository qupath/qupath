package qupath.imagej.processing;

import ij.plugin.filter.RankFilters;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.function.Consumer;

/**
 * Helper class for filtering ImageJ images.
 * <p>
 * Many of these methods call built-in ImageJ filters, but adding them as static methods
 * in a single class here may make them easier to find and use... and there are some extras
 * that aren't part of ImageJ.
 * <p>
 * <b>Important notes:</b>
 * <ul>
 *     <li>In general, the input image is unchanged and a new output image is created.</li>
 *     <li>These methods do not pay attention to any Roi that has been set on the image!</li>
 * </ul>
 * This lack of Roi-attention may result in images being cropped when duplicates are created internally.
 * <i>It is therefore strongly recommended to reset the Roi for any images provided as input.</i>
 *
 * @since v0.6.0
 */
public class IJFilters {

    /**
     * Apply a mean (average) filter.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     */
    public static ImageProcessor mean(ImageProcessor ip, double radius) {
        return rankFilter(ip, radius, RankFilters.MEAN);
    }

    /**
     * Apply a median filter.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     */
    public static ImageProcessor median(ImageProcessor ip, double radius) {
        return rankFilter(ip, radius, RankFilters.MEDIAN);
    }

    /**
     * Apply a maximum filter.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     */
    public static ImageProcessor maximum(ImageProcessor ip, double radius) {
        return rankFilter(ip, radius, RankFilters.MAX);
    }

    /**
     * Apply a minimum filter.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     */
    public static ImageProcessor minimum(ImageProcessor ip, double radius) {
        return rankFilter(ip, radius, RankFilters.MIN);
    }

    /**
     * Apply a dilation; this is equivalent to applying a maximum filter.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     */
    public static ImageProcessor dilate(ImageProcessor ip, double radius) {
        return maximum(ip, radius);
    }

    /**
     * Apply an erosion; this is equivalent to applying a minimum filter.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     */
    public static ImageProcessor erode(ImageProcessor ip, double radius) {
        return minimum(ip, radius);
    }

    /**
     * Apply a morphological opening; this is equivalent to applying a minimum followed by a
     * maximum filter.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     */
    public static ImageProcessor open(ImageProcessor ip, double radius) {
        var ip2 = ip.duplicate();
        rankFilterInPlace(ip2, radius, RankFilters.MIN);
        rankFilterInPlace(ip2, radius, RankFilters.MAX);
        return ip2;
    }

    /**
     * Apply a morphological closing; this is equivalent to applying a maximum followed by a
     * minimum filter.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     */
    public static ImageProcessor close(ImageProcessor ip, double radius) {
        var ip2 = ip.duplicate();
        rankFilterInPlace(ip2, radius, RankFilters.MAX);
        rankFilterInPlace(ip2, radius, RankFilters.MIN);
        return ip2;
    }

    /**
     * Apply a black tophat filter; this is equivalent to subtracting an 'opened' image from
     * the original.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     * @see #open(ImageProcessor, double)
     */
    public static ImageProcessor blackTopHat(ImageProcessor ip, double radius) {
        return IJProcessing.subtract(
                ip, open(ip, radius)
        );
    }

    /**
     * Apply a white tophat filter; this is equivalent to subtracting the original image from
     * the a 'closed' image.
     * @param ip the input image
     * @param radius the filter radius
     * @return the filtered image
     * @see #close(ImageProcessor, double)
     */
    public static ImageProcessor whiteTopHat(ImageProcessor ip, double radius) {
        return IJProcessing.subtract(
                close(ip, radius), ip
        );
    }

    /**
     * Apply an opening by (morphological) reconstruction.
     * @param ip the input image
     * @param radius the radius of the initial opening filter
     * @return the filtered image
     */
    public static ImageProcessor openingByReconstruction(ImageProcessor ip, double radius) {
        return MorphologicalReconstruction.openingByReconstruction(ip, radius);
    }

    /**
     * Apply a closing by (morphological) reconstruction.
     * @param ip the input image
     * @param radius the radius of the initial closing filter
     * @return the filtered image
     */
    public static ImageProcessor closingByReconstruction(ImageProcessor ip, double radius) {
        return MorphologicalReconstruction.closingByReconstruction(ip, radius);
    }

    /**
     * Find regional maxima in an image.
     * <p>
     * <b>Note:</b> Use with caution! This method is experimental and may change.
     *
     * @param ip the input image
     * @return a binary image with 255 at the location of regional maxima and 0 elsewhere
     */
    public static ByteProcessor regionalMaxima(ImageProcessor ip) {
        var ipMask = ip.convertToFloatProcessor();
        var ipMarker = ipMask.duplicate();
        ipMask.add(1.0);
        MorphologicalReconstruction.morphologicalReconstruction(ipMarker, ipMask);
        return SimpleThresholding.greaterThan(ipMask, ipMarker);
    }

    /**
     * Find regional minima in an image.
     * <p>
     * <b>Note:</b> Use with caution! This method is experimental and may change.
     *
     * @param ip the input image
     * @return a binary image with 255 at the location of regional minima and 0 elsewhere
     */
    public static ByteProcessor regionalMinima(ImageProcessor ip) {
        var ip2 = ip.duplicate();
        ip2.invert();
        return regionalMaxima(ip2);
    }

    /**
     * Suppress small local maxima in an image using a H-maxima transform.
     * <p>
     * <b>Note:</b> Use with caution! This method is experimental and may change.
     * @param ip the input image
     * @param h the height of maxima to suppress
     * @return the input with maxima suppressed
     */
    public static FloatProcessor hMaxima(ImageProcessor ip, double h) {
        var fpMarker = ip.convertToFloatProcessor();
        var fpMask = fpMarker.duplicate();
        fpMarker.subtract(h);
        MorphologicalReconstruction.morphologicalReconstruction(fpMarker, fpMask);
        return fpMarker;
    }

    /**
     * Suppress small local minima in an image using a H-minima transform.
     * <p>
     * <b>Note:</b> Use with caution! This method is experimental and may change.
     * @param ip the input image
     * @param h the height of minima to suppress
     * @return the input with minima suppressed
     */
    public static FloatProcessor hMinima(ImageProcessor ip, double h) {
        var fpMarker = ip.convertToFloatProcessor();
        fpMarker.multiply(-1.0);
        var fpMask = fpMarker.duplicate();
        fpMarker.subtract(h);
        MorphologicalReconstruction.morphologicalReconstruction(fpMarker, fpMask);
        fpMarker.multiply(-1.0);
        return fpMarker;
    }

    /**
     * Find regional maxima in an image above a defined height.
     * <p>
     * <b>Note:</b> Use with caution! This method is experimental and may change.
     * @param ip the input image
     * @param h the height of the maxima
     * @return a binary image with 255 at the location of regional maxima and 0 elsewhere
     */
    public static ByteProcessor extendedMaxima(ImageProcessor ip, double h) {
        var hmax = hMaxima(ip, h);
        return regionalMaxima(hmax);
    }

    /**
     * Find regional minima in an image above a defined height.
     * <p>
     * <b>Note:</b> Use with caution! This method is experimental and may change.
     * @param ip the input image
     * @param h the height of the minima
     * @return a binary image with 255 at the location of regional minima and 0 elsewhere
     */
    public static ByteProcessor extendedMinima(ImageProcessor ip, double h) {
        var ipDuplicate = ip.duplicate();
        ipDuplicate.invert();
        return extendedMaxima(ipDuplicate, h);
    }

    /**
     * Apply a Gaussian filter to an input image.
     * @param ip the input image
     * @param sigma the sigma value of the Gaussian filter
     * @return the filtered image
     */
    public static ImageProcessor gaussian(ImageProcessor ip, double sigma) {
        return applyToDuplicateInPlace(ip, ip2 -> ip2.blurGaussian(sigma));
    }

    /**
     * Apply a Difference of Gaussians filter to an input image.
     * @param ip the input image
     * @param sigma1 the sigma value of the first Gaussian filter
     * @param sigma2 the sigma value of the second Gaussian filter (to be subtracted)
     * @return the filtered image
     */
    public static FloatProcessor differenceOfGaussians(ImageProcessor ip, double sigma1, double sigma2) {
        var fp1 = ip.convertToFloatProcessor();
        var fp2 = fp1.duplicate();
        fp1.blurGaussian(sigma1);
        fp2.blurGaussian(sigma2);
        return IJProcessing.subtract(fp1, fp2);
    }

    private static ImageProcessor rankFilter(ImageProcessor ip, double radius, int type) {
        return applyToDuplicateInPlace(ip, ip2 -> rankFilterInPlace(ip2, radius, type));
    }

    private static ImageProcessor applyToDuplicateInPlace(ImageProcessor ip, Consumer<ImageProcessor> consumer) {
        var ipDuplicate = ip.duplicate();
        consumer.accept(ipDuplicate);
        return ipDuplicate;
    }

    private static void rankFilterInPlace(ImageProcessor ip, double radius, int type) {
        new RankFilters().rank(ip, radius, type);
    }


}
