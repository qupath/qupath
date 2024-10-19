package qupath.imagej.processing;

import ij.plugin.filter.RankFilters;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.function.Consumer;

public class IJFilters {

    public static ImageProcessor mean(ImageProcessor ip, double radius) {
        return rankFilter(ip, radius, RankFilters.MEAN);
    }

    public static ImageProcessor median(ImageProcessor ip, double radius) {
        return rankFilter(ip, radius, RankFilters.MEDIAN);
    }

    public static ImageProcessor maximum(ImageProcessor ip, double radius) {
        return rankFilter(ip, radius, RankFilters.MAX);
    }

    public static ImageProcessor minimum(ImageProcessor ip, double radius) {
        return rankFilter(ip, radius, RankFilters.MIN);
    }

    public static ImageProcessor dilate(ImageProcessor ip, double radius) {
        return maximum(ip, radius);
    }

    public static ImageProcessor erode(ImageProcessor ip, double radius) {
        return minimum(ip, radius);
    }

    public static ImageProcessor open(ImageProcessor ip, double radius) {
        var ip2 = ip.duplicate();
        rankFilterInPlace(ip2, radius, RankFilters.MIN);
        rankFilterInPlace(ip2, radius, RankFilters.MAX);
        return ip2;
    }

    public static ImageProcessor close(ImageProcessor ip, double radius) {
        var ip2 = ip.duplicate();
        rankFilterInPlace(ip2, radius, RankFilters.MAX);
        rankFilterInPlace(ip2, radius, RankFilters.MIN);
        return ip2;
    }

    public static ImageProcessor blackTopHat(ImageProcessor ip, double radius) {
        return IJProcessing.subtract(
                ip, open(ip, radius)
        );
    }

    public static ImageProcessor whiteTopHat(ImageProcessor ip, double radius) {
        return IJProcessing.subtract(
                close(ip, radius), ip
        );
    }

    public static ImageProcessor gaussian(ImageProcessor ip, double sigma) {
        return applyToDuplicateInPlace(ip, ip2 -> ip2.blurGaussian(sigma));
    }

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
