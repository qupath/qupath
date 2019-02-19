package qupath.lib.images.servers.sparse;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * A prototype ImageServer that combines regions from multiple separate ImageServers, 
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
	
	private Map<String, BufferedImage> emptyTileMap = new HashMap<>();
	
	private String[] channelNames;
	private Integer[] channelColors;
	
	private ColorModel colorModel;
	
	private int originX = 0, originY = 0;

	public SparseImageServer(String path) throws IOException {
		this(path, SparseImageServerManager.fromJSON(new FileReader(new File(path))));
	}
	
	public SparseImageServer(String path, SparseImageServerManager manager) throws IOException {
		super();
		
		this.manager = manager;
		
		ImageServerMetadata metadata = null;
		
		int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = -Integer.MAX_VALUE, y2 = -Integer.MAX_VALUE;
		
		for (ImageRegion region: manager.getRegions()) {
			if (region.getX() < x1)
				x1 = region.getX();
			if (region.getY() < y1)
				y1 = region.getY();
			if (region.getX() + region.getWidth() > x2)
				x2 = region.getX() + region.getWidth();
			if (region.getY() + region.getHeight() > y2)
				y2 = region.getY() + region.getHeight();
			
			// Read the first server
			if (metadata == null) {
				ImageServer<BufferedImage> server = manager.getServer(region, 1);
				channelNames = new String[server.nChannels()];
				channelColors = new Integer[server.nChannels()];
				for (int c = 0; c < server.nChannels(); c++) {
					channelNames[c] = server.getChannelName(c);
					channelColors[c] = server.getDefaultChannelColor(c);
				}
				metadata = server.getMetadata();
				colorModel = server.getBufferedThumbnail(100, 100, 0).getColorModel();
			}
		}
		// Here, we assume origin at zero
//		int width = x2;
//		int height = y2;
		
		originX = x1;
		originY = y1;
		int width = x2 - x1;
		int height = y2 - y1;
		
		this.metadata = new ImageServerMetadata.Builder(getClass(), metadata)
				.path(path)
				.width(width)
				.height(height)
				.preferredTileSize(1024, 1024)
				.levelsFromDownsamples(manager.getAvailableDownsamples())
				.build();
	}

	
	
	@Override
	public Integer getDefaultChannelColor(int channel) {
		return channelColors[channel];
	}

	@Override
	public String getChannelName(int channel) {
		return channelNames[channel];
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
				
				int x1 = Math.max(tileRequest.getImageX() + originX, subRegion.getX());
				int y1 = Math.max(tileRequest.getImageY() + originY, subRegion.getY());
				int x2 = Math.min(tileRequest.getImageX() + originX + tileRequest.getImageWidth(), subRegion.getX() + subRegion.getWidth());
				int y2 = Math.min(tileRequest.getImageY() + originY + tileRequest.getImageHeight(), subRegion.getY() + subRegion.getHeight());
				
				// Determine request coordinates
				// TODO: Test whether sparse images with pyramidal regions work
				int xr = x1 - subRegion.getX();
				int yr = y1 - subRegion.getY();
				int xr2 = x2 - subRegion.getX();
				int yr2 = y2 - subRegion.getY();
				double requestDownsample = downsample;
				if (requestDownsample > 1 && serverTemp.nResolutions() == 1) {
					xr = (int)Math.round(xr / downsample);					
					yr = (int)Math.round(yr / downsample);					
					xr2 = (int)Math.round(xr2 / downsample);					
					yr2 = (int)Math.round(yr2 / downsample);	
					requestDownsample = 1;
				}
				
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
		return new BufferedImage(colorModel, raster, false, null);
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
		private transient Map<String, ImageServer<BufferedImage>> serverMap = new HashMap<>();
		
		/**
		 * Add the path to a new ImageServer for a specified region & downsample.
		 * @param path
		 * @param region
		 * @param downsample
		 */
		public synchronized void addRegionServer(String path, ImageRegion region, double downsample) {
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
			resolutions.add(ind, new SparseImageServerManagerResolution(path, downsample));
		}
		
		/**
		 * Add a new ImageServer for a specified region & downsample.
		 * <p>
		 * This is useful for creating a sparse server in a script, relying on pre-created servers.
		 * 
		 * @param server
		 * @param region
		 * @param downsample
		 */
		public synchronized void addRegionServer(ImageServer<BufferedImage> server, ImageRegion region, double downsample) {
			 if (!serverMap.containsKey(server.getPath()))
				serverMap.put(server.getPath(), server);
			 addRegionServer(server.getPath(), region, downsample);
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
			String path = resolutions.get(level).getPath();
			ImageServer<BufferedImage> server = serverMap.get(path);
			if (server == null) {
				server = ImageServerProvider.buildServer(path, BufferedImage.class);
				serverMap.put(path, server);
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
		
		public String toJSON() {
			return toJSON(false);
		}
		
		public String toJSON(boolean prettyPrint) {
			
			List<SparseImageServerManagerRegion> regions = new ArrayList<>();
			for (Entry<ImageRegion, List<SparseImageServerManagerResolution>> entry : regionMap.entrySet()) {
				regions.add(new SparseImageServerManagerRegion(entry.getKey(), entry.getValue()));
			}
			GsonBuilder builder = new GsonBuilder();
			if (prettyPrint)
				builder.setPrettyPrinting();
			return builder.create().toJson(regions);
		}
		
		public static SparseImageServerManager fromJSON(Reader input) {
			List<SparseImageServerManagerRegion> list = new Gson().fromJson(input, new TypeToken<ArrayList<SparseImageServerManagerRegion>>() {}.getType());
			SparseImageServerManager manager = new SparseImageServerManager();
			for (SparseImageServerManagerRegion region : list) {
				for (SparseImageServerManagerResolution resolution : region.resolutions)
					manager.addRegionServer(resolution.getPath(), region.region, resolution.getDownsample());
			}
			return manager;
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
	
	
	static class SparseImageServerManagerResolution {
		
		private final double downsample;
		private final String path;

		SparseImageServerManagerResolution(String path, double downsample) {
			this.path = path;
			this.downsample = downsample;
		}
		
		String getPath() {
			return path;
		}
		
		double getDownsample() {
			return downsample;
		}
				
	}
	

}
