/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.images.servers;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Helper class for creating ImageServers from a given URI and optional argument list.
 * 
 * @author Pete Bankhead
 *
 */
public interface ImageServerBuilder<T> {

	/**
	 * Check whether a URI is supported by this builder.
	 * <p>
	 * This can be used to gain an estimate of how well the format is supported, and the number of images found.	
	 * @param uri
	 * @param args optional String args (may be ignored)
	 * @return
	 * @throws IOException
	 */
	public UriImageSupport<T> checkImageSupport(URI uri, String...args) throws IOException;
	
	/**
	 * Attempt to create {@code ImageServer<T>} from the specified path.
	 * @param uri
	 * @param args optional String arguments that may be used by the builder.
	 * @return
	 */
	public ImageServer<T> buildServer(URI uri, String... args) throws Exception;
	
	/**
	 * Get a human-readable name for the kind of ImageServer this builds.
	 * @return
	 */
	public String getName();
	
	/**
	 * Get a short, human-readable description for display in a GUI.
	 * @return
	 */
	public String getDescription();
	
	/**
	 * Returns the base class for the images supported by this server. 
	 * Typically this is BufferedImage.class.
	 * @return
	 */
	public Class<T> getImageType();
	
	
	
	/**
	 * Interface that defines a class encapsulating all that is required to build an ImageServer.
	 * <p>
	 * Instances should be sufficiently lightweight that they can be easily serialized to/from JSON 
	 * for storage within projects.
	 * <p>
	 * Instances should also be immutable.
	 * 
	 * @param <T>
	 */
	public static interface ServerBuilder<T> {
		
		/**
		 * Build a new ImageServer instance.
		 * @return
		 * @throws Exception
		 */
		public ImageServer<T> build() throws Exception;
		
		/**
		 * Get a list of URIs required by this builder.
		 * The purpose is to identify resources that are required.
		 * @return
		 * 
		 * @see #updateURIs(Map)
		 */
		public Collection<URI> getURIs();
		
		/**
		 * Update the URIs required by this builder.
		 * The purpose is to handle resources that may have moved (e.g. files).
		 * Because ServerBuilder should be immutable, this returns a new builder.
		 * @param updateMap
		 * @return
		 * 
		 * @see #getURIs()
		 */
		public ServerBuilder<T> updateURIs(Map<URI, URI> updateMap);
		
	}
	
	
	/**
	 * Abstract ServerBuilder implementation that handles metadata.
	 *
	 * @param <T>
	 */
	static abstract class AbstractServerBuilder<T> implements ServerBuilder<T> {
		
		private ImageServerMetadata metadata;
		
		AbstractServerBuilder(ImageServerMetadata metadata) {
			this.metadata = metadata;
		}
		
		protected abstract ImageServer<T> buildOriginal() throws Exception;
		
		protected ImageServerMetadata getMetadata() {
			return metadata;
		}
		
		@Override
		public ImageServer<T> build() throws Exception {
			var server = buildOriginal();
			if (server == null)
				return null;
			if (metadata != null)
				server.setMetadata(metadata);
			return server;
		}
		
	}
	
	
	/**
	 * Helper class to summarize which {@linkplain ImageServer ImageServers} can be build by a particular {@link ImageServerBuilder} 
	 * for a given URI, and a level of confidence.
	 * This may be used to select which {@link ImageServerBuilder} is used to open the image(s).
	 * 
	 * @param <T>
	 */
	public static class UriImageSupport<T> {
		
		private Class<? extends ImageServerBuilder<T>> providerClass;
		private float supportLevel;
		
		private List<ServerBuilder<T>> builders = new ArrayList<>();
		
		@Override
		public String toString() {
			return getClass().getSimpleName() + " (" + providerClass + ") support=" + supportLevel + ", builders=" + builders.size();
		}
		
		UriImageSupport(Class<? extends ImageServerBuilder<T>> providerClass, float supportLevel, Collection<ServerBuilder<T>> builders) {
			this.providerClass = providerClass;
			this.supportLevel = supportLevel;
			this.builders = Collections.unmodifiableList(new ArrayList<>(builders));
		}
		
		/**
		 * Create a {@link UriImageSupport} for (possibly multiple) images that can be read from a single URI.
		 * @param <T>
		 * @param providerClass
		 * @param supportLevel
		 * @param builders
		 * @return
		 */
		public static <T> UriImageSupport<T> createInstance(Class<? extends ImageServerBuilder<T>> providerClass, float supportLevel, Collection<ServerBuilder<T>> builders) {
			return new UriImageSupport<>(providerClass, supportLevel, builders);
		}
		
		/**
		 * Create a {@link UriImageSupport} for a single image that can be read from a single URI.
		 * @param <T>
		 * @param providerClass
		 * @param supportLevel
		 * @param builder
		 * @return
		 */
		public static <T> UriImageSupport<T> createInstance(Class<? extends ImageServerBuilder<T>> providerClass, float supportLevel, ServerBuilder<T> builder) {
			return new UriImageSupport<>(providerClass, supportLevel, Collections.singletonList(builder));
		}
		
		/**
		 * Get the class of the associated {@link ImageServerBuilder}.
		 * @return
		 */
		public Class<? extends ImageServerBuilder<T>> getProviderClass() {
			return providerClass;
		}
		
