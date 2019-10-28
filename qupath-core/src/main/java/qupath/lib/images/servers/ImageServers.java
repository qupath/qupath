package qupath.lib.images.servers;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.images.servers.ImageServerBuilder.AbstractServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.DefaultImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.RotatedImageServer.Rotation;
import qupath.lib.images.servers.SparseImageServer.SparseImageServerManagerRegion;
import qupath.lib.images.servers.SparseImageServer.SparseImageServerManagerResolution;
import qupath.lib.io.GsonTools;
import qupath.lib.projects.Project;
import qupath.lib.regions.ImageRegion;

/**
 * Helper class for working with {@link ImageServer} objects.
 * <p>
 * The main use is to convert ImageServers to and from serialized representations for use within a {@link Project}.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageServers {
	
	private static RuntimeTypeAdapterFactory<ServerBuilder> serverBuilderFactory = 
			RuntimeTypeAdapterFactory.of(ServerBuilder.class, "builderType")
			.registerSubtype(DefaultImageServerBuilder.class, "uri")
			.registerSubtype(RotatedImageServerBuilder.class, "rotated")
			.registerSubtype(ConcatChannelsImageServerBuilder.class, "channels")
			.registerSubtype(AffineTransformImageServerBuilder.class, "affine")
			.registerSubtype(SparseImageServerBuilder.class, "sparse")
			.registerSubtype(CroppedImageServerBuilder.class, "cropped")
			.registerSubtype(PyramidGeneratingServerBuilder.class, "pyramidize")
			;
	
	/**
	 * Get a TypeAdapterFactory to handle {@linkplain ServerBuilder ServerBuilders}.
	 * @return
	 */
	public static TypeAdapterFactory getServerBuilderFactory() {
		return serverBuilderFactory;
	}

	
	static class SparseImageServerBuilder extends AbstractServerBuilder<BufferedImage> {
		
		private List<SparseImageServerManagerRegion> regions;
		private String path;
		
		SparseImageServerBuilder(ImageServerMetadata metadata, Collection<SparseImageServerManagerRegion> regions, String path) {
			super(metadata);
			this.regions = new ArrayList<>(regions);
			this.path = path;
		}

		@Override
		protected ImageServer<BufferedImage> buildOriginal() throws Exception {
			return new SparseImageServer(regions, path);
		}

		@Override
		public Collection<URI> getURIs() {
			Set<URI> uris = new LinkedHashSet<>();
			for (var region : regions) {
				for (var res : region.getResolutions()) {
					uris.addAll(res.getServerBuilder().getURIs());
				}
			}
			return uris;
		}

		@Override
		public ServerBuilder<BufferedImage> updateURIs(Map<URI, URI> updateMap) {
			List<SparseImageServerManagerRegion> newRegions = new ArrayList<>();
			for (var region : regions) {
				List<SparseImageServerManagerResolution> newResolutions = new ArrayList<>();
				for (var res : region.getResolutions()) {
					newResolutions.add(new SparseImageServerManagerResolution(
							res.getServerBuilder().updateURIs(updateMap), res.getDownsample()));
				}
				newRegions.add(new SparseImageServerManagerRegion(region.getRegion(), newResolutions));
			}
			return new SparseImageServerBuilder(getMetadata(), newRegions, path);
		}
		
	}
	
	
	static class PyramidGeneratingServerBuilder extends AbstractServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		
		PyramidGeneratingServerBuilder(ImageServerMetadata metadata, ServerBuilder<BufferedImage> builder) {
			super(metadata);
			this.builder = builder;
		}
		
		@Override
		protected ImageServer<BufferedImage> buildOriginal() throws Exception {
			var metadata = getMetadata();
			return new PyramidGeneratingImageServer(builder.build(),
					metadata.getPreferredTileWidth(),
					metadata.getPreferredTileHeight(),
					metadata.getPreferredDownsamplesArray());
		}

		@Override
		public Collection<URI> getURIs() {
			return builder.getURIs();
		}

		@Override
		public ServerBuilder<BufferedImage> updateURIs(Map<URI, URI> updateMap) {
			ServerBuilder<BufferedImage> newBuilder = builder.updateURIs(updateMap);
			if (newBuilder == builder)
				return this;
			return new PyramidGeneratingServerBuilder(getMetadata(), newBuilder);
		}
		
	}
	
	/**
	 * Wrap an ImageServer to dynamically generate a pyramid. This does not involve writing any new image, 
	 * and may be rather processor and memory-intensive as high-resolution tiles must be accessed to fulfil 
	 * low-resolution requests. However, tile caching means that after tiles have been accessed once perceived 
	 * performance can be considerably improved.
	 * 
	 * @param server the server to wrap (typically having only one resolution level)
	 * @param downsamples optional array giving the downsamples of the new pyramid. If not provided, 
	 * @return
	 */
	public static ImageServer<BufferedImage> pyramidalize(ImageServer<BufferedImage> server, double...downsamples) {
		var oldMetadata = server.getMetadata();
		
		// Determine tile sizes
		int tileWidth = 256;
		int tileHeight = 256;
		if (oldMetadata.getPreferredTileWidth() < oldMetadata.getWidth())
			tileWidth = oldMetadata.getPreferredTileWidth();
		
		if (oldMetadata.getPreferredTileHeight() < oldMetadata.getHeight())
			tileHeight = oldMetadata.getPreferredTileHeight();
		
		return pyramidalizeTiled(server, tileWidth, tileHeight, downsamples);
	}
	
	/**
	 * Wrap an ImageServer to dynamically generate a pyramid, using specified tile sizes.
	 * This does not involve writing any new image, and may be rather processor and memory-intensive as high-resolution 
	 * tiles must be accessed to fulfil low-resolution requests.
	 * However, tile caching means that after tiles have been accessed once perceived 
	 * performance can be considerably improved.
	 * 
	 * @param server the server to wrap (typically having only one resolution level)
	 * @param tileWidth requested tile height
	 * @param tileHeight requested tile height
	 * @param downsamples optional array giving the downsamples of the new pyramid. If not provided, 
	 * @return
	 */
	public static ImageServer<BufferedImage> pyramidalizeTiled(ImageServer<BufferedImage> server, int tileWidth, int tileHeight, double...downsamples) {
		var oldMetadata = server.getMetadata();
		if (downsamples.length == 0) {
			List<Double> downsampleList = new ArrayList<>();
			double downsample = oldMetadata.getDownsampleForLevel(0);
			double nextWidth = server.getWidth() / downsample;
			double nextHeight = server.getHeight() / downsample;
			do {
				downsampleList.add(downsample);
				downsample *= 4;
				nextWidth = server.getWidth() / downsample;
				nextHeight = server.getHeight() / downsample;
			} while ((nextWidth > tileWidth || nextHeight > tileHeight) && nextWidth >= 8 && nextHeight >= 8);
			downsamples = downsampleList.stream().mapToDouble(d -> d).toArray();
		}
		return new PyramidGeneratingImageServer(server, tileWidth, tileHeight, downsamples);
	}
	
	
	static class CroppedImageServerBuilder extends AbstractServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		private ImageRegion region;
		
		CroppedImageServerBuilder(ImageServerMetadata metadata, ServerBuilder<BufferedImage> builder, ImageRegion region) {
			super(metadata);
			this.builder = builder;
			this.region = region;
		}
		
		@Override
		protected ImageServer<BufferedImage> buildOriginal() throws Exception {
			return new CroppedImageServer(builder.build(), region);
		}

		@Override
		public Collection<URI> getURIs() {
			return builder.getURIs();
		}

		@Override
		public ServerBuilder<BufferedImage> updateURIs(Map<URI, URI> updateMap) {
			ServerBuilder<BufferedImage> newBuilder = builder.updateURIs(updateMap);
			if (newBuilder == builder)
				return this;
			return new CroppedImageServerBuilder(getMetadata(), newBuilder, region);
		}
		
	}
	
	static class AffineTransformImageServerBuilder extends AbstractServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		private AffineTransform transform;
		
		AffineTransformImageServerBuilder(ImageServerMetadata metadata, ServerBuilder<BufferedImage> builder, AffineTransform transform) {
			super(metadata);
			this.builder = builder;
			this.transform = transform;
		}
		
		@Override
		protected ImageServer<BufferedImage> buildOriginal() throws Exception {
			return new AffineTransformImageServer(builder.build(), transform);
		}

		@Override
		public Collection<URI> getURIs() {
			return builder.getURIs();
		}

		@Override
		public ServerBuilder<BufferedImage> updateURIs(Map<URI, URI> updateMap) {
			ServerBuilder<BufferedImage> newBuilder = builder.updateURIs(updateMap);
			if (newBuilder == builder)
				return this;
			return new AffineTransformImageServerBuilder(getMetadata(), newBuilder, transform);
		}
		
	}
	
	static class ConcatChannelsImageServerBuilder extends AbstractServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		private List<ServerBuilder<BufferedImage>> channels;
		
		ConcatChannelsImageServerBuilder(ImageServerMetadata metadata, ServerBuilder<BufferedImage> builder, List<ServerBuilder<BufferedImage>> channels) {
			super(metadata);
			this.builder = builder;
			this.channels = channels;
		}
		
		@Override
		protected ImageServer<BufferedImage> buildOriginal() throws Exception {
			List<ImageServer<BufferedImage>> servers = new ArrayList<>();
			ImageServer<BufferedImage> server = null;
			for (ServerBuilder<BufferedImage> channel :channels) {
				var temp = channel.build();
				servers.add(temp);
				if (builder.equals(channel))
					server = temp;
			}
			if (server == null)
				server = builder.build();
			return new ConcatChannelsImageServer(server, servers);
		}
		
		@Override
		public Collection<URI> getURIs() {
			Set<URI> uris = new LinkedHashSet<>();
			uris.addAll(builder.getURIs());
			for (var temp : channels)
				uris.addAll(temp.getURIs());
			return uris;
		}

		@Override
		public ServerBuilder<BufferedImage> updateURIs(Map<URI, URI> updateMap) {
			ServerBuilder<BufferedImage> newBuilder = builder.updateURIs(updateMap);
			boolean changes = newBuilder != builder;
			List<ServerBuilder<BufferedImage>> newChannels = new ArrayList<>();
			for (var temp : channels) {
				var newChannel = temp.updateURIs(updateMap);
				newChannels.add(newChannel);
				changes = changes || newChannel != temp;
			}
			if (!changes)
				return this;
			return new ConcatChannelsImageServerBuilder(getMetadata(), newBuilder, newChannels);
		}
		
	}

	static class RotatedImageServerBuilder extends AbstractServerBuilder<BufferedImage> {
	
		private ServerBuilder<BufferedImage> builder;
		private Rotation rotation;
	
		RotatedImageServerBuilder(ImageServerMetadata metadata, ServerBuilder<BufferedImage> builder, Rotation rotation) {
			super(metadata);
			this.builder = builder;
			this.rotation = rotation;
		}
	
		@Override
		protected ImageServer<BufferedImage> buildOriginal() throws Exception {
			return new RotatedImageServer(builder.build(), rotation);
		}
		
		@Override
		public Collection<URI> getURIs() {
			return builder.getURIs();
		}

		@Override
		public ServerBuilder<BufferedImage> updateURIs(Map<URI, URI> updateMap) {
			ServerBuilder<BufferedImage> newBuilder = builder.updateURIs(updateMap);
			if (newBuilder == builder)
				return this;
			return new RotatedImageServerBuilder(getMetadata(), newBuilder, rotation);
		}
	
	}
	
	static class ImageServerTypeAdapterFactory implements TypeAdapterFactory {
		
		private boolean includeMetadata;
		
		ImageServerTypeAdapterFactory(boolean includeMetadata) {
			this.includeMetadata = includeMetadata;
		}

		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			if (ImageServer.class.isAssignableFrom(type.getRawType()))
				return (TypeAdapter<T>)getTypeAdapter(includeMetadata);
			return null;
		}
		
	}
	
	/**
	 * Get a TypeAdapterFactory for ImageServers, optionally including metadata in the serialized 
	 * form of the server.
	 * @param includeMetadata
	 * @return
	 */
	public static TypeAdapterFactory getImageServerTypeAdapterFactory(boolean includeMetadata) {
		return new ImageServerTypeAdapterFactory(includeMetadata);
	}
	
	private static TypeAdapter<ImageServer<BufferedImage>> getTypeAdapter(boolean includeMetadata) {
		return new ImageServerTypeAdapter(includeMetadata);
	}
	
	static class ImageServerTypeAdapter extends TypeAdapter<ImageServer<BufferedImage>> {
		
		private static Logger logger = LoggerFactory.getLogger(ImageServerTypeAdapter.class);
		
		private final boolean includeMetadata;
		
		ImageServerTypeAdapter() {
			this(true);
		}
		
		ImageServerTypeAdapter(boolean includeMetadata) {
			this.includeMetadata = includeMetadata;
		}
		
		
//		private Gson gson = new GsonBuilder()
//				.setLenient()
//				.serializeSpecialFloatingPointValues()
//				.setPrettyPrinting()
//				.registerTypeAdapterFactory(serverBuilderFactory)
////				.registerTypeAdapterFactory(new ImageServerTypeAdapterFactory())
//				.create();
		
		@Override
		public void write(JsonWriter out, ImageServer<BufferedImage> server) throws IOException {
			boolean lenient = out.isLenient();
			try {
				out.setLenient(true);
				var builder = server.getBuilder();
				out.beginObject();
				out.name("builder");
				GsonTools.getInstance().toJson(builder, ServerBuilder.class, out);
				if (includeMetadata) {
					out.name("metadata");
					var metadata = server.getMetadata();
					GsonTools.getInstance().toJson(metadata, ImageServerMetadata.class, out);					
				}
				out.endObject();
				return;
			} catch (Exception e) {
				throw new IOException(e);
			} finally {
				out.setLenient(lenient);
			}
		}

		@Override
		public ImageServer<BufferedImage> read(JsonReader in) throws IOException {
			boolean lenient = in.isLenient();
			try {
				in.setLenient(true);
				JsonElement element = JsonParser.parseReader(in);
				JsonObject obj = element.getAsJsonObject();
				
				// Create from builder
				ImageServer<BufferedImage> server = GsonTools.getInstance().fromJson(obj.get("builder"), ServerBuilder.class).build();
				
				// Set metadata, if we have any
				if (obj.has("metadata")) {
					ImageServerMetadata metadata = GsonTools.getInstance().fromJson(obj.get("metadata"), ImageServerMetadata.class);
					if (metadata != null)
						server.setMetadata(metadata);
				}
								
				return server;
			} catch (Exception e) {
				throw new IOException(e);
			} finally {
				in.setLenient(lenient);
			}
		}
		
		
	}

}
