package qupath.lib.images.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Weigher;
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.MemoryTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A generic cache to store image regions (tiles).
 * This also manages a thread pool that enables tiles to be requested asynchronously.
 *
 * @param <T> generic parameter for the image type
 * @since v0.8.0
 */
public class GenericImageCache<T> {

    private static final Logger logger = LoggerFactory.getLogger(GenericImageCache.class);

    private static final AtomicLong counter = new AtomicLong(0L);
    private static final long LOWEST_MAX_SIZE_BYTES = 1024*1024*16; // 16 MB

    private final Thread shutdownHook;
    private final String threadName;
    private final AsyncCache<RegionRequest, T> cache;

    private final Cache<String, LatencyStatistics> latencyCache;

    private boolean isClosed = false;

    private final ThreadPoolExecutor pool;

    protected GenericImageCache(final SizeEstimator<T> sizeEstimator, final long maxSizeBytes, final int parallelism) {
        threadName = getThreadNames();
        pool = (ThreadPoolExecutor)Executors.newFixedThreadPool(Math.max(4, parallelism),
                ThreadTools.createThreadFactory(threadName, true));

        // Because Caffeine uses integer weights, and we sometimes have *very* large images, we convert our size estimates KB
        Weigher<RegionRequest, T> weigher = (var _, var t) -> (int)Long.min(Integer.MAX_VALUE, bytesToKB(sizeEstimator.getApproxImageSize(t)));
        long maxWeight = bytesToKB(Long.max(LOWEST_MAX_SIZE_BYTES, maxSizeBytes));
        this.cache = Caffeine.newBuilder()
                .weigher(weigher)
                .maximumWeight(maxWeight)
                .recordStats()
                // Use evictionListener because it is notified as part of an atomic removal;
                // using a removalListener can result in exceptions during shutdown
                .evictionListener(this::handleEviction)
                .executor(pool)
                .initialCapacity(1024)
                .buildAsync();

        this.latencyCache = Caffeine.newBuilder()
                .initialCapacity(1024)
                .build();

        shutdownHook = new Thread(this::closeImpl);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private String getThreadNames() {
        return "tile-cache-[" + counter.getAndIncrement() + "]";
    }

    private void handleEviction(RegionRequest key, T value, RemovalCause cause) {
        if (cause == RemovalCause.COLLECTED) {
            logger.debug("Cached tile collected: {}", key);
        } else {
            logger.trace("Cached tile removed due to {}: {}", cause, key);
        }
    }


    // It's more intuitive to define image sizes in bytes,
    // but internally we use KB weights because some of our images are huge and we're limited to integer weights.
    // We don't permit a size of 0, since this would mean a tile can't be ejected when the cache becomes full.
    // 0 indicates an empty (or null) tile, but we don't want lots of meaningless map entries to accumulate.
    private static long bytesToKB(long bytes) {
        return Long.max(1, Math.round(bytes / 1024.0));
    }

    private static long kbToBytes(long bytes) {
        return bytes * 1024;
    }

    /**
     * Create a new image cache.
     * @param sizeEstimator an estimator that can calculate the size (in bytes) of an image
     * @param maxSizeBytes the maximum permitted size of the cache, in bytes.
     * @param parallelism the number of parallel threads to use for tile requests
     * @return a new cache
     * @param <T> generic image parameter
     */
    public static <T> GenericImageCache<T> create(SizeEstimator<T> sizeEstimator, long maxSizeBytes, int parallelism) {
        return new GenericImageCache<>(sizeEstimator, maxSizeBytes, parallelism);
    }


    /**
     * Get the tile cache size, in bytes.
     * <p>
     * When an attempt is made to cache new tiles that would result in the cache size being exceeded,
     * older tiles will be discarded.
     * @return the maximum number of bytes available to store image tiles
     * @see #setMaxSizeBytes(long)
     */
    public long getMaxSizeBytes() {
        return kbToBytes(cache.synchronous().policy().eviction().map(Policy.Eviction::getMaximum).orElse(0L));
    }

    /**
     * Set the tile cache size, in bytes.
     * <p>
     * When an attempt is made to cache new tiles that would result in the cache size being exceeded,
     * older tiles will be discarded.
     * <p>
     * <b>Note:</b> This is not a strict limit that is enforced precisely.
     * Rather, it is based upon an estimate of the size of each tile, calculated using a {@link SizeEstimator}.
     * Internally, bytes are rescaled to kilobytes to avoid overflow errors arising from the cache implementation's use
     * of integers.
     * @see #getMaxSizeBytes()
     */
    public void setMaxSizeBytes(long newMaxSizeBytes) {
        long size;
        if (newMaxSizeBytes <= LOWEST_MAX_SIZE_BYTES) {
            logger.warn("Invalid cache size {} bytes. This will be updated to {} bytes", newMaxSizeBytes, LOWEST_MAX_SIZE_BYTES);
            size = LOWEST_MAX_SIZE_BYTES;
        } else {
            size = newMaxSizeBytes;
        }
        cache.synchronous().policy().eviction().ifPresent(evictionPolicy -> evictionPolicy.setMaximum(bytesToKB(size)));
    }

    /**
     * Set the cache size as a percentage of the maximum available memory.
     * If this is outside the range 10-90%, it will be clipped to be within that range.
     * @param percentage the percentage of RAM to use for caching; this should be between 10 and 90.
     */
    public void setMaxSizeByPercent(double percentage) {
        var maxAvailable = MemoryTools.getMaxMemory();
        long maxMemory;
        if (maxAvailable.isPresent()) {
            logger.warn("No inherent maximum memory set - for caching purposes, will assume 64 GB");
            maxMemory = maxAvailable.getAsLong();
        } else {
            maxMemory = MemoryTools.gibibytesToBytes(64L);
        }
        if (percentage < 10) {
            logger.warn("At least 10% of available memory needs to be used for tile caching (you requested {}%)", percentage);
            percentage = 10;
        } else if (percentage > 90) {
            logger.warn("No more than 90% of available memory can be used for tile caching (you requested {}%)", percentage);
            percentage = 90;
        }
        long tileCacheSize = MemoryTools.getMemoryPercent(maxMemory, percentage);
        logger.info(String.format("Setting tile cache size to %.2f MB (%.1f%% max memory)", tileCacheSize/(1024.*1024.), percentage));
        setMaxSizeBytes(tileCacheSize);
    }

    /**
     * Get a (snapshot) estimate of the current cache size, in terms of bytes.
     * The cache will evict entries when necessary, in an effort to stop this exceeding {@link #getMaxSizeBytes()}.
     * @return the current cache size, or -1 if the size could not be calculated.
     * @see #getMaxSizeBytes()
     * @see #getTileCount()
     */
    public long getCurrentSizeBytes() {
        var eviction = this.cache.synchronous().policy().eviction().orElse(null);
        if (eviction == null || eviction.weightedSize().isEmpty())
            return -1L;
        return kbToBytes(eviction.weightedSize().getAsLong());
    }

    /**
     * Estimate the fill ratio, which is a value between 0 (empty cache) and 1 (full cache).
     * It is effectively {@code getCurrentSizeBytes() / getMaxSizeBytes()}.
     * @return a current fill ratio snapshot, or NaN if a fill ratio could not be calculated.
     * @implNote This is not clipped to be strictly between 0 and 1, to assist with debugging
     *         (in case the cache temporarily exceeds its requested maximum size, before tiles have been evicted).
     */
    public double getFillRatio() {
        var eviction = this.cache.synchronous().policy().eviction().orElse(null);
        if (eviction == null || eviction.weightedSize().isEmpty())
            return Double.NaN;
        return (double)eviction.weightedSize().getAsLong() / eviction.getMaximum();
    }

    /**
     * Get a tile if it is currently present in the cache, without fetching the tile otherwise.
     * @param request the request to search for
     * @return the tile, or null if it is either not found or has a value of null
     */
    public T getIfPresent(final RegionRequest request) {
        var future = cache.getIfPresent(request);
        return future == null ? null : future.getNow(null);
    }

    /**
     * Get a concurrent map view of this cache.
     * <p>
     * <b>Important!</b> This must not be modified from within any computation that is also updating the cache.
     * For example, it is not possible to request a large region from the cache, which then splits the request into
     * smaller regions that are themselves added to the cache.
     * It is generally better to avoid relying upon the map, in favor of working with the main cache methods.
     * @return a view of the cache as a concurrent map
     */
    public ConcurrentMap<RegionRequest, T> asMap() {
        return cache.synchronous().asMap();
    }

    /**
     * Get an estimate of the number of tiles currently stored within the cache.
     * Note that this can include empty tiles, if these are returned by the {@link ImageServer}.
     * It is often more useful to call {@link #getCurrentSizeBytes()} or {@link #getFillRatio()}.
     * @return an estimate of the number of tiles in the cache
     */
    public long getTileCount() {
        return cache.synchronous().estimatedSize();
    }

    /**
     * Query if the map currently contains a specific key.
     * <p>
     * This can be used to check if a {@code null} returned by {@link #getIfPresent(RegionRequest)} indicates
     * an empty tile or that the tile was not cached (under the assumption the corresponding cache entry has not
     * changed between calls to each method).
     * @param request the key to check
     * @return true if the map contains the key, false otherwise
     */
    public boolean containsKey(final RegionRequest request) {
        return cache.synchronous().asMap().containsKey(request);
    }

    /**
     * Get a map of all cached tiles pertaining to a specific ImageServer.
     * @param serverPath the value of {@link ImageServer#getPath()}
     * @return
     */
    public Map<RegionRequest, T> getCachedTilesForServer(String serverPath) {
        Map<RegionRequest, T> tiles = new HashMap<>();
        for (var entry : cache.asMap().entrySet()) {
            if (Objects.equals(serverPath, entry.getKey().getPath())) {
                var future = entry.getValue();
                if (future.isDone()) {
                    var img = future.getNow(null);
                    if (img != null) {
                        tiles.put(entry.getKey(), future.getNow(null));
                    }
                }
            }
        }
        return tiles;
    }


    /**
     * Submit an image tile request for an image server, returning a completable future that can be used to get the tile.
     *
     * @param request the request; note that {@link RegionRequest#getPath()} should match {@link ImageServer#getPath()}
     *                and uniquely distinguish the server providing these tiles.
     *                If the server should be required to return <i>different</i> tiles, then its path needs to change.
     * @param server  the server that can provide the tile
     * @return a completable future that can be queries for the tile
     */
    public CompletableFuture<T> requestImageTile(final RegionRequest request, final ImageServer<T> server) {
        return requestImageTile(request, r -> readTile(server, r));
    }

    /**
     * Submit an image tile request using an arbitrary function, returning a completable future that can be used to get the tile.
     * <p>
     * This exists to allow tiles to be cached without necessarily creating an {@link ImageServer}, for example
     * to apply (possibly expensive) transforms to an image.
     *
     * @param request the request; note that {@link RegionRequest#getPath()} must be unique for the function, similar to
     *                how it should be unique for an {@link ImageServer}.
     * @param function the function used to compute the tile
     * @return a completable future that can be queries for the tile
     */
    public CompletableFuture<T> requestImageTile(final RegionRequest request, final Function<RegionRequest, T> function) {
        if (isClosed) {
            return cache.getIfPresent(request);
        }
        return cache.get(request, r -> requestWithLatency(r, function));
    }

    /**
     * Get statistics about the time taken for each successful (non-null) tile request,
     * expressed using the mean read time per pixel.
     * @param path the path used for the region requests (usually equivalent to {@link ImageServer#getPath()})
     * @return the statistics
     */
    public LatencyStatistics getLatencyStatistics(String path) {
        return latencyCache.get(path, _ -> new LatencyStatistics());
    }

    private T requestWithLatency(final RegionRequest request, final Function<RegionRequest, T> function) {
        long startTime = System.nanoTime();
        var img = function.apply(request);
        long endTime = System.nanoTime();
        if (img != null) {
            double scale = 1.0/(request.getDownsample() * request.getDownsample());
            double nPixels = scale * request.getWidth() * request.getHeight();
            getLatencyStatistics(request.getPath()).addNanos((endTime - startTime) / nPixels);
        }
        return img;
    }


    private T readTile(ImageServer<T> server, RegionRequest request) {
        if (isClosed) {
            logger.warn("Tile cache is closed, request will be ignored");
            return null;
        }
        try {
            var img = server.readRegion(request);
            logger.debug("Read tile {} in {}", request, Thread.currentThread());
            return img;
        } catch (IOException e) {
            logger.error("Error reading image region: {} ({})", e.getMessage(), request);
            logger.debug("Error reading image region", e);
            return null; // TODO: Consider exception propagation
        }
    }


    /**
     * Clear the cache and cancel any pending requests.
     */
    public void clearCache() {
        clearCache(true);
    }


    /**
     * Clear the cache and optionally stop any pending requests.
     * @param cancelPending cancel any tasks that are currently fetching tiles
     */
    public synchronized void clearCache(final boolean cancelPending) {
        if (cancelPending) {
            var map = cache.asMap();
            for (var future : map.values()) {
                future.cancel(cancelPending);
            }
        }
        cache.synchronous().invalidateAll();
    }


    private synchronized void clearCache(Predicate<? super RegionRequest> filter) {
        var iterator = cache.asMap().entrySet().iterator();
        List<RegionRequest> toRemove = new ArrayList<>();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (filter.test(entry.getKey())) {
                toRemove.add(entry.getKey());
                entry.getValue().cancel(false);
            }
        }
        cache.synchronous().invalidateAll(toRemove);
        cache.synchronous().cleanUp();
    }

