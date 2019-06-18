package qupath.lib.images.servers;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
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

import qupath.lib.images.servers.ImageServerBuilder.DefaultImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.RotatedImageServer.Rotation;
import qupath.lib.images.servers.SparseImageServer.SparseImageServerManager;
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
	
	public static RuntimeTypeAdapterFactory<ServerBuilder> serverBuilderFactory = 
			RuntimeTypeAdapterFactory.of(ServerBuilder.class, "builderType")
			.registerSubtype(DefaultImageServerBuilder.class, "uri")
			.registerSubtype(RotatedImageServerBuilder.class, "rotated")
			.registerSubtype(ConcatChannelsImageServerBuilder.class, "channels")
			.registerSubtype(AffineTransformImageServerBuilder.class, "affine")
			.registerSubtype(SparseImageServerBuilder.class, "sparse")
			.registerSubtype(CroppedImageServerBuilder.class, "cropped")
			;

	
	static class SparseImageServerBuilder implements ServerBuilder<BufferedImage> {
		
		private SparseImageServerManager regions;
		private String path;
		
		SparseImageServerBuilder(SparseImageServerManager regions, String path) {
			this.regions = regions;
			this.path = path;
		}

		@Override
		public ImageServer<BufferedImage> build() throws Exception {
			return new SparseImageServer(regions, path);
		}

		@Override
		public Collection<URI> getURIs() {
			// TODO: IMPLEMENT URI QUERY!
			return Collections.emptyList();
		}

		@Override
		public ServerBuilder<BufferedImage> updateURIs(Map<URI, URI> updateMap) {
			// TODO: IMPLEMENT URI UPDATE!
			return this;
		}
		
	}
	
	static class CroppedImageServerBuilder implements ServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		private ImageRegion region;
		
		CroppedImageServerBuilder(ServerBuilder<BufferedImage> builder, ImageRegion region) {
			this.builder = builder;
			this.region = region;
		}
		
		@Override
		public ImageServer<BufferedImage> build() throws Exception {
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
			return new CroppedImageServerBuilder(newBuilder, region);
		}
		
	}
	
	static class AffineTransformImageServerBuilder implements ServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		private AffineTransform transform;
		
		AffineTransformImageServerBuilder(ServerBuilder<BufferedImage> builder, AffineTransform transform) {
			this.builder = builder;
			this.transform = transform;
		}
		
		@Override
		public ImageServer<BufferedImage> build() throws Exception {
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
			return new AffineTransformImageServerBuilder(newBuilder, transform);
		}
		
	}
	
	static class ConcatChannelsImageServerBuilder implements ServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		private List<ServerBuilder<BufferedImage>> channels;
		
		ConcatChannelsImageServerBuilder(ServerBuilder<BufferedImage> builder, List<ServerBuilder<BufferedImage>> channels) {
			this.builder = builder;
			this.channels = channels;
		}
		
		@Override
		public ImageServer<BufferedImage> build() throws Exception {
			Map<ServerBuilder<BufferedImage>, ImageServer<BufferedImage>> map = new LinkedHashMap<>();
			for (ServerBuilder<BufferedImage> channel :channels)
				map.put(channel, channel.build());
			ImageServer<BufferedImage> server = map.get(builder);
			if (server == null)
				server = builder.build();
			return new ConcatChannelsImageServer(server, map.values());
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
			return new ConcatChannelsImageServerBuilder(newBuilder, newChannels);
		}
		
	}

	static class RotatedImageServerBuilder implements ServerBuilder<BufferedImage> {
	
		private ServerBuilder<BufferedImage> builder;
		private Rotation rotation;
	
		RotatedImageServerBuilder(ServerBuilder<BufferedImage> builder, Rotation rotation) {
			this.builder = builder;
			this.rotation = rotation;
		}
	
		@Override
		public ImageServer<BufferedImage> build() throws Exception {
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
			return new RotatedImageServerBuilder(newBuilder, rotation);
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
	
	public static TypeAdapterFactory getImageServerTypeAdapterFactory(boolean includeMetadata) {
		return new ImageServerTypeAdapterFactory(includeMetadata);
	}
	
	public static TypeAdapter<ImageServer<BufferedImage>> getTypeAdapter(boolean includeMetadata) {
		return new ImageServerTypeAdapter(includeMetadata);
	}
	
	public static class ImageServerTypeAdapter extends TypeAdapter<ImageServer<BufferedImage>> {
		
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
			try {
				var builder = server.getBuilder();
				out.beginObject();
				out.name("builder");
				GsonTools.getGsonDefault().toJson(builder, ServerBuilder.class, out);
				if (includeMetadata) {
					out.name("metadata");
					var metadata = server.getMetadata();
					GsonTools.getGsonDefault().toJson(metadata, ImageServerMetadata.class, out);					
				}
				out.endObject();
				return;
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		@Override
		public ImageServer<BufferedImage> read(JsonReader in) throws IOException {
			boolean lenient = in.isLenient();
			try {
				JsonElement element = new JsonParser().parse(in);
				JsonObject obj = element.getAsJsonObject();
				
				// Create from builder
				ImageServer<BufferedImage> server = GsonTools.getGsonDefault().fromJson(obj.get("builder"), ServerBuilder.class).build();
				
				// Set metadata, if we have any
				if (obj.has("metadata")) {
					ImageServerMetadata metadata = GsonTools.getGsonDefault().fromJson(obj.get("metadata"), ImageServerMetadata.class);
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
