package qupath.lib.images.servers;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import qupath.lib.images.PathImage;
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
public abstract class WrappedImageServer<T> implements ImageServer<T> {
	
	private ImageServer<T> server;
	
	protected WrappedImageServer(ImageServer<T> server) {
		this.server = server;
	}
	
	/**
	 * Get underlying ImageServer, that is being wrapped.
	 * 
	 * @return
	 */
	protected ImageServer<T> getWrappedServer() {
		return server;
	}

	@Override
	public String getPath() {
		return server.getPath();
	}

	@Override
	public String getShortServerName() {
		return server.getShortServerName();
	}

	@Override
	public double[] getPreferredDownsamples() {
		return server.getPreferredDownsamples();
	}

	@Override
	public double getPreferredDownsampleFactor(double requestedDownsample) {
		return server.getPreferredDownsampleFactor(requestedDownsample);
	}

	@Override
	public int getPreferredTileWidth() {
		return server.getPreferredTileWidth();
	}

	@Override
	public int getPreferredTileHeight() {
		return server.getPreferredTileHeight();
	}

	@Override
	public double getMagnification() {
		return server.getMagnification();
	}

	@Override
	public int getWidth() {
		return server.getWidth();
	}

	@Override
	public int getHeight() {
		return server.getHeight();
	}

	@Override
	public int nChannels() {
		return server.nChannels();
	}

	@Override
	public boolean isRGB() {
		return server.isRGB();
	}

	@Override
	public int nZSlices() {
		return server.nZSlices();
	}

	@Override
	public int nTimepoints() {
		return server.nTimepoints();
	}

	@Override
	public double getTimePoint(int ind) {
		return server.getTimePoint(ind);
	}

	@Override
	public TimeUnit getTimeUnit() {
		return server.getTimeUnit();
	}

	@Override
	public double getZSpacingMicrons() {
		return server.getZSpacingMicrons();
	}

	@Override
	public double getPixelWidthMicrons() {
		return server.getPixelWidthMicrons();
	}

	@Override
	public double getPixelHeightMicrons() {
		return server.getPixelHeightMicrons();
	}

	@Override
	public double getAveragedPixelSizeMicrons() {
		return server.getAveragedPixelSizeMicrons();
	}

	@Override
	public boolean hasPixelSizeMicrons() {
		return server.hasPixelSizeMicrons();
	}

	@Override
	public T getBufferedThumbnail(int maxWidth, int maxHeight, int zPosition) {
		return server.getBufferedThumbnail(maxWidth, maxHeight, zPosition);
	}

	@Override
	public PathImage<T> readRegion(RegionRequest request) {
		return server.readRegion(request);
	}

	@Override
	public T readBufferedImage(RegionRequest request) {
		return server.readBufferedImage(request);
	}

	@Override
	public void close() {
		server.close();
	}

	@Override
	public String getServerType() {
		return server.getServerType();
	}

	@Override
	public List<String> getSubImageList() {
		return server.getSubImageList();
	}

	@Override
	public String getSubImagePath(String imageName) {
		return server.getSubImagePath(imageName);
	}

	@Override
	public List<String> getAssociatedImageList() {
		return server.getAssociatedImageList();
	}

	@Override
	public T getAssociatedImage(String name) {
		return server.getAssociatedImage(name);
	}

	@Override
	public String getDisplayedImageName() {
		return server.getDisplayedImageName();
	}

	@Override
	public boolean containsSubImages() {
		return server.containsSubImages();
	}

	@Override
	public boolean usesBaseServer(ImageServer<?> server) {
		return this.server == server || this.server.usesBaseServer(server);
	}

	@Override
	public File getFile() {
		return server.getFile();
	}

	@Override
	public boolean isEmptyRegion(RegionRequest request) {
		return server.isEmptyRegion(request);
	}

	@Override
	public int getBitsPerPixel() {
		return server.getBitsPerPixel();
	}

	@Override
	public Integer getDefaultChannelColor(int channel) {
		return server.getDefaultChannelColor(channel);
	}

	@Override
	public ImageServerMetadata getMetadata() {
		return server.getMetadata();
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		server.setMetadata(metadata);
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}

	@Override
	public boolean usesOriginalMetadata() {
		return server.usesOriginalMetadata();
	}

}
