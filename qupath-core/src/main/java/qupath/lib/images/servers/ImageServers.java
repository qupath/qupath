package qupath.lib.images.servers;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.images.servers.RotatedImageServer.Rotation;
import qupath.lib.images.servers.SparseImageServer.SparseImageServerManagerRegion;
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
	
	static class ImageServerTypeAdapterFactory implements TypeAdapterFactory {

		public ImageServerTypeAdapterFactory() {}
		
		private static String typeName = "serverClass";
		
		private final static RuntimeTypeAdapterFactory<ImageServer> imageServerTypeAdapter = 
				RuntimeTypeAdapterFactory.of(ImageServer.class, typeName);
		
		public static void registerSubtype(Class<? extends ImageServer> cls) {
			imageServerTypeAdapter.registerSubtype(cls);
		}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return imageServerTypeAdapter.create(gson, type);
		}
		
	}
	
	
	
	private static Gson gson = new GsonBuilder()
		    .serializeSpecialFloatingPointValues()
		    .setLenient()
		    .setPrettyPrinting()
		    .registerTypeHierarchyAdapter(ImageServer.class, new ImageServers.ImageServerTypeAdapter())
		    .create();
	
	private static Gson gsonNoMetadata = new GsonBuilder()
		    .serializeSpecialFloatingPointValues()
		    .setLenient()
		    .setPrettyPrinting()
		    .registerTypeHierarchyAdapter(ImageServer.class, new ImageServers.ImageServerTypeAdapter(false))
		    .create();
	
	private static final java.lang.reflect.Type type = new TypeToken<ImageServer<BufferedImage>>() {}.getType();
	
	/**
	 * Serialize an ImageServer to a JSON String. Note that not all servers support this.
	 * @param <T>
	 * @param server the server to serialize
	 * @param includeMetadata optionally include the metadata in the representation;
	 * 						  this will require more space, but means the representation is more self-contained.
	 * @return
	 */
	public static <T> String toJson(ImageServer<T> server, boolean includeMetadata) {
		if (includeMetadata)
			return gson.toJson(server);
		else
			return gsonNoMetadata.toJson(server);
	}
	
	/**
	 * Serialize an ImageServer to a JSON element. Note that not all servers support this.
	 * @param <T>
	 * @param server the server to serialize
	 * @param includeMetadata optionally include the metadata in the representation;
	 * 						  this will require more space, but means the representation is more self-contained.
	 * @return
	 */
	public static <T> JsonElement toJsonElement(ImageServer<T> server, boolean includeMetadata) {
		var builder = server.getBuilder();
		if (builder != null)
			return gson.toJsonTree(builder);
		if (includeMetadata)
			return gson.toJsonTree(server);
		else
			return gsonNoMetadata.toJsonTree(server);
	}
	
	/**
	 * Read an {@code ImageServer<BufferedImage>} from its JSON element representation.
	 * @param element JSON representation of the server
	 * @return
	 */
	public static ImageServer<BufferedImage> fromJson(JsonElement element) {
		return gson.fromJson(element, type);
	}
	
