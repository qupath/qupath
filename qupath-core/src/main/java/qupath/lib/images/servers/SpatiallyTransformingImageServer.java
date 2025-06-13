package qupath.lib.images.servers;

import qupath.lib.regions.RegionRequest;

/**
 * An ImageServer implementation used to apply spatial transforms to another ImageServer.
 * <p>
 * Subclasses may only implement the methods necessary to apply the required transform,
 * such as {@link #readRegion(RegionRequest)} since much of the remaining functionality
 * is left up to the {@link AbstractImageServer} and {@link TransformingImageServer}
 * implementation.
 *
 * @author Carlo Castoldi
 *
 * @param <T>
 */
public abstract class SpatiallyTransformingImageServer<T> extends TransformingImageServer<T> {
    protected SpatiallyTransformingImageServer(ImageServer server) {
        super(server);
    }

    protected abstract ImageServerMetadata updateMetadata(ImageServerMetadata embeddedMetadata);

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        ImageServerMetadata embeddedMetadata =  new ImageServerMetadata.Builder(getWrappedServer().getOriginalMetadata())
                .spatiallyTransformed(true)
                .build();
        return updateMetadata(embeddedMetadata);
    }
}
