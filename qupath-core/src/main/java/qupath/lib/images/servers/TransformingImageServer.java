package qupath.lib.images.servers;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import qupath.lib.regions.RegionRequest;

/**
 * An ImageServer implementation used to apply transforms to another ImageServer.
 * This might be a spatial or pixel intensity transformation, for example.
 * <p>
 * Subclasses may only implement the methods necessary to apply the required transform, 
 * such as {@link #readBufferedImage(RegionRequest)} since much of the remaining functionality 
 * is left up to the {@link AbstractImageServer} implementation.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class TransformingImageServer<T> extends AbstractImageServer<T> {
	
	private ImageServer<T> server;
	
	protected TransformingImageServer(ImageServer<T> server) {
		super(server.getImageClass());
		this.server = server;
	}
	
	/**
	 * Get underlying ImageServer, i.e. the one that is being wrapped.
	 * 
	 * @return
	 */
	protected ImageServer<T> getWrappedServer() {
		return server;
	}
	
	@Override
	public Collection<URI> getURIs() {
		return getWrappedServer().getURIs();
	}

	@Override
	public T readBufferedImage(RegionRequest request) throws IOException {
		return server.readBufferedImage(request);
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}

}