		/**
		 * Get a list of {@linkplain ServerBuilder ServerBuilders}, one for each image that can be read based on the specified URI. 
		 * For a 'simple' file that contains a single image, a singleton list should be returned.
		 * @return
		 */
		public List<ServerBuilder<T>> getBuilders() {
			return builders;
		}
		
		/**
		 * Estimated 'support level' for a given file path, where support level is a summary of the likelihood that
		 * pixel values and metadata will be returned correctly and in a way that achieves good performance.
		 * <p>
		 * The support level should be a value between 0 and 4.  The following is a guide to its interpretation:
		 * <ul>
		 * <li>4 - 'ideal' support, e.g. the image was written by the library behind the ImageServer</li>
		 * <li>3 - good support</li>
		 * <li>2 - unknown support, i.e. worth a try</li>
		 * <li>1 - partial/poor support, i.e. there are known limitations and all higher-scoring possibilities should be tried first</li>
		 * <li>0 - no support</li>
		 * </ul>
		 * The use of floating point enables subclasses to make more subtle evaluations of performance, e.g. if an ImageServer
		 * is particularly strong for RGB images, but falls short of guaranteeing ideal performance.
		 * <p>
		 * In practice, this is used to rank potential builders so that the 'best' ones
		 * are tried first for new image paths, and those with 0 support may be ignored.
		 * @return
		 */
		public float getSupportLevel() {
			return supportLevel;
		}
		
	}
	
	
	/**
	 * Default {@link ServerBuilder} that requires a URI and (optional) array of String arguments to create an {@link ImageServer} 
	 * with the help of a {@link ImageServerBuilder}.
	 *
	 * @param <T>
	 */
	public static class DefaultImageServerBuilder<T> extends AbstractServerBuilder<T> {
		
		private String providerClassName;
		private URI uri;
		private String[] args;
		
		private DefaultImageServerBuilder(String providerClassName, URI uri, String[] args, ImageServerMetadata metadata) {
			super(metadata);
			this.providerClassName = providerClassName;
			this.uri = uri;
			this.args = args;
		}

		private DefaultImageServerBuilder(Class<? extends ImageServerBuilder<T>> providerClass, URI uri, String[] args, ImageServerMetadata metadata) {
			this(providerClass.getName(), uri, args, metadata);
		}
		
		/**
		 * Create a {@link ServerBuilder} that reads an image from a URI and args, and uses the specified metadata (possibly replacing the default metadata).
		 * @param <T>
		 * @param providerClass
		 * @param metadata
		 * @param uri
		 * @param args
		 * @return
		 */
		public static <T> ServerBuilder<T> createInstance(Class<? extends ImageServerBuilder<T>> providerClass, ImageServerMetadata metadata, URI uri, String...args) {
			return new DefaultImageServerBuilder<>(providerClass, uri, args, metadata);
		}

		/**
		 * Create a {@link ServerBuilder} that reads an image from a URI and args, using the default server metadata.
		 * @param <T>
		 * @param providerClass
		 * @param uri
		 * @param args
		 * @return
		 */
		public static <T> ServerBuilder<T> createInstance(Class<? extends ImageServerBuilder<T>> providerClass, URI uri, String...args) {
			return createInstance(providerClass, null, uri, args);
		}

		/**
		 * Get the URI used by this builder.
		 * @return
		 */
		public URI getURI() {
			return uri;
		}

		/**
		 * Get the args array. This returns a clone of any original array.
		 * @return
		 */
		public String[] getArgs() {
			return args == null ? null : args.clone();
		}

		@Override
		protected ImageServer<T> buildOriginal() throws Exception {
			for (ImageServerBuilder<?> provider : ImageServerProvider.getInstalledImageServerBuilders()) {
				if (provider.getClass().getName().equals(providerClassName)) {
					ImageServer<T> server = (ImageServer<T>)provider.buildServer(uri, args);
					if (server != null)
						return server;
				}
			}
			throw new IOException("Unable to build ImageServer for " + uri + " (args=" + Arrays.asList(args) + ")");
		}
		
		@Override
		public Collection<URI> getURIs() {
			if (uri == null)
				return Collections.emptyList();
			return Collections.singletonList(uri);
		}

		@Override
		public ServerBuilder<T> updateURIs(Map<URI, URI> updateMap) {
			if (uri == null)
				return this;
			URI uriNew = updateMap.getOrDefault(uri, null);
			if (uriNew == null)
				return this;
			return new DefaultImageServerBuilder<>(providerClassName, uriNew, args, getMetadata());
		}
		
		@Override
		public String toString() {
			return String.format("DefaultImageServerBuilder (classname=%s, uri=%s, args=%s)", providerClassName, uri.toString(), String.join(", ", args));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(args);
			result = prime * result + ((providerClassName == null) ? 0 : providerClassName.hashCode());
			result = prime * result + ((uri == null) ? 0 : uri.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DefaultImageServerBuilder other = (DefaultImageServerBuilder) obj;
			if (!Arrays.equals(args, other.args))
				return false;
			if (providerClassName == null) {
				if (other.providerClassName != null)
					return false;
			} else if (!providerClassName.equals(other.providerClassName))
				return false;
			if (uri == null) {
				if (other.uri != null)
					return false;
			} else if (!uri.equals(other.uri))
				return false;
			if (!Objects.equals(getMetadata(), other.getMetadata()))
				return false;
			return true;
		}
		
	}
		
	
}
