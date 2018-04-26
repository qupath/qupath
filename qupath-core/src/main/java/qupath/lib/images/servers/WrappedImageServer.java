package qupath.lib.images.servers;

import qupath.lib.regions.RegionRequest;

/**
 * An ImageServer that simply wraps around an existing ImageServer.
 * 
 * Its purpose is to help implement other ImageServers that apply pixel or spatial 
 * transforms to an alternative ImageServer.
 * 
 * Subclasses may only implement the methods necessary to apply the required transform.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class WrappedImageServer<T> extends AbstractImageServer<T> {
	
	private ImageServer<T> server;
	
	protected WrappedImageServer(ImageServer<T> server) {
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
	public T readBufferedImage(RegionRequest request) {
		return server.readBufferedImage(request);
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}

}
