package qupath.lib.display;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.analysis.stats.ArrayWrappers;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to help with managing histograms for image channels.
 * This is intended for use with {@link ImageDisplay}.
 */
class HistogramManager {

    private static final Logger logger = LoggerFactory.getLogger(HistogramManager.class);

    /**
     * Default number of bins per histogram.
     */
    private static final int NUM_BINS = 1024;

    /**
     * Default target number of pixels per histogram.
     * Large images may be subsampled to avoid needing to work with (and duplicate) very large pixel arrays.
     */
    private static final long TARGET_HISTOGRAM_N_PIXELS = 10_000_000;

    private final Map<String, HistogramForRegions> map = new ConcurrentHashMap<>();


    HistogramManager() {}

    private String getKey(final ChannelDisplayInfo channel) {
        return channel.getClass().getName() + "::" + channel.getName();
    }

    /**
     * Update all channels using the specified images.
     * This can set min/max values, and also cache any associated histograms.
     * @param server the image
     * @param channels the channels to update
     * @param imgList the images to use
     */
    void updateChannels(final ImageServer<BufferedImage> server, final Collection<? extends ChannelDisplayInfo> channels,
                        final Map<RegionRequest, BufferedImage> imgList) {

        // Check what we might need to process
        List<SingleChannelDisplayInfo> channelsToProcess = new ArrayList<>();
        float serverMin = server.getMetadata().getMinValue().floatValue();
        float serverMax = server.getMetadata().getMaxValue().floatValue();

        for (ChannelDisplayInfo channel : channels) {
            var histogramForRegions = map.getOrDefault(getKey(channel), null);
            if (histogramForRegions != null && histogramForRegions.histogram() != null && histogramForRegions.sameRegions(imgList.keySet())) {
                // We have the histogram - use the min & max values to determine which values are allowed in the channel
                if (channel instanceof ChannelDisplayInfo.ModifiableChannelDisplayInfo modifiableChannel) {
                    var histogram = histogramForRegions.histogram();
                    modifiableChannel.setMinMaxAllowed(
                            (float) Math.min(0, histogram.getMinValue()), (float) histogram.getMaxValue());
                }
            } else if (channel instanceof SingleChannelDisplayInfo singleChannel) {
                // We don't have the histogram, but we can compute it later
                channelsToProcess.add(singleChannel);
                if (channel instanceof ChannelDisplayInfo.ModifiableChannelDisplayInfo modifiableChannel) {
                    modifiableChannel.setMinMaxAllowed(serverMin, serverMax);
                }
            } else {
                // A histogram doesn't exist for the channel, and we can't compute one
                map.put(getKey(channel), new HistogramForRegions(null, imgList.keySet()));
            }
        }

        if (channelsToProcess.isEmpty() || imgList.isEmpty())
            return;

        logger.debug("Building {} histograms for {}", channelsToProcess.size(), server.getPath());
        long startTime = System.currentTimeMillis();

        // Count number of pixels
        long nPixels = countPixels(imgList.values());

        // Determine stride so that we subsample to have no more than approx TARGET_HISTOGRAM_N_PIXELS values
        int stride = (int) Math.max(Math.ceil((double)nPixels / TARGET_HISTOGRAM_N_PIXELS), 1);

        for (SingleChannelDisplayInfo channel : channelsToProcess) {
            var histogram = createHistogram(channel, imgList, stride, true);
            map.put(getKey(channel), new HistogramForRegions(histogram, imgList.keySet()));
        }
        long endTime = System.currentTimeMillis();
        logger.debug("Histograms built in {} ms", (endTime - startTime));
    }


