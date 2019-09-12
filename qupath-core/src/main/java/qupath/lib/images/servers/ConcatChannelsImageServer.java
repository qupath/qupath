package qupath.lib.images.servers;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServers.ConcatChannelsImageServerBuilder;
import qupath.lib.regions.RegionRequest;

/**
 * Concatenate ImageServers along the channel dimension.
 * 
 * @author Pete Bankhead
 *
 */
public class ConcatChannelsImageServer extends TransformingImageServer<BufferedImage> {
	
	private ImageServerMetadata originalMetadata;
	private List<ImageServer<BufferedImage>> allServers = new ArrayList<>();

	/**
	 * Define a main ImageServer and a collection of others to append along the channel dimension.
	 * <p>
	 * The order of entries in the collection determines the order in which the channels will be appended.
	 * <p>
	 * The main server is used to determine the metadata. If the main server is also inside the 
	 * collection, then it will be inserted at the corresponding location in the collection; 
	 * otherwise it will be the first server (i.e. first channels).
	 * 
	 * @param server
	 * @param imageServers
	 */
	public ConcatChannelsImageServer(ImageServer<BufferedImage> server, Collection<ImageServer<BufferedImage>> imageServers) {
		super(server);
		if (!imageServers.contains(server))
			allServers.add(0, server);
		allServers.addAll(imageServers);
		
		var channels = new ArrayList<ImageChannel>();
		for (var s : allServers)
			channels.addAll(s.getMetadata().getChannels());
		
		originalMetadata = new ImageServerMetadata.Builder(server.getMetadata())
//				.path("Merged channels ["+String.join(", ", allServers.stream().map(s -> s.getPath()).collect(Collectors.toList())) + "]")
				.rgb(server.getMetadata().isRGB() && allServers.size() == 1)
				.channels(channels)
				.build();
	}

	@Override
	protected String createID() {
		StringBuilder sb = new StringBuilder();
		for (var server : allServers) {
			if (sb.length() == 0)
				sb.append(", ");
			sb.append(server.getPath());
		}
		return getClass().getName() + ": [" + sb + "]";
	}
	
	@Override
	public String getServerType() {
		return "Channel concat image server";
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}
	
	/**
	 * Get an unmodifiable list of all ImageServers being concatenated.
	 * @return
	 */
	public List<ImageServer<BufferedImage>> getAllServers() {
		return Collections.unmodifiableList(allServers);
	}
	
	@Override
	public BufferedImage readBufferedImage(RegionRequest request) throws IOException {
		
		List<WritableRaster> rasters = new ArrayList<>();
		boolean premultiplied = false;
		int nBands = 0;
		for (var server : allServers) {
			var img = server.readBufferedImage(request);
			premultiplied = img.isAlphaPremultiplied();
			nBands += img.getRaster().getNumBands();
			rasters.add(img.getRaster());
		}
		var first = rasters.get(0);
		int width = first.getWidth();
		int height = first.getHeight();
		
		WritableRaster raster;
		if (first.getDataBuffer().getDataType() != DataBuffer.TYPE_BYTE && first.getDataBuffer().getDataType() != DataBuffer.TYPE_USHORT) {
			BandedSampleModel sampleModel = new BandedSampleModel(DataBuffer.TYPE_FLOAT, width, height, nBands);
			raster = WritableRaster.createWritableRaster(sampleModel, null);
		} else
			raster = WritableRaster.createInterleavedRaster(first.getDataBuffer().getDataType(), width, height, nBands, null);
		
//		var raster = WritableRaster.createBandedRaster(first.getDataBuffer().getDataType(), width, height, nBands, null);
		float[] samples = new float[width * height];
		int currentBand = 0;
		for (var temp : rasters) {
			int w = Math.min(width, temp.getWidth());
			int h = Math.min(height, temp.getHeight());
			for (int b = 0; b < temp.getNumBands(); b++) {
				samples = temp.getSamples(0, 0, w, h, b, samples);
				raster.setSamples(0, 0, w, h, currentBand, samples);
				currentBand++;
			}
		}
		
		return new BufferedImage(
				ColorModelFactory.getDummyColorModel(getPixelType().getBitsPerPixel()),
				raster, premultiplied, null);
	}
	
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return new ConcatChannelsImageServerBuilder(
				getMetadata(),
				getWrappedServer().getBuilder(),
				allServers.stream().map(s -> s.getBuilder()).collect(Collectors.toList())
				);
	}
	
}