//	public static String toJson(URI uri, String... args) throws IOException {
//		StringWriter writer = new StringWriter();
//		try (JsonWriter out = gson.newJsonWriter(writer)) {
//			out.name("uri");
//			out.value(uri.toString());
//			out.name("args");
//			out.beginArray();
//			for (String arg: args)
//				out.value(arg);
//			out.endArray();
//		}
//		return writer.toString();
//	}
	
	/**
	 * Read an ImageServer from its JSON String representation.
	 * @param <T>
	 * @param json JSON representation of the server
	 * @param cls generic type of the ImageServer, usually BufferedImage.class
	 * @return
	 */
	public static <T> ImageServer<T> fromJson(String json, Class<T> cls) {
		return gson.fromJson(json, type);
	}
	
	
	/**
	 * Read an ImageServer from its JSON representation.
	 * @param <T>
	 * @param reader reader that provides the JSON representation of the server
	 * @param cls generic type of the ImageServer, usually BufferedImage.class
	 * @return
	 */
	public static <T> ImageServer<T> fromJson(Reader reader, Class<T> cls) {
		return gson.fromJson(reader, type);
	}
		    
	
	/**
	 * @author Pete Bankhead
	 *
	 */
	public static class ImageServerTypeAdapter extends TypeAdapter<ImageServer<BufferedImage>> {
		
		private static Logger logger = LoggerFactory.getLogger(ImageServerTypeAdapter.class);
		
		private final boolean includeMetadata;
		
		ImageServerTypeAdapter() {
			this(true);
		}
		
		ImageServerTypeAdapter(boolean includeMetadata) {
			this.includeMetadata = includeMetadata;
		}
		
		private Gson gson = new GsonBuilder()
				.setLenient()
				.serializeSpecialFloatingPointValues()
				.setPrettyPrinting()
//				.registerTypeAdapterFactory(new ImageServerTypeAdapterFactory())
				.create();
		
		@Override
		public void write(JsonWriter out, ImageServer<BufferedImage> server) throws IOException {
			if (server instanceof TransformingImageServer<?>) {
				
				ImageServer<BufferedImage> wrappedServer = ((TransformingImageServer)server).getWrappedServer();
				Gson gson = new GsonBuilder()
						.setLenient()
						.serializeSpecialFloatingPointValues()
						.registerTypeHierarchyAdapter(ImageServer.class, this)
						.create();
				
				out.beginObject();

				out.name(ImageServerTypeAdapterFactory.typeName);
				out.value(server.getClass().getName());
				
				if (server instanceof CroppedImageServer) {
					out.name("region");
					Streams.write(gson.toJsonTree(((CroppedImageServer)server).getCropRegion()), out);
				} else if (server instanceof RotatedImageServer) {
					out.name("rotation");
					Streams.write(gson.toJsonTree(((RotatedImageServer)server).getRotation()), out);				
				} else if (server instanceof ConcatChannelsImageServer) {
					out.name("channelServers");
					out.beginArray();
					for (ImageServer<BufferedImage> channelServers : ((ConcatChannelsImageServer)server).getAllServers())
						Streams.write(gson.toJsonTree(channelServers), out);
					out.endArray();
				} else if (server instanceof AffineTransformImageServer) {
					out.name("transform");
					double[] matrix = new double[6];
					AffineTransform transform = ((AffineTransformImageServer)server).getTransform();
					transform.getMatrix(matrix);
					Streams.write(gson.toJsonTree(matrix), out);					
				}
				
				out.name("server");
				Streams.write(gson.toJsonTree(wrappedServer), out);
				
//				String json = gson.toJson(wrappedServer);
//				out.jsonValue(json);
//				out.endObject();
				
				out.endObject();
				
				return;
			} else if (server instanceof SparseImageServer) {
				out.beginObject();
				out.name(ImageServerTypeAdapterFactory.typeName);
				out.value(server.getClass().getName());
				out.name("sparseRegions");
				Streams.write(gson.toJsonTree(((SparseImageServer)server).getRegions()), out);
				out.endObject();
				return;
			}
			
			out.beginObject();

			out.name(ImageServerTypeAdapterFactory.typeName);
			out.value(server.getClass().getName());

			URI uri = server.getURI();
			if (uri != null) {
				out.name("uri");
				out.value(uri.normalize().toString());
			}
			
			String path = server.getPath();
			if (path != null) {
				out.name("path");
				out.value(path);
			}

			out.name("args");
			out.beginArray();
			for (String arg: server.getMetadata().getArguments())
				out.value(arg);
			out.endArray();
			
			if (includeMetadata) {
				out.name("metadata");
				Streams.write(gson.toJsonTree(server.getMetadata()), out);
			}

			out.endObject();
			
		}

		@Override
		public ImageServer<BufferedImage> read(JsonReader in) throws IOException {
			boolean lenient = in.isLenient();
			try {
				ImageServer<BufferedImage> server = null;
				
				JsonElement element = new JsonParser().parse(in);
				JsonObject obj = element.getAsJsonObject();
				URI uri = null;
				List<String> args = new ArrayList<>();
				String serverType = null;
				
				// Request server type
				if (obj.has(ImageServerTypeAdapterFactory.typeName)) {
					serverType = obj.get(ImageServerTypeAdapterFactory.typeName).getAsString();
				}
				
				
				// Try to get a URI, if possible
				if (obj.has("uri")) {
					uri = new URI(obj.get("uri").getAsString()).normalize();
				}
//				if (obj.has("path")) {
//					String path = obj.get("path").getAsString();
////					if (uri == null) {
////						if (path.startsWith("file") || path.startsWith("http"))
////							uri = new URI(path);
////						else
////							uri = new File(path).toURI();
////					}
//				}
				
				// Request any args
				if (obj.has("args")) {
					var array = obj.get("args").getAsJsonArray();
					args = new ArrayList<>();
					for (int i = 0; i < array.size(); i++)
						args.add(array.get(i).getAsString());
				}
				
				// If we have a URI, try to construct in the 'normal' way using the URI & args
				if (serverType != null) {
					args.add(0, "--classname");
					args.add(1, serverType);
				}
				if (uri != null) {
					try {
						server = ImageServerProvider.buildServer(uri.toString(), BufferedImage.class, args.toArray(String[]::new));
					} catch (IOException e1) {
						logger.warn("Unable to construct server (uri={}, args={}) - {}", uri, args, e1.getLocalizedMessage());
					}
				}
				
				
				if (server == null && serverType != null) {
					
					Gson gson = new GsonBuilder()
							.setLenient()
							.serializeSpecialFloatingPointValues()
							.registerTypeHierarchyAdapter(ImageServer.class, this)
							.create();
					
					// Sparse
					if (serverType.equals(SparseImageServer.class.getName())) {
						List<SparseImageServerManagerRegion> regions = gson.fromJson(obj.get("sparseRegions"),
								new TypeToken<List<SparseImageServerManagerRegion>>() {}.getType());
						server = new SparseImageServer(regions, null);
					}
					
					// Cropped
					if (serverType.equals(CroppedImageServer.class.getName())) {
						ImageRegion region = gson.fromJson(obj.get("region"), ImageRegion.class);
						ImageServer<BufferedImage> wrappedServer = gson.fromJson(obj.get("server"), ImageServer.class);
						server = new CroppedImageServer(wrappedServer, region);
					}

					// Rotated
					if (serverType.equals(RotatedImageServer.class.getName())) {
						Rotation rotation = gson.fromJson(obj.get("rotation"), Rotation.class);
						ImageServer<BufferedImage> wrappedServer = gson.fromJson(obj.get("server"), ImageServer.class);
						server = new RotatedImageServer(wrappedServer, rotation);
					}

					// Affine transform
					if (serverType.equals(AffineTransformImageServer.class.getName())) {
						double[] matrix = gson.fromJson(obj.get("transform"), double[].class);
						AffineTransform transform = new AffineTransform(matrix);
						ImageServer<BufferedImage> wrappedServer = gson.fromJson(obj.get("server"), ImageServer.class);
						server = new AffineTransformImageServer(wrappedServer, transform);
					}
					
					// TODO: Color deconvolution
	
					// Concat channels
					if (serverType.equals(ConcatChannelsImageServer.class.getName())) {
						ImageServer<BufferedImage> wrappedServer = gson.fromJson(obj.get("server"), ImageServer.class);
						List<ImageServer<BufferedImage>> channelServers = gson.fromJson(obj.get("channelServers"),
								new TypeToken<List<ImageServer<BufferedImage>>>() {}.getType());
						server = new ConcatChannelsImageServer(wrappedServer, channelServers);
					}
				}
				
				
				// As a last resort, try to read directly from the JSON
				if (server == null)
					gson.fromJson(element, type);					
				
				// Set the metadata, if we have any
				if (obj.has("metadata")) {
					var metadata = gson.fromJson(obj.get("metadata"), ImageServerMetadata.class);
					if (!server.getPath().equals(metadata.getPath())) {
						logger.warn("Server and metadata paths are different! Metadata path will be updated to match.");
						logger.warn("Server:  \t {}", server.getPath());
						logger.warn("Metadata:\t {}", metadata.getPath());
						metadata = new ImageServerMetadata.Builder(server.getClass(), metadata)
								.path(server.getPath())
								.build();
					}
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