    /**
     * Create a histogram for a single channel.
     * @param channel the channel to use
     * @param images the images to use
     * @param stride the stride for subsampling
     * @param permitMinMaxUpdate if true, the min/max values of the channel can be updated based upon the values
     *                           that were extracted.
     * @return a new histogram for the channel based on the (possibly subsampled) pixels from the images
     */
    private Histogram createHistogram(SingleChannelDisplayInfo channel,
                                      Map<RegionRequest, BufferedImage> images,
                                      int stride, boolean permitMinMaxUpdate) {
        List<ArrayWrappers.ArrayWrapper> wrappers = new ArrayList<>();
        double minValue = Double.POSITIVE_INFINITY;
        double maxValue = Double.NEGATIVE_INFINITY;
        long size = 0;
        double downsample = 1;
        for (var entry : images.entrySet()) {
            downsample = Math.max(entry.getKey().getDownsample(), 1);
            var img = entry.getValue();
            var vals = channel.getValues(img, 0, 0, img.getWidth(), img.getHeight(), null);
            size += vals.length;
            if (size >= Integer.MAX_VALUE)
                break;
            if (stride == 1) {
                // Use the entire array
                wrappers.add(ArrayWrappers.makeFloatArrayWrapper(vals));
            } else {
                // Subsample the array to speed things up - and reduce memory requirements
                wrappers.add(ArrayWrappers.makeFloatArrayWrapper(subsample(vals, stride)));
            }
            // Calculate min/max from the full array
            for (var val : vals) {
                if (val < minValue)
                    minValue = val;
                if (val > maxValue)
                    maxValue = val;
            }
        }

        var histogram = new Histogram(ArrayWrappers.concatenate(wrappers), NUM_BINS, minValue, maxValue);

        // If we have more than an 8-bit image, set the display range according to actual values - with additional scaling if we downsampled
        if (permitMinMaxUpdate && channel instanceof ChannelDisplayInfo.ModifiableChannelDisplayInfo modifiableChannelDisplayInfo) {
            float scale = downsample < 2 ? 1 : 1.5f;
            if (!histogram.isInteger() || Math.max(Math.abs(channel.getMaxAllowed()), Math.abs(channel.getMinAllowed())) > 4096) {
                modifiableChannelDisplayInfo.setMinMaxAllowed(
                        (float) Math.min(0, minValue) * scale, (float) Math.max(0, maxValue) * scale);
            }
        }

        return histogram;
    }


    /**
     * Get the total number of pixels in a collection of images.
     * The number of channels is ignored (treated as 1).
     */
    private static long countPixels(Collection<? extends BufferedImage> images) {
        long nPixels = 0;
        for (var img : images) {
            nPixels += (long)img.getWidth() * img.getHeight();
        }
        return nPixels;
    }


    /**
     * Get a histogram for image regions.
     * @param server the image to use
     * @param channel the channel display that extracts pixel values
     * @param images a map of images; this should not be empty
     * @return a histogram if available, otherwise null
     */
    Histogram getHistogram(final ImageServer<BufferedImage> server, final ChannelDisplayInfo channel,
                           final Map<RegionRequest, BufferedImage> images) {
        String key = getKey(channel);
        if (channel instanceof SingleChannelDisplayInfo singleChannel) {
            // Always recompute histogram for mutable channels
            if (singleChannel.isMutable()) {
                map.remove(key);
            }
        }
        updateChannels(server, Collections.singletonList(channel), images);
        var histogramForRegions = map.getOrDefault(key, null);
        return histogramForRegions == null ? null : histogramForRegions.histogram();
    }


    /**
     * Subsample an array using a specified stride.
     * @param values the input array
     * @param stride the stride to use when extracting elements
     * @return a subsampled array, or the original array if the stride &leq; 1.
     */
    private static float[] subsample(float[] values, int stride) {
        if (stride <= 1)
            return values;
        float[] arr2 = new float[values.length / stride];
        for (int i = 0; i < arr2.length; i++) {
            arr2[i] = values[i * stride];
        }
        return arr2;
    }


    /**
     * Helper class to store a histogram and the regions used to generate it.
     * This is used to support caching histograms that were generated from specified regions.
     */
    private record HistogramForRegions(Histogram histogram, Set<RegionRequest> regions) {

        private HistogramForRegions(final Histogram histogram, final Set<RegionRequest> regions) {
            this.histogram = histogram;
            this.regions = Set.copyOf(regions);
        }

        boolean sameRegions(final Set<RegionRequest> regions) {
            return this.regions.equals(regions);
        }

    }

}