    /**
     * Remove all entries from the cache that have the specified server path in their requests.
     * @param serverPath the value of {@link RegionRequest#getPath()}.
     */
    public synchronized void clearCacheForServer(final String serverPath) {
        clearCache(r -> Objects.equals(serverPath, r.getPath()));
    }

    /**
     * Remove all entries from the cache that overlap with a specified region.
     * This makes use of {@link RegionRequest#overlapsRequest(RegionRequest)}.
     * @param request the region
     */
    public void clearCacheForRequestOverlap(final RegionRequest request) {
        clearCache(r -> r.overlapsRequest(request));
    }

    /**
     * Close the cache. This attempts an orderly shutdown of the associated thread pool,
     * clears all tiles. and cancels any pending tiles.
     */
    public void close() {
        closeImpl();
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
    }

    private void closeImpl() {
        // Try to cancel all workers
        isClosed = true;
        clearCache();
        if (!pool.isShutdown()) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("Timed out waiting for pool to shut down");
                    var futures = pool.shutdownNow();
                    if (!futures.isEmpty()) {
                        logger.warn("Number of shut down tasks in pool: {}", futures.size());
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for pool to shutdown");
            }
        }
    }

    /**
     * Put the tile in the cache, replacing any existing entry.
     * @param request the request, which is used as a key
     * @param tile the tile to put
     */
    public void put(final RegionRequest request, final T tile) {
        var map = asMap();
        if (map.containsKey(request)) {
            return;
        }
        cache.put(request, CompletableFuture.completedFuture(tile));
    }

    /**
     * Helper class to record the time taken to read pixels.
     * <p>
     * Statistics are provided as 'nanoseconds per pixel', where a 'pixel' includes all associated channels
     * (i.e. 10ns per pixel for a 5 channel image would mean that all 5 values are read in approximately 10ns).
     * <p>
     * An alternative (perhaps more intuitive, although less precise) interpretation would be
     * 'milliseconds per 1000 x 1000 pixel region'.
     * <p>
     * This interpretation is chosen to normalize results across image, independent of tile size.
     * This is also helpful if you want to benchmark the impact of tile size on latency.
     */
    public static class LatencyStatistics {

        private final SynchronizedSummaryStatistics stats = new SynchronizedSummaryStatistics();

        void addNanos(double nanosPerPixel) {
            stats.addValue(nanosPerPixel);
        }

        /**
         * Get the mean time per pixel, in nanoseconds.
         * @return the mean
         */
        public double getMean() {
            return stats.getMean();
        }

        /**
         * Get the time per pixel, in nanoseconds, calculated from the tile that took
         * the longest time to read (normalized by tile size).
         * @return the maximum
         */
        public double getMax() {
            return stats.getMax();
        }

        /**
         * Get the time per pixel, in nanoseconds, calculated from the tile that took
         * the shortest time to read (normalized by tile size)..
         * @return the minimum
         */
        public double getMin() {
            return stats.getMin();
        }

        /**
         * Get the standard deviation of read time per pixel, in nanoseconds, calculated
         * from the read times for individual tiles.
         * @return the standard deviation
         */
        public double getStandardDeviation() {
            return stats.getStandardDeviation();
        }

        /**
         * Get the total number of observations (i.e. tiles that were successfully read,
         * for which there is a latency measurement).
         */
        public void getN() {
            stats.getN();
        }

        /**
         * Reset the statistics.
         */
        public void clear() {
            stats.clear();
        }

    }


}
