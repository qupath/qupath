package qupath.lib.images.servers;

import java.util.Collection;
import java.util.LinkedHashSet;

import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * A wrapper for a RegionRequest, useful to precisely specify image tiles at a particular resolution.
 * <p>
 * Why?
 * <p>
 * Because downsamples can be defined with floating point precision, and are not always unambiguous when 
 * calculated as the ratios of pyramid level dimensions (i.e. different apparent horizontal & vertical scaling), 
 * a RegionRequest is too fuzzy a way to refer to a specific rectangle of pixels from a specific pyramid level. 
 * Rounding errors can easily occur, and different image readers might respond differently to the same request.
 * <p>
 * Consequently, TileRequests provide a means to reproducibly define coordinates at pyramid levels and not only 
 * the full resolution image space.  They wrap a RegionRequest, because this is still used for caching purposes. 
 */
public class TileRequest {
			
	private final int level;
	private final ImageRegion tileRegion;
	private transient RegionRequest request;

	/**
	 * Request a collection of <i>all</i> tiles that this server must be capable of returning. 
	 * The default implementation provides a dense grid of all expected tiles across all resolutions, 
	 * time points, z-slices and x,y coordinates.
	 * 
	 * @return
	 */
	static Collection<TileRequest> getAllTileRequests(ImageServer<?> server) {
		var set = new LinkedHashSet<TileRequest>();
		
		var tileWidth = server.getMetadata().getPreferredTileWidth();
		var tileHeight = server.getMetadata().getPreferredTileHeight();
		var downsamples = server.getPreferredDownsamples();
		
		for (int level = 0; level < downsamples.length; level++) {
			var resolutionLevel = server.getMetadata().getLevel(level);
			int height = resolutionLevel.getHeight();
			int width = resolutionLevel.getWidth();
			for (int t = 0; t < server.nTimepoints(); t++) {
				for (int z = 0; z < server.nZSlices(); z++) {
					for (int y = 0; y < height; y += tileHeight) {
						int th = tileHeight;
						if (y + th > height)
							th = height - y;
						
						for (int x = 0; x < width; x += tileWidth) {
							int tw = tileWidth;
							if (x + tw > width)
								tw = width - x;
							
							var tile = TileRequest.createInstance(server, level, 
									ImageRegion.createInstance(
											x, y, tw, th, z, t)
									);
							set.add(tile);
						}
					}					
				}
			}
		}
		return set;
	}
	
	
	public static TileRequest createInstance(ImageServer<?> server, int level, ImageRegion tileRegion) {
		double downsample = server.getDownsampleForResolution(level);
		return new TileRequest(
				getRegionRequest(server.getPath(), downsample, tileRegion),
				level,
				tileRegion
				);
	}

	static RegionRequest getRegionRequest(String path, double downsample, ImageRegion tileRegion) {
		double x1 = tileRegion.getX() * downsample;
		double y1 = tileRegion.getY() * downsample;
		double x2 = (tileRegion.getX() + tileRegion.getWidth()) * downsample;
		double y2 = (tileRegion.getY() + tileRegion.getHeight()) * downsample;
		return RegionRequest.createInstance(path, downsample,
				(int)Math.round(x1),
				(int)Math.round(y1),
				(int)Math.round(x2 - Math.round(x1)),
				(int)Math.round(y2 - Math.round(y1)),
				tileRegion.getZ(),
				tileRegion.getT());
	}
	
	public RegionRequest getRegionRequest() {
		return request;
	}
	
	private TileRequest(final RegionRequest request, final int level, final ImageRegion tileRegion) {
		this.request = request;
		this.level = level;
		this.tileRegion = tileRegion;
	}
	
	public double getDownsample() {
		return request.getDownsample();
	}
	
	public int getLevel() {
		return level;
	}
	
	public int getImageX() {
		return request.getX();
	}
	
	public int getImageY() {
		return request.getY();
	}
	
	public int getImageWidth() {
		return request.getWidth();
	}
	
	public int getImageHeight() {
		return request.getHeight();
	}
	
	public int getTileX() {
		return tileRegion.getX();
	}
	
	public int getTileY() {
		return tileRegion.getY();
	}
	
	public int getTileWidth() {
		return tileRegion.getWidth();
	}
	
	public int getTileHeight() {
		return tileRegion.getHeight();
	}
	
	public int getZ() {
		return tileRegion.getZ();
	}
	
	public int getT() {
		return tileRegion.getT();
	}
	
	public ImagePlane getPlane() {
		return ImagePlane.getPlane(getZ(), getT());
	}
	
	@Override
	public String toString() {
		return String.format("Tile: level=%d, bounds=(%d, %d, %d, %d), %s", level,
				tileRegion.getX(), tileRegion.getY(), tileRegion.getWidth(), tileRegion.getHeight(), request.toString());
	}
	
}