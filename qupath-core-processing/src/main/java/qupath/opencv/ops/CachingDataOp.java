package qupath.opencv.ops;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.RegionRequest;

/**
 * {@link ImageDataOp} implementation that wraps another op so that it can internally
 * cache the result of the first op.
 * <p>
 * A use for this is in pixel classification, where expensive feature calculations for
 * one data op might be used as input for a different (postprocessing) {@link ImageOp}.
 * Wrapping the feature calculations here and then appending the classification step
 * could give a performance improvement.
 */
class CachingDataOp implements ImageDataOp {

    private static final Logger logger = LoggerFactory.getLogger(CachingDataOp.class);

    /**
     * Default cache size, in bytes. Currently corresponds to 250 MB.
     */
    private static final long DEFAULT_MAX_BYTES = 250L * 1024L * 1024L;

    private transient Cache<RegionRequest, Mat> cache;

    private final ImageDataOp dataOp;
    private final ImageOp postprocessing;

    private final long maxCacheSizeBytes;

    CachingDataOp(ImageDataOp dataOp) {
        this(dataOp, null, -1);
    }

    CachingDataOp(ImageDataOp dataOp, ImageOp postprocessing, long maxCacheSizeBytes) {
        this(dataOp, postprocessing, maxCacheSizeBytes, null);
    }

    private CachingDataOp(ImageDataOp dataOp, ImageOp postprocessing, long maxCacheSizeBytes, Cache<RegionRequest, Mat> cache) {
        this.dataOp = dataOp;
        this.postprocessing = postprocessing;
        this.maxCacheSizeBytes = maxCacheSizeBytes;
        this.cache = cache;
    }

    @Override
    public Mat apply(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
        try {
            var requestInternal = request.updatePath(imageData.getServerPath());
            if (cache == null)
                buildCache();
            Mat mat = cache.getIfPresent(requestInternal);
            if (mat == null || mat.isNull()) {
                if (mat != null && mat.isNull()) {
                    logger.debug("Null Mat for {}", requestInternal);
                }
                cache.invalidate(requestInternal);
                // Note that we *could* only cache slow requests
                long startTime = System.nanoTime();
                mat = cache.get(requestInternal, () -> applyDataOp(imageData, requestInternal));
                long endTime = System.nanoTime();
                logger.trace("Request time: {} ms", (endTime - startTime) / 1000_000);
            }
            // MUST clone as Mat likely to be modified in-place!
            mat = mat.clone();
            if (postprocessing != null)
                mat.put(postprocessing.apply(mat));
            return mat;
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }

    private Mat applyDataOp(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
        // We need to retain the reference to have a meaningful cache in case the Mat
        // is being created within a PointerScope, and so will be deallocated at the end of the scope
        logger.debug("Applying data op for {}", request);
        return dataOp.apply(imageData, request).retainReference();
    }

    @Override
    public boolean supportsImage(ImageData<BufferedImage> imageData) {
        return dataOp.supportsImage(imageData);
    }

    @Override
    public List<ImageChannel> getChannels(ImageData<BufferedImage> imageData) {
        if (postprocessing == null)
            return dataOp.getChannels(imageData);
        else
            return postprocessing.getChannels(dataOp.getChannels(imageData));
    }

    @Override
    public ImageDataOp appendOps(ImageOp... ops) {
        if (ops.length == 0)
            return this;
        List<ImageOp> sequential = new ArrayList<>();
        if (postprocessing != null)
            sequential.add(postprocessing);
        for (var op : ops) {
            sequential.add(op);
        }
        // Reuse the same cache, since only the post-processing has changed
        return new CachingDataOp(
                dataOp,
                ImageOps.Core.sequential(sequential),
                maxCacheSizeBytes,
                cache
        );
    }

    @Override
    public PixelType getOutputType(PixelType inputType) {
        if (postprocessing == null)
            return dataOp.getOutputType(inputType);
        else
            return postprocessing.getOutputType(dataOp.getOutputType(inputType));
    }

    @Override
    public Collection<URI> getURIs() throws IOException {
        if (postprocessing == null || postprocessing.getURIs().isEmpty())
            return dataOp.getURIs();
        var uris = new ArrayList<>(dataOp.getUris());
        uris.addAll(postprocessing.getURIs());
        return uris;
    }

    @Override
    public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
        boolean changes = dataOp.updateURIs(replacements);
        if (postprocessing != null)
            changes = changes | postprocessing.updateURIs(replacements);
        return changes;
    }


    private synchronized void buildCache() {
        if (this.cache != null)
            return;
        var sizeBytes = maxCacheSizeBytes;
        if (sizeBytes <= 0L) {
            sizeBytes = Math.max(DEFAULT_MAX_BYTES,
                    Runtime.getRuntime().maxMemory() / 5);
        }
        this.cache = CacheBuilder.newBuilder()
                .softValues()
                .weigher(CachingDataOp::weigh)
                .maximumWeight(sizeBytes) // Defined in bytes
                .concurrencyLevel(1) // More deterministic and interpretable control of weight
                .removalListener(CachingDataOp::handleRemoval)
                .build();
    }

    private static int weigh(RegionRequest key, Mat value) {
        long size = value.total() * value.elemSize();
        if (size > 0 && size < Integer.MAX_VALUE) {
            return (int) size;
        } else {
            logger.warn("Can't determine size for {}", value);
            return 1;
        }
    }

    private static void handleRemoval(RemovalNotification<RegionRequest, Mat> notification) {
        var value = notification.getValue();
        logger.debug("Removing {} ({})", value, notification.getKey());
        if (value != null)
            value.close();
    }

}
