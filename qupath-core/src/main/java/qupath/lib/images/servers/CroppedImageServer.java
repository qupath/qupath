package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.io.IOException;

import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer that treats a particular sub-region of another ImageServer as a full image, 
 * i.e. it performs dynamic cropping, without a need to export the cropped region separately.
 * 
 * @author Pete Bankhead
 *
 */
public class CroppedImageServer extends WrappedImageServer<BufferedImage> {
	
	private ImageServerMetadata metadata;
	
	private ImageRegion region;

	protected CroppedImageServer(final ImageServer<BufferedImage> server, ImageRegion region) {
		super(server);
		this.region = region;
		
		metadata = new ImageServerMetadata.Builder(
				server.getPath() + ": Cropped " + region.toString(), region.getWidth(), region.getHeight())
				.setBitDepth(server.getMetadata().getBitDepth())
				.setRGB(server.getMetadata().isRGB())
				.setMagnification(server.getMetadata().getMagnification())
				.setName(String.format("%s (%d, %d, %d, %d)", server.getMetadata().getName(), region.getX(), region.getY(), region.getWidth(), region.getHeight()))
				.setPixelSizeMicrons(server.getMetadata().getPixelWidthMicrons(), server.getMetadata().getPixelHeightMicrons())
				.setPreferredTileSize(server.getMetadata().getPreferredTileWidth(), server.getMetadata().getPreferredTileHeight())
				.channels(server.getMetadata().getChannels())
				.setSizeZ(server.getMetadata().getSizeZ())
				.setSizeT(server.getMetadata().getSizeT())
				.setTimeUnit(server.getMetadata().getTimeUnit())
				.setZSpacingMicrons(server.getMetadata().getZSpacingMicrons())
				.setPreferredDownsamples(server.getMetadata().getPreferredDownsamples())
				.build();
	}
	
	
	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
		RegionRequest request2 = RegionRequest.createInstance(
				request.getPath(), request.getDownsample(),
				request.getX() + region.getX(),
				request.getY() + region.getY(),
				request.getWidth(), request.getHeight());
		BufferedImage img = getWrappedServer().readBufferedImage(request2);
		// TODO: Mask as ellipse, if necessary?
		return img;
	}
	
	
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	
	@Override
	public String getServerType() {
		return "Cropped image server";
	}

}
