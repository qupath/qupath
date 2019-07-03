package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * An ImageServer that combines regions from multiple separate ImageServers, 
 * repositioning these as necessary to generate one larger field of view.
 * <p>
 * Regions are currently defined within a JSON file passed as the 'path' argument, 
 * and this also contains the full paths to the other ImageServers.
 * 
 * @author Pete Bankhead
 *
 */
public class SparseImageServer extends AbstractTileableImageServer {
	
	private final static Logger logger = LoggerFactory.getLogger(SparseImageServer.class);
	
	private final ImageServerMetadata metadata;
	
	private SparseImageServerManager manager;
	
	private transient Map<String, BufferedImage> emptyTileMap = new HashMap<>();
	
	private transient ColorModel colorModel;
	
	private int originX = 0, originY = 0;
	
	SparseImageServer(List<SparseImageServerManagerRegion> regions, String path) throws IOException {
		this(createManager(regions), path);
	}
	
	/**
	 * Create a new SparseImageServer.
	 * @param manager manager defining the regions to include
	 * @param path path to use as an identifier for the server
	 * @throws IOException
	 */
	private SparseImageServer(SparseImageServerManager manager, String path) throws IOException {
		super();
		
		this.manager = manager;
		
		ImageServerMetadata metadata = null;
		
		int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = -Integer.MAX_VALUE, y2 = -Integer.MAX_VALUE;
		
		List<String> paths = new ArrayList<>();
		
		for (ImageRegion region: manager.getRegions()) {
			if (region.getX() < x1)
				x1 = region.getX();
			if (region.getY() < y1)
				y1 = region.getY();
			if (region.getX() + region.getWidth() > x2)
				x2 = region.getX() + region.getWidth();
			if (region.getY() + region.getHeight() > y2)
				y2 = region.getY() + region.getHeight();

			ImageServer<BufferedImage> server = null;
			
			// Read the first server if we need it
			if (metadata == null || path == null) {
				server = manager.getServer(region, 1);
				if (metadata == null) {
					metadata = server.getMetadata();
					colorModel = server.getDefaultThumbnail(0, 0).getColorModel();
				}
				if (path == null)
					paths.add(region.toString() + " (" + server.getPath() + ")");
			}
		}
		if (path == null)
			path = String.join(", ", paths);
		// Here, we assume origin at zero
//		int width = x2;
//		int height = y2;
		
		originX = x1;
		originY = y1;
		int width = x2 - x1;
		int height = y2 - y1;
		
		this.metadata = new ImageServerMetadata.Builder(getClass(), metadata)
				.id(path)
//				.id(UUID.randomUUID().toString())
				.name("Sparse image (" + manager.getRegions().size() + " regions)")
				.width(width)
				.height(height)
				.preferredTileSize(1024, 1024)
				.levelsFromDownsamples(manager.getAvailableDownsamples())
				.build();
		
	}
	
	/**
	 * Get the manager, which defines from whence the regions originate.
	 * @return
	 */
	public SparseImageServerManager getManager() {
		return manager;
	}

