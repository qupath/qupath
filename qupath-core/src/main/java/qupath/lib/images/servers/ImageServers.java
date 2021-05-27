/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.images.servers;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Unmatched;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.images.servers.ImageServerBuilder.AbstractServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.DefaultImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.UriImageSupport;
import qupath.lib.images.servers.ImageServerProvider.UriImageSupportComparator;
import qupath.lib.images.servers.RotatedImageServer.Rotation;
import qupath.lib.images.servers.SparseImageServer.SparseImageServerManagerRegion;
import qupath.lib.images.servers.SparseImageServer.SparseImageServerManagerResolution;
import qupath.lib.io.GsonTools;
import qupath.lib.io.GsonTools.SubTypeAdapterFactory;
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
	
	private static SubTypeAdapterFactory<ServerBuilder> serverBuilderFactory = 
			GsonTools.createSubTypeAdapterFactory(ServerBuilder.class, "builderType")
			.registerSubtype(DefaultImageServerBuilder.class, "uri")
			.registerSubtype(RotatedImageServerBuilder.class, "rotated")
			.registerSubtype(ConcatChannelsImageServerBuilder.class, "channels")
			.registerSubtype(ChannelTransformFeatureServerBuilder.class, "color")
			.registerSubtype(AffineTransformImageServerBuilder.class, "affine")
			.registerSubtype(SparseImageServerBuilder.class, "sparse")
			.registerSubtype(CroppedImageServerBuilder.class, "cropped")
			.registerSubtype(PyramidGeneratingServerBuilder.class, "pyramidize") // For consistency, this would ideally be pyramidalize... but need to keep backwards-compatibility
			.registerSubtype(ReorderRGBServerBuilder.class, "swapRedBlue")
			.registerSubtype(ColorDeconvolutionServerBuilder.class, "color_deconvolved")
			;
	
	static {
		GsonTools.getDefaultBuilder()
			.registerTypeAdapterFactory(ImageServers.getImageServerTypeAdapterFactory(true))
			.registerTypeAdapterFactory(ImageServers.getServerBuilderFactory());
	}
	
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
	
	
	/**
	 * Build a {@link ImageServer} for the specified URI path and optional args.
	 * <p>
	 * See {@link #buildServer(URI, String...)} for more information about how args are applied.
	 * 
	 * @param path path for the image; it should be possible to convert this to a URI
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static ImageServer<BufferedImage> buildServer(String path, String... args) throws IOException {
		return buildServer(ImageServerProvider.legacyPathToURI(path), args);
	}
	
	/**
	 * Build a {@link ImageServer} for the specified URI and optional args.
	 * This differs from {@link ImageServerProvider#buildServer(String, Class, String...)} in two main ways:
	 * <ol>
	 *   <li>it always uses {@link BufferedImage} as the server class</li>
	 *   <li>where possible, args that request a transformed server are applied</li>
	 * </ol>
	 * 
	 * Supported arguments include
	 * <ol>
	 *   <li>{@code --classname} to request an image provider class</li>
	 *   <li>{@code --order RGB/BGR} etc. to request reordering for RGB channels</li>
	 *   <li>{@code --rotate ROTATE_90/ROTATE_180/ROTATE_270} to request rotation</li>
	 * </ol>
	 * More arguments are likely to be added in future versions. All unmatched arguments are passed to the 
	 * image reading library.
	 * 
	 * @param uri
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static ImageServer<BufferedImage> buildServer(URI uri, String... args) throws IOException {
		var supports = getAllImageSupports(uri, args);
		List<Exception> exceptions = new ArrayList<>();
		for (var support : supports) {
			try {
				return support.getBuilders().iterator().next().build();
			} catch (Exception e) {
				exceptions.add(e);
			}
		}
		var exception = new IOException(buildBaseExceptionMessage(uri, args));
		for (var e : exceptions)
			exception.addSuppressed(e);
		throw exception;
	}
	
	private static String buildBaseExceptionMessage(URI uri, String... args) {
		if (args.length == 0)
			return "Unable to open " + uri;
		else
			return "Unable to open " + uri + " with args [" + Arrays.stream(args).collect(Collectors.joining(", ")) + "]";
	}
	
	/**
	 * Get all {@link UriImageSupport} that claim to be able to open the specified URI with optional args.
	 * <p>
	 * See {@link #buildServer(URI, String...)} for more information about how args are applied.
	 * 
	 * @param uri
	 * @param args
	 * @return a list or {@link UriImageSupport}, ranked in descending order of support level.
	 * @throws IOException
	 */
	public static List<UriImageSupport<BufferedImage>> getAllImageSupports(URI uri, String... args) throws IOException {
		
		var serverArgs = parseServerArgs(args);
		String[] requestedClassnames = serverArgs.requestedClassnames;
		
		List<UriImageSupport<BufferedImage>> supports = new ArrayList<>();
		var availableBuilders = ImageServerProvider.getInstalledImageServerBuilders(BufferedImage.class);
		List<Exception> exceptions = new ArrayList<>();
		
		// If we've requested a particular builder, only check that
		if (requestedClassnames.length > 0) {
			for (var builder : availableBuilders) {
				if (builder.matchClassName(requestedClassnames)) {
					try {
						var support = getImageSupport(builder, uri, serverArgs);
						if (support != null)
							supports.add(support);
						else
							exceptions.add(new IOException("Unable to open " + uri + " with " + builder));
					} catch (IOException e) {
						exceptions.add(e);
					}
				}
			}
			if (supports.isEmpty()) {
				throw new IOException("No compatible readers found with classnames [" + Arrays.stream(requestedClassnames).collect(Collectors.joining(", ")) + "]");
			}
		} else {
			// If we don't know what builder we want, check all of them
			for (var builder : availableBuilders) {
				try {
					var support = getImageSupport(builder, uri, serverArgs);
					if (support != null && support.getSupportLevel() > 0f)
						supports.add(support);
				} catch (IOException e) {
					exceptions.add(e);
				}
			}
		}
		if (supports.isEmpty()) {
			var exception = new IOException(buildBaseExceptionMessage(uri, args));
			for (var e : exceptions)
				exception.addSuppressed(e);
			throw exception;
		}
		Comparator<UriImageSupport<BufferedImage>> comparator = Collections.reverseOrder(new UriImageSupportComparator<>());
		supports.sort(comparator);
		return supports;
	}
	
	private static UriImageSupport<BufferedImage> getImageSupport(ImageServerBuilder<BufferedImage> builder, URI uri, ServerArgs serverArgs) throws IOException {
		var extraArgs = serverArgs.unmatched.clone();
		var support = builder.checkImageSupport(uri, extraArgs);
		return transformSupport(support, serverArgs);
	}
	
	/**
	 * Get the {@link UriImageSupport} that is best able to open the specified image with optional args.
	 * <p>
	 * See {@link #buildServer(URI, String...)} for more information about how args are applied.
	 * 
	 * @param uri
	 * @param args
	 * @return a list or {@link UriImageSupport}, ranked in descending order of support level.
	 * @throws IOException
	 */
	public static UriImageSupport<BufferedImage> getImageSupport(URI uri, String... args) throws IOException {
		var supports = getAllImageSupports(uri, args);
		return supports.isEmpty() ? null : supports.get(0);
	}
	
	/**
	 * Get the {@link UriImageSupport} associated with an {@link ImageServerBuilder}, or null if the builder does not support the image.
	 * <p>
	 * See {@link #buildServer(URI, String...)} for more information about how args are applied.
	 * 
	 * @param builder
	 * @param uri
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static UriImageSupport<BufferedImage> getImageSupport(ImageServerBuilder<BufferedImage> builder, URI uri, String... args) throws IOException {
		var serverArgs = parseServerArgs(args);
		return getImageSupport(builder, uri, serverArgs);
	}
	
	
	private static ServerArgs parseServerArgs(String... args) {
		var serverArgs = new ServerArgs();
		new CommandLine(serverArgs).parseArgs(args);
		return serverArgs;
	}
	
	
	/**
	 * In the case that we are working with BufferedImages, we can apply some default operations based on 
	 * requested arguments.
	 * @param support
	 * @param args
	 * @return
	 */
	static UriImageSupport<BufferedImage> transformSupport(UriImageSupport<BufferedImage> support, ServerArgs args) {
		if (support == null)
			return null;
		List<Function<ServerBuilder<BufferedImage>, ServerBuilder<BufferedImage>>> functions = new ArrayList<>();
		if (args.rotation != null && args.rotation != Rotation.ROTATE_NONE)
			functions.add(b -> RotatedImageServer.getRotatedBuilder(b, args.rotation));
		String orderRGB = args.getOrderRGB();
		if (orderRGB != null) {
			functions.add(b -> RearrangeRGBImageServer.getSwapRedBlueBuilder(b, orderRGB));
		}
		if (functions.isEmpty())
			return support;
		List<ServerBuilder<BufferedImage>> buildersNew = new ArrayList<>();
		for (var builder : support.getBuilders()) {
			for (var fun : functions)
				builder = fun.apply(builder);
			buildersNew.add(builder);
		}
		return new UriImageSupport<>(support.getProviderClass(), support.getSupportLevel(), buildersNew);
	}
	
	
	
	static class ServerArgs {
		
		@Option(names = {"--classname"}, description = "Requested classnames for the ImageServerProvider")
		String[] requestedClassnames = new String[0];

		@Option(names = {"--rotate"}, description = "Rotate the image during reading by an increment of 90 degrees.")
		Rotation rotation = Rotation.ROTATE_NONE;
		
		@Option(names = {"--order"}, description = "Rearrange the channels of an RGB image (to correct errors in the image reader)")
		String orderRGB = null;
		
		@Unmatched
		String[] unmatched = new String[0];
		
		String getOrderRGB() {
			return orderRGB == null || orderRGB.isBlank() ? null : orderRGB.trim().toUpperCase();
		}
		
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
			double[] flat = new double[6];
			// The state is a transient property, which is not restored when using JSON serialization -
			// so we need to create a new transform, rather than simply using the existing one.
			// If we don't, operations aren't performed because the transform is assumed to be the identity 
			// (based upon state, even though the values are 'correct')
			
			// Note: as of v0.2.1 the default Gson will actually use an AffineTransformProxy as an intermediate, 
			// which should make this step unnecessary
			transform.getMatrix(flat);
			return new AffineTransformImageServer(builder.build(), new AffineTransform(flat));
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
			for (ServerBuilder<BufferedImage> channel : channels) {
				var temp = channel.build();
				servers.add(temp);
				// TODO: Warning! in general, ServerBuilders do not necessarily override equals/hashcode - which can be problematic here
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
	
	static class ChannelTransformFeatureServerBuilder extends AbstractServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		private List<ColorTransform> transforms;
		
		ChannelTransformFeatureServerBuilder(ImageServerMetadata metadata, ServerBuilder<BufferedImage> builder, List<ColorTransform> transforms) {
			super(metadata);
			this.builder = builder;
			this.transforms = transforms;
		}
		
		@Override
		protected ImageServer<BufferedImage> buildOriginal() throws Exception {
			return new ChannelTransformFeatureServer(builder.build(), transforms);
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
			return new ChannelTransformFeatureServerBuilder(getMetadata(), newBuilder, transforms);
		}
		
	}
	
	static class ColorDeconvolutionServerBuilder extends AbstractServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		private ColorDeconvolutionStains stains;
		private int[] channels;
		
		ColorDeconvolutionServerBuilder(ImageServerMetadata metadata, ServerBuilder<BufferedImage> builder, ColorDeconvolutionStains stains, int... channels) {
			super(metadata);
			this.builder = builder;
			this.stains = stains;
			this.channels = channels == null ? null : channels.clone();
		}
		
		@Override
		protected ImageServer<BufferedImage> buildOriginal() throws Exception {
			return new ColorDeconvolutionImageServer(builder.build(), stains, channels);
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
			return new ColorDeconvolutionServerBuilder(getMetadata(), newBuilder, stains, channels);
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
	
	static class ReorderRGBServerBuilder extends AbstractServerBuilder<BufferedImage> {
		
		private ServerBuilder<BufferedImage> builder;
		private String order;
		
		ReorderRGBServerBuilder(ImageServerMetadata metadata, ServerBuilder<BufferedImage> builder, String order) {
			super(metadata);
			this.builder = builder;
			this.order = order;
		}
		
		@Override
		protected ImageServer<BufferedImage> buildOriginal() throws Exception {
			return new RearrangeRGBImageServer(builder.build(), order);
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
			return new ReorderRGBServerBuilder(getMetadata(), newBuilder, order);
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