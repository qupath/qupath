package qupath.lib.images.cache;

import ij.plugin.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.MemoryTools;
import qupath.lib.common.ThreadTools;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;

/**
 * A cache to store image tiles as {@link BufferedImage} instances.
 * @since v0.8.0
 */
public class ImageCache extends GenericImageCache<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(ImageCache.class);

    private static final ImageCache INSTANCE = create(getDefaultMaxSizeBytes());

    private ImageCache(long maxSizeBytes, int parallelism) {
        super(ImageCache::approxBufferedImageSize, maxSizeBytes, parallelism);
    }

    /**
     * Create a new image cache.
     * <p>
     * Note that creating a new cache is almost always unnecessary, and memory requirements are better
     * handled by use of {@link #getSharedInstance()}.
     *
     * @param maxSizeBytes maximize total bytes the cache should make available for pixel data.
     * @return a new image cache
     */
    public static ImageCache create(long maxSizeBytes) {
        return new ImageCache(maxSizeBytes, getDefaultParallelism());
    }

    /**
     * Create a new image cache.
     * <p>
     * Note that creating a new cache is almost always unnecessary, and memory requirements are better
     * handled by use of {@link #getSharedInstance()}.
     *
     * @param maxSizeBytes maximize total bytes the cache should make available for pixel data.
     * @param parallelism number of parallel threads to use for tile requests
     * @return a new image cache
     */
    public static ImageCache create(long maxSizeBytes, int parallelism) {
        return new ImageCache(maxSizeBytes, parallelism);
    }

    /**
     * Get a shared image cache, intended for use throughout the application.
     * This is guaranteed to be non-null, but its size can be adjusted.
     * @return a shared cache instance
     */
    public static ImageCache getSharedInstance() {
        return INSTANCE;
    }

    /**
     * Get a sensible default cache size, in bytes.
     * @return a quarter of the maximum available memory, if known, or 4 GB otherwise (this is subject to change)
     */
    private static long getDefaultMaxSizeBytes() {
        return MemoryTools.getMaxMemoryPercent(25).orElse(MemoryTools.gigabytesToBytes(4));
    }

    /**
     * Estimate the size of a BufferedImage in bytes.
     * This only looks at the pixel buffer, ignoring any extra overhead.
     * @param img the image
     * @return an estimate of the number of bytes required to store the pixel data
     */
    private static long approxBufferedImageSize(BufferedImage img) {
        if (img == null)
            return 0;
        DataBuffer data = img.getRaster().getDataBuffer();
        return (long)data.getSize() * (long)(DataBuffer.getDataTypeSize(data.getDataType())/8) * data.getNumBanks();
    }


    private static int getDefaultParallelism() {
        return Math.max(4, ThreadTools.getParallelism() * 2);
    }

}