	@Override
	public String getServerType() {
		return "Sparse image server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	@Override
	public ServerBuilder<BufferedImage> getBuilder() {
		List<SparseImageServerManagerRegion> resolutions = new ArrayList<>();
		for (var entry : manager.regionMap.entrySet())
			resolutions.add(new SparseImageServerManagerRegion(entry.getKey(), entry.getValue()));
		return new ImageServers.SparseImageServerBuilder(getMetadata(), resolutions, getPath());
	}
	
	@Override
	public void close() throws Exception {
		manager.close();
		super.close();
	}
	

	@Override
	protected BufferedImage readTile(final TileRequest tileRequest) throws IOException {
		
		WritableRaster raster = null;
		
		for (ImageRegion subRegion : manager.getRegions()) {
			double downsample = tileRequest.getRegionRequest().getDownsample();
			if (subRegion.intersects(tileRequest.getImageX() + originX, tileRequest.getImageY() + originY, tileRequest.getImageWidth(), tileRequest.getImageHeight())) {
				// If we overlap, request the overlapping portion
				ImageServer<BufferedImage> serverTemp = manager.getServer(subRegion, downsample);
				
				// Get image coordinates for bounding box of valid region
				int x1 = Math.max(tileRequest.getImageX() + originX, subRegion.getX());
				int y1 = Math.max(tileRequest.getImageY() + originY, subRegion.getY());
				int x2 = Math.min(tileRequest.getImageX() + originX + tileRequest.getImageWidth(), subRegion.getX() + subRegion.getWidth());
				int y2 = Math.min(tileRequest.getImageY() + originY + tileRequest.getImageHeight(), subRegion.getY() + subRegion.getHeight());
				
				// Determine request coordinates
				// TODO: Test whether sparse images with pyramidal regions work, or images stored as single planes at pre-specified downsamples
				int xr = x1 - subRegion.getX();
				int yr = y1 - subRegion.getY();
				int xr2 = x2 - subRegion.getX();
				int yr2 = y2 - subRegion.getY();
				double requestDownsample = downsample;
//				if (requestDownsample > 1 && serverTemp.nResolutions() == 1) {
//					requestDownsample = serverTemp.getDownsampleForResolution(0);
//					double scale = requestDownsample / downsample;
//					xr = (int)Math.round(xr * scale);					
//					yr = (int)Math.round(yr * scale);					
//					xr2 = (int)Math.round(xr2 * scale);					
//					yr2 = (int)Math.round(yr2 * scale);	
//					System.err.println(downsample + ", " + scale + ": " + serverTemp.getPath());
//				}
				
				RegionRequest requestTemp = RegionRequest.createInstance(
						serverTemp.getPath(), requestDownsample,
						xr, yr, xr2-xr, yr2-yr, tileRequest.getZ(), tileRequest.getT());
				
				BufferedImage imgTemp = null;
				synchronized (serverTemp) {
					imgTemp = serverTemp.readBufferedImage(requestTemp);					
				}
				
				if (imgTemp == null)
					continue;
				
				// If we don't have an output image yet, create a compatible one
				if (raster == null) {
					raster = imgTemp.getRaster().createCompatibleWritableRaster(tileRequest.getTileWidth(), tileRequest.getTileHeight());					
				}
				
				int x = (int)Math.round((x1 - tileRequest.getImageX() - originX) / downsample);
				int y = (int)Math.round((y1 - tileRequest.getImageY() - originY) / downsample);
				int w = Math.min(imgTemp.getWidth(), raster.getWidth()-x);
				int h = Math.min(imgTemp.getHeight(), raster.getHeight()-y);
				raster.setDataElements(x, y, w, h, imgTemp.getRaster().getDataElements(0, 0, w, h, null));
			}
		}
		
		// To avoid problems with returning nulls, create an empty compatible raster where needed - 
		// reusing an existing raster where possible to reduce memory requirements.
		if (raster == null) {
			String key = tileRequest.getTileWidth() + "x" + tileRequest.getTileHeight();
			BufferedImage imgEmpty = emptyTileMap.get(key);
			if (imgEmpty == null) {
				logger.trace("Creating new reusable empty tile for {}", tileRequest.getRegionRequest());
				raster = colorModel.createCompatibleWritableRaster(tileRequest.getTileWidth(), tileRequest.getTileHeight());
				imgEmpty = new BufferedImage(colorModel, raster, false, null);
				emptyTileMap.put(key, imgEmpty);
			} else {
				logger.trace("Returning reusable empty tile for {}", tileRequest.getRegionRequest());
			}
			return imgEmpty;
		}
//		System.err.println(String.format("%.2f - %.2f", (double)tileRequest.getImageHeight()/raster.getHeight(), tileRequest.getDownsample()));
		return new BufferedImage(colorModel, raster, false, null);
	}
	
	
	/**
	 * Builder to create a new {@link SparseImageServer}.
	 */
	public static class Builder {
		
		private SparseImageServerManager manager = new SparseImageServerManager();
		
		/**
		 * Add a region based on a JSON representation of an ImageServer.
		 * @param region the region within this image where the pixels requested from the server should be positioned
		 * @param downsample the downsample value for the represented region
		 * @param builder the ServerBuilder representation of the server to include
		 * @return
		 * 
		 * @see ImageServers
		 */
		public synchronized Builder jsonRegion(ImageRegion region, double downsample, ServerBuilder<BufferedImage> builder) {
			manager.addRegionServer(region, downsample, builder);
			return this;
		}
		
		/**
		 * Add a region based on an existing ImageServer.
		 * @param region the region within this image where the pixels requested from the server should be positioned
		 * @param downsample the downsample value for the represented region
		 * @param server the server to include, supplying pixels for the region
		 * @return
		 */
		public synchronized Builder serverRegion(ImageRegion region, double downsample, ImageServer<BufferedImage> server) {
			manager.addRegionServer(region, downsample, server);
			return this;
		}
		
		/**
		 * Build a new SparseImageServer.
		 * @return
		 * @throws IOException
		 */
		public SparseImageServer build() throws IOException {
			return new SparseImageServer(manager, null);
		}
		
	}
	
	
	List<SparseImageServerManagerRegion> getRegions() {
		List<SparseImageServerManagerRegion> regions = new ArrayList<>();
		for (Entry<ImageRegion, List<SparseImageServerManagerResolution>> entry : manager.regionMap.entrySet()) {
			regions.add(new SparseImageServerManagerRegion(entry.getKey(), entry.getValue()));
		}
		return regions;
	}

	static SparseImageServerManager createManager(List<SparseImageServerManagerRegion> regions) {
		SparseImageServerManager manager = new SparseImageServerManager();
		for (SparseImageServerManagerRegion region : regions) {
			for (SparseImageServerManagerResolution resolution : region.resolutions)
				manager.addRegionServer(region.region, resolution.getDownsample(), resolution.getServerBuilder());
		} 
		return manager;
	}
	
		
	/**
	 * Helper class for SparseImageServers, capable of returning the appropriate ImageServer for 
	 * different ImageRegions and different resolutions.
	 * <p>
	 * This also allows serialization/deserialization with JSON.
	 */
	public static class SparseImageServerManager implements AutoCloseable {
		
		private Map<ImageRegion, List<SparseImageServerManagerResolution>> regionMap = new LinkedHashMap<>();
		private Set<Double> downsamples = new TreeSet<>();
		
		private transient List<ImageRegion> regionList;
		private transient Map<ServerBuilder<BufferedImage>, ImageServer<BufferedImage>> serverMap = new HashMap<>();
		
		/**
		 * Add the path to a new ImageServer for a specified region & downsample.
		 * 
		 * @param region
		 * @param downsample
		 * @param json a JSON String representing the server
		 */
		private synchronized void addRegionServer(ImageRegion region, double downsample, ServerBuilder<BufferedImage> serverBuilder) {
			resetCaches();
			
			List<SparseImageServerManagerResolution> resolutions = regionMap.get(region);
			if (resolutions == null) {
				resolutions = new ArrayList<>();
				regionMap.put(region, resolutions);
			}
			downsamples.add(downsample);
			int ind = 0;
			while (ind < resolutions.size() && downsample > resolutions.get(ind).getDownsample()) {
				ind++;
			}
			resolutions.add(ind, new SparseImageServerManagerResolution(serverBuilder, downsample));
		}
		
		/**
		 * Add a new ImageServer for a specified region & downsample.
		 * <p>
		 * This is useful for creating a sparse server in a script, relying on pre-created servers.
		 * 
		 * @param region
		 * @param downsample
		 * @param server
		 */
		private synchronized void addRegionServer(ImageRegion region, double downsample, ImageServer<BufferedImage> server) {
			ServerBuilder<BufferedImage> builder = server.getBuilder();
			 if (!serverMap.containsKey(builder))
				serverMap.put(builder, server);
			 addRegionServer(region, downsample, builder);
		 }
		
		private void resetCaches() {
			regionList = null;
		}
		

		/**
		 * Get an unmodifiable collection for all available regions.
		 * <p>
		 * This can be used to iterate through regions to check which overlap a request.
		 * @return
		 */
		public synchronized Collection<ImageRegion> getRegions() {
			if (regionList == null) {
				regionList = new ArrayList<>(regionMap.keySet());
				regionList = Collections.unmodifiableList(regionList);
			}
			return regionList;
		}
		
		/**
		 * Request the server for a specific downsample.
		 * <p>
		 * Note that this does not aim to return a server for any arbitrary region; rather, 
		 * a server <i>must</i> exist for the specified region and downsample, otherwise this will return {@code null}. 
		 * 
		 * @param region specified region to which the server should correspond (must be found within {@code getRegions()})
		 * @param downsample specified downsample for the server (must be found within {@code getDownsamples()})
		 * @return
		 * @throws IOException 
		 */
		public synchronized ImageServer<BufferedImage> getServer(ImageRegion region, double downsample) throws IOException {
			// Get the best resolution map for the specified region & return null if none found
			List<SparseImageServerManagerResolution> resolutions = regionMap.get(region);
			if (resolutions == null || resolutions.isEmpty())
				return null;
			int level = resolutions.size()-1;
			while (level > 0 && resolutions.get(level).getDownsample() > downsample) {
				level--;
			}
			
			// Create a new ImageServer if we need to, or reuse an existing one
			// Note: the same server might be reused for multiple regions/resolutions if they have the same path
			ServerBuilder<BufferedImage> builder = resolutions.get(level).getServerBuilder();
			ImageServer<BufferedImage> server = serverMap.get(builder);
			if (server == null) {
				try {
					server = builder.build();
				} catch (IOException e) {
					throw e;
				} catch (Exception e) {
					throw new IOException(e);
				}
				serverMap.put(builder, server);
			}
			return server;
		}

		@Override
		public void close() throws Exception {
			for (ImageServer<BufferedImage> server : serverMap.values())
				server.close();
		}
		
		double[] getAvailableDownsamples() {
			return downsamples.stream().mapToDouble(d -> d).toArray();
		}
		
	}
	
	static class SparseImageServerManagerRegion {
		
		private ImageRegion region;
		private List<SparseImageServerManagerResolution> resolutions;
		
		SparseImageServerManagerRegion(ImageRegion region, List<SparseImageServerManagerResolution> resolutions) {
			this.region = region;
			this.resolutions = resolutions;
		}
		
	}
	
	
	private static class SparseImageServerManagerResolution {
		
		private final double downsample;
		private final ServerBuilder<BufferedImage> serverBuilder;

		SparseImageServerManagerResolution(ServerBuilder<BufferedImage> serverBuilder, double downsample) {
			this.serverBuilder = serverBuilder;
			this.downsample = downsample;
		}
		
		ServerBuilder<BufferedImage> getServerBuilder() {
			return serverBuilder;
		}
		
		double getDownsample() {
			return downsample;
		}
				
	}
	
}