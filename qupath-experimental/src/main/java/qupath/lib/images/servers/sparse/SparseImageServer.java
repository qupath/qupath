package qupath.lib.images.servers.sparse;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
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
	
	private final ImageServerMetadata metadata;
	
	private Map<String, ImageRegion> regionMap = new LinkedHashMap<>();
	private Map<String, ImageServer<BufferedImage>> serverMap = new HashMap<>();
	
	private String[] channelNames;
	private Integer[] channelColors;
	
	private int originX = 0, originY = 0;

	protected SparseImageServer(Map<RegionRequest, BufferedImage> cache, String path) throws IOException {
		super(cache);
				
//		cache.clear();
		
		String text = GeneralTools.readFileAsString(path);
		regionMap = new Gson().fromJson(text, new TypeToken<Map<String, ImageRegion>>() {}.getType());
		
		ImageServerMetadata metadata = null;
		
		int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = -Integer.MAX_VALUE, y2 = -Integer.MAX_VALUE;
		
		for (Entry<String, ImageRegion> entry : regionMap.entrySet()) {
			ImageRegion region = entry.getValue();
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
				String firstPath = entry.getKey();
				ImageServer<BufferedImage> server = ImageServerProvider.buildServer(firstPath, BufferedImage.class);
				
				channelNames = new String[server.nChannels()];
				channelColors = new Integer[server.nChannels()];
				for (int c = 0; c < server.nChannels(); c++) {
					channelNames[c] = server.getChannelName(c);
					channelColors[c] = server.getDefaultChannelColor(c);
				}
				
				serverMap.put(firstPath, server);
				metadata = server.getMetadata();
			}
		}
		// Here, we assume origin at zero
//		int width = x2;
//		int height = y2;		
		
		originX = x1;
		originY = y1;
		int width = x2 - x1;
		int height = y2 - y1;
		
		// Request the servers we will need, in parallel
		regionMap.keySet().parallelStream().forEach(p -> getRegionServer(p));
				
		this.metadata = new ImageServerMetadata.Builder(path, width, height)
				.setBitDepth(metadata.getBitDepth())
				.setRGB(metadata.isRGB())
				.setMagnification(metadata.getMagnification())
				.setPixelSizeMicrons(metadata.getPixelWidthMicrons(), metadata.getPixelHeightMicrons())
				.setPreferredTileSize(1024, 1024)
				.setSizeC(metadata.getSizeC())
				.setSizeT(metadata.getSizeT())
				.setSizeZ(metadata.getSizeZ())
				.setTimeUnit(metadata.getTimeUnit())
				.setPreferredDownsamples(1, 4, 32, 64, 128)
				.setZSpacingMicrons(metadata.getZSpacingMicrons())
				.build();
		
	}
	
	private ImageServer<BufferedImage> getRegionServer(final String serverPath) {
		ImageServer<BufferedImage> server = serverMap.get(serverPath);
		if (server == null) {
			server = ImageServerProvider.buildServer(serverPath, BufferedImage.class);
			serverMap.put(serverPath, server);
		}
		return server;
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
	public void close() {
		for (ImageServer<BufferedImage> server : serverMap.values())
			server.close();
		super.close();
	}
	

	@Override
	protected BufferedImage readTile(final TileRequest tileRequest) throws IOException {
		
		BufferedImage imgOutput = null;
		WritableRaster raster = null;
		
		double downsample = tileRequest.getRegionRequest().getDownsample();
		
		for (Entry<String, ImageRegion> entry : regionMap.entrySet()) {
			ImageRegion subRegion = entry.getValue();
			if (subRegion.intersects(tileRequest.getImageX() + originX, tileRequest.getImageY() + originY, tileRequest.getImageWidth(), tileRequest.getImageHeight())) {
				// If we overlap, request the overlapping portion
				String serverPath = entry.getKey();
				ImageServer<BufferedImage> serverTemp = getRegionServer(serverPath);
				
				int x1 = Math.max(tileRequest.getImageX() + originX, subRegion.getX());
				int y1 = Math.max(tileRequest.getImageY() + originY, subRegion.getY());
				int x2 = Math.min(tileRequest.getImageX() + originX + tileRequest.getImageWidth(), subRegion.getX() + subRegion.getWidth());
				int y2 = Math.min(tileRequest.getImageY() + originY + tileRequest.getImageHeight(), subRegion.getY() + subRegion.getHeight());
				RegionRequest requestTemp = RegionRequest.createInstance(
						serverPath, downsample,
						x1-subRegion.getX(), y1-subRegion.getY(), x2-x1, y2-y1, tileRequest.getZ(), tileRequest.getT());
				
				BufferedImage imgTemp = null;
				synchronized (serverTemp) {
					imgTemp = serverTemp.readBufferedImage(requestTemp);					
				}
				
				if (imgTemp == null)
					continue;
				
				// If we don't have an output image yet, create a compatible one
				if (imgOutput == null) {
					raster = imgTemp.getRaster().createCompatibleWritableRaster(tileRequest.getTileWidth(), tileRequest.getTileHeight());					
					imgOutput = new BufferedImage(imgTemp.getColorModel(), raster, imgTemp.isAlphaPremultiplied(), null);
				}
				
				int x = (int)Math.round((x1 - tileRequest.getImageX() - originX) / downsample);
				int y = (int)Math.round((y1 - tileRequest.getImageY() - originY) / downsample);
				int w = Math.min(imgTemp.getWidth(), raster.getWidth()-x);
				int h = Math.min(imgTemp.getHeight(), raster.getHeight()-y);
				raster.setDataElements(x, y, w, h, imgTemp.getRaster().getDataElements(0, 0, w, h, null));
			}
		}
		
		return imgOutput;
	}

}
